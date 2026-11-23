const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

exports.notifyChatParticipants = onDocumentCreated(
  "chatThreads/{threadId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message || !message.uid) return;
    const threadSnap = await db.collection("chatThreads").doc(event.params.threadId).get();
    if (!threadSnap.exists) return;
    const participants = (threadSnap.get("participants") || []).filter((uid) => uid !== message.uid);
    if (!participants.length) return;

    const tokenSnapshots = await Promise.all(participants.map((uid) =>
      db.collection("users").doc(uid).collection("fcmTokens").get()
    ));
    const tokenOwners = [];
    tokenSnapshots.forEach((snapshot) => snapshot.forEach((doc) => {
      const token = doc.get("token");
      if (token) tokenOwners.push({ token, ref: doc.ref });
    }));
    if (!tokenOwners.length) return;

    const sender = String(message.authorName || "صديق").slice(0, 60);
    const preview = message.type === "image" ? "أرسل صورة" :
      message.type === "audio" ? "أرسل رسالة صوتية" :
      String(message.body || "رسالة جديدة").slice(0, 160);
    const result = await getMessaging().sendEachForMulticast({
      tokens: tokenOwners.map((entry) => entry.token),
      data: {
        title: sender,
        body: preview,
        threadId: event.params.threadId,
      },
      android: { priority: "high" },
    });
    const stale = [];
    result.responses.forEach((response, index) => {
      if (!response.success && response.error &&
          ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(response.error.code)) {
        stale.push(tokenOwners[index].ref.delete());
      }
    });
    await Promise.all(stale);
  }
);
