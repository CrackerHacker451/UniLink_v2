import admin from 'firebase-admin';
import { signRequest } from '@worldcoin/idkit-core/signing';
import { hashSignal } from '@worldcoin/idkit-core/hashing';
import { Nullifier } from '@worldcoin/idkit-core/nullifier';
import gateModule from '../../gate.js';

const { createGate } = gateModule;
const ROUTES = ['startVerification', 'rpSignature', 'verifyProof'];

let cached;
function realGate() {
  if (cached) return cached;
  const env = process.env;
  for (const k of ['FIREBASE_SERVICE_ACCOUNT', 'WORLD_APP_ID', 'WORLD_RP_ID', 'WORLD_RP_SIGNING_KEY']) {
    if (!env[k]) throw new Error('missing_env_' + k);
  }
  if (!admin.apps.length) {
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT)) });
  }
  cached = createGate({
    db: admin.firestore(),
    auth: admin.auth(),
    appCheck: admin.appCheck(),
    fetchFn: fetch,
    signRequest, hashSignal, Nullifier,
    serverTimestamp: () => admin.firestore.FieldValue.serverTimestamp(),
    config: {
      signingKeyHex: env.WORLD_RP_SIGNING_KEY,
      appId: env.WORLD_APP_ID,
      rpId: env.WORLD_RP_ID,
      environment: env.WORLD_ENVIRONMENT || 'production',
      requireAppCheck: env.APP_CHECK_ENFORCE !== 'false',
      appCheckAppId: env.FIREBASE_APP_ID || null,
    },
  });
  return cached;
}

export function createHandler(getGate) {
  const json = (status, body) => new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', 'cache-control': 'no-store' },
  });
  return async (req) => {
    const route = new URL(req.url).pathname.replace(/^\/api\//, '');
    if (!ROUTES.includes(route)) return json(404, { success: false, error: 'not_found' });
    let body = {};
    if (req.method === 'POST') {
      try { body = await req.json(); } catch (_) { return json(400, { success: false, error: 'bad_json' }); }
    }
    let out = { code: 500, body: { success: false, error: 'server_error' } };
    const res = {
      status(c) { out = { code: c, body: null }; return res; },
      json(b) { out.body = b; return res; },
    };
    try {
      await getGate()[route]({ method: req.method, headers: Object.fromEntries(req.headers), body }, res);
    } catch (e) {
      console.error('gate error', route, e && e.message); // fail closed: never leak details, never succeed
      return json(500, { success: false, error: 'server_error' });
    }
    return json(out.code, out.body);
  };
}

export default createHandler(realGate);
export const config = { path: ROUTES.map((r) => '/api/' + r) };
