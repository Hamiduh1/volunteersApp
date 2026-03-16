/* eslint-disable max-len */

// Import necessary modules from Firebase SDKs
import * as admin from "firebase-admin";
import {DataSnapshot} from "firebase-admin/database";
import * as functions from "firebase-functions/v1";
import {database, EventContext} from "firebase-functions/v1";
import {onCall} from "firebase-functions/v1/https";
// import {onDocumentCreated} from "firebase-functions/v1/firestore";

// Initialize the admin SDK ONCE at the top level
admin.initializeApp();
const db = admin.firestore();

// -----------------------------------------------------------------------------
//  1. YOUR EXISTING REALTIME DATABASE FUNCTION (REFORMATTED)
// -----------------------------------------------------------------------------

interface JobPostingData {
    employerUid: string;
    organizationName?: string;
    eventName?: string;
    jobTitle?: string;
    description?: string;
    date?: string;
    time?: string;
    location?: string;
    category?: string;
    volunteersNeeded?: number;
    status?: string;
    timestamp?: number;
}

export const onNewJobPosting = database.ref("/job_postings/{postingId}")
  .onCreate(async (snapshot: DataSnapshot, context: EventContext) => {
    const jobData = snapshot.val() as JobPostingData | null;
    const postingId = context.params.postingId;

    if (!jobData?.employerUid) {
      console.error(`New job posting at ID '${postingId}' is missing data or employerUid.`, jobData);
      return {success: false, error: "Missing employerUid"};
    }

    const employerUid: string = jobData.employerUid;
    console.log(`Processing new job posting. ID: '${postingId}', Employer UID: '${employerUid}'`);

    const employerJobRef = admin.database().ref(`/employer_job_postings/${employerUid}/${postingId}`);

    try {
      await employerJobRef.set(true);
      console.log(`Successfully created entry for employer '${employerUid}', posting ID '${postingId}'.`);
      return {success: true, postingId, employerUid};
    } catch (error) {
      console.error(`Error creating entry for employer '${employerUid}', posting ID '${postingId}':`, error);
      return {success: false, error: (error as Error).message};
    }
  });

// -----------------------------------------------------------------------------
//  2. YOUR EXISTING FIRESTORE FUNCTION FOR MARKETPLACE (REFORMATTED)
// -----------------------------------------------------------------------------

export const processPurchaseRequest = functions.firestore
  .document("purchase_requests/{requestId}")
  .onCreate(async (snap, context) => {
    const requestId = context.params.requestId;
    const requestData = snap.data();
    functions.logger.log(`Processing purchase request: ${requestId}`);

    if (!requestData || !snap) {
      functions.logger.error("Purchase request data is missing.");
      return;
    }

    const {buyerId, sellerId, itemId, price} = requestData;

    if (!buyerId || !sellerId || !itemId || typeof price !== "number" || price <= 0) {
      functions.logger.error("Invalid purchase request data.", requestData);
      return snap.ref.update({
        status: "failed",
        resultMessage: "Invalid request data. Cannot be processed.",
      });
    }

    const buyerRef = db.collection("users").doc(buyerId);
    const sellerRef = db.collection("users").doc(sellerId);
    const itemRef = db.collection("marketplace_items").doc(itemId);

    try {
      await db.runTransaction(async (transaction) => {
        const buyerDoc = await transaction.get(buyerRef);
        const sellerDoc = await transaction.get(sellerRef);
        const itemDoc = await transaction.get(itemRef);

        if (!buyerDoc.exists || !sellerDoc.exists) {
          throw new Error("Buyer or Seller account does not exist.");
        }
        if (!itemDoc.exists || itemDoc.data()?.status !== "AVAILABLE") {
          throw new Error("Item is no longer available.");
        }

        const buyerBalance = buyerDoc.data()?.wallet?.balance ?? 0;
        if (buyerBalance < price) {
          transaction.update(snap.ref, {
            status: "failed",
            resultMessage: "Purchase failed: Insufficient funds.",
          });
          return;
        }

        transaction.update(buyerRef, "wallet.balance", admin.firestore.FieldValue.increment(-price));
        transaction.update(sellerRef, "wallet.balance", admin.firestore.FieldValue.increment(price));
        transaction.update(itemRef, {status: "SOLD"});

        const timestamp = admin.firestore.Timestamp.now();
        const itemTitle = itemDoc.data()?.title || "Marketplace Item";
        const buyerTransaction = {title: `Purchase: ${itemTitle}`, amount: price, type: "DEBIT", status: "COMPLETED", timestamp, note: `Bought from ${sellerDoc.data()?.name || "seller"}`, source: "MARKETPLACE"};
        const sellerTransaction = {title: `Sale: ${itemTitle}`, amount: price, type: "CREDIT", status: "COMPLETED", timestamp, note: `Sold to ${buyerDoc.data()?.name || "buyer"}`, source: "MARKETPLACE"};
        transaction.set(db.collection("users").doc(buyerId).collection("transactions").doc(), buyerTransaction);
        transaction.set(db.collection("users").doc(sellerId).collection("transactions").doc(), sellerTransaction);

        transaction.update(snap.ref, {
          status: "completed",
          resultMessage: "Purchase Successful! The item is yours.",
        });
      });

      functions.logger.log(`Successfully processed transaction for request: ${requestId}`);
      return null;
    } catch (error) {
      functions.logger.error(`Transaction failed for request ${requestId}:`, error);
      return snap.ref.update({
        status: "failed",
        resultMessage: "An unexpected error occurred. Please try again.",
      });
    }
  });

