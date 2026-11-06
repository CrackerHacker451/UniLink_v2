'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createGate } = require('../gate');
const { signRequest } = require('@worldcoin/idkit-core/signing');
const { hashSignal } = require('@worldcoin/idkit-core/hashing');
const { Nullifier } = require('@worldcoin/idkit-core/nullifier');

// ---- tiny in-memory Firestore / Auth fakes ----
function makeDb() {
  const store = new Map();
  const key = (c, id) => c + '/' + id;
  const docRef = (c, id) => ({
    id, _k: key(c, id),
    async get() { const v = store.get(key(c, id)); return { exists: v !== undefined, data: () => (v ? { ...v } : undefined) }; },
    async set(d) { store.set(key(c, id), { ...d }); },
    async update(d) { store.set(key(c, id), { ...store.get(key(c, id)), ...d }); },
  });
  return {
    store,
    collection: (c) => ({ doc: (id) => docRef(c, id) }),
    async runTransaction(fn) {
      const writes = [];
      const tx = {
        get: async (ref) => ref.get(),
        set: (ref, d) => writes.push(() => ref.set(d)),
        update: (ref, d) => writes.push(() => ref.update(d)),
      };
      await fn(tx);
      for (const w of writes) await w();
    },
  };
}
function makeAuth(users) {
  return {
    claims: {},
    async verifyIdToken(t) { if (!users[t]) throw new Error('bad token'); return { uid: users[t] }; },
    async getUser(uid) { return { uid, customClaims: this.claims[uid] }; },
    async setCustomUserClaims(uid, c) { this.claims[uid] = c; },
  };
}
const res = () => { const r = { code: 0, body: null, status(c) { r.code = c; return r; }, json(b) { r.body = b; return r; } }; return r; };

const KEY = '11'.repeat(32);
function setup(opts = {}) {
  const db = makeDb();
  const auth = makeAuth({ tokenA: 'userA', tokenB: 'userB' });
  let portalImpl = opts.portal || (async () => ({ ok: true, json: async () => ({ success: true, environment: 'production' }) }));
  let now = 1_000_000;
  const gate = createGate({
    db, auth,
    fetchFn: (...a) => portalImpl(...a),
    signRequest, hashSignal, Nullifier,
    serverTimestamp: () => 'TS',
    config: { signingKeyHex: KEY, appId: 'app_x', rpId: 'rp_x', now: () => now },
  });
  return { db, auth, gate, setPortal: (f) => { portalImpl = f; }, tick: (ms) => { now += ms; } };
}
async function begin(s, token = 'tokenA') {
  const r1 = res();
  await s.gate.startVerification({ method: 'POST', headers: { authorization: 'Bearer ' + token } }, r1);
  assert.equal(r1.code, 200);
  const pairingId = r1.body.pairingId;
  const r2 = res();
  await s.gate.rpSignature({ method: 'POST', body: { pairingId } }, r2);
  assert.equal(r2.code, 200);
  return { pairingId, rp: r2.body };
}
const NULLIFIER = '0x04e5f6a1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccdd';
function proof(rp, uid, over = {}) {
  return {
    protocol_version: '4.0', nonce: rp.rpContext.nonce, action: rp.action, environment: 'production',
    responses: [{ identifier: 'selfie', signal_hash: hashSignal(uid), proof: ['0x1'], nullifier: NULLIFIER, issuer_schema_id: 11, ...(over.resp || {}) }],
    ...over.top,
  };
}
const verify = async (s, pairingId, idkitResult) => { const r = res(); await s.gate.verifyProof({ method: 'POST', body: { pairingId, idkitResult } }, r); return r; };

test('startVerification rejects missing / bad auth', async () => {
  const s = setup();
  const r = res(); await s.gate.startVerification({ method: 'POST', headers: {} }, r); assert.equal(r.code, 401);
  const r2 = res(); await s.gate.startVerification({ method: 'POST', headers: { authorization: 'Bearer nope' } }, r2); assert.equal(r2.code, 401);
});

test('happy path: real proof -> claim, nullifier, user doc', async () => {
  const s = setup();
  await s.db.collection('users').doc('userA').set({ name: 'A' });
  const { pairingId, rp } = await begin(s);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 200); assert.equal(r.body.success, true);
  assert.equal(s.auth.claims.userA.humanVerified, true);
  assert.equal((await s.db.collection('users').doc('userA').get()).data().worldIdVerified, true);
  assert.ok(s.db.store.size > 0);
});

