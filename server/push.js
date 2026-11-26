'use strict';
const crypto = require('node:crypto');
const chatTypes = new Set(['chat', 'duel', 'token']);
const adminTypes = new Set(['reward', 'system_update']);
const allowedTypes = new Set([...chatTypes, ...adminTypes, 'video_call', 'vouch']);
const isId = (x) => typeof x === 'string' && x.length > 0 && x.length <= 128 && !/[\/\\\x00-\x1f]/.test(x) && x !== '.' && x !== '..';
const hash = (s) => crypto.createHash('sha256').update(JSON.stringify(s)).digest('hex');
const error = (status, code) => ({ status, code });
const contains = (list, id) => Array.isArray(list) && list.includes(id);
function isAdmin(user, sender) {
  return user.admin === true || (user.email_verified === true &&
    ['laltesh@unilink.app', 'admin@nl-lobby.app'].includes(user.email)) || sender?.isAdmin === true;
}
function authorize({ user, receiverUid, notificationId, record, sender, receiver, target, now }) {
  const uid = user.uid;
  if (!sender || !receiver || uid === receiverUid || !record || record.notificationId !== notificationId ||
      record.senderId !== uid || record.isRead !== false || !allowedTypes.has(record.type)) return false;
  const created = record.timestamp?.toMillis?.();
  if (!Number.isFinite(created) || created > now + 5000 || now - created > (record.type === 'video_call' ? 60000 : 600000)) return false;
  if (contains(receiver.blockedUsers, uid) || contains(sender.blockedUsers, receiverUid)) return false;
  if (chatTypes.has(record.type)) return contains(target?.participants, uid) && contains(target?.participants, receiverUid);
  if (record.type === 'video_call') return target?.callerId === uid && target?.calleeId === receiverUid && target?.status === 'active' && !!target?.offer;
  if (adminTypes.has(record.type)) return isAdmin(user, sender);
  if (record.type === 'vouch') return contains(receiver.linkers, 'star_' + uid) ||
    Object.values(receiver.skillVouches || {}).some((v) => contains(v, uid));
  return false;
}
function nextCounter(previous, now, perMinute, perDay) {
  const minute = Math.floor(now / 60000), day = Math.floor(now / 86400000);
  const m = previous?.minute === minute ? previous.m || 0 : 0;
  const d = previous?.day === day ? previous.d || 0 : 0;
  if (m >= perMinute || d >= perDay) return null;
  return { minute, day, m: m + 1, d: d + 1 };
}
function payload(receiverUid, notificationId, record, token) {
  const call = record.type === 'video_call';
  const data = { recipientUid: receiverUid, notificationId, type: call ? 'video_call' : 'activity',
    title: 'UniLink', body: call ? 'Incoming campus video call' : chatTypes.has(record.type) ?
      'You have new chat activity.' : 'You have new campus activity.' };
  if (call) Object.assign(data, { senderId: record.senderId, targetId: record.targetId });
  return { token, data, android: { priority: 'high', ttl: call ? 60000 : 3600000 } };
}
function createPush({ db, auth, appCheck, messaging, requireAppCheck = true, appId, now = Date.now, dailyLimit = 1000 }) {
  return async function sendPush({ authorization, appCheckToken, body }) {
    const match = typeof authorization === 'string' && /^Bearer ([^\s]+)$/.exec(authorization);
    if (!match) return error(401, 'unauthenticated');
    let user;
    try { user = await auth.verifyIdToken(match[1], true); } catch (_) { return error(401, 'unauthenticated'); }
    if (!isId(user?.uid) || user.humanVerified !== true) return error(403, 'human_verification_required');
    if (requireAppCheck) {
      try {
        if (!appCheckToken) return error(401, 'app_check_required');
        const verified = await appCheck.verifyToken(appCheckToken);
        if (appId && verified.appId !== appId) return error(403, 'wrong_app');
      } catch (_) { return error(401, 'invalid_app_check'); }
    }
    if (!body || !isId(body.receiverUid) || !isId(body.notificationId) ||
        Object.keys(body).some(k => !['receiverUid', 'notificationId'].includes(k))) return error(400, 'invalid_request');
    const { receiverUid, notificationId } = body, uid = user.uid;
    if (receiverUid === uid) return error(403, 'not_authorized');
    const users = db.collection('users');
    const notification = users.doc(receiverUid).collection('notifications').doc(notificationId);
    const registration = db.collection('pushRegistrations').doc(receiverUid);
    const rates = db.collection('pushRateLimits');
    // All reservations are persisted atomically across separate serverless instances.
    const reserved = await db.runTransaction(async tx => {
      const [notice, senderDoc, receiverDoc, reg] = await Promise.all([
        tx.get(notification), tx.get(users.doc(uid)), tx.get(users.doc(receiverUid)), tx.get(registration)
      ]);
      const record = notice.data(), sender = senderDoc.data(), receiver = receiverDoc.data();
      if (!record || record.senderId !== uid) return error(403, 'not_authorized');
      if (record.pushAttemptedAt) return { status: 200, code: 'already_attempted' };
      let target;
      if (chatTypes.has(record.type) || record.type === 'video_call') {
        if (!isId(record.targetId)) return error(403, 'not_authorized');
        target = (await tx.get(db.collection(record.type === 'video_call' ? 'calls' : 'chats').doc(record.targetId))).data();
      }
      const time = now();
      if (!authorize({ user, receiverUid, notificationId, record, sender, receiver, target, now: time })) return error(403, 'not_authorized');
      const token = reg.data()?.token;
      if (typeof token !== 'string' || !token || token.length > 4096) return { status: 200, code: 'no_device' };
      const admin = adminTypes.has(record.type);
      const limits = [
        [rates.doc('sender_' + hash(uid)), admin ? 120 : 30, admin ? 1000 : 200],
        [rates.doc('recipient_' + hash(receiverUid)), 60, 400],
        [rates.doc('pair_' + hash([uid, receiverUid])), record.type === 'video_call' ? 3 : 12, 100],
        [rates.doc('global'), 250, dailyLimit]
      ];
      const snapshots = await Promise.all(limits.map(([ref]) => tx.get(ref)));
      const counters = snapshots.map((snap, i) => nextCounter(snap.data(), time, limits[i][1], limits[i][2]));
      if (counters.some(c => !c)) return error(429, 'rate_limited');
      for (let i = 0; i < limits.length; i++) tx.set(limits[i][0], counters[i]);
      tx.update(notification, { pushAttemptedAt: time });
      return { status: 200, code: 'reserved', record, token };
    });
    if (reserved.code !== 'reserved') return reserved;
    try {
      await messaging.send(payload(receiverUid, notificationId, reserved.record, reserved.token));
      return { status: 200, code: 'sent' };
    } catch (e) {
      if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(e?.code)) {
        await db.runTransaction(async tx => {
          const current = await tx.get(registration);
          if (current.data()?.token === reserved.token) tx.delete(registration);
        });
        return { status: 200, code: 'no_device' };
      }
      return error(502, 'delivery_failed');
    }
  };
}
module.exports = { createPush, authorize, nextCounter, payload };
