/**
 * Deletes all call_sessions and chat messages / call_logs (bulk reset).
 *
 * Prerequisites:
 *   cd my-firebase-project/my-firebase-functions && npm install
 *   firebase login  OR  set GOOGLE_APPLICATION_CREDENTIALS
 *
 * Run:
 *   node ../scripts/clear-chat-call-history.cjs
 *   node ../scripts/clear-chat-call-history.cjs --dry-run
 */
const path = require("path");
const admin = require(path.join(__dirname, "../my-firebase-functions/node_modules/firebase-admin"));

const dryRun = process.argv.includes("--dry-run");
const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || "volunteersapp-968b2";

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId,
});

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

async function deleteBatch(refs) {
  if (refs.length === 0) return 0;
  if (dryRun) return refs.length;
  const batch = db.batch();
  refs.forEach((ref) => batch.delete(ref));
  await batch.commit();
  return refs.length;
}

async function wipeCollection(collectionPath) {
  let total = 0;
  while (true) {
    const snap = await db.collection(collectionPath).limit(400).get();
    if (snap.empty) break;
    const refs = snap.docs.map((d) => d.ref);
    total += await deleteBatch(refs);
    console.log(`  ${collectionPath}: deleted ${total} so far…`);
  }
  return total;
}

async function wipeChatSubcollections() {
  let messages = 0;
  let callLogs = 0;
  let chatsCleared = 0;
  const chats = await db.collection("chats").get();
  for (const chatDoc of chats.docs) {
    const chatRef = chatDoc.ref;
    while (true) {
      const snap = await chatRef.collection("messages").limit(400).get();
      if (snap.empty) break;
      messages += await deleteBatch(snap.docs.map((d) => d.ref));
    }
    while (true) {
      const snap = await chatRef.collection("call_logs").limit(400).get();
      if (snap.empty) break;
      callLogs += await deleteBatch(snap.docs.map((d) => d.ref));
    }
    if (!dryRun) {
      await chatRef.update({
        lastMessage: FieldValue.delete(),
        lastMessageText: FieldValue.delete(),
        lastMessageTimestamp: FieldValue.delete(),
        lastMessageId: FieldValue.delete(),
        lastMessageSenderId: FieldValue.delete(),
        lastMessageType: FieldValue.delete(),
        lastMessageDelivered: FieldValue.delete(),
        lastMessageRead: FieldValue.delete(),
        lastCallType: FieldValue.delete(),
        lastCallStatus: FieldValue.delete(),
        lastCallTimestamp: FieldValue.delete(),
        lastCallInitiatorId: FieldValue.delete(),
        lastCallReceiverId: FieldValue.delete(),
      });
    }
    chatsCleared += 1;
  }
  return { messages, callLogs, chatsCleared };
}

async function main() {
  console.log(`Project: ${projectId}`);
  console.log(dryRun ? "DRY RUN — no writes" : "LIVE — deleting data");

  const callSessions = await wipeCollection("call_sessions");
  console.log(`call_sessions: ${callSessions}`);

  const sub = await wipeChatSubcollections();
  console.log(
    `messages: ${sub.messages}, call_logs: ${sub.callLogs}, chats metadata cleared: ${sub.chatsCleared}`
  );
  console.log("Done.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
