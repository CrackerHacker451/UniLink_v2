"use strict";
const CHAT_TYPES = new Set(["chat", "duel", "token"]);
function buildPush({ receiverUid, notificationId, record, token, callAllowed = false }) {
  if (!record || record.isRead !== false || typeof record.senderId !== "string" ||
      !record.senderId || record.senderId === receiverUid ||
      typeof token !== "string" || !token) return null;
  const isCall = record.type === "video_call" && callAllowed;
  const data = {
    recipientUid: receiverUid, notificationId,
    type: isCall ? "video_call" : "activity", title: "UniLink",
    body: isCall ? "Incoming campus video call" :
      CHAT_TYPES.has(record.type) ? "You have new chat activity." : "You have new campus activity."
  };
  if (isCall) {
    data.senderId = record.senderId;
    data.targetId = record.targetId;
  }
  return { token, data, android: { priority: "high", ttl: isCall ? 60000 : 3600000 } };
}
module.exports = { buildPush, CHAT_TYPES };
