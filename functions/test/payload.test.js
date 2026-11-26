"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { buildPush } = require("../payload");
const input = { receiverUid: "receiver", notificationId: "n1", token: "token", record: { senderId: "sender", type: "chat", isRead: false, message: "private chat" } };
test("background push is high priority data-only and has no private message", () => {
  const p = buildPush(input);
  assert.equal(p.android.priority, "high");
  assert.equal(p.data.recipientUid, "receiver");
  assert.equal(p.data.notificationId, "n1");
  assert.equal(p.notification, undefined);
  assert.ok(!JSON.stringify(p).includes("private chat"));
});
test("self, read and unregistered notifications are skipped", () => {
  assert.equal(buildPush({ ...input, receiverUid: "sender" }), null);
  assert.equal(buildPush({ ...input, token: "" }), null);
  assert.equal(buildPush({ ...input, record: { ...input.record, isRead: true } }), null);
});
test("call alerts require validated signaling and expire quickly", () => {
  const call = { ...input, record: { ...input.record, type: "video_call", targetId: "call1" } };
  assert.equal(buildPush(call).data.type, "activity");
  const p = buildPush({ ...call, callAllowed: true });
  assert.equal(p.data.targetId, "call1");
  assert.equal(p.android.ttl, 60000);
});
