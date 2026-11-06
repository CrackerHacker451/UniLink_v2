'use strict';
/**
 * Fail-closed World ID human gate.
 *
 * Nothing in here ever "succeeds" on an error, a timeout, a malformed payload or an
 * unreachable World ID service. The ONLY way a user gets the `humanVerified` custom claim
 * is a proof that the World ID Developer Portal itself confirmed, for this user's own
 * one-time challenge, from a human nobody else has used before.
 */
const crypto = require('crypto');

const PAIRING_TTL_MS = 10 * 60 * 1000;

function createGate({ db, auth, appCheck, fetchFn, signRequest, hashSignal, Nullifier, serverTimestamp, config }) {
  const c = Object.assign({
    action: 'unilink-human-gate-v1',
    environment: 'production',
    acceptedIdentifiers: ['selfie', 'proof_of_human'],
    enforceSignal: true,
    requireAppCheck: true, // only the genuine, untampered app may start a verification
    appCheckAppId: null,
    verifyBase: 'https://developer.world.org/api/v4/verify/',
    now: () => Date.now(),
  }, config);

  const send = (res, code, body) => res.status(code).json(body);
  const bad = (res, code, error) => { console.error('gate_reject', code, error); return send(res, code, { success: false, error }); };

  const header = (req, name) => {
    const h = req.headers || {};
    const k = Object.keys(h).find((x) => x.toLowerCase() === name);
    return k ? h[k] : '';
  };

  /** App Check: scripts, agents calling our API directly, emulators and modified apps have no valid token. */
  async function appIsGenuine(req) {
    if (!c.requireAppCheck) return true;
    const token = header(req, 'x-firebase-appcheck');
    if (!token || !appCheck) return false; // fail closed, including when App Check is not configured
    try {
      const r = await appCheck.verifyToken(token);
      return !c.appCheckAppId || r.appId === c.appCheckAppId;
    } catch (_) { return false; }
  }

  async function uidFromBearer(req) {
    const h = (req.headers && (req.headers.authorization || req.headers.Authorization)) || '';
    const m = /^Bearer (.+)$/.exec(h);
    if (!m) return null;
    try { return (await auth.verifyIdToken(m[1])).uid; } catch (_) { return null; }
  }

  async function loadPairing(pairingId) {
    if (typeof pairingId !== 'string' || !/^[a-f0-9]{32}$/.test(pairingId)) return null;
    const ref = db.collection('worldPairings').doc(pairingId);
    const snap = await ref.get();
    if (!snap.exists) return null;
    const p = snap.data();
    if (p.used === true || c.now() > p.expiresAtMs) return null;
    return { ref, data: p };
  }

  /** App -> server (Firebase ID token). Starts a one-time verification session. */
  async function startVerification(req, res) {
    if (req.method !== 'POST') return bad(res, 405, 'method_not_allowed');
    if (!(await appIsGenuine(req))) return bad(res, 401, 'app_check_failed');
    const uid = await uidFromBearer(req);
    if (!uid) return bad(res, 401, 'unauthenticated');
    const user = await auth.getUser(uid);
    if (user.customClaims && user.customClaims.humanVerified === true) {
      return send(res, 200, { success: true, alreadyVerified: true });
    }
    const pairingId = crypto.randomBytes(16).toString('hex');
    await db.collection('worldPairings').doc(pairingId).set({
      uid, used: false, nonce: null, createdAt: serverTimestamp(), expiresAtMs: c.now() + PAIRING_TTL_MS,
    });
    return send(res, 200, { success: true, pairingId });
  }

  /** Verification page -> server. Signs the World ID request (the signing key never leaves here). */
  async function rpSignature(req, res) {
    if (req.method !== 'POST') return bad(res, 405, 'method_not_allowed');
    const pairing = await loadPairing(req.body && req.body.pairingId);
    if (!pairing) return bad(res, 400, 'invalid_or_expired_session');
    const { sig, nonce, createdAt, expiresAt } = signRequest({ signingKeyHex: c.signingKeyHex, action: c.action });
    await pairing.ref.update({ nonce, rpExpiresAt: expiresAt });
    return send(res, 200, {
      success: true,
      appId: c.appId, rpId: c.rpId, action: c.action, environment: c.environment,
      signal: pairing.data.uid, // binds the proof to this exact account
      rpContext: { rp_id: c.rpId, nonce, created_at: createdAt, expires_at: expiresAt, signature: sig },
    });
  }

  /** Verification page -> server. Verifies the proof and, only on success, grants the claim. */
  async function verifyProof(req, res) {
    if (req.method !== 'POST') return bad(res, 405, 'method_not_allowed');
    const body = req.body || {};
    const pairing = await loadPairing(body.pairingId);
    if (!pairing) return bad(res, 400, 'invalid_or_expired_session');
    const uid = pairing.data.uid;

    const r = body.idkitResult;
    if (!r || typeof r !== 'object' || !Array.isArray(r.responses) || r.responses.length < 1) return bad(res, 400, 'malformed_proof');
    if (r.protocol_version !== '4.0') return bad(res, 400, 'unsupported_protocol');
    if (r.environment !== c.environment) return bad(res, 400, 'wrong_environment');
    if (r.action !== c.action) return bad(res, 400, 'wrong_action');
    if (!pairing.data.nonce || r.nonce !== pairing.data.nonce) return bad(res, 400, 'challenge_mismatch');

    const resp = r.responses[0];
    if (!resp || typeof resp !== 'object' || !c.acceptedIdentifiers.includes(resp.identifier)) return bad(res, 400, 'credential_not_accepted');
    if (c.enforceSignal && String(resp.signal_hash || '').toLowerCase() !== String(hashSignal(uid)).toLowerCase()) return bad(res, 400, 'signal_mismatch');

    let nullifierDec;
    try { nullifierDec = Nullifier.fromHex(String(resp.nullifier)).toBigInt().toString(); } catch (_) { return bad(res, 400, 'invalid_nullifier'); }

    // Ask World ID itself. Any failure, anywhere, means NOT verified.
    let portal;
    try {
      const upstream = await fetchFn(c.verifyBase + encodeURIComponent(c.rpId), {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(Object.assign({}, r, { environment: c.environment })),
      });
      if (!upstream.ok) { try { console.error('portal_reject', upstream.status, String(await upstream.text()).slice(0, 400)); } catch (_) {} return bad(res, 400, 'verification_failed'); }
      portal = await upstream.json();
    } catch (_) {
      return bad(res, 502, 'verification_unavailable');
    }
    if (!portal || portal.success !== true || portal.environment !== c.environment) { try { console.error('portal_reject_body', JSON.stringify(portal).slice(0, 400)); } catch (_) {} return bad(res, 400, 'verification_failed'); }

    // One human = one account.
    const nullRef = db.collection('worldNullifiers').doc(c.action + '_' + nullifierDec);
    const userRef = db.collection('users').doc(uid);
    const humanRef = db.collection('worldHumanByUid').doc(uid);
    let conflict = false;
    try {
      await db.runTransaction(async (tx) => {
        const [nSnap, pSnap, uSnap] = await Promise.all([tx.get(nullRef), tx.get(pairing.ref), tx.get(userRef)]);
        if (!pSnap.exists || pSnap.data().used === true) throw new Error('session_used');
        if (nSnap.exists && nSnap.data().uid !== uid) { conflict = true; throw new Error('duplicate_human'); }
        tx.set(nullRef, { uid, action: c.action, identifier: resp.identifier, verifiedAt: serverTimestamp() });
        tx.set(humanRef, { uid, identifier: resp.identifier, verifiedAt: serverTimestamp() });
        tx.update(pairing.ref, { used: true });
        if (uSnap.exists) {
          tx.update(userRef, { worldIdVerified: true, worldIdBadge: 'Human Verified 🌐', worldIdCredential: resp.identifier, lastVerifiedAt: serverTimestamp() });
        }
      });
    } catch (e) {
      if (conflict) return bad(res, 409, 'human_already_linked');
      return bad(res, 400, 'session_used');
    }

    try {
      const existing = (await auth.getUser(uid)).customClaims || {};
      await auth.setCustomUserClaims(uid, Object.assign({}, existing, { humanVerified: true, humanVerifiedAt: c.now() }));
    } catch (_) {
      return bad(res, 500, 'claim_failed'); // safe to retry: the nullifier is already mapped to this uid
    }
    return send(res, 200, { success: true });
  }

  return { startVerification, rpSignature, verifyProof };
}

module.exports = { createGate, PAIRING_TTL_MS };
