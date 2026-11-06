import test from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from '../netlify/functions/api.mjs';

const call = (handler, path, init) => handler(new Request('https://x.netlify.app' + path, init));

test('routes to the gate and returns its status and body', async () => {
  let seen;
  const h = createHandler(() => ({
    startVerification: async (req, res) => { seen = req; res.status(200).json({ success: true, pairingId: 'p' }); },
  }));
  const r = await call(h, '/api/startVerification', { method: 'POST', headers: { Authorization: 'Bearer t' }, body: '{}' });
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { success: true, pairingId: 'p' });
  assert.equal(seen.headers.authorization, 'Bearer t');
});

test('unknown routes and bad JSON are rejected', async () => {
  const h = createHandler(() => ({}));
  assert.equal((await call(h, '/api/other', { method: 'POST', body: '{}' })).status, 404);
  assert.equal((await call(h, '/api/verifyProof', { method: 'POST', body: 'not json' })).status, 400);
});

test('FAIL CLOSED: a crash or missing configuration is a 500, never a success', async () => {
  const boom = createHandler(() => { throw new Error('missing_env_FIREBASE_SERVICE_ACCOUNT'); });
  const r = await call(boom, '/api/verifyProof', { method: 'POST', body: '{}' });
  assert.equal(r.status, 500);
  assert.equal((await r.json()).success, false);
  const silent = createHandler(() => ({ verifyProof: async () => {} })); // a handler that never answers
  const r2 = await call(silent, '/api/verifyProof', { method: 'POST', body: '{}' });
  assert.equal(r2.status, 500);
});