test('FAIL CLOSED: World ID unreachable -> not verified', async () => {
  const s = setup({ portal: async () => { throw new Error('network down'); } });
  const { pairingId, rp } = await begin(s);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 502); assert.equal(s.auth.claims.userA, undefined);
});

test('FAIL CLOSED: portal says HTTP 400 -> not verified', async () => {
  const s = setup({ portal: async () => ({ ok: false, json: async () => ({}) }) });
  const { pairingId, rp } = await begin(s);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 400); assert.equal(s.auth.claims.userA, undefined);
});

test('FAIL CLOSED: portal 200 but success:false -> not verified', async () => {
  const s = setup({ portal: async () => ({ ok: true, json: async () => ({ success: false, environment: 'production' }) }) });
  const { pairingId, rp } = await begin(s);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 400); assert.equal(s.auth.claims.userA, undefined);
});

test('FAIL CLOSED: portal answers with a different environment -> not verified', async () => {
  const s = setup({ portal: async () => ({ ok: true, json: async () => ({ success: true, environment: 'staging' }) }) });
  const { pairingId, rp } = await begin(s);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 400); assert.equal(s.auth.claims.userA, undefined);
});

test('fabricated / client-made payload is rejected before reaching the portal', async () => {
  let called = false;
  const s = setup({ portal: async () => { called = true; return { ok: true, json: async () => ({ success: true, environment: 'production' }) }; } });
  const { pairingId } = await begin(s);
  const fake = { protocol_version: '4.0', nonce: 'nonce_nl_123', action: 'unilink-human-gate-v1', environment: 'production',
    responses: [{ identifier: 'selfie', signal_hash: '0x0', proof: ['0x1a2b'], nullifier: '0x04e5f6_userA_nullifier' }] };
  const r = await verify(s, pairingId, fake);
  assert.equal(r.code, 400); assert.equal(called, false); assert.equal(s.auth.claims.userA, undefined);
});

test('rejects wrong nonce, wrong env, wrong action, wrong credential, wrong signal', async () => {
  for (const over of [
    { top: { nonce: 'other' } }, { top: { environment: 'staging' } }, { top: { action: 'other' } },
    { resp: { identifier: 'orb' } }, { resp: { signal_hash: hashSignal('someone-else') } },
    { top: { protocol_version: '3.0' } }, { top: { responses: [] } },
  ]) {
    const s = setup(); const { pairingId, rp } = await begin(s);
    const r = await verify(s, pairingId, proof(rp, 'userA', over));
    assert.equal(r.code, 400, JSON.stringify(over)); assert.equal(s.auth.claims.userA, undefined);
  }
});

test('same human cannot verify a second account', async () => {
  const s = setup();
  const a = await begin(s, 'tokenA'); assert.equal((await verify(s, a.pairingId, proof(a.rp, 'userA'))).code, 200);
  const b = await begin(s, 'tokenB'); const r = await verify(s, b.pairingId, proof(b.rp, 'userB'));
  assert.equal(r.code, 409); assert.equal(s.auth.claims.userB, undefined);
});

test('a used session cannot be replayed', async () => {
  const s = setup();
  const { pairingId, rp } = await begin(s);
  assert.equal((await verify(s, pairingId, proof(rp, 'userA'))).code, 200);
  assert.equal((await verify(s, pairingId, proof(rp, 'userA'))).code, 400);
});

test('expired session is rejected', async () => {
  const s = setup();
  const { pairingId, rp } = await begin(s);
  s.tick(11 * 60 * 1000);
  const r = await verify(s, pairingId, proof(rp, 'userA'));
  assert.equal(r.code, 400); assert.equal(s.auth.claims.userA, undefined);
});

test('garbage input is rejected', async () => {
  const s = setup();
  for (const body of [undefined, {}, { pairingId: 'x' }, { pairingId: 'a'.repeat(32), idkitResult: {} }]) {
    const r = res(); await s.gate.verifyProof({ method: 'POST', body }, r); assert.equal(r.code, 400);
  }
  const r = res(); await s.gate.verifyProof({ method: 'GET' }, r); assert.equal(r.code, 405);
});

test('already verified users are told so', async () => {
  const s = setup(); s.auth.claims.userA = { humanVerified: true };
  const r = res(); await s.gate.startVerification({ method: 'POST', headers: { authorization: 'Bearer tokenA' } }, r);
  assert.equal(r.body.alreadyVerified, true);
});
