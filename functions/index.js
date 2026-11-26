"use strict";
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { buildPush, CHAT_TYPES } = require("./payload");

initializeApp();
exports.deliverNotification = onDocumentCreated(
  "users/{receiverUid}/notifications/{notificationId}", async (event) => {
    const record = event.data?.data();
    const { receiverUid, notificationId } = event.params;
    if (!record || record.isRead !== false || typeof record.senderId !== "string" ||
        !record.senderId || record.senderId === receiverUid) return;
    const db = getFirestore();
    const targetId = typeof record.targetId === "string" && record.targetId.length <= 1500 &&
      !record.targetId.includes("/") ? record.targetId : null;
    if (CHAT_TYPES.has(record.type)) {
      if (!targetId) return;
      const chat = (await db.collection("chats").doc(targetId).get()).data();
      if (!Array.isArray(chat?.participants) || !chat.participants.includes(receiverUid) ||
          !chat.participants.includes(record.senderId)) return;
    }
    let callAllowed = false;
    if (record.type === "video_call" && targetId) {
      const call = (await db.collection("calls").doc(targetId).get()).data();
      callAllowed = call?.callerId === record.senderId && call?.status === "active";
    }
    const registration = await db.collection("pushRegistrations").doc(receiverUid).get();
    const token = registration.data()?.token;
    const payload = buildPush({ receiverUid, notificationId, record, token, callAllowed });
    if (!payload) return;
    try {
      await getMessaging().send(payload);
    } catch (error) {
      if (error.code === "messaging/registration-token-not-registered" ||
          error.code === "messaging/invalid-registration-token") {
        await db.runTransaction(async (tx) => {
          const current = await tx.get(registration.ref);
          if (current.data()?.token === token) tx.delete(registration.ref);
        });
        return;
      }
      throw error;
    }
  }
);