// -----------------------------------------------------------------------------
//  3. NEW, SECURE CALLABLE FUNCTION FOR AGENT PAYOUTS
// -----------------------------------------------------------------------------

interface AgentPayoutRequest {
  secretCode: string;
}

/**
 * A secure callable function for an agent to process a user's withdrawal request.
 * The agent's app will call this function with the user's secret code.
 */
export const processAgentPayout = onCall(async (request) => {
  const requestData = request.data as AgentPayoutRequest;
  const context = request.auth;
  // 1. Authentication: Ensure a user is logged in.
  if (!context?.uid) {
    throw new functions.https.HttpsError("unauthenticated", "You must be logged in to perform this action.");
  }
  const agentId = context.uid;

  // 2. Authorization: Ensure the logged-in user has the 'agent' role.
  const agentDoc = await db.collection("users").doc(agentId).get();
  if (agentDoc.data()?.role !== "agent") {
    throw new functions.https.HttpsError("permission-denied", "You must be an authorized agent.");
  }

  // 3. Validation: Ensure the secret code was provided.
  const secretCode = requestData.secretCode;
  if (!secretCode || typeof secretCode !== "string") {
    throw new functions.https.HttpsError("invalid-argument", "The function must be called with a 'secretCode'.");
  }

  functions.logger.log(`Agent ${agentId} is processing payout for code: ${secretCode}`);

  // 4. Find the matching payout request.
  const requestQuery = await db.collection("payout_requests")
    .where("secretCode", "==", secretCode)
    .where("status", "==", "PENDING")
    .limit(1)
    .get();

  if (requestQuery.empty) {
    throw new functions.https.HttpsError("not-found", "Invalid, expired, or already used code.");
  }

  const requestDoc = requestQuery.docs[0];
  const requestId = requestDoc.id;
  const {senderId: userId, amount} = requestDoc.data();

  // 5. Perform the secure transaction.
  try {
    await db.runTransaction(async (transaction) => {
      const userRef = db.collection("users").doc(userId);
      const agentRef = db.collection("users").doc(agentId);
      const userSnap = await transaction.get(userRef);

      if (!userSnap.exists) {
        throw new functions.https.HttpsError("not-found", "The user for this code could not be found.");
      }

      const userBalance = userSnap.data()?.wallet?.balance ?? 0;
      if (userBalance < amount) {
        transaction.update(requestDoc.ref, {status: "FAILED", resultMessage: "User had insufficient funds."});
        throw new functions.https.HttpsError("failed-precondition", "User has insufficient funds for this withdrawal.");
      }

      // All checks pass - perform the atomic transfer.
      transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-amount));
      transaction.update(agentRef, "wallet.balance", admin.firestore.FieldValue.increment(amount));
      transaction.update(requestDoc.ref, {status: "COMPLETED", agentId: agentId});

      // Record transaction history for both parties.
      const timestamp = admin.firestore.Timestamp.now();
      const userTransaction = {title: "Agent Cash-out", amount: amount, type: "DEBIT", status: "COMPLETED", timestamp, note: `Withdrawal via agent ${agentDoc.data()?.name || agentId}`, source: "WALLET_AGENT"};
      const agentTransaction = {title: "Agent Payout Service", amount: amount, type: "CREDIT", status: "COMPLETED", timestamp, note: `Payout for user ${userSnap.data()?.name || userId}`, source: "WALLET_AGENT"};
      transaction.set(db.collection("users").doc(userId).collection("transactions").doc(), userTransaction);
      transaction.set(db.collection("users").doc(agentId).collection("transactions").doc(), agentTransaction);
    });

    functions.logger.log(`Successfully completed payout for request ${requestId} by agent ${agentId}.`);
    return {success: true, message: `Successfully paid out $${amount}.`};
  } catch (error) {
    functions.logger.error(`Payout transaction failed for request ${requestId}:`, error);
    // If the error is one we threw, re-throw it. Otherwise, throw a generic one.
    if (error instanceof functions.https.HttpsError) {
      throw error;
    } else {
      // Update the doc so the user isn't left hanging.
      await requestDoc.ref.update({status: "FAILED", resultMessage: "An internal error occurred."}).catch();
      throw new functions.https.HttpsError("internal", "An unexpected error occurred. Please try again.");
    }
  }
});
