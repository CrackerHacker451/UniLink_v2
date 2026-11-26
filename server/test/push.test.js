'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createPush, authorize, nextCounter, payload } = require('../push');
const now = 1720000000000;
const notice = { notificationId: 'n', senderId: 'sender', type: 'chat', targetId: 'room',
  isRead: false, timestamp: { toMillis: () => now }, message: 'private text' };
function setup({ record = notice, user = { uid: 'sender', humanVerified: true }, receiver = {}, app = 'app', errorCode } = {}) {
  const map = new Map([
    ['users/sender', {}], ['users/receiver', receiver],
    ['users/receiver/notifications/n', record], ['pushRegistrations/receiver', { token: 'device' }],
    ['chats/room', { participants: ['sender', 'receiver'] }]
  ]);
  function ref(path) {
    return { path, doc: id => ref(path + '/' + id), collection: name => ref(path + '/' + name) };
  }
  const db = { collection: name => ref(name), async runTransaction(fn) {
    const writes = [];
    const result = await fn({
      get: async r => ({ data: () => map.get(r.path) }),
      set: (r, v) => writes.push(() => map.set(r.path, v)),
      update: (r, v) => writes.push(() => map.set(r.path, { ...map.get(r.path), ...v })),
      delete: r => writes.push(() => map.delete(r.path))
    });
    writes.forEach(w => w());
    return result;
  } };
  const sent = [];
  const send = createPush({ db, auth: { async verifyIdToken(t, revocation) {
    assert.equal(revocation, true); if (t !== 'valid') throw Error('bad token'); return user;
  } }, appCheck: { async verifyToken(t) { if (t !== 'good') throw Error('bad app'); return { appId: app }; } },
  messaging: { async send(p) { sent.push(p); if (errorCode) throw Object.assign(Error('send'), { code: errorCode }); } },
  now: () => now, appId: 'app', dailyLimit: 2 });
  const req = { authorization: 'Bearer valid', appCheckToken: 'good', body: { receiverUid: 'receiver', notificationId: 'n' } };
  return { send, req, map, sent };
}
test('verified request sends only once with private generic payload', async () => {
  const s = setup();
  assert.deepEqual(await s.send(s.req), { status: 200, code: 'sent' });
  assert.equal(s.sent.length, 1);
  assert.equal(s.sent[0].data.recipientUid, 'receiver');
  assert.equal(s.sent[0].android.priority, 'high');
  assert.ok(!JSON.stringify(s.sent[0]).includes('private text'));
  assert.equal((await s.send(s.req)).code, 'already_attempted');
  assert.equal(s.sent.length, 1);
});
test('auth, human gate, App Check, malformed input and sender spoofing fail closed', async () => {
  const s = setup();
  assert.equal((await s.send({ ...s.req, authorization: 'Bearer invalid' })).status, 401);
  assert.equal((await s.send({ ...s.req, appCheckToken: '' })).status, 401);
  assert.equal((await s.send({ ...s.req, body: { ...s.req.body, token: 'victim' } })).status, 400);
  assert.equal((await s.send({ ...s.req, body: { ...s.req.body, receiverUid: '../x' } })).status, 400);
  assert.equal((await s.send({ ...s.req, body: { ...s.req.body, notificationId: 'x\\y' } })).status, 400);
  const unverified = setup({ user: { uid: 'sender' } });
  assert.equal((await unverified.send(unverified.req)).status, 403);
  const wrongApp = setup({ app: 'different' });
  assert.equal((await wrongApp.send(wrongApp.req)).status, 403);
  const spoof = setup({ record: { ...notice, senderId: 'someone_else' } });
  assert.equal((await spoof.send(spoof.req)).status, 403);
  assert.equal(s.sent.length, 0);
});
test('recipient block and unrelated chat prevent delivery', async () => {
  const blocked = setup({ receiver: { blockedUsers: ['sender'] } });
  assert.equal((await blocked.send(blocked.req)).status, 403);
  const nonmember = setup();
  nonmember.map.set('chats/room', { participants: ['sender', 'other'] });
  assert.equal((await nonmember.send(nonmember.req)).status, 403);
});
test('rate limits persist across separate notification IDs', async () => {
  const s = setup();
  for (const id of ['n', 'n2', 'n3']) {
    s.map.set('users/receiver/notifications/' + id, { ...notice, notificationId: id });
  }
  const send = id => s.send({ ...s.req, body: { ...s.req.body, notificationId: id } });
  assert.equal((await send('n')).code, 'sent');
  assert.equal((await send('n2')).code, 'sent');
  assert.equal((await send('n3')).status, 429);
  assert.equal(s.sent.length, 2);
});
test('invalid token cleanup leaves a replacement token untouched and does not retry', async () => {
  const s = setup({ errorCode: 'messaging/registration-token-not-registered' });
  assert.equal((await s.send(s.req)).code, 'no_device');
  assert.equal(s.map.has('pushRegistrations/receiver'), false);
  assert.equal((await s.send(s.req)).code, 'already_attempted');
});
test('expired notifications and unauthorized admin or call alerts are denied', () => {
  const args = { user: { uid: 'sender' }, sender: {}, receiver: {}, receiverUid: 'receiver', notificationId: 'n', now, record: notice };
  assert.equal(authorize({ ...args, now: now + 600001, target: { participants: ['sender','receiver'] } }), false);
  assert.equal(authorize({ ...args, record: { ...notice, type: 'system_update' } }), false);
  assert.equal(authorize({ ...args, record: { ...notice, type: 'reward' }, user: { uid: 'sender', admin: true } }), true);
  const call = { ...notice, type: 'video_call' };
  assert.equal(authorize({ ...args, record: call, target: { callerId: 'sender', calleeId: 'other', offer: {}, status: 'active' } }), false);
  assert.equal(authorize({ ...args, record: call, target: { callerId: 'sender', calleeId: 'receiver', offer: {}, status: 'active' } }), true);
  assert.equal(payload('receiver', 'n', call, 'device').android.ttl, 60000);
});
test('fixed rate windows reset correctly', () => {
  const c = nextCounter(undefined, now, 1, 2);
  assert.equal(nextCounter(c, now, 1, 2), null);
  const d = nextCounter(c, now + 60000, 1, 2);
  assert.equal(d.d, 2);
  assert.equal(nextCounter(d, now + 120000, 1, 2), null);
  assert.equal(nextCounter(d, now + 86400000, 1, 2).d, 1);
});
