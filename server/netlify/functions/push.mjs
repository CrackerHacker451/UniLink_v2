import admin from 'firebase-admin';
import pushModule from '../../push.js';
const { createPush } = pushModule;
let cached;
function getPush() {
  if (cached) return cached;
  if (!process.env.FIREBASE_SERVICE_ACCOUNT) throw new Error('missing configuration');
  if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) });
  const cap = Number(process.env.PUSH_DAILY_LIMIT || '1000');
  if (!Number.isSafeInteger(cap) || cap < 1 || cap > 10000) throw new Error('invalid limit');
  cached = createPush({
    db: admin.firestore(), auth: admin.auth(), appCheck: admin.appCheck(), messaging: admin.messaging(),
    requireAppCheck: process.env.PUSH_APP_CHECK_ENFORCE !== 'false',
    appId: process.env.FIREBASE_APP_ID || undefined, dailyLimit: cap
  });
  return cached;
}
export function createHandler(getService) {
  const json = (status, code) => new Response(JSON.stringify({ success: status === 200, result: code }), {
    status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...(status === 429 ? { 'retry-after': '60' } : {}) }
  });
  return async req => {
    if (req.method !== 'POST') return json(405, 'method_not_allowed');
    if (!/^application\/json(?:\s*;|$)/i.test(req.headers.get('content-type') || '')) return json(415, 'unsupported_media_type');
    if (Number(req.headers.get('content-length')) > 4096) return json(413, 'too_large');
    let body;
    try {
      const raw = await req.text();
      if (Buffer.byteLength(raw) > 4096) return json(413, 'too_large');
      body = JSON.parse(raw);
    } catch (_) { return json(400, 'bad_json'); }
    try {
      const result = await getService()({ authorization: req.headers.get('authorization'), appCheckToken: req.headers.get('x-firebase-appcheck'), body });
      return json(result.status, result.code);
    } catch (_) {
      console.error('push endpoint unavailable');
      return json(503, 'unavailable');
    }
  };
}
export default createHandler(getPush);
export const config = { path: '/api/sendPush' };
