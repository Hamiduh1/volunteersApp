/* eslint-disable max-len */

// =============================================================================
//  IMPORTS & INITIALIZATION
// =============================================================================
import axios from "axios";
import {createHash, randomInt} from "crypto";
import * as admin from "firebase-admin";
import {RtcRole, RtcTokenBuilder} from "agora-token";
import {DataSnapshot} from "firebase-admin/database";
import * as functions from "firebase-functions/v1";
import {database, EventContext} from "firebase-functions/v1";
import Stripe from "stripe";

// Initialize the admin SDK ONCE at the top level
admin.initializeApp();
const db = admin.firestore();

let runtimeConfigCache: Record<string, unknown> | null = null;
const getRuntimeConfig = (): Record<string, unknown> => {
  if (runtimeConfigCache) return runtimeConfigCache;

  const cloudRuntimeConfig = (() => {
    const raw = process.env.CLOUD_RUNTIME_CONFIG;
    if (!raw) return {} as Record<string, unknown>;
    try {
      return JSON.parse(raw) as Record<string, unknown>;
    } catch (error) {
      functions.logger.warn("Failed to parse CLOUD_RUNTIME_CONFIG.", {error});
      return {} as Record<string, unknown>;
    }
  })();

  const configFn = (functions as unknown as {config?: () => Record<string, unknown>}).config;
  let fnConfig: Record<string, unknown> = {};
  if (typeof configFn === "function") {
    try {
      fnConfig = configFn();
    } catch (error) {
      functions.logger.warn("functions.config() unavailable at runtime.", {error});
      fnConfig = {};
    }
  }

  const merged = {...cloudRuntimeConfig, ...fnConfig};
  try {
    functions.logger.log("Runtime config loaded flags", {
      hasCloudRuntimeConfig: !!process.env.CLOUD_RUNTIME_CONFIG,
      hasStripeConfig: !!((merged["stripe"] as Record<string, unknown> | undefined)?.["secret_key"]),
      hasExchangeRateConfig: !!((merged["exchangerate"] as Record<string, unknown> | undefined)?.["api_key"]),
      hasEnvStripeSecret: !!process.env.STRIPE_SECRET_KEY,
      hasEnvExchangeRate: !!process.env.EXCHANGERATE_API_KEY,
    });
  } catch {
    // no-op logging guard
  }

  runtimeConfigCache = merged;
  return runtimeConfigCache;
};
// =============================================================================
//  CONFIGURATION ACCESS (SECURE - using environment variables)
// =============================================================================
// Get API keys from environment - these are loaded at runtime by Firebase
const getApiKeys = () => {
  const runtimeConfig = getRuntimeConfig();
  const stripeConfig = (runtimeConfig["stripe"] as Record<string, unknown> | undefined) || {};
  const exchangeRateConfig = (runtimeConfig["exchangerate"] as Record<string, unknown> | undefined) || {};

  return {
    exchangeRate: (process.env.EXCHANGERATE_API_KEY ||
      exchangeRateConfig["api_key"] ||
      exchangeRateConfig["apikey"] ||
      exchangeRateConfig["key"]) as string | undefined,
    stripe: (process.env.STRIPE_SECRET_KEY ||
      stripeConfig["secret_key"] ||
      stripeConfig["secret"] ||
      stripeConfig["key"]) as string | undefined,
  };
};

const getAppConfig = () => {
  const runtimeConfig = getRuntimeConfig();
  const configGroup = (runtimeConfig["config"] as Record<string, unknown> | undefined) || {};
  return {
    // Margins
    forexProfitMargin: (process.env.CONFIG_FOREX_PROFIT_MARGIN ||
      configGroup["forex_profit_margin"] ||
      "0.010") as string,
    stripeForexDepositProfitMargin: (process.env.CONFIG_STRIPE_FOREX_DEPOSIT_PROFIT_MARGIN ||
      configGroup["stripe_forex_deposit_profit_margin"] ||
      "0.005") as string,
    mobileMoneyHiddenFeeRate: (process.env.CONFIG_MOBILE_MONEY_HIDDEN_FEE_RATE ||
      configGroup["mobile_money_hidden_fee_rate"] ||
      "0.0") as string,

    // Fees
    agentAuthorizationFeeUsd: (process.env.CONFIG_AGENT_AUTHORIZATION_FEE_USD ||
      configGroup["agent_authorization_fee_usd"] ||
      "50") as string,
    ownerUserId: (process.env.CONFIG_OWNER_USER_ID ||
      configGroup["owner_user_id"] ||
      "") as string,
    ownerBootstrapEmail: (process.env.CONFIG_OWNER_BOOTSTRAP_EMAIL ||
      configGroup["owner_bootstrap_email"] ||
      "") as string,
    blindDateFee: (process.env.CONFIG_BLIND_DATE_FEE ||
      configGroup["blind_date_fee"] ||
      "10.0") as string,
    stripeConnectReturnUrl: (process.env.STRIPE_CONNECT_RETURN_URL ||
      configGroup["stripe_connect_return_url"]) as string | undefined,
    stripeConnectRefreshUrl: (process.env.STRIPE_CONNECT_REFRESH_URL ||
      configGroup["stripe_connect_refresh_url"]) as string | undefined,
  };
};

const getAgoraConfig = () => {
  const runtimeConfig = getRuntimeConfig();
  const agoraConfig = (runtimeConfig["agora"] as Record<string, unknown> | undefined) || {};
  const ttlSecondsRaw = Number(
    process.env.AGORA_TOKEN_TTL_SECONDS ||
      agoraConfig["token_ttl_seconds"] ||
      3600
  );
  const ttlSeconds = Number.isFinite(ttlSecondsRaw) && ttlSecondsRaw > 0 ? ttlSecondsRaw : 3600;

  return {
    appId: (process.env.AGORA_APP_ID ||
      agoraConfig["app_id"] ||
      agoraConfig["appId"]) as string | undefined,
    appCertificate: (process.env.AGORA_APP_CERTIFICATE ||
      process.env.AGORA_APP_CERT ||
      agoraConfig["app_certificate"] ||
      agoraConfig["appCertificate"] ||
      agoraConfig["certificate"]) as string | undefined,
    ttlSeconds,
  };
};

const defaultTermsAndConditions = `Last updated: March 10, 2026

Welcome to Volunteers App. By using this app, you agree to these terms:

1. Eligibility and Account Responsibilities
- You must provide accurate account information and keep your credentials secure.
- You are responsible for all activity under your account.

2. Acceptable Use
- You must not post unlawful, abusive, fraudulent, or harmful content.
- You must not impersonate others, misuse payment tools, or attempt unauthorized access.

3. Events, Jobs, and Applications
- Organizers and employers are responsible for the accuracy of listings they publish.
- Volunteers are responsible for application details they submit.
- Platform availability and participation are not guaranteed.

4. Payments and Wallet Features
- Transactions are processed through supported payment providers.
- Fees, reversals, and settlement timing may apply depending on payment method and jurisdiction.

5. Privacy and Data
- We process account and activity data to operate and secure the platform.
- By using the service, you consent to data handling consistent with our Privacy Policy.

6. Suspension and Termination
- We may suspend or terminate accounts that violate these terms or applicable laws.

7. Limitation of Liability
- The platform is provided on an "as is" basis. To the maximum extent permitted by law, liability is limited for indirect or consequential losses.

8. Changes to Terms
- We may update these terms. Continued use after updates means you accept the revised terms.

If you do not agree with these terms, discontinue use of the app.`;

const defaultPrivacyPolicy = `Last updated: March 10, 2026

Volunteers App collects and uses personal data to provide app functionality, including user accounts, events, jobs, messaging, payments, and support workflows.

What we collect:
- Account information (name, email, phone, profile data)
- Activity data (applications, event/job interactions, chat metadata)
- Payment-related records needed for wallet operations and compliance

How we use data:
- To provide core platform features
- To secure accounts and prevent abuse or fraud
- To comply with legal, financial, and regulatory obligations

Data sharing:
- With trusted service providers necessary for hosting, messaging, and payment processing
- When required by law or to protect users and platform integrity

Your controls:
- You can update profile information from account settings
- You may request account changes and support through in-app channels

We retain data only as needed for service, legal, and security purposes.`;

export const getAgoraRtcToken = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const channelName = String(data?.channelName || "").trim();
    if (!channelName) {
      throw new functions.https.HttpsError("invalid-argument", "channelName is required.");
    }
    if (channelName.length > 64) {
      throw new functions.https.HttpsError("invalid-argument", "channelName must be 64 characters or fewer.");
    }

    const requestedRole = String(data?.role || "publisher").trim().toLowerCase();
    if (!["publisher", "subscriber"].includes(requestedRole)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "role must be either 'publisher' or 'subscriber'."
      );
    }

    const requestedUid = Number(data?.uid ?? 0);
    if (!Number.isInteger(requestedUid) || requestedUid < 0 || requestedUid > 4294967295) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "uid must be an integer between 0 and 4294967295."
      );
    }

    const {appId, appCertificate, ttlSeconds} = getAgoraConfig();
    if (!appId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Agora App ID is not configured in Cloud Functions environment."
      );
    }

    if (!appCertificate) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Agora App Certificate is not configured in Cloud Functions environment."
      );
    }

    const rtcRole = requestedRole === "subscriber" ? RtcRole.SUBSCRIBER : RtcRole.PUBLISHER;

    const token = RtcTokenBuilder.buildTokenWithUid(
      appId,
      appCertificate,
      channelName,
      requestedUid,
      rtcRole,
      ttlSeconds,
      ttlSeconds
    );

    return {
      token,
      tokenRequired: true,
      expiresInSeconds: ttlSeconds,
      role: requestedRole,
      uid: requestedUid,
    };
  });

export const seedLegalDocuments = functions.region("us-central1").https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({error: "Method not allowed. Use POST."});
    return;
  }

  const providedSecret = String(
    req.header("x-admin-secret") ||
      req.query["secret"] ||
      ((req.body as Record<string, unknown> | undefined)?.["secret"] as string | undefined) ||
      ""
  ).trim();
  const expectedSecret = String(process.env.ADMIN_PAYOUT_REVERSAL_SECRET || "").trim();

  if (!expectedSecret || providedSecret !== expectedSecret) {
    res.status(403).json({error: "Forbidden"});
    return;
  }

  const now = admin.firestore.FieldValue.serverTimestamp();
  const docs = [
    {
      path: "app_config/terms_and_conditions",
      data: {
        title: "Terms and Conditions",
        text: defaultTermsAndConditions,
        version: "2026-03-10",
        updatedAt: now,
      },
    },
    {
      path: "app_config/privacy_policy",
      data: {
        title: "Privacy Policy",
        text: defaultPrivacyPolicy,
        version: "2026-03-10",
        updatedAt: now,
      },
    },
    // Legacy alias copies to support older collection naming in clients.
    {
      path: "appConfig/terms_and_conditions",
      data: {
        title: "Terms and Conditions",
        text: defaultTermsAndConditions,
        version: "2026-03-10",
        updatedAt: now,
      },
    },
    {
      path: "appConfig/privacy_policy",
      data: {
        title: "Privacy Policy",
        text: defaultPrivacyPolicy,
        version: "2026-03-10",
        updatedAt: now,
      },
    },
  ];

  try {
    const batch = db.batch();
    for (const doc of docs) {
      batch.set(db.doc(doc.path), doc.data, {merge: true});
    }
    await batch.commit();

    res.status(200).json({
      success: true,
      seededPaths: docs.map((doc) => doc.path),
    });
  } catch (error) {
    functions.logger.error("Failed to seed legal documents", {error});
    res.status(500).json({
      error: "Failed to seed legal documents.",
    });
  }
});

const AGENT_CASHOUT_FEE_TIERS = [
  {min: 0, max: 2, rate: 0.09},
  {min: 2, max: 5, rate: 0.05},
  {min: 5, max: 20, rate: 0.026},
  {min: 20, max: 40, rate: 0.0164},
  {min: 40, max: 200, rate: 0.013},
  {min: 200, max: 600, rate: 0.008},
  {min: 600, max: 1200, rate: 0.00475},
  {min: 1200, max: null, rate: 0.00314},
];

const AGENT_CASHOUT_AGENT_SHARE = 0.6;
const AGENT_CASHOUT_OWNER_SHARE = 0.4;
const EVENT_TICKET_OWNER_FEE_RATE = 0.05;
const TWO_WEEKS_MS = 14 * 24 * 60 * 60 * 1000;

const roundMoney = (value: number): number => Number(value.toFixed(2));

const resolveAgentCashOutRate = (amount: number): number => {
  if (amount < 1) return 0.09;
  for (const tier of AGENT_CASHOUT_FEE_TIERS) {
    const matchesMin = amount >= tier.min;
    const matchesMax = tier.max === null || amount < tier.max;
    if (matchesMin && matchesMax) return tier.rate;
  }
  return 0.09;
};

const calculateAgentCashOutFee = (amount: number): {rate: number; fee: number} => {
  const rate = resolveAgentCashOutRate(amount);
  return {rate, fee: roundMoney(amount * rate)};
};

const splitAgentCashOutFee = (fee: number): {agentShare: number; ownerShare: number} => {
  const agentShare = roundMoney(fee * AGENT_CASHOUT_AGENT_SHARE);
  const ownerShare = roundMoney(fee * AGENT_CASHOUT_OWNER_SHARE);
  return {agentShare, ownerShare};
};

const STAFF_FREE_ROLES = new Set(["owner", "admin", "associate", "support", "support_associate"]);

const normalizeRoleForFeePolicy = (value: unknown): string =>
  String(value || "").trim().toLowerCase();

const hasStaffFreeClaim = (authToken?: Record<string, unknown>): boolean => {
  if (!authToken) return false;
  const claimRole = normalizeRoleForFeePolicy(authToken["role"]);
  return (
    STAFF_FREE_ROLES.has(claimRole) ||
    authToken["owner"] === true ||
    authToken["admin"] === true ||
    authToken["associate"] === true
  );
};

const isStaffOnboardingActive = (value: unknown): boolean => {
  const status = String(value || "").trim().toUpperCase();
  return status === "" || status === "ACTIVE";
};

const isStaffFeeExempt = (
  userData?: Record<string, unknown>,
  authToken?: Record<string, unknown>
): boolean => {
  const role = normalizeRoleForFeePolicy(userData?.role);
  const roleEligible = STAFF_FREE_ROLES.has(role) || hasStaffFreeClaim(authToken);
  if (!roleEligible) return false;
  return isStaffOnboardingActive(userData?.staffOnboardingStatus);
};

type PlatformRevenueSource =
  | "stripeForexEarnings"
  | "mobileMoneyHiddenFee"
  | "blindDateFees"
  | "agentAuthorizationFees"
  | "agentCashoutOwnerShare"
  | "eventTicketOwnerFee"
  | "marketplacePlatinumFee"
  | "garageSaleFee"
  | "otherIncome";

type PlatformRevenueEntry = {
  source: PlatformRevenueSource;
  amount: number;
  note?: string;
  relatedUserId?: string;
};

const platformRevenueRef = db.collection("system").doc("platform_revenue");

const recordPlatformRevenue = (
  transaction: admin.firestore.Transaction,
  entry: PlatformRevenueEntry
): void => {
  const timestamp = admin.firestore.Timestamp.now();
  const revenueUpdate: Record<string, unknown> = {
    totalCollected: admin.firestore.FieldValue.increment(entry.amount),
    balance: admin.firestore.FieldValue.increment(entry.amount),
    transactionCount: admin.firestore.FieldValue.increment(1),
    lastUpdate: timestamp,
  };
  revenueUpdate[entry.source] = admin.firestore.FieldValue.increment(entry.amount);

  transaction.set(platformRevenueRef, revenueUpdate, {merge: true});
  const txRef = platformRevenueRef.collection("transactions").doc();
  transaction.set(txRef, {
    source: entry.source,
    amount: entry.amount,
    note: entry.note || null,
    relatedUserId: entry.relatedUserId || null,
    createdAt: timestamp,
  });
};

type FollowerNotificationSetting = "jokesPosts" | "liveStreams";

const shouldNotifyFollower = (settings: admin.firestore.DocumentData | undefined, field: FollowerNotificationSetting): boolean => {
  if (!settings) return true;
  const value = settings[field];
  return value === undefined ? true : Boolean(value);
};

const notifyFollowers = async (params: {
  actorId: string;
  title: string;
  body: string;
  type: string;
  referenceId: string;
  settingsField: FollowerNotificationSetting;
}): Promise<void> => {
  const {actorId, title, body, type, referenceId, settingsField} = params;
  const followersSnap = await db.collection("users").doc(actorId).collection("followers").get();
  if (followersSnap.empty) {
    return;
  }

  const tokens: string[] = [];
  const batch = db.batch();
  const timestamp = admin.firestore.FieldValue.serverTimestamp();

  for (const followerDoc of followersSnap.docs) {
    const followerId = followerDoc.id;
    const settingsDoc = await db.collection("users").doc(followerId)
      .collection("settings").doc("notifications").get();
    const settings = settingsDoc.data();

    if (!shouldNotifyFollower(settings, settingsField)) {
      continue;
    }

    const followerUserDoc = await db.collection("users").doc(followerId).get();
    const token = followerUserDoc.data()?.fcmToken as string | undefined;
    if (token) {
      tokens.push(token);
    }

    const notificationRef = db.collection("users").doc(followerId).collection("notifications").doc();
    batch.set(notificationRef, {
      type,
      title,
      body,
      actorId,
      referenceId,
      read: false,
      createdAt: timestamp,
    });
  }

  await batch.commit();

  if (tokens.length > 0) {
    await admin.messaging().sendEachForMulticast({
      tokens,
      notification: {title, body},
      data: {type, actorId, referenceId},
    });
  }
};

const notifyIncomingCall = async (params: {
  receiverId: string;
  callerId: string;
  chatId: string;
  callId: string;
  callerName: string;
  callType: string;
}): Promise<void> => {
  const {receiverId, callerId, chatId, callId, callerName, callType} = params;
  const userDoc = await db.collection("users").doc(receiverId).get();
  const token = userDoc.data()?.fcmToken as string | undefined;
  if (!token) return;

  await admin.messaging().send({
    token,
    notification: {
      title: callType === "video" ? "Incoming video call" : "Incoming voice call",
      body: callerName || "Incoming call",
    },
    data: {
      type: "incoming_call",
      receiverId,
      callerId,
      chatId,
      callId,
      callerName: callerName || "Incoming call",
      callType,
    },
  });
};

// =============================================================================
//  SERVICE INITIALIZATION (Stripe, etc.)
// =============================================================================
// Initialize Stripe lazily - only when needed, so config is loaded
let stripeInstance: Stripe | null = null;

const getStripe = (): Stripe => {
  if (!stripeInstance) {
    const apiKeys = getApiKeys();
    if (!apiKeys.stripe) {
      throw new Error("Payment provider secret key is not configured. Set STRIPE_SECRET_KEY or firebase functions config key stripe.secret_key.");
    }
    stripeInstance = new Stripe(apiKeys.stripe, {
      apiVersion: "2026-01-28.clover",
    });
  }
  return stripeInstance;
};

// =============================================================================
//  1. YOUR EXISTING REALTIME DATABASE FUNCTION (Unchanged)
// =============================================================================

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

// =============================================================================
//  2. YOUR EXISTING FIRESTORE FUNCTION FOR MARKETPLACE (Unchanged)
// =============================================================================

export const processPurchaseRequest = functions.firestore
  .document("purchase_requests/{requestId}")
  .onCreate(async (snap, context) => {
    const requestId = context.params.requestId;
    const requestData = snap.data();
    functions.logger.log(`Processing purchase request: ${requestId}`);

    if (!requestData || !snap) {
      functions.logger.error("Purchase request data is missing.");
      return undefined;
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

        const sellerData = (sellerDoc.data() || {}) as Record<string, unknown>;
        const sellerStaffFeeExempt = isStaffFeeExempt(sellerData);

        const platinumFeeRate = 0.02;
        const platformFee = sellerStaffFeeExempt ? 0 : roundMoney(price * platinumFeeRate);
        const sellerNet = roundMoney(price - platformFee);

        transaction.update(buyerRef, "wallet.balance", admin.firestore.FieldValue.increment(-price));
        transaction.update(sellerRef, "wallet.balance", admin.firestore.FieldValue.increment(sellerNet));
        transaction.update(itemRef, {status: "SOLD"});

        if (platformFee > 0) {
          recordPlatformRevenue(transaction, {
            source: "marketplacePlatinumFee",
            amount: platformFee,
            note: `Marketplace platinum fee for item ${itemId}`,
            relatedUserId: buyerId,
          });
        }

        const timestamp = admin.firestore.Timestamp.now();
        const itemTitle = itemDoc.data()?.title || "Marketplace Item";
        const buyerTransaction = {title: `Purchase: ${itemTitle}`, amount: -price, type: "DEBIT", status: "COMPLETED", timestamp, note: `Bought from ${sellerDoc.data()?.name || "seller"}. Platinum fee: $${platformFee.toFixed(2)}`, source: "MARKETPLACE"};
        const sellerTransaction = {title: `Sale: ${itemTitle}`, amount: sellerNet, type: "CREDIT", status: "COMPLETED", timestamp, note: sellerStaffFeeExempt ? `Sold to ${buyerDoc.data()?.name || "buyer"}. Staff fee exemption applied.` : `Sold to ${buyerDoc.data()?.name || "buyer"}. Platinum fee: $${platformFee.toFixed(2)}`, source: "MARKETPLACE"};
        transaction.set(db.collection("users").doc(buyerId).collection("transactions").doc(), buyerTransaction);
        transaction.set(db.collection("users").doc(sellerId).collection("transactions").doc(), sellerTransaction);

        transaction.update(snap.ref, {
          status: "completed",
          resultMessage: "Purchase Successful! The item is yours.",
        });
      });

      functions.logger.log(`Successfully processed transaction for request: ${requestId}`);
    } catch (error) {
      functions.logger.error(`Transaction failed for request ${requestId}:`, error);
      await snap.ref.update({
        status: "failed",
        resultMessage: "An unexpected error occurred. Please try again.",
      });
    }
    return undefined;
  });


// =============================================================================
//  2A. CALL SESSION NOTIFICATIONS (NEW)
// =============================================================================

export const onCallSessionCreated = functions.firestore
  .document("call_sessions/{sessionId}")
  .onCreate(async (snap, context) => {
    const session = snap.data();
    if (!session) return undefined;

    const receiverId = session.receiverId as string | undefined;
    const callerId = session.callerId as string | undefined;
    const chatId = session.chatId as string | undefined;
    const callType = session.callType as string | undefined;
    const callerName = session.callerName as string | undefined;

    if (!receiverId || !callerId || !chatId || !callType) return undefined;

    await notifyIncomingCall({
      receiverId,
      callerId,
      chatId,
      callId: context.params.sessionId,
      callerName: callerName || "Incoming call",
      callType,
    });

    return undefined;
  });


// =============================================================================
//  2A. GARAGE SALE PAYMENTS (NEW)
// =============================================================================

export const processGarageSalePayment = functions.firestore
  .document("garage_sale_payments/{paymentId}")
  .onCreate(async (snap, context) => {
    const paymentId = context.params.paymentId;
    const paymentData = snap.data();
    functions.logger.log(`Processing garage sale payment: ${paymentId}`);

    if (!paymentData || !snap) {
      functions.logger.error("Garage sale payment data is missing.");
      return undefined;
    }

    const {buyerId, sellerId, garageSaleId, amount} = paymentData;

    if (!buyerId || !sellerId || !garageSaleId || typeof amount !== "number" || amount <= 0) {
      functions.logger.error("Invalid garage sale payment data.", paymentData);
      return snap.ref.update({
        status: "failed",
        resultMessage: "Invalid payment data. Cannot be processed.",
      });
    }

    const buyerRef = db.collection("users").doc(buyerId);
    const sellerRef = db.collection("users").doc(sellerId);
    const saleRef = db.collection("garage_sales").doc(garageSaleId);

    try {
      await db.runTransaction(async (transaction) => {
        const buyerDoc = await transaction.get(buyerRef);
        const sellerDoc = await transaction.get(sellerRef);
        const saleDoc = await transaction.get(saleRef);

        if (!buyerDoc.exists || !sellerDoc.exists) {
          throw new Error("Buyer or Seller account does not exist.");
        }

        const buyerBalance = buyerDoc.data()?.wallet?.balance ?? 0;
        if (buyerBalance < amount) {
          transaction.update(snap.ref, {
            status: "failed",
            resultMessage: "Payment failed: Insufficient funds.",
          });
          return;
        }

        const sellerData = (sellerDoc.data() || {}) as Record<string, unknown>;
        const sellerStaffFeeExempt = isStaffFeeExempt(sellerData);

        const platformFeeRate = 0.02;
        const platformFee = sellerStaffFeeExempt ? 0 : roundMoney(amount * platformFeeRate);
        const sellerNet = roundMoney(amount - platformFee);

        transaction.update(buyerRef, "wallet.balance", admin.firestore.FieldValue.increment(-amount));
        transaction.update(sellerRef, "wallet.balance", admin.firestore.FieldValue.increment(sellerNet));

        if (platformFee > 0) {
          recordPlatformRevenue(transaction, {
            source: "garageSaleFee",
            amount: platformFee,
            note: `Garage sale fee for sale ${garageSaleId}`,
            relatedUserId: buyerId,
          });
        }

        const timestamp = admin.firestore.Timestamp.now();
        const saleTitle = saleDoc.exists ? saleDoc.data()?.title || "Garage Sale" : "Garage Sale";
        const buyerTransaction = {
          title: `Garage Sale Payment: ${saleTitle}`,
          amount: -amount,
          type: "DEBIT",
          status: "COMPLETED",
          timestamp,
          note: `Paid to ${sellerDoc.data()?.name || "seller"}. Platinum fee: $${platformFee.toFixed(2)}`,
          source: "GARAGE_SALE",
        };
        const sellerTransaction = {
          title: `Garage Sale Earnings: ${saleTitle}`,
          amount: sellerNet,
          type: "CREDIT",
          status: "COMPLETED",
          timestamp,
          note: sellerStaffFeeExempt ?
            `Received from ${buyerDoc.data()?.name || "buyer"}. Staff fee exemption applied.` :
            `Received from ${buyerDoc.data()?.name || "buyer"}. Platinum fee: $${platformFee.toFixed(2)}`,
          source: "GARAGE_SALE",
        };
        transaction.set(db.collection("users").doc(buyerId).collection("transactions").doc(), buyerTransaction);
        transaction.set(db.collection("users").doc(sellerId).collection("transactions").doc(), sellerTransaction);

        transaction.update(snap.ref, {
          status: "completed",
          resultMessage: "Payment successful!",
        });
      });

      functions.logger.log(`Successfully processed garage sale payment: ${paymentId}`);
    } catch (error) {
      functions.logger.error(`Garage sale payment failed for ${paymentId}:`, error);
      await snap.ref.update({
        status: "failed",
        resultMessage: "An unexpected error occurred. Please try again.",
      });
    }
    return undefined;
  });


// =============================================================================
//  2B. JOKES + LIVE NOTIFICATIONS (NEW)
// =============================================================================

export const onJokePostedNotifyFollowers = functions.firestore
  .document("users/{userId}/jokes/{jokeId}")
  .onCreate(async (snap, context) => {
    const actorId = context.params.userId as string;
    const joke = snap.data();
    if (!joke) return undefined;

    const authorName = joke.authorName || "Someone";
    const preview = typeof joke.text === "string" ? joke.text.slice(0, 80) : "New post";

    await notifyFollowers({
      actorId,
      title: `${authorName} posted a new joke`,
      body: preview,
      type: "jokesPost",
      referenceId: context.params.jokeId,
      settingsField: "jokesPosts",
    });

    return undefined;
  });

export const onLiveSessionStartedNotifyFollowers = functions.firestore
  .document("live_sessions/{sessionId}")
  .onCreate(async (snap) => {
    const session = snap.data();
    if (!session) return undefined;
    if (session.status && session.status !== "live") return undefined;

    const actorId = session.hostId as string | undefined;
    if (!actorId) return undefined;

    const title = session.title || "Live now";
    const hostName = session.hostName || "Someone";

    await notifyFollowers({
      actorId,
      title: `${hostName} is live now`,
      body: title,
      type: "liveStream",
      referenceId: snap.id,
      settingsField: "liveStreams",
    });

    return undefined;
  });


// =============================================================================
//  3. AGENT PAYOUTS FUNCTION (UPDATED)
// =============================================================================

interface AgentPayoutRequest {
  secretCode: string;
}

export const processAgentPayout = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to perform this action.");
    }
    const agentId = context.auth.uid;
    const ownerUserId = getAppConfig().ownerUserId;
    if (!ownerUserId) {
      throw new functions.https.HttpsError("failed-precondition", "Owner account is not configured.");
    }

    const agentDoc = await db.collection("users").doc(agentId).get();
    if (agentDoc.data()?.role !== "agent") {
      throw new functions.https.HttpsError("permission-denied", "You must be an authorized agent.");
    }

    const secretCode = (data as AgentPayoutRequest).secretCode;
    if (!secretCode || typeof secretCode !== "string") {
      throw new functions.https.HttpsError("invalid-argument", "The function must be called with a 'secretCode'.");
    }

    functions.logger.log(`Agent ${agentId} is processing payout for code: ${secretCode}`);

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
    const requestData = requestDoc.data();
    const {senderId: userId, amount} = requestData;
    if (!userId || typeof amount !== "number" || amount <= 0) {
      await requestDoc.ref.set({status: "FAILED", resultMessage: "Invalid request data."}, {merge: true});
      throw new functions.https.HttpsError("invalid-argument", "Invalid payout request data.");
    }

    const expiresAt = requestData.expiresAt as admin.firestore.Timestamp | Date | undefined;
    if (expiresAt) {
      const expiryDate = expiresAt instanceof admin.firestore.Timestamp ? expiresAt.toDate() : expiresAt;
      if (expiryDate.getTime() <= Date.now()) {
        await requestDoc.ref.set({status: "EXPIRED", resultMessage: "Code expired."}, {merge: true});
        throw new functions.https.HttpsError("failed-precondition", "This code has expired.");
      }
    }

    try {
      await db.runTransaction(async (transaction) => {
        const userRef = db.collection("users").doc(userId);
        const agentRef = db.collection("users").doc(agentId);
        const userSnap = await transaction.get(userRef);

        if (!userSnap.exists) {
          throw new functions.https.HttpsError("not-found", "The user for this code could not be found.");
        }

        const userData = (userSnap.data() || {}) as Record<string, unknown>;
        const userStaffFeeExempt = isStaffFeeExempt(userData);
        const feeInfo = userStaffFeeExempt ? {rate: 0, fee: 0} : calculateAgentCashOutFee(amount);
        const totalDebit = roundMoney(amount + feeInfo.fee);
        const {agentShare, ownerShare} = splitAgentCashOutFee(feeInfo.fee);

        const userBalance = userSnap.data()?.wallet?.balance ?? 0;
        if (userBalance < totalDebit) {
          transaction.update(requestDoc.ref, {status: "FAILED", resultMessage: "User had insufficient funds."});
          throw new functions.https.HttpsError("failed-precondition", "User has insufficient funds for this withdrawal.");
        }

        transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-totalDebit));
        transaction.update(agentRef, "wallet.balance", admin.firestore.FieldValue.increment(amount));
        if (agentShare > 0) {
          transaction.update(agentRef, "agentEarnings.balance", admin.firestore.FieldValue.increment(agentShare));
        }
        transaction.update(requestDoc.ref, {
          status: "COMPLETED",
          agentId: agentId,
          fee: feeInfo.fee,
          feeRate: feeInfo.rate,
          agentShare,
          ownerShare,
          processedAt: admin.firestore.Timestamp.now(),
        });

        if (ownerShare > 0) {
          recordPlatformRevenue(transaction, {
            source: "agentCashoutOwnerShare",
            amount: ownerShare,
            note: `Owner share from agent cash-out (${agentId})`,
            relatedUserId: userId,
          });
        }

        const timestamp = admin.firestore.Timestamp.now();
        const agentName = agentDoc.data()?.name || agentId;
        const userName = userSnap.data()?.name || userId;
        const userTransaction = {
          title: "Agent Cash-out",
          amount: -totalDebit,
          fee: feeInfo.fee,
          type: "DEBIT",
          status: "COMPLETED",
          timestamp,
          note: userStaffFeeExempt ?
            `Withdrawal via agent ${agentName}. Staff fee exemption applied.` :
            `Withdrawal via agent ${agentName}. Fee $${feeInfo.fee.toFixed(2)} applied.`,
          source: "WALLET_AGENT",
        };
        const agentTransaction = {
          title: "Agent Payout Service",
          amount: amount,
          type: "CREDIT",
          status: "COMPLETED",
          timestamp,
          note: `Cash payout to ${userName}`,
          source: "WALLET_AGENT",
        };
        const agentCommissionTransaction = {
          title: "Agent Commission",
          amount: agentShare,
          type: "CREDIT",
          status: "COMPLETED",
          timestamp,
          note: `Commission earned from ${userName}`,
          source: "AGENT_COMMISSION",
        };
        transaction.set(db.collection("users").doc(userId).collection("transactions").doc(), userTransaction);
        transaction.set(db.collection("users").doc(agentId).collection("transactions").doc(), agentTransaction);
        transaction.set(db.collection("users").doc(agentId).collection("transactions").doc(), agentCommissionTransaction);
      });

      functions.logger.log(`Successfully completed payout for request ${requestId} by agent ${agentId}.`);
      return {success: true, message: `Successfully paid out $${amount}.`};
    } catch (error) {
      functions.logger.error(`Payout transaction failed for request ${requestId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      await requestDoc.ref.update({status: "FAILED", resultMessage: "An internal error occurred."}).catch();
      throw new functions.https.HttpsError("internal", "An unexpected error occurred. Please try again.");
    }
  });

// =============================================================================
//  3B. AGENT EARNINGS CASH-OUT (NEW)
// =============================================================================

export const cashOutAgentEarnings = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (_data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to perform this action.");
    }

    const agentId = context.auth.uid;
    const agentRef = db.collection("users").doc(agentId);
    const agentDoc = await agentRef.get();
    if (!agentDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Agent account not found.");
    }
    if (agentDoc.data()?.role !== "agent") {
      throw new functions.https.HttpsError("permission-denied", "You must be an authorized agent.");
    }

    const earningsBalance = Number(agentDoc.data()?.agentEarnings?.balance || 0);
    if (!earningsBalance || earningsBalance <= 0) {
      throw new functions.https.HttpsError("failed-precondition", "No earnings available to cash out.");
    }

    const lastCashout = agentDoc.data()?.agentEarnings?.lastCashoutAt as admin.firestore.Timestamp | Date | undefined;
    if (lastCashout) {
      const lastDate = lastCashout instanceof admin.firestore.Timestamp ? lastCashout.toDate() : lastCashout;
      const nextEligibleAt = new Date(lastDate.getTime() + TWO_WEEKS_MS);
      if (Date.now() < nextEligibleAt.getTime()) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          `Next earnings cash-out available on ${nextEligibleAt.toDateString()}.`
        );
      }
    }

    await db.runTransaction(async (transaction) => {
      const timestamp = admin.firestore.Timestamp.now();
      transaction.update(agentRef, "wallet.balance", admin.firestore.FieldValue.increment(earningsBalance));
      transaction.set(agentRef, {
        agentEarnings: {
          balance: 0,
          lastCashoutAt: timestamp,
        },
      }, {merge: true});
      transaction.set(agentRef.collection("transactions").doc(), {
        title: "Agent Earnings Cash-out",
        amount: earningsBalance,
        type: "CREDIT",
        status: "COMPLETED",
        timestamp,
        note: "Commission paid to wallet",
        source: "AGENT_EARNINGS",
      });
    });

    return {success: true, message: `Earnings of $${earningsBalance.toFixed(2)} added to wallet.`};
  });

// =============================================================================
//  3C. OWNER REVENUE CASH-OUT (NEW)
// =============================================================================

export const cashOutOwnerRevenue = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (_data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to perform this action.");
    }

    const ownerUserId = getAppConfig().ownerUserId;
    if (!ownerUserId || context.auth.uid !== ownerUserId) {
      throw new functions.https.HttpsError("permission-denied", "Owner access required.");
    }

    await db.runTransaction(async (transaction) => {
      const platformSnap = await transaction.get(platformRevenueRef);
      const balance = Number(platformSnap.data()?.balance || 0);
      if (!balance || balance <= 0) {
        throw new functions.https.HttpsError("failed-precondition", "No revenue available to cash out.");
      }

      const ownerRef = db.collection("users").doc(ownerUserId);
      transaction.update(ownerRef, "wallet.balance", admin.firestore.FieldValue.increment(balance));
      transaction.set(ownerRef.collection("transactions").doc(), {
        title: "Owner Revenue Cash-out",
        amount: balance,
        type: "CREDIT",
        status: "COMPLETED",
        timestamp: admin.firestore.Timestamp.now(),
        note: "Platform revenue moved to wallet",
        source: "OWNER_REVENUE",
      });

      const timestamp = admin.firestore.Timestamp.now();
      transaction.set(platformRevenueRef, {
        balance: admin.firestore.FieldValue.increment(-balance),
        lastUpdate: timestamp,
      }, {merge: true});

      const txRef = platformRevenueRef.collection("transactions").doc();
      transaction.set(txRef, {
        source: "ownerCashout",
        amount: -balance,
        note: "Owner revenue cash-out",
        relatedUserId: ownerUserId,
        createdAt: timestamp,
      });
    });

    return {success: true, message: "Revenue transferred to your wallet."};
  });

// =============================================================================
//  3D. EVENT SIGN-UP PAYMENT SPLIT (OWNER 5%)
// =============================================================================

interface ApplyForEventRequest {
  eventId?: string;
}

export const applyForEvent = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to apply.");
    }

    const userId = context.auth.uid;
    const request = data as ApplyForEventRequest;
    const eventId = String(request?.eventId || "").trim();
    if (!eventId) {
      throw new functions.https.HttpsError("invalid-argument", "Missing required field: eventId.");
    }

    const userRef = db.collection("users").doc(userId);
    const eventRef = db.collection("events").doc(eventId);
    const applicationRef = eventRef.collection("applications").doc(userId);

    const result = await db.runTransaction(async (transaction) => {
      const [userDoc, eventDoc, applicationDoc] = await Promise.all([
        transaction.get(userRef),
        transaction.get(eventRef),
        transaction.get(applicationRef),
      ]);

      if (!userDoc.exists) {
        throw new functions.https.HttpsError("not-found", "User profile not found.");
      }
      if (!eventDoc.exists) {
        throw new functions.https.HttpsError("not-found", "Event not found.");
      }
      if (applicationDoc.exists) {
        throw new functions.https.HttpsError("failed-precondition", "You have already applied for this event.");
      }

      const eventData = eventDoc.data() || {};
      const organizerId = String(eventData.organizerId || "").trim();
      if (!organizerId) {
        throw new functions.https.HttpsError("failed-precondition", "Event organizer is missing.");
      }
      if (organizerId === userId) {
        throw new functions.https.HttpsError("failed-precondition", "Organizers cannot apply to their own event.");
      }
      if (Boolean(eventData.closeEntries) === true) {
        throw new functions.https.HttpsError("failed-precondition", "Event entries are closed.");
      }

      const eventTitle = String(eventData.title || "Event");
      const eventFeeRaw = Number(eventData.eventFee ?? eventData.payment ?? 0);
      const eventFee = Number.isFinite(eventFeeRaw) ? roundMoney(Math.max(0, eventFeeRaw)) : 0;
      const ownerFeeAmount = eventFee > 0 ? roundMoney(eventFee * EVENT_TICKET_OWNER_FEE_RATE) : 0;
      const organizerNetAmount = eventFee > 0 ? roundMoney(eventFee - ownerFeeAmount) : 0;

      const timestamp = admin.firestore.Timestamp.now();
      const userBalance = Number(userDoc.data()?.wallet?.balance || 0);
      if (eventFee > 0 && userBalance < eventFee) {
        throw new functions.https.HttpsError("failed-precondition", "Insufficient wallet balance.");
      }

      if (eventFee > 0) {
        transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-eventFee));
        transaction.set(userRef.collection("transactions").doc(), {
          title: `Event Ticket: ${eventTitle}`,
          amount: -eventFee,
          type: "DEBIT",
          status: "COMPLETED",
          timestamp,
          source: "EVENT_TICKET",
          note: `Paid for event ${eventId}. Owner fee (5%): $${ownerFeeAmount.toFixed(2)}.`,
        });

        const organizerRef = db.collection("users").doc(organizerId);
        transaction.set(organizerRef, {
          wallet: {
            balance: admin.firestore.FieldValue.increment(organizerNetAmount),
          },
        }, {merge: true});
        transaction.set(organizerRef.collection("transactions").doc(), {
          title: `Event Ticket Sale: ${eventTitle}`,
          amount: organizerNetAmount,
          type: "CREDIT",
          status: "COMPLETED",
          timestamp,
          source: "EVENT_TICKET_SALE",
          note: `Net from event ${eventId} after 5% owner fee.`,
          relatedUserId: userId,
        });

        if (ownerFeeAmount > 0) {
          recordPlatformRevenue(transaction, {
            source: "eventTicketOwnerFee",
            amount: ownerFeeAmount,
            note: `5% owner fee from event ${eventId}`,
            relatedUserId: userId,
          });
        }
      } else {
        transaction.set(userRef.collection("transactions").doc(), {
          title: `Event Sign-up: ${eventTitle}`,
          amount: 0,
          type: "INFO",
          status: "COMPLETED",
          timestamp,
          source: "EVENT_SIGNUP_FREE",
          note: "Free event sign-up",
        });
      }

      transaction.set(applicationRef, {
        applicationId: applicationRef.id,
        eventId,
        eventTitle,
        organizerId,
        organizerUid: organizerId,
        volunteerUid: userId,
        volunteerId: userId,
        userId,
        volunteerName: String(userDoc.data()?.name || userDoc.data()?.displayName || "Volunteer"),
        volunteerEmail: String(userDoc.data()?.email || context.auth?.token?.email || ""),
        status: eventFee > 0 ? "APPROVED" : "PENDING",
        transactionAmount: organizerNetAmount,
        ticketPrice: eventFee,
        ownerFeeRate: EVENT_TICKET_OWNER_FEE_RATE,
        ownerFeeAmount,
        organizerNetAmount,
        currency: "USD",
        paymentStatus: eventFee > 0 ? "PAID" : "NOT_REQUIRED",
        appliedDate: timestamp,
        paymentProcessedAt: eventFee > 0 ? timestamp : null,
      });

      return {
        success: true,
        eventId,
        charged: eventFee > 0,
        ticketPrice: eventFee,
        ownerFeeRate: EVENT_TICKET_OWNER_FEE_RATE,
        ownerFeeAmount,
        organizerNetAmount,
      };
    });

    return result;
  });


// =============================================================================
//  4. BLIND DATE FUNCTION (UPDATED)
// =============================================================================

interface JoinBlindDateRequest {
  mediaUrls: string[];
  bio: string;
  gender: string;
}

export const joinBlindDate = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to join.");
    }
    const userId = context.auth.uid;
    const requestData = data as JoinBlindDateRequest;
    if (!requestData.mediaUrls || !Array.isArray(requestData.mediaUrls) || requestData.mediaUrls.length === 0 || !requestData.bio || !requestData.gender) {
      throw new functions.https.HttpsError("invalid-argument", "Missing required data: mediaUrls, bio, or gender.");
    }

    const blindDateFee = Number.parseFloat(getAppConfig().blindDateFee);
    const userRef = db.collection("users").doc(userId);
    const blindDateProfileRef = db.collection("blindDateProfiles").doc(userId);
    const userRecord = await admin.auth().getUser(userId);

    functions.logger.log(`User ${userId} attempting to join blind date for a fee of ${blindDateFee}.`);

    try {
      let feeCharged = blindDateFee;
      await db.runTransaction(async (transaction) => {
        const userDoc = await transaction.get(userRef);
        if (!userDoc.exists) throw new functions.https.HttpsError("not-found", "User profile not found.");
        const userData = (userDoc.data() || {}) as Record<string, unknown>;
        const isStaffOrOwner = isStaffFeeExempt(
          userData,
          (context.auth?.token || {}) as Record<string, unknown>
        );
        const now = admin.firestore.Timestamp.now();
        if (!isStaffOrOwner) {
          const userBalance = Number(userDoc.data()?.wallet?.balance ?? 0);
          if (userBalance < blindDateFee) {
            throw new functions.https.HttpsError("failed-precondition", "Insufficient funds. Please top up your wallet.");
          }
          transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-blindDateFee));
          const transactionRef = userRef.collection("transactions").doc();
          transaction.set(transactionRef, {
            title: "Blind Date Entry Fee",
            amount: -blindDateFee,
            type: "DEBIT",
            status: "COMPLETED",
            timestamp: now,
            source: "BLIND_DATE_FEE",
          });
          recordPlatformRevenue(transaction, {
            source: "blindDateFees",
            amount: blindDateFee,
            note: "Blind Date Entry Fee",
            relatedUserId: userId,
          });
        } else {
          feeCharged = 0;
          const transactionRef = userRef.collection("transactions").doc();
          transaction.set(transactionRef, {
            title: "Blind Date Entry (Staff Exempt)",
            amount: 0,
            type: "INFO",
            status: "COMPLETED",
            timestamp: now,
            source: "BLIND_DATE_STAFF_EXEMPT",
            note: "Owner/admin/associate access applied. No fee charged.",
          });
        }
        const profileData = {userId: userId, name: userRecord.displayName ?? "Anonymous User", profilePictureUrl: userRecord.photoURL ?? "", gender: requestData.gender, media: requestData.mediaUrls, bio: requestData.bio, status: "active", createdAt: admin.firestore.Timestamp.now()};
        transaction.set(blindDateProfileRef, profileData);
      });
      functions.logger.log(`User ${userId} successfully joined the blind date.`);
      return {
        success: true,
        charged: feeCharged > 0,
        feeCharged,
        message: feeCharged > 0 ?
          "Successfully joined the blind date." :
          "Staff/owner exemption applied. You joined with no fee.",
      };
    } catch (error) {
      functions.logger.error(`Failed to join blind date for user ${userId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "An unexpected error occurred. Please try again.");
    }
  });


// =============================================================================
//  5. ACCEPT INVITATION FUNCTION (UPDATED)
// =============================================================================

interface AcceptInvitationRequest {
  senderId: string;
}

export const acceptBlindDateInvitation = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const acceptorId = context.auth.uid;
    const senderId = (data as AcceptInvitationRequest).senderId;
    if (!senderId) throw new functions.https.HttpsError("invalid-argument", "The 'senderId' is required.");

    const acceptorProfileRef = db.collection("blindDateProfiles").doc(acceptorId);
    const senderProfileRef = db.collection("blindDateProfiles").doc(senderId);
    const acceptorInviteRef = db.collection("users").doc(acceptorId).collection("blindDateInvitations").doc(senderId);
    const senderSentInviteRef = db.collection("users").doc(senderId).collection("blindDateSentInvitations").doc(acceptorId);
    const newChatRef = db.collection("chats").doc();

    functions.logger.log(`User ${acceptorId} is attempting to accept invitation from ${senderId}.`);
    try {
      await db.runTransaction(async (transaction) => {
        const acceptorProfile = await transaction.get(acceptorProfileRef);
        const senderProfile = await transaction.get(senderProfileRef);
        const acceptorInvite = await transaction.get(acceptorInviteRef);

        if (!acceptorProfile.exists || acceptorProfile.data()?.status !== "active") throw new functions.https.HttpsError("failed-precondition", "Your profile is not active for matching.");
        if (!senderProfile.exists || senderProfile.data()?.status !== "active") throw new functions.https.HttpsError("failed-precondition", "The other user is no longer available for matching.");
        if (!acceptorInvite.exists) throw new functions.https.HttpsError("not-found", "Invitation not found.");
        const invitationStatus = String(acceptorInvite.data()?.status || "").toLowerCase();
        if (invitationStatus !== "pending") throw new functions.https.HttpsError("failed-precondition", "This invitation is no longer pending.");

        const now = admin.firestore.Timestamp.now();
        const chatData = {chatId: newChatRef.id, participants: [acceptorId, senderId], lastMessageText: "Blind Date match! Say hi.", lastMessageTimestamp: now};
        transaction.set(newChatRef, chatData);
        transaction.update(acceptorProfileRef, "status", "matched");
        transaction.update(senderProfileRef, "status", "matched");
        transaction.update(acceptorInviteRef, {
          status: "accepted",
          updatedAt: now,
          respondedAt: now,
          matchedChatId: newChatRef.id,
        });
        transaction.set(senderSentInviteRef, {
          status: "matched",
          updatedAt: now,
          respondedAt: now,
          matchedChatId: newChatRef.id,
        }, {merge: true});
      });
      functions.logger.log(`Successfully matched users ${acceptorId} and ${senderId}. Chat ID: ${newChatRef.id}`);
      return {success: true, chatId: newChatRef.id, otherUserId: senderId};
    } catch (error) {
      functions.logger.error(`Match creation failed between ${acceptorId} and ${senderId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "Failed to accept the invitation. Please try again.");
    }
  });

interface DeclineInvitationRequest {
  senderId: string;
}

export const declineBlindDateInvitation = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const recipientId = context.auth.uid;
    const senderId = (data as DeclineInvitationRequest).senderId;
    if (!senderId) throw new functions.https.HttpsError("invalid-argument", "The 'senderId' is required.");

    const recipientInviteRef = db.collection("users").doc(recipientId).collection("blindDateInvitations").doc(senderId);
    const senderSentInviteRef = db.collection("users").doc(senderId).collection("blindDateSentInvitations").doc(recipientId);

    functions.logger.log(`User ${recipientId} is declining invitation from ${senderId}.`);
    try {
      await db.runTransaction(async (transaction) => {
        const recipientInvite = await transaction.get(recipientInviteRef);
        if (!recipientInvite.exists) throw new functions.https.HttpsError("not-found", "Invitation not found.");
        const invitationStatus = String(recipientInvite.data()?.status || "").toLowerCase();
        if (invitationStatus !== "pending") throw new functions.https.HttpsError("failed-precondition", "This invitation is no longer pending.");

        const now = admin.firestore.Timestamp.now();
        transaction.update(recipientInviteRef, {
          status: "declined",
          updatedAt: now,
          respondedAt: now,
        });
        transaction.set(senderSentInviteRef, {
          status: "declined",
          updatedAt: now,
          respondedAt: now,
        }, {merge: true});
      });
      return {success: true, message: "Invitation declined."};
    } catch (error) {
      functions.logger.error(`Failed to decline invitation for ${recipientId} from ${senderId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "Failed to decline invitation. Please try again.");
    }
  });


// =============================================================================
//  6. RE-JOIN BLIND DATE FUNCTION (UPDATED)
// =============================================================================

export const rejoinBlindDate = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in to rejoin.");
    const userId = context.auth.uid;
    const blindDateFee = Number.parseFloat(getAppConfig().blindDateFee);
    const userRef = db.collection("users").doc(userId);
    const blindDateProfileRef = db.collection("blindDateProfiles").doc(userId);
    functions.logger.log(`User ${userId} attempting to RE-JOIN blind date for a fee of ${blindDateFee}.`);
    try {
      let feeCharged = blindDateFee;
      await db.runTransaction(async (transaction) => {
        const userDoc = await transaction.get(userRef);
        const blindDateProfileDoc = await transaction.get(blindDateProfileRef);

        if (!userDoc.exists) throw new functions.https.HttpsError("not-found", "User profile not found.");
        if (!blindDateProfileDoc.exists) throw new functions.https.HttpsError("failed-precondition", "You do not have an existing blind date profile to rejoin.");
        if (blindDateProfileDoc.data()?.status !== "matched") throw new functions.https.HttpsError("failed-precondition", "You are not in a 'matched' state. You might already be active.");
        const userData = (userDoc.data() || {}) as Record<string, unknown>;
        const isStaffOrOwner = isStaffFeeExempt(
          userData,
          (context.auth?.token || {}) as Record<string, unknown>
        );
        const now = admin.firestore.Timestamp.now();
        if (!isStaffOrOwner) {
          const userBalance = Number(userDoc.data()?.wallet?.balance ?? 0);
          if (userBalance < blindDateFee) {
            throw new functions.https.HttpsError("failed-precondition", "Insufficient funds. Please top up your wallet.");
          }
          transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-blindDateFee));
          const transactionRef = userRef.collection("transactions").doc();
          transaction.set(transactionRef, {
            title: "Blind Date Re-join Fee",
            amount: -blindDateFee,
            type: "DEBIT",
            status: "COMPLETED",
            timestamp: now,
            source: "BLIND_DATE_REJOIN_FEE",
          });
          recordPlatformRevenue(transaction, {
            source: "blindDateFees",
            amount: blindDateFee,
            note: "Blind Date Re-join Fee",
            relatedUserId: userId,
          });
        } else {
          feeCharged = 0;
          const transactionRef = userRef.collection("transactions").doc();
          transaction.set(transactionRef, {
            title: "Blind Date Re-join (Staff Exempt)",
            amount: 0,
            type: "INFO",
            status: "COMPLETED",
            timestamp: now,
            source: "BLIND_DATE_STAFF_EXEMPT",
            note: "Owner/admin/associate access applied. No fee charged.",
          });
        }
        transaction.update(blindDateProfileRef, {status: "active"});
      });
      functions.logger.log(`User ${userId} successfully RE-JOINED the blind date.`);
      return {
        success: true,
        charged: feeCharged > 0,
        feeCharged,
        message: feeCharged > 0 ?
          "Payment successful! You are back in the loop." :
          "Staff/owner exemption applied. You are back in the loop with no fee.",
      };
    } catch (error) {
      functions.logger.error(`Failed to rejoin blind date for user ${userId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "An unexpected error occurred. Please try again.");
    }
  });


// =============================================================================
//  8. EXCHANGE RATE FUNCTION (UPDATED)
// =============================================================================

interface CalculateExchangeRequest {
  fromCurrency: string;
  toCurrency: string;
}

export const getSecureExchangeRate = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    functions.logger.log("=== getSecureExchangeRate CALLED ===");
    functions.logger.log("Received data:", JSON.stringify(data));
    functions.logger.log("Context auth UID:", context.auth?.uid || null);
    functions.logger.log("Context app ID:", context.app?.appId || null);

    try {
      functions.logger.log("--- START getSecureExchangeRate FUNCTION EXECUTION ---");

      const requestData = data as CalculateExchangeRequest;
      functions.logger.log("Parsed request data:", JSON.stringify(requestData));
      if (!requestData.fromCurrency || !requestData.toCurrency) throw new functions.https.HttpsError("invalid-argument", "Missing 'fromCurrency' or 'toCurrency'.");
      if (requestData.fromCurrency === requestData.toCurrency) return {success: true, rate: 1};

      let isStaffOrOwner = false;
      if (context.auth?.uid) {
        const requesterSnap = await db.collection("users").doc(context.auth.uid).get();
        const requesterData = (requesterSnap.data() || {}) as Record<string, unknown>;
        isStaffOrOwner = isStaffFeeExempt(
          requesterData,
          (context.auth?.token || {}) as Record<string, unknown>
        );
      }

      const apiKey = getApiKeys().exchangeRate;
      if (!apiKey) {
        functions.logger.error("EXCHANGE_RATE_API_KEY is not set in environment variables.");
        throw new functions.https.HttpsError("internal", "Server configuration error: Missing API key.");
      }

      const url = `https://v6.exchangerate-api.com/v6/${apiKey}/pair/${requestData.fromCurrency}/${requestData.toCurrency}`;
      const response = await axios.get(url);
      const responseData = response.data as {result: string; conversion_rate: number};

      if (responseData.result === "success") {
        const realRate = responseData.conversion_rate;
        const profitMargin = Number.parseFloat(getAppConfig().forexProfitMargin);
        const appRate = isStaffOrOwner ? realRate : realRate * (1 - profitMargin);
        functions.logger.log(
          `Rate for ${requestData.fromCurrency}->${requestData.toCurrency}: Real=${realRate}, App=${appRate}, StaffExempt=${isStaffOrOwner}`
        );
        return {success: true, rate: appRate, staffFeeExempt: isStaffOrOwner};
      } else {
        throw new functions.https.HttpsError("not-found", "Could not fetch the exchange rate.");
      }
    } catch (error) {
      functions.logger.error("getSecureExchangeRate error:", error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "An unexpected error occurred while fetching the rate.");
    }
  });


// =============================================================================
//  9. AGENT AUTHORIZATION FUNCTION (UPDATED)
// =============================================================================

export const payForAgentRole = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    functions.logger.log("=== payForAgentRole CALLED ===", {
      authUid: context.auth?.uid || null,
      appId: context.app?.appId || null,
    });
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const userId = context.auth.uid;
    const userRef = db.collection("users").doc(userId);
    try {
      await db.runTransaction(async (transaction) => {
        const userDoc = await transaction.get(userRef);
        if (!userDoc.exists) throw new functions.https.HttpsError("not-found", "User profile not found.");
        if (userDoc.data()?.role === "agent") throw new functions.https.HttpsError("failed-precondition", "You are already an agent.");

        const userData = (userDoc.data() || {}) as Record<string, unknown>;
        const isStaffOrOwner = isStaffFeeExempt(
          userData,
          (context.auth?.token || {}) as Record<string, unknown>
        );
        const fee = isStaffOrOwner ? 0 : Number.parseFloat(getAppConfig().agentAuthorizationFeeUsd);
        functions.logger.log(`User ${userId} attempting to become an agent for a fee of $${fee}.`);
        const userBalance = userDoc.data()?.wallet?.balance ?? 0;
        if (userBalance < fee) throw new functions.https.HttpsError("failed-precondition", "Insufficient funds. Please top up your wallet.");
        if (fee > 0) {
          transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(-fee));
        }
        transaction.update(userRef, "role", "agent");
        const transactionRef = userRef.collection("transactions").doc();
        transaction.set(transactionRef, {
          title: fee > 0 ? "Agent Authorization Fee" : "Agent Authorization (Staff Exempt)",
          amount: fee > 0 ? -fee : 0,
          type: fee > 0 ? "DEBIT" : "INFO",
          status: "COMPLETED",
          timestamp: admin.firestore.Timestamp.now(),
          source: fee > 0 ? "AGENT_FEE" : "AGENT_FEE_STAFF_EXEMPT",
          note: fee > 0 ? null : "Staff fee exemption applied.",
        });
        if (fee > 0) {
          recordPlatformRevenue(transaction, {
            source: "agentAuthorizationFees",
            amount: fee,
            note: "Agent Authorization Fee",
            relatedUserId: userId,
          });
        }
      });
      functions.logger.log(`User ${userId} successfully became an agent.`);
      return {success: true, message: "Congratulations! You are now an authorized agent."};
    } catch (error) {
      functions.logger.error(`Agent authorization failed for user ${userId}:`, error);
      if (error instanceof functions.https.HttpsError) throw error;
      throw new functions.https.HttpsError("internal", "An unexpected error occurred.");
    }
  });


// =============================================================================
//  10. UNIFIED TRANSFER FUNCTION (COMPLETE & FINAL VERSION)
// =============================================================================

// (Your interfaces remain the same)
interface RecipientBeneficiary {
  id?: string;
  name: string;
  accountNumber: string;
  bankCode?: string;
  country: string;
  mobileNumber?: string;
  network?: string; // Network is important for some gateways
}

interface InitiateTransferRequest {
  recipientId?: string;
  recipientBeneficiary?: RecipientBeneficiary;
  beneficiaryVerificationId?: string;
  amount: number;
  fundingSourceType: "WALLET" | "EXTERNAL_CARD" | "MOBILE_MONEY" | "EXTERNAL_MOBILE_MONEY" | "EXTERNAL_BANK";
  destinationType?: "WALLET" | "CARD" | "BANK";
  recipientPaymentMethodId?: string;
  recipientExternalAccountId?: string;
  fundingPaymentMethodId?: string;
  prioritizeExternalFunding?: boolean;
}

interface CreateBeneficiaryVerificationRequest {
  recipientBeneficiary?: RecipientBeneficiary;
  amount?: number;
  currency?: string;
}

type BeneficiaryVerificationStatus =
  | "APPROVED"
  | "REJECTED"
  | "UNVERIFIED"
  | "EXPIRED"
  | "CONSUMED";

type BeneficiaryVerificationMatchLevel =
  | "PHONE_AND_PROVIDER_CONFIRMED"
  | "PHONE_ONLY"
  | "MISMATCH";

const mobileMoneyCurrencyMap: { [key: string]: string } = {
  "Algeria": "DZD",
  "Angola": "AOA",
  "Benin": "XOF",
  "Botswana": "BWP",
  "Burkina Faso": "XOF",
  "Burundi": "BIF",
  "Cabo Verde": "CVE",
  "Cameroon": "XAF",
  "Central African Republic": "XAF",
  "Chad": "XAF",
  "Comoros": "KMF",
  "Congo": "CDF",
  "Cote d'Ivoire": "XOF",
  "Democratic Republic of the Congo": "CDF",
  "Djibouti": "DJF",
  "Egypt": "EGP",
  "Equatorial Guinea": "XAF",
  "Eritrea": "ERN",
  "Eswatini": "SZL",
  "Ethiopia": "ETB",
  "Gabon": "XAF",
  "Gambia": "GMD",
  "Ghana": "GHS",
  "Guinea": "GNF",
  "Guinea-Bissau": "XOF",
  "Kenya": "KES",
  "Lesotho": "LSL",
  "Liberia": "LRD",
  "Libya": "LYD",
  "Madagascar": "MGA",
  "Malawi": "MWK",
  "Mali": "XOF",
  "Mauritania": "MRU",
  "Mauritius": "MUR",
  "Morocco": "MAD",
  "Mozambique": "MZN",
  "Namibia": "NAD",
  "Niger": "XOF",
  "Nigeria": "NGN",
  "Republic of the Congo": "XAF",
  "Rwanda": "RWF",
  "Sao Tome and Principe": "STN",
  "Senegal": "XOF",
  "Seychelles": "SCR",
  "Sierra Leone": "SLL",
  "Somalia": "SOS",
  "South Africa": "ZAR",
  "South Sudan": "SSP",
  "Sudan": "SDG",
  "Tanzania": "TZS",
  "Togo": "XOF",
  "Tunisia": "TND",
  "Uganda": "UGX",
  "Zambia": "ZMW",
  "Zimbabwe": "ZWL",
};

const pawaPaySupportedCountries = new Set<string>([
  "Burkina Faso",
  "Republic of the Congo",
  "Democratic Republic of the Congo",
  "Ethiopia",
  "Gabon",
  "Cote d'Ivoire",
  "Malawi",
  "Rwanda",
  "Senegal",
  "Sierra Leone",
  "Zambia",
]);

const pawaPayCountryAliases: Record<string, string> = {
  "burkina faso": "Burkina Faso",
  "burkina-faso": "Burkina Faso",
  "congo brazzaville": "Republic of the Congo",
  "congo-brazzaville": "Republic of the Congo",
  "republic of congo": "Republic of the Congo",
  "congo republic": "Republic of the Congo",
  "drc": "Democratic Republic of the Congo",
  "dr congo": "Democratic Republic of the Congo",
  "congo kinshasa": "Democratic Republic of the Congo",
  "democratic republic of congo": "Democratic Republic of the Congo",
  "democratic republic of the congo": "Democratic Republic of the Congo",
  "ethiopia": "Ethiopia",
  "ethopia": "Ethiopia",
  "gabon": "Gabon",
  "ivory coast": "Cote d'Ivoire",
  "ivorycoast": "Cote d'Ivoire",
  "cote divoire": "Cote d'Ivoire",
  "cote d'ivoire": "Cote d'Ivoire",
  "cote d’ivoire": "Cote d'Ivoire",
  "malawi": "Malawi",
  "rwanda": "Rwanda",
  "senegal": "Senegal",
  "sierra leone": "Sierra Leone",
  "sierraleone": "Sierra Leone",
  "zambia": "Zambia",
};

const pawaPayIso3ByCountry: Record<string, string> = {
  "Burkina Faso": "BFA",
  "Republic of the Congo": "COG",
  "Democratic Republic of the Congo": "COD",
  "Ethiopia": "ETH",
  "Gabon": "GAB",
  "Cote d'Ivoire": "CIV",
  "Malawi": "MWI",
  "Rwanda": "RWA",
  "Senegal": "SEN",
  "Sierra Leone": "SLE",
  "Zambia": "ZMB",
};

const normalizeCountryKey = (value: string): string =>
  value.trim().toLowerCase().replace(/[_-]+/g, " ").replace(/\s+/g, " ");

const canonicalMobileMoneyCountry = (value: unknown): string | null => {
  if (typeof value !== "string" || !value.trim()) return null;
  const normalized = normalizeCountryKey(value);
  const aliasHit = pawaPayCountryAliases[normalized];
  if (aliasHit) return aliasHit;

  for (const country of Object.keys(mobileMoneyCurrencyMap)) {
    if (normalizeCountryKey(country) === normalized) {
      return country;
    }
  }
  return null;
};

const getMobileMoneyProviderName = (): string =>
  String(process.env.MOBILE_MONEY_PROVIDER_NAME || "PAWAPAY").trim().toUpperCase();

const toUpperOrNull = (value: unknown): string | null => {
  if (typeof value !== "string" || !value.trim()) return null;
  return value.trim().toUpperCase();
};

const getPawaPayCountryIso3 = (canonicalCountry: string): string => {
  const iso3 = pawaPayIso3ByCountry[canonicalCountry];
  if (!iso3) {
    throw new Error(`Missing pawaPay ISO3 mapping for country ${canonicalCountry}.`);
  }
  return iso3;
};

const getSupportedCountriesForProvider = (providerName: string): string[] => {
  if (providerName === "PAWAPAY") {
    return [...pawaPaySupportedCountries];
  }
  return [];
};

const isMobileMoneyCountrySupportedByProvider = (
  providerName: string,
  canonicalCountry: string | null
): boolean => {
  if (!canonicalCountry) return false;
  if (providerName === "PAWAPAY") {
    return pawaPaySupportedCountries.has(canonicalCountry);
  }
  return true;
};

const assertCountrySupportedForConfiguredProvider = (country: unknown): string | null => {
  const canonicalCountry = canonicalMobileMoneyCountry(country);
  const providerMode = String(process.env.MOBILE_MONEY_PROVIDER_MODE || "MANUAL").toUpperCase();
  if (providerMode !== "HTTP_API") {
    return canonicalCountry;
  }

  const providerName = getMobileMoneyProviderName();
  if (!isMobileMoneyCountrySupportedByProvider(providerName, canonicalCountry)) {
    const supported = getSupportedCountriesForProvider(providerName);
    const suffix = supported.length > 0 ?
      ` Supported countries for ${providerName}: ${supported.join(", ")}.` :
      "";
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Mobile money transfers for ${String(country || "this country")} are not supported by ${providerName}.${suffix}`
    );
  }

  return canonicalCountry;
};

const resolveMobileMoneyCurrency = (data: Record<string, unknown>): string | null => {
  const canonicalCountry = canonicalMobileMoneyCountry(data.country);
  return (data.localCurrency as string | undefined) ||
    ((data.currency as string | undefined)?.length === 3 ? (data.currency as string) : undefined) ||
    (canonicalCountry ? mobileMoneyCurrencyMap[canonicalCountry] : undefined) ||
    null;
};

type BeneficiaryVerificationProviderInfo = {
  providerMode: string;
  providerName: string;
  predictedProvider: string | null;
  activeConfValidated: boolean;
  providerAccountName: string | null;
  nameMatchScore: number | null;
  rawStatus: string | null;
};

type PreparedBeneficiaryVerificationInput = {
  nameInput: string;
  accountNumberInput: string;
  mobileNumberInput: string;
  networkInput: string;
  countryInput: string;
  countryCanonical: string | null;
  phoneDigits: string;
  phoneE164: string;
  amount: number | null;
  currency: string | null;
  fingerprint: string;
};

type BeneficiaryVerificationDecision = {
  status: BeneficiaryVerificationStatus;
  canProceed: boolean;
  matchLevel: BeneficiaryVerificationMatchLevel;
  reasonCode: string;
  reasonMessage: string;
  provider: BeneficiaryVerificationProviderInfo;
  aml: BeneficiaryVerificationAmlInfo;
};

type BeneficiaryVerificationUsage = {
  verificationId: string;
  status: string;
  matchLevel: string;
  reasonCode: string;
  reasonMessage: string;
  fingerprint: string;
  amlStatus: string | null;
  amlBlocked: boolean;
  amlMatchCount: number;
  amlTopMatchName: string | null;
  checkedAt: admin.firestore.Timestamp;
};

type BeneficiaryVerificationAmlInfo = {
  provider: "DILISENSE";
  enabled: boolean;
  screened: boolean;
  blocked: boolean;
  status: "NOT_CONFIGURED" | "SKIPPED" | "CLEAR" | "POTENTIAL_MATCH" | "ERROR";
  reasonCode: string | null;
  reasonMessage: string | null;
  matchCount: number;
  topMatchName: string | null;
  rawStatus: string | null;
};

type DilisenseConfig = {
  enabled: boolean;
  apiKey: string;
  baseUrl: string;
  timeoutMs: number;
  fuzzySearch: 0 | 1;
  includes: string | null;
  blockOnMatch: boolean;
  failClosed: boolean;
};

const normalizeBeneficiaryName = (value: unknown): string =>
  String(value || "").trim().replace(/\s+/g, " ").toLowerCase();

const normalizeBeneficiaryNetwork = (value: unknown): string =>
  String(value || "").trim().toUpperCase();

const normalizeBeneficiaryPhoneDigits = (value: unknown): string =>
  String(value || "").replace(/\D/g, "");

const normalizeBeneficiaryPhoneE164 = (value: unknown): string | null => {
  const raw = String(value || "").trim();
  const digits = normalizeBeneficiaryPhoneDigits(raw);
  if (!digits) return null;
  if (raw.startsWith("+")) return `+${digits}`;
  return digits;
};

const toTimestampMillis = (value: unknown): number | null => {
  if (value instanceof admin.firestore.Timestamp) return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return null;
};

const parseBooleanEnv = (value: string | undefined, fallback: boolean): boolean => {
  const normalized = String(value || "").trim().toLowerCase();
  if (!normalized) return fallback;
  if (["1", "true", "yes", "y", "on"].includes(normalized)) return true;
  if (["0", "false", "no", "n", "off"].includes(normalized)) return false;
  return fallback;
};

const toRecordArray = (value: unknown): Record<string, unknown>[] => {
  if (!Array.isArray(value)) return [];
  return value.filter((item) => item && typeof item === "object") as Record<string, unknown>[];
};

const extractDilisenseMatches = (value: unknown): Record<string, unknown>[] => {
  if (Array.isArray(value)) {
    return toRecordArray(value);
  }
  if (!value || typeof value !== "object") {
    return [];
  }
  const data = value as Record<string, unknown>;
  const candidateArrays = [
    data["matches"],
    data["results"],
    data["hits"],
    data["data"],
    data["items"],
    data["records"],
  ];
  for (const candidate of candidateArrays) {
    const parsed = toRecordArray(candidate);
    if (parsed.length > 0) return parsed;
  }
  return [];
};

const getDilisenseTopMatchName = (match: Record<string, unknown> | null): string | null => {
  if (!match) return null;
  return asNonEmptyString(
    match["name"],
    match["full_name"],
    match["entityName"],
    match["match_name"],
    match["search_name"]
  ) || null;
};

const buildNoopDilisenseResult = (status: BeneficiaryVerificationAmlInfo["status"], reasonCode: string, reasonMessage: string): BeneficiaryVerificationAmlInfo => ({
  provider: "DILISENSE",
  enabled: false,
  screened: false,
  blocked: false,
  status,
  reasonCode,
  reasonMessage,
  matchCount: 0,
  topMatchName: null,
  rawStatus: null,
});

const getDilisenseConfig = (): DilisenseConfig => {
  const apiKey = asNonEmptyString(process.env.DILISENSE_API_KEY) || "";
  const enabled = parseBooleanEnv(process.env.DILISENSE_ENABLED, true) && apiKey.length > 0;
  const baseUrl = asNonEmptyString(process.env.DILISENSE_BASE_URL) || "https://api.dilisense.com/v1";
  const timeoutRaw = Number(process.env.DILISENSE_TIMEOUT_MS || 10000);
  const timeoutMs = Number.isFinite(timeoutRaw) && timeoutRaw > 0 ? Math.trunc(timeoutRaw) : 10000;
  const fuzzyRaw = Number(process.env.DILISENSE_FUZZY_SEARCH || 1);
  const fuzzySearch: 0 | 1 = fuzzyRaw === 0 ? 0 : 1;
  const includes = asNonEmptyString(process.env.DILISENSE_INCLUDES) || null;
  const blockOnMatch = parseBooleanEnv(process.env.DILISENSE_BLOCK_ON_MATCH, true);
  const failClosed = parseBooleanEnv(process.env.DILISENSE_FAIL_CLOSED, true);

  return {
    enabled,
    apiKey,
    baseUrl,
    timeoutMs,
    fuzzySearch,
    includes,
    blockOnMatch,
    failClosed,
  };
};

const screenNameWithDilisense = async (name: string): Promise<BeneficiaryVerificationAmlInfo> => {
  const config = getDilisenseConfig();
  if (!config.enabled) {
    return buildNoopDilisenseResult(
      "NOT_CONFIGURED",
      "AML_PROVIDER_NOT_CONFIGURED",
      "Dilisense AML provider is not configured."
    );
  }

  const trimmedName = asNonEmptyString(name);
  if (!trimmedName) {
    return buildNoopDilisenseResult(
      "SKIPPED",
      "AML_NAME_EMPTY",
      "Beneficiary name is required for AML screening."
    );
  }

  const url = `${config.baseUrl.replace(/\/+$/, "")}/checkName`;
  const params: Record<string, string | number> = {
    names: trimmedName,
    fuzzy_search: config.fuzzySearch,
  };
  if (config.includes) {
    params.includes = config.includes;
  }

  try {
    const response = await axios.get(url, {
      headers: {
        "x-api-key": config.apiKey,
        "Accept": "application/json",
      },
      params,
      timeout: config.timeoutMs,
    });

    const matchRows = extractDilisenseMatches(response.data);
    const topMatch = matchRows.length > 0 ? matchRows[0] : null;
    const topMatchName = getDilisenseTopMatchName(topMatch);
    const blocked = config.blockOnMatch && matchRows.length > 0;
    const rawStatus = asNonEmptyString(
      (response.data as Record<string, unknown> | undefined)?.["status"],
      String(response.status)
    ) || null;

    return {
      provider: "DILISENSE",
      enabled: true,
      screened: true,
      blocked,
      status: blocked ? "POTENTIAL_MATCH" : "CLEAR",
      reasonCode: blocked ? "AML_POTENTIAL_MATCH" : null,
      reasonMessage: blocked ?
        `Potential AML/CFT match found for '${trimmedName}'.` :
        "No AML/CFT match found.",
      matchCount: matchRows.length,
      topMatchName,
      rawStatus,
    };
  } catch (error) {
    const errorMessage = parseProviderErrorMessage(error);
    const blocked = config.failClosed;
    return {
      provider: "DILISENSE",
      enabled: true,
      screened: false,
      blocked,
      status: "ERROR",
      reasonCode: blocked ? "AML_SCREENING_UNAVAILABLE" : "AML_SCREENING_WARNING",
      reasonMessage: `Dilisense screening unavailable: ${errorMessage}`,
      matchCount: 0,
      topMatchName: null,
      rawStatus: "ERROR",
    };
  }
};

const getBeneficiaryVerificationTtlMs = (): number => {
  const ttlSecondsRaw = Number(process.env.BENEFICIARY_VERIFICATION_TTL_SECONDS || 900);
  const ttlSeconds = Number.isFinite(ttlSecondsRaw) ? Math.trunc(ttlSecondsRaw) : 900;
  const boundedSeconds = Math.max(60, Math.min(24 * 60 * 60, ttlSeconds));
  return boundedSeconds * 1000;
};

const buildBeneficiaryVerificationFingerprint = (params: {
  name: unknown;
  phone: unknown;
  network: unknown;
  country: unknown;
  currency: unknown;
}): string => {
  const canonicalCountry = canonicalMobileMoneyCountry(params.country) || "";
  const payload = [
    normalizeBeneficiaryName(params.name),
    normalizeBeneficiaryPhoneDigits(params.phone),
    normalizeBeneficiaryNetwork(params.network),
    canonicalCountry.toUpperCase(),
    String(params.currency || "").trim().toUpperCase(),
  ].join("|");
  return createHash("sha256").update(payload).digest("hex");
};

const getBeneficiaryVerificationCurrency = (params: {
  beneficiary: RecipientBeneficiary;
  requestedCurrency?: unknown;
}): string | null => {
  const explicitCurrency = asNonEmptyString(params.requestedCurrency)?.toUpperCase();
  if (explicitCurrency && explicitCurrency.length === 3) {
    return explicitCurrency;
  }
  return resolveMobileMoneyCurrency({
    country: params.beneficiary.country,
    currency: explicitCurrency || undefined,
    localCurrency: explicitCurrency || undefined,
  });
};

const prepareBeneficiaryVerificationInput = (params: {
  beneficiary: RecipientBeneficiary;
  amount?: unknown;
  requestedCurrency?: unknown;
}): PreparedBeneficiaryVerificationInput => {
  const nameInput = asNonEmptyString(params.beneficiary.name) || "";
  const accountNumberInput = asNonEmptyString(params.beneficiary.accountNumber) || "";
  const mobileNumberInput = asNonEmptyString(
    params.beneficiary.mobileNumber,
    params.beneficiary.accountNumber
  ) || "";
  const networkInput = asNonEmptyString(params.beneficiary.network) || "";
  const countryInput = asNonEmptyString(params.beneficiary.country) || "";
  const countryCanonical = canonicalMobileMoneyCountry(countryInput);
  const phoneDigits = normalizeBeneficiaryPhoneDigits(mobileNumberInput);
  const phoneE164 = normalizeBeneficiaryPhoneE164(mobileNumberInput) || "";
  const amountRaw = Number(params.amount);
  const amount = Number.isFinite(amountRaw) && amountRaw > 0 ? roundMoney(amountRaw) : null;
  const currency = getBeneficiaryVerificationCurrency({
    beneficiary: {
      ...params.beneficiary,
      country: countryCanonical || countryInput,
    },
    requestedCurrency: params.requestedCurrency,
  });
  const fingerprint = buildBeneficiaryVerificationFingerprint({
    name: nameInput,
    phone: phoneDigits,
    network: networkInput,
    country: countryCanonical || countryInput,
    currency: currency || "",
  });

  return {
    nameInput,
    accountNumberInput,
    mobileNumberInput,
    networkInput,
    countryInput,
    countryCanonical,
    phoneDigits,
    phoneE164,
    amount,
    currency,
    fingerprint,
  };
};

const getMobileMoneyProviderHttpOptions = (): {
  providerName: string;
  providerUrl: string;
  headers: Record<string, string>;
  timeoutMs: number;
} => {
  const providerUrl = process.env.MOBILE_MONEY_PROVIDER_URL;
  const providerApiKey = process.env.MOBILE_MONEY_PROVIDER_API_KEY;

  if (!providerUrl) {
    throw new Error("MOBILE_MONEY_PROVIDER_URL is not configured.");
  }
  if (!providerApiKey) {
    throw new Error("MOBILE_MONEY_PROVIDER_API_KEY is not configured.");
  }

  const authHeader = asNonEmptyString(process.env.MOBILE_MONEY_PROVIDER_AUTH_HEADER) || "Authorization";
  const keyPrefix = process.env.MOBILE_MONEY_PROVIDER_API_KEY_PREFIX;
  const timeoutMsRaw = Number(process.env.MOBILE_MONEY_PROVIDER_TIMEOUT_MS || 30000);
  const timeoutMs = Number.isFinite(timeoutMsRaw) && timeoutMsRaw > 0 ? timeoutMsRaw : 30000;
  const providerName = getMobileMoneyProviderName();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    [authHeader]: keyPrefix === "" ? providerApiKey : `${keyPrefix || "Bearer"} ${providerApiKey}`,
  };

  return {
    providerName,
    providerUrl,
    headers,
    timeoutMs,
  };
};

const runBeneficiaryVerificationDecision = async (params: {
  prepared: PreparedBeneficiaryVerificationInput;
}): Promise<BeneficiaryVerificationDecision> => {
  const providerMode = String(process.env.MOBILE_MONEY_PROVIDER_MODE || "MANUAL").toUpperCase();
  const providerName = getMobileMoneyProviderName();
  const provider: BeneficiaryVerificationProviderInfo = {
    providerMode,
    providerName,
    predictedProvider: null,
    activeConfValidated: false,
    providerAccountName: null,
    nameMatchScore: null,
    rawStatus: null,
  };
  const amlNotRun = buildNoopDilisenseResult(
    "SKIPPED",
    "AML_NOT_RUN",
    "AML screening was not executed."
  );

  if (!params.prepared.nameInput || !params.prepared.phoneDigits || !params.prepared.countryInput) {
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "INVALID_BENEFICIARY_INPUT",
      reasonMessage: "Beneficiary name, phone, and country are required.",
      provider,
      aml: amlNotRun,
    };
  }

  if (!params.prepared.countryCanonical) {
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "UNSUPPORTED_COUNTRY",
      reasonMessage: `Country ${params.prepared.countryInput} is not supported.`,
      provider,
      aml: amlNotRun,
    };
  }

  const aml = await screenNameWithDilisense(params.prepared.nameInput);
  if (aml.blocked) {
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: aml.reasonCode || "AML_SCREENING_BLOCKED",
      reasonMessage: aml.reasonMessage || "Transfer blocked by AML/CFT screening.",
      provider,
      aml,
    };
  }

  if (providerMode !== "HTTP_API") {
    provider.rawStatus = "BYPASSED_NON_HTTP_MODE";
    return {
      status: "APPROVED",
      canProceed: true,
      matchLevel: "PHONE_ONLY",
      reasonCode: "VERIFIED_PHONE_ONLY",
      reasonMessage: "Provider API checks are bypassed in non-HTTP provider mode.",
      provider,
      aml,
    };
  }

  if (!isMobileMoneyCountrySupportedByProvider(providerName, params.prepared.countryCanonical)) {
    const supported = getSupportedCountriesForProvider(providerName);
    const suffix = supported.length > 0 ?
      ` Supported countries: ${supported.join(", ")}.` :
      "";
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "COUNTRY_NOT_SUPPORTED_BY_PROVIDER",
      reasonMessage: `Country ${params.prepared.countryCanonical} is not supported by ${providerName}.${suffix}`,
      provider,
      aml,
    };
  }

  let options: {providerName: string; providerUrl: string; headers: Record<string, string>; timeoutMs: number};
  try {
    options = getMobileMoneyProviderHttpOptions();
  } catch (error) {
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "PROVIDER_CONFIG_MISSING",
      reasonMessage: parseProviderErrorMessage(error),
      provider,
      aml,
    };
  }

  if (!params.prepared.currency) {
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "MISSING_CURRENCY",
      reasonMessage: "Unable to resolve payout currency for this beneficiary.",
      provider,
      aml,
    };
  }

  if (providerName !== "PAWAPAY") {
    provider.rawStatus = "NO_PROVIDER_PRECHECK_IMPLEMENTATION";
    return {
      status: "APPROVED",
      canProceed: true,
      matchLevel: "PHONE_ONLY",
      reasonCode: "VERIFIED_PHONE_ONLY",
      reasonMessage: `${providerName} pre-check is not implemented; phone format checks passed.`,
      provider,
      aml,
    };
  }

  try {
    const predictProviderUrl = buildPawaPayPredictProviderUrl(options.providerUrl);
    const predictionResponse = await axios.post(
      predictProviderUrl,
      {phoneNumber: params.prepared.phoneDigits},
      {headers: options.headers, timeout: options.timeoutMs}
    );

    const predictionData = (predictionResponse.data || {}) as Record<string, unknown>;
    const predictedProvider = asNonEmptyString(predictionData["provider"]);
    if (!predictedProvider) {
      throw new Error("pawaPay provider prediction failed for the recipient number.");
    }

    const countryIso3 = getPawaPayCountryIso3(params.prepared.countryCanonical);
    await assertPawaPayProviderAndCurrencyEnabled(
      countryIso3,
      predictedProvider,
      params.prepared.currency,
      {
        payoutsUrl: options.providerUrl,
        headers: options.headers,
        timeoutMs: options.timeoutMs,
      }
    );

    provider.predictedProvider = predictedProvider;
    provider.activeConfValidated = true;
    provider.rawStatus = "OK";

    return {
      status: "APPROVED",
      canProceed: true,
      matchLevel: "PHONE_AND_PROVIDER_CONFIRMED",
      reasonCode: "VERIFIED_PROVIDER_ACTIVE_CONF",
      reasonMessage: "Recipient phone/provider checks passed.",
      provider,
      aml,
    };
  } catch (error) {
    provider.rawStatus = "FAILED";
    return {
      status: "REJECTED",
      canProceed: false,
      matchLevel: "MISMATCH",
      reasonCode: "PROVIDER_PRECHECK_FAILED",
      reasonMessage: parseProviderErrorMessage(error),
      provider,
      aml,
    };
  }
};

const readAndValidateBeneficiaryVerificationData = (params: {
  data: FirebaseFirestore.DocumentData;
  senderId: string;
  expectedFingerprint: string;
  nowMs: number;
}): BeneficiaryVerificationUsage => {
  const data = params.data || {};
  const docSenderId = String(data.senderId || "");
  if (!docSenderId || docSenderId !== params.senderId) {
    throw new functions.https.HttpsError("permission-denied", "Beneficiary verification does not belong to this sender.");
  }

  const status = String(data.status || "").trim().toUpperCase();
  const canProceed = data.canProceed === true;
  if (status !== "APPROVED" || !canProceed) {
    throw new functions.https.HttpsError("failed-precondition", "Beneficiary verification is not approved.");
  }

  if (data.consumedAt || data.consumedByPayoutRequestId) {
    throw new functions.https.HttpsError("failed-precondition", "Beneficiary verification was already used.");
  }

  const expiresAtMs = toTimestampMillis(data.expiresAt);
  if (expiresAtMs && expiresAtMs <= params.nowMs) {
    throw new functions.https.HttpsError("failed-precondition", "Beneficiary verification expired. Re-verify beneficiary.");
  }

  const fingerprint = String(data.fingerprint || "").trim().toLowerCase();
  if (!fingerprint || fingerprint !== params.expectedFingerprint.toLowerCase()) {
    throw new functions.https.HttpsError("failed-precondition", "Beneficiary verification does not match current transfer details.");
  }

  const aml = (data.aml || {}) as Record<string, unknown>;
  const amlStatus = asNonEmptyString(aml["status"]) || null;
  const amlBlocked = aml["blocked"] === true;
  const amlMatchCountRaw = Number(aml["matchCount"] || 0);
  const amlMatchCount = Number.isFinite(amlMatchCountRaw) ? Math.max(0, Math.trunc(amlMatchCountRaw)) : 0;
  const amlTopMatchName = asNonEmptyString(aml["topMatchName"]) || null;

  return {
    verificationId: "",
    status,
    matchLevel: String(data.matchLevel || "PHONE_ONLY"),
    reasonCode: String(data.reasonCode || ""),
    reasonMessage: String(data.reasonMessage || ""),
    fingerprint,
    amlStatus,
    amlBlocked,
    amlMatchCount,
    amlTopMatchName,
    checkedAt: admin.firestore.Timestamp.now(),
  };
};

const assertBeneficiaryVerificationReadyForUse = async (params: {
  verificationRef: FirebaseFirestore.DocumentReference;
  senderId: string;
  expectedFingerprint: string;
}): Promise<BeneficiaryVerificationUsage> => {
  const verificationSnap = await params.verificationRef.get();
  if (!verificationSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Beneficiary verification not found.");
  }
  const usage = readAndValidateBeneficiaryVerificationData({
    data: verificationSnap.data() || {},
    senderId: params.senderId,
    expectedFingerprint: params.expectedFingerprint,
    nowMs: Date.now(),
  });
  return {
    ...usage,
    verificationId: verificationSnap.id,
  };
};

const consumeBeneficiaryVerificationInTransaction = async (params: {
  transaction: FirebaseFirestore.Transaction;
  verificationRef: FirebaseFirestore.DocumentReference;
  senderId: string;
  expectedFingerprint: string;
  payoutRequestId: string;
}): Promise<BeneficiaryVerificationUsage> => {
  const verificationSnap = await params.transaction.get(params.verificationRef);
  if (!verificationSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Beneficiary verification not found.");
  }

  const usage = readAndValidateBeneficiaryVerificationData({
    data: verificationSnap.data() || {},
    senderId: params.senderId,
    expectedFingerprint: params.expectedFingerprint,
    nowMs: Date.now(),
  });

  const now = admin.firestore.Timestamp.now();
  params.transaction.set(params.verificationRef, {
    status: "CONSUMED",
    consumedAt: now,
    consumedByPayoutRequestId: params.payoutRequestId,
    updatedAt: now,
  }, {merge: true});

  return {
    ...usage,
    verificationId: verificationSnap.id,
    checkedAt: now,
  };
};

const normalizeCompletedTransactionTitle = (value: unknown): string => {
  return String(value || "")
    .replace(/\s*\(Pending\)\s*$/i, "")
    .trim();
};

const loadSenderTransactionsForPayout = async (
  senderId: string,
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Promise<FirebaseFirestore.DocumentSnapshot[]> => {
  const senderTxCollection = db.collection("users").doc(senderId).collection("transactions");
  const snapshotsById = new Map<string, FirebaseFirestore.DocumentSnapshot>();

  const senderTransactionIds = Array.isArray(payoutData.senderTransactionIds) ?
    payoutData.senderTransactionIds.filter((v: unknown): v is string => typeof v === "string" && v.trim().length > 0) :
    [];

  if (senderTransactionIds.length > 0) {
    const idSnapshots = await Promise.all(
      senderTransactionIds.map((id) => senderTxCollection.doc(id).get())
    );
    for (const snap of idSnapshots) {
      if (snap.exists) snapshotsById.set(snap.id, snap);
    }
  }

  const byPayoutId = await senderTxCollection
    .where("payoutRequestId", "==", payoutRef.id)
    .limit(100)
    .get();
  for (const snap of byPayoutId.docs) {
    snapshotsById.set(snap.id, snap);
  }

  if (snapshotsById.size > 0) {
    return [...snapshotsById.values()];
  }

  functions.logger.warn("No sender transactions found by payoutRequestId. Trying fallback match.", {
    senderId,
    payoutRequestId: payoutRef.id,
  });

  try {
    const pendingCandidates = await senderTxCollection
      .where("source", "==", "MOBILE_MONEY")
      .where("status", "in", ["PENDING", "PROCESSING", "PENDING_PROVIDER", "PROCESSING_PROVIDER"])
      .limit(100)
      .get();

    const recipientInfo = (payoutData.recipientInfo || {}) as {mobileNumber?: string};
    const recipientPhone = String(payoutData.recipientPhone || recipientInfo.mobileNumber || "");
    const payoutAmount = Number(payoutData.amount || 0);
    const createdAt = payoutData.createdAt as admin.firestore.Timestamp | undefined;

    for (const snap of pendingCandidates.docs) {
      const note = String(snap.get("note") || "");
      const txAmount = Number(snap.get("amount") || 0);
      const txTimestamp = snap.get("timestamp") as admin.firestore.Timestamp | undefined;

      const phoneMatches = recipientPhone ? note.includes(recipientPhone) : true;
      const amountMatches = payoutAmount > 0 ? Math.abs(Math.abs(txAmount) - payoutAmount) < 0.000001 : true;
      const timeMatches = createdAt && txTimestamp ?
        Math.abs(txTimestamp.toMillis() - createdAt.toMillis()) <= 15 * 60 * 1000 :
        true;

      if (phoneMatches && amountMatches && timeMatches) {
        snapshotsById.set(snap.id, snap);
      }
    }
  } catch (fallbackError) {
    functions.logger.warn("Fallback sender transaction lookup failed", {
      senderId,
      payoutRequestId: payoutRef.id,
      fallbackError,
    });
  }

  return [...snapshotsById.values()];
};

const finalizeSenderTransactionsForPayout = async (
  senderId: string,
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData,
  nextStatus: "COMPLETED" | "FAILED"
): Promise<number> => {
  const txSnapshots = await loadSenderTransactionsForPayout(senderId, payoutRef, payoutData);
  if (txSnapshots.length === 0) {
    functions.logger.warn("No sender transactions matched for payout status update", {
      senderId,
      payoutRequestId: payoutRef.id,
      nextStatus,
    });
    return 0;
  }

  let batch = db.batch();
  let ops = 0;
  let updated = 0;
  const now = admin.firestore.Timestamp.now();

  for (const snap of txSnapshots) {
    if (!snap.exists) continue;
    const currentStatus = String(snap.get("status") || "").toUpperCase();
    if (currentStatus === nextStatus) continue;
    if (currentStatus === "COMPLETED" || currentStatus === "FAILED") continue;

    const updates: Record<string, unknown> = {
      status: nextStatus,
      processedAt: now,
      payoutRequestId: payoutRef.id,
    };

    if (nextStatus === "COMPLETED") {
      const currentTitle = String(snap.get("title") || "");
      const normalizedTitle = normalizeCompletedTransactionTitle(currentTitle);
      if (normalizedTitle && normalizedTitle !== currentTitle) {
        updates.title = normalizedTitle;
      }
    }

    batch.set(snap.ref, updates, {merge: true});
    ops += 1;
    updated += 1;

    if (ops >= 400) {
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }

  if (ops > 0) {
    await batch.commit();
  }

  functions.logger.info("Sender transaction payout status reconciliation complete", {
    senderId,
    payoutRequestId: payoutRef.id,
    nextStatus,
    matched: txSnapshots.length,
    updated,
  });

  return updated;
};

type MobileMoneyProviderResultStatus = "COMPLETED" | "PROCESSING" | "FAILED";

type MobileMoneyProviderResult = {
  status: MobileMoneyProviderResultStatus;
  providerTransferId?: string;
  providerMessage?: string;
  rawStatus?: string;
};

const asNonEmptyString = (...values: unknown[]): string | undefined => {
  for (const value of values) {
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (trimmed.length > 0) return trimmed;
    }
  }
  return undefined;
};

const normalizeMobileMoneyProviderResultStatus = (value: unknown): MobileMoneyProviderResultStatus => {
  const raw = String(value || "").trim().toUpperCase();
  if (!raw) return "PROCESSING";

  if ([
    "COMPLETED",
    "COMPLETE",
    "SUCCESS",
    "SUCCEEDED",
    "SUCCESSFUL",
    "PAID",
  ].includes(raw)) {
    return "COMPLETED";
  }

  if ([
    "FAILED",
    "FAIL",
    "DECLINED",
    "REJECTED",
    "CANCELLED",
    "CANCELED",
    "ERROR",
  ].includes(raw)) {
    return "FAILED";
  }

  return "PROCESSING";
};

type AxiosLikeError = {
  response?: {
    data?: unknown;
  };
  message?: string;
};

const isAxiosLikeError = (value: unknown): value is AxiosLikeError => {
  if (!value || typeof value !== "object") return false;
  return "response" in value || "message" in value;
};

const parseProviderErrorMessage = (error: unknown): string => {
  if (isAxiosLikeError(error)) {
    const rawResponse = error.response?.data;
    const responseData = (
      Array.isArray(rawResponse) ?
        (rawResponse[0] as Record<string, unknown> | undefined) :
        (rawResponse as Record<string, unknown> | undefined)
    );
    const providerMessage = asNonEmptyString(
      responseData?.["message"],
      responseData?.["error"],
      responseData?.["detail"],
      responseData?.["reason"],
      responseData?.["failureReason"],
      responseData?.["failureCode"]
    );
    if (providerMessage) {
      return providerMessage;
    }

    if (error.message) {
      return error.message;
    }
  }

  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "Provider request failed.";
};

const recordMobileMoneyHiddenFeeRevenueIfNeeded = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData,
  senderId?: string
): Promise<void> => {
  await db.runTransaction(async (transaction) => {
    const payoutSnap = await transaction.get(payoutRef);
    if (!payoutSnap.exists) return;
    const latestData = payoutSnap.data() || payoutData || {};
    const hiddenFee = Number(latestData.hiddenFeeAmount || 0);
    if (hiddenFee <= 0 || latestData.hiddenFeeRevenueRecorded === true) return;

    if (senderId) {
      const senderRef = db.collection("users").doc(senderId);
      const senderSnap = await transaction.get(senderRef);
      const senderData = (senderSnap.data() || {}) as Record<string, unknown>;
      if (isStaffFeeExempt(senderData)) {
        transaction.set(payoutRef, {
          hiddenFeeAmount: 0,
          hiddenFeeRevenueRecorded: true,
          hiddenFeeRecordedAt: admin.firestore.Timestamp.now(),
          hiddenFeeExemptionReason: "STAFF_FEE_EXEMPT",
        }, {merge: true});
        return;
      }
    }

    const payoutType = String(latestData.type || "MOBILE_MONEY");
    recordPlatformRevenue(transaction, {
      source: "mobileMoneyHiddenFee",
      amount: hiddenFee,
      note: `Hidden mobile money fee (${payoutType})`,
      relatedUserId: senderId,
    });

    transaction.set(payoutRef, {
      hiddenFeeRevenueRecorded: true,
      hiddenFeeRecordedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});
  });
};

const buildMobileMoneyProviderPayload = (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Record<string, unknown> => {
  const recipientInfo = (payoutData.recipientInfo || {}) as Record<string, unknown>;
  const canonicalCountry = canonicalMobileMoneyCountry(
    asNonEmptyString(recipientInfo["country"], payoutData.country)
  );
  const transferType = String(payoutData.type || "").trim().toUpperCase();
  const walletAmount = Number(payoutData.amount || 0);
  const localAmountRaw = Number(payoutData.localAmount || 0);
  const providerAmount = transferType === "CASH_IN" && Number.isFinite(localAmountRaw) && localAmountRaw > 0 ?
    localAmountRaw :
    walletAmount;
  const providerCurrency = transferType === "CASH_IN" ?
    asNonEmptyString(payoutData.localCurrency, payoutData.currency)?.toUpperCase() || null :
    asNonEmptyString(payoutData.currency, payoutData.localCurrency)?.toUpperCase() || null;

  return {
    payoutRequestId: payoutRef.id,
    type: payoutData.type || null,
    senderId: payoutData.senderId || null,
    fundingSource: payoutData.fundingSource || null,
    amount: providerAmount,
    currency: providerCurrency,
    recipient: {
      name: asNonEmptyString(
        payoutData.recipientName,
        recipientInfo["name"],
        payoutData.registeredName
      ) || null,
      phone: asNonEmptyString(
        payoutData.recipientPhone,
        recipientInfo["mobileNumber"],
        recipientInfo["accountNumber"],
        payoutData.phone
      ) || null,
      network: asNonEmptyString(
        payoutData.recipientNetwork,
        recipientInfo["network"],
        payoutData.network
      ) || null,
      country: canonicalCountry || asNonEmptyString(recipientInfo["country"]) || null,
    },
    metadata: {
      senderTransactionIds: Array.isArray(payoutData.senderTransactionIds) ? payoutData.senderTransactionIds : [],
      createdAt: payoutData.createdAt || null,
      paymentMethodId: asNonEmptyString(payoutData.paymentMethodId) || null,
      walletAmount: walletAmount,
      walletCurrency: asNonEmptyString(payoutData.currency)?.toUpperCase() || null,
      localAmount: Number.isFinite(localAmountRaw) ? localAmountRaw : null,
      localCurrency: asNonEmptyString(payoutData.localCurrency)?.toUpperCase() || null,
    },
  };
};

const resolveMobileMoneyProviderEndpoint = (
  payoutsUrl: string,
  transferType: string
): string => {
  if (transferType !== "CASH_IN") return payoutsUrl;

  const explicitCollectionUrl = asNonEmptyString(process.env.MOBILE_MONEY_COLLECTION_URL);
  if (explicitCollectionUrl) return explicitCollectionUrl;

  try {
    const url = new URL(payoutsUrl);
    if (/\/payouts\/?$/i.test(url.pathname)) {
      url.pathname = url.pathname.replace(/\/payouts\/?$/i, "/deposits");
    } else {
      url.pathname = `${url.pathname.replace(/\/+$/, "")}/deposits`;
    }
    return url.toString();
  } catch {
    return payoutsUrl.replace(/\/payouts\/?$/i, "/deposits");
  }
};

const buildPawaPayPredictProviderUrl = (payoutsUrl: string): string => {
  try {
    const url = new URL(payoutsUrl);
    if (/\/payouts\/?$/i.test(url.pathname)) {
      url.pathname = url.pathname.replace(/\/payouts\/?$/i, "/predict-provider");
    } else {
      url.pathname = `${url.pathname.replace(/\/+$/, "")}/predict-provider`;
    }
    return url.toString();
  } catch {
    return payoutsUrl.replace(/\/payouts\/?$/i, "/predict-provider");
  }
};

const buildPawaPayActiveConfUrl = (payoutsUrl: string): string => {
  const override = asNonEmptyString(process.env.MOBILE_MONEY_PROVIDER_ACTIVE_CONF_URL);
  if (override) return override;

  try {
    const url = new URL(payoutsUrl);
    if (/\/payouts\/?$/i.test(url.pathname)) {
      url.pathname = url.pathname.replace(/\/payouts\/?$/i, "/active-conf");
    } else {
      url.pathname = `${url.pathname.replace(/\/+$/, "")}/active-conf`;
    }
    return url.toString();
  } catch {
    return payoutsUrl.replace(/\/payouts\/?$/i, "/active-conf");
  }
};

type PawaPayActiveConfCurrencyEntry = {
  currency?: string;
  operationTypes?: unknown;
};

type PawaPayActiveConfProviderEntry = {
  provider?: string;
  currencies?: PawaPayActiveConfCurrencyEntry[];
  operationTypes?: unknown;
};

type PawaPayActiveConfCountryEntry = {
  country?: string;
  providers?: PawaPayActiveConfProviderEntry[];
};

const pawaPayActiveConfCache = new Map<string, {expiresAtMs: number; countryConfig: PawaPayActiveConfCountryEntry | null}>();

const getPawaPayActiveConfCacheTtlMs = (): number => {
  const parsed = Number(process.env.MOBILE_MONEY_PROVIDER_ACTIVE_CONF_CACHE_TTL_MS || 300000);
  if (!Number.isFinite(parsed) || parsed <= 0) return 300000;
  return parsed;
};

const extractOperationTypes = (value: unknown): Set<string> => {
  const result = new Set<string>();
  if (!value) return result;

  if (Array.isArray(value)) {
    for (const item of value) {
      const itemUpper = toUpperOrNull(item);
      if (itemUpper) {
        result.add(itemUpper);
        continue;
      }

      if (item && typeof item === "object") {
        const itemRecord = item as Record<string, unknown>;
        const opFromField = toUpperOrNull(itemRecord["operationType"] || itemRecord["type"] || itemRecord["name"]);
        if (opFromField) result.add(opFromField);
      }
    }
    return result;
  }

  if (value && typeof value === "object") {
    const valueRecord = value as Record<string, unknown>;
    for (const [key, raw] of Object.entries(valueRecord)) {
      if (raw === true || raw === 1 || raw === "true") {
        result.add(key.toUpperCase());
      }
      const rawUpper = toUpperOrNull(raw);
      if (rawUpper) result.add(rawUpper);
    }
  }

  return result;
};

const parseActiveConfCountries = (rawData: unknown): PawaPayActiveConfCountryEntry[] => {
  if (Array.isArray(rawData)) {
    return rawData.filter((item) => item && typeof item === "object") as PawaPayActiveConfCountryEntry[];
  }

  if (!rawData || typeof rawData !== "object") return [];
  const data = rawData as Record<string, unknown>;

  if (Array.isArray(data["countries"])) {
    return data["countries"].filter((item) => item && typeof item === "object") as PawaPayActiveConfCountryEntry[];
  }

  const hasCountryShape = typeof data["country"] === "string" || Array.isArray(data["providers"]);
  if (hasCountryShape) {
    return [data as PawaPayActiveConfCountryEntry];
  }

  return [];
};

const fetchPawaPayActiveConfCountry = async (
  countryIso3: string,
  options: {payoutsUrl: string; headers: Record<string, string>; timeoutMs: number}
): Promise<PawaPayActiveConfCountryEntry | null> => {
  const cacheKey = countryIso3.toUpperCase();
  const now = Date.now();
  const cacheHit = pawaPayActiveConfCache.get(cacheKey);
  if (cacheHit && cacheHit.expiresAtMs > now) {
    return cacheHit.countryConfig;
  }

  const activeConfUrl = buildPawaPayActiveConfUrl(options.payoutsUrl);
  const response = await axios.get(activeConfUrl, {
    headers: options.headers,
    timeout: options.timeoutMs,
    params: {
      country: cacheKey,
      operationType: "PAYOUT",
    },
  });

  const countries = parseActiveConfCountries(response.data);
  const matchedCountry =
    countries.find((entry) => toUpperOrNull(entry.country) === cacheKey) ||
    (countries.length === 1 ? countries[0] : null);

  pawaPayActiveConfCache.set(cacheKey, {
    countryConfig: matchedCountry,
    expiresAtMs: now + getPawaPayActiveConfCacheTtlMs(),
  });

  return matchedCountry;
};

const assertPawaPayProviderAndCurrencyEnabled = async (
  countryIso3: string,
  provider: string,
  currency: string,
  options: {payoutsUrl: string; headers: Record<string, string>; timeoutMs: number}
): Promise<void> => {
  const countryConfig = await fetchPawaPayActiveConfCountry(countryIso3, options);
  if (!countryConfig) {
    throw new Error(`pawaPay active-conf has no enabled payout configuration for country ${countryIso3}.`);
  }

  const providerUpper = provider.toUpperCase();
  const currencyUpper = currency.toUpperCase();

  const providerConfig = (countryConfig.providers || [])
    .find((entry) => toUpperOrNull(entry.provider) === providerUpper);

  if (!providerConfig) {
    throw new Error(`pawaPay provider ${providerUpper} is not enabled for country ${countryIso3}.`);
  }

  const currencyConfig = (providerConfig.currencies || [])
    .find((entry) => toUpperOrNull(entry.currency) === currencyUpper);

  if (!currencyConfig) {
    throw new Error(`Currency ${currencyUpper} is not enabled on provider ${providerUpper} for country ${countryIso3}.`);
  }

  const opTypes = extractOperationTypes(currencyConfig.operationTypes);
  if (opTypes.size === 0) {
    const providerLevelOps = extractOperationTypes(providerConfig.operationTypes);
    if (providerLevelOps.size > 0 && !providerLevelOps.has("PAYOUT")) {
      throw new Error(`Provider ${providerUpper} does not support PAYOUT operation for country ${countryIso3}.`);
    }
    return;
  }

  if (!opTypes.has("PAYOUT")) {
    throw new Error(`Currency ${currencyUpper} on provider ${providerUpper} is not enabled for PAYOUT in country ${countryIso3}.`);
  }
};

const submitPawaPayPayout = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData,
  options: {
    payoutsUrl: string;
    headers: Record<string, string>;
    timeoutMs: number;
  }
): Promise<MobileMoneyProviderResult> => {
  const recipientInfo = (payoutData.recipientInfo || {}) as Record<string, unknown>;
  const canonicalCountry = canonicalMobileMoneyCountry(
    asNonEmptyString(recipientInfo["country"], payoutData.country)
  );
  const phoneRaw = asNonEmptyString(
    payoutData.recipientPhone,
    recipientInfo["mobileNumber"],
    recipientInfo["accountNumber"]
  );
  const amountRaw = Number(payoutData.amount || 0);
  const currency = asNonEmptyString(payoutData.currency, payoutData.localCurrency)?.toUpperCase();

  if (!phoneRaw) {
    throw new Error("Missing recipient phone number for pawaPay payout.");
  }
  if (!Number.isFinite(amountRaw) || amountRaw <= 0) {
    throw new Error("Invalid payout amount for pawaPay payout.");
  }
  if (!currency) {
    throw new Error("Missing payout currency for pawaPay payout.");
  }
  if (!canonicalCountry) {
    throw new Error("Missing recipient country for pawaPay payout.");
  }

  const normalizedPhone = String(phoneRaw).replace(/[^\d]/g, "");
  const predictProviderUrl = buildPawaPayPredictProviderUrl(options.payoutsUrl);
  const countryIso3 = getPawaPayCountryIso3(canonicalCountry);

  const predictionResponse = await axios.post(
    predictProviderUrl,
    {phoneNumber: normalizedPhone},
    {headers: options.headers, timeout: options.timeoutMs}
  );

  const predictionData = (predictionResponse.data || {}) as Record<string, unknown>;
  const predictedProvider = asNonEmptyString(predictionData["provider"]);
  const predictedPhone = asNonEmptyString(predictionData["phoneNumber"], normalizedPhone);
  if (!predictedProvider || !predictedPhone) {
    throw new Error("pawaPay provider prediction failed for the recipient number.");
  }
  await assertPawaPayProviderAndCurrencyEnabled(
    countryIso3,
    predictedProvider,
    currency,
    options
  );

  const amount = Number.isInteger(amountRaw) ? amountRaw.toString() : amountRaw.toFixed(2);
  const payload = {
    payoutId: payoutRef.id,
    amount,
    currency,
    recipient: {
      type: "MMO",
      accountDetails: {
        phoneNumber: predictedPhone,
        provider: predictedProvider,
      },
    },
  };

  const payoutResponse = await axios.post(
    options.payoutsUrl,
    payload,
    {headers: options.headers, timeout: options.timeoutMs}
  );

  const payoutResponseData = (payoutResponse.data || {}) as Record<string, unknown>;
  const rawStatus = asNonEmptyString(payoutResponseData["status"]);
  const providerMessage = asNonEmptyString(
    payoutResponseData["message"],
    payoutResponseData["detail"],
    payoutResponseData["reason"]
  ) || `pawaPay payout initiated with provider ${predictedProvider}.`;

  return {
    status: normalizeMobileMoneyProviderResultStatus(rawStatus),
    rawStatus,
    providerTransferId: asNonEmptyString(
      payoutResponseData["providerTransactionId"],
      payoutResponseData["payoutId"],
      payoutRef.id
    ),
    providerMessage,
  };
};

const submitMobileMoneyPayoutToHttpProvider = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Promise<MobileMoneyProviderResult> => {
  const providerUrl = process.env.MOBILE_MONEY_PROVIDER_URL;
  const providerApiKey = process.env.MOBILE_MONEY_PROVIDER_API_KEY;

  if (!providerUrl) {
    throw new Error("MOBILE_MONEY_PROVIDER_URL is not configured.");
  }
  if (!providerApiKey) {
    throw new Error("MOBILE_MONEY_PROVIDER_API_KEY is not configured.");
  }

  const authHeader = asNonEmptyString(process.env.MOBILE_MONEY_PROVIDER_AUTH_HEADER) || "Authorization";
  const keyPrefix = process.env.MOBILE_MONEY_PROVIDER_API_KEY_PREFIX;
  const timeoutMs = Number(process.env.MOBILE_MONEY_PROVIDER_TIMEOUT_MS || 30000);
  const providerName = getMobileMoneyProviderName();
  const timeout = Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 30000;
  const transferType = String(payoutData.type || "").trim().toUpperCase();
  const providerEndpoint = resolveMobileMoneyProviderEndpoint(providerUrl, transferType);

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    [authHeader]: keyPrefix === "" ? providerApiKey : `${keyPrefix || "Bearer"} ${providerApiKey}`,
  };

  if (providerName === "PAWAPAY" && transferType !== "CASH_IN") {
    return submitPawaPayPayout(payoutRef, payoutData, {
      payoutsUrl: providerUrl,
      headers,
      timeoutMs: timeout,
    });
  }

  const payload = buildMobileMoneyProviderPayload(payoutRef, payoutData);
  const response = await axios.post(providerEndpoint, payload, {
    headers,
    timeout,
  });

  const responseData = (response.data || {}) as Record<string, unknown>;
  const rawStatus = asNonEmptyString(
    responseData["status"],
    responseData["transfer_status"],
    responseData["state"],
    responseData["result"]
  );
  const status = normalizeMobileMoneyProviderResultStatus(rawStatus);

  const providerTransferId = asNonEmptyString(
    responseData["providerTransferId"],
    responseData["provider_transfer_id"],
    responseData["transferId"],
    responseData["transfer_id"],
    responseData["reference"],
    responseData["tx_ref"],
    responseData["id"]
  );

  const providerMessage = asNonEmptyString(
    responseData["message"],
    responseData["detail"],
    responseData["reason"],
    responseData["error"]
  );

  return {
    status,
    providerTransferId,
    providerMessage,
    rawStatus,
  };
};

const applyMobileMoneyProviderResult = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData,
  result: MobileMoneyProviderResult
): Promise<void> => {
  const senderId = payoutData.senderId as string | undefined;
  const payoutType = String(payoutData.type || "").toUpperCase();
  const now = admin.firestore.Timestamp.now();
  const statusUpdate: Record<string, unknown> = {
    processedAt: now,
    providerStatus: result.status,
    providerMessage: result.providerMessage || null,
  };

  if (result.providerTransferId) {
    statusUpdate.providerTransferId = result.providerTransferId;
  }
  if (result.rawStatus) {
    statusUpdate.providerRawStatus = result.rawStatus;
  }

  if (result.status === "COMPLETED") {
    await payoutRef.set({
      ...statusUpdate,
      status: "COMPLETED",
      completedAt: now,
    }, {merge: true});

    if (senderId) {
      await finalizeSenderTransactionsForPayout(senderId, payoutRef, payoutData, "COMPLETED");
    }
    await recordMobileMoneyHiddenFeeRevenueIfNeeded(payoutRef, payoutData, senderId);
    return;
  }

  if (result.status === "FAILED") {
    await payoutRef.set({
      ...statusUpdate,
      status: "FAILED",
      errorMessage: result.providerMessage || "Mobile money provider rejected payout.",
    }, {merge: true});

    if (senderId && payoutType === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(
        senderId,
        payoutData,
        "FAILED",
        result.providerMessage || "Provider rejected mobile money verification."
      );
    }

    if (senderId) {
      await finalizeSenderTransactionsForPayout(senderId, payoutRef, payoutData, "FAILED");
    }
    return;
  }

  await payoutRef.set({
    ...statusUpdate,
    status: "PROCESSING_PROVIDER",
    providerProcessingStartedAt: now,
  }, {merge: true});

  if (senderId && payoutType === "CASH_IN") {
    await markMobileMoneyMethodVerificationStatus(senderId, payoutData, "AWAITING_CONFIRMATION");
  }
};

const processPendingMobileMoneyProviderPayout = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  inputData?: FirebaseFirestore.DocumentData
): Promise<void> => {
  const latestSnap = await payoutRef.get();
  if (!latestSnap.exists) return;
  const data = inputData || latestSnap.data() || {};
  if (data.status !== "PENDING_PROVIDER") return;

  const type = String(data.type || "");
  if (type !== "CASH_OUT" && type !== "BENEFICIARY_TRANSFER" && type !== "CASH_IN") return;
  const senderId = data.senderId as string | undefined;
  const providerName = getMobileMoneyProviderName();
  const payoutCountry = canonicalMobileMoneyCountry(
    asNonEmptyString((data.recipientInfo || {}).country, data.country)
  );

  const providerMode = String(process.env.MOBILE_MONEY_PROVIDER_MODE || "MANUAL").toUpperCase();
  if (providerMode === "MANUAL") {
    functions.logger.warn("Mobile money provider processing is in MANUAL mode; payout remains pending.", {
      payoutRequestId: payoutRef.id,
      senderId,
      type,
      providerMode,
    });
    await payoutRef.set({
      providerStatus: "WAITING_MANUAL_PROVIDER",
      providerMessage: "Awaiting manual provider processing.",
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});
    if (senderId && type === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(senderId, data, "AWAITING_CONFIRMATION");
    }
    return;
  }

  if (providerMode === "SIMULATED") {
    functions.logger.warn("Mobile money provider processing is running in SIMULATED mode; no real funds are sent.", {
      payoutRequestId: payoutRef.id,
      senderId,
      type,
    });

    await applyMobileMoneyProviderResult(payoutRef, data, {
      status: "COMPLETED",
      providerTransferId: `mm_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
      providerMessage: "Simulated provider completion.",
      rawStatus: "SIMULATED_COMPLETED",
    });
    return;
  }

  if (providerMode !== "HTTP_API") {
    functions.logger.warn("Unknown mobile money provider mode. Falling back to manual pending mode.", {
      payoutRequestId: payoutRef.id,
      senderId,
      type,
      providerMode,
    });
    await payoutRef.set({
      providerStatus: "WAITING_MANUAL_PROVIDER",
      providerMessage: `Unknown provider mode '${providerMode}'. Awaiting manual provider processing.`,
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});
    if (senderId && type === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(senderId, data, "AWAITING_CONFIRMATION");
    }
    return;
  }

  if (!isMobileMoneyCountrySupportedByProvider(providerName, payoutCountry)) {
    const supportedCountries = getSupportedCountriesForProvider(providerName);
    const countryLabel = payoutCountry || String((data.recipientInfo || {}).country || data.country || "Unknown country");
    const providerMessage = supportedCountries.length > 0 ?
      `Country ${countryLabel} is not supported by ${providerName}. Supported countries: ${supportedCountries.join(", ")}.` :
      `Country ${countryLabel} is not supported by ${providerName}.`;

    functions.logger.warn("Mobile money payout blocked due to unsupported provider country.", {
      payoutRequestId: payoutRef.id,
      senderId,
      type,
      providerName,
      payoutCountry: countryLabel,
    });

    await payoutRef.set({
      status: "FAILED",
      providerStatus: "FAILED",
      providerMessage,
      errorMessage: providerMessage,
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});

    if (senderId && type === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(senderId, data, "FAILED", providerMessage);
    }

    if (senderId) {
      await finalizeSenderTransactionsForPayout(senderId, payoutRef, data, "FAILED");
    }
    return;
  }

  await payoutRef.set({
    status: "PROCESSING_PROVIDER",
    providerStatus: "PROCESSING",
    providerProcessingStartedAt: admin.firestore.Timestamp.now(),
    processedAt: admin.firestore.Timestamp.now(),
  }, {merge: true});
  if (senderId && type === "CASH_IN") {
    await markMobileMoneyMethodVerificationStatus(senderId, data, "AWAITING_CONFIRMATION");
  }

  try {
    const providerResult = await submitMobileMoneyPayoutToHttpProvider(payoutRef, data);
    await applyMobileMoneyProviderResult(payoutRef, data, providerResult);
  } catch (error) {
    const errorMessage = parseProviderErrorMessage(error);
    functions.logger.error("Mobile money provider request failed", {
      payoutRequestId: payoutRef.id,
      senderId,
      type,
      error,
      errorMessage,
    });

    await payoutRef.set({
      status: "FAILED",
      providerStatus: "FAILED",
      providerMessage: errorMessage,
      errorMessage: `Provider request failed: ${errorMessage}`,
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});

    if (senderId && type === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(senderId, data, "FAILED", errorMessage);
    }

    if (senderId) {
      await finalizeSenderTransactionsForPayout(senderId, payoutRef, data, "FAILED");
    }
  }
};

const markMobileMoneyMethodVerifiedFromCashIn = async (
  senderId: string,
  payoutData: FirebaseFirestore.DocumentData,
  payoutRequestId: string
): Promise<void> => {
  const paymentMethodId = asNonEmptyString(payoutData.paymentMethodId);
  const senderMethodsRef = db.collection("users").doc(senderId).collection("payment_methods");
  const now = admin.firestore.Timestamp.now();
  const normalizedPhoneDigits = normalizeDigits(
    asNonEmptyString(
      payoutData.phone,
      payoutData.recipientPhone,
      (payoutData.recipientInfo || {}).mobileNumber
    ) || ""
  );
  const normalizedNetwork = normalizeText(
    asNonEmptyString(
      payoutData.network,
      payoutData.recipientNetwork,
      (payoutData.recipientInfo || {}).network
    ) || ""
  );
  const normalizedCountry = normalizeText(
    asNonEmptyString(
      payoutData.country,
      (payoutData.recipientInfo || {}).country
    ) || ""
  );

  if (paymentMethodId) {
    const methodRef = senderMethodsRef.doc(paymentMethodId);
    const methodSnap = await methodRef.get();
    if (methodSnap.exists && String(methodSnap.get("type") || "").toUpperCase() === "MOBILE_MONEY") {
      await methodRef.set({
        phoneOwnershipVerified: true,
        verificationStatus: "VERIFIED",
        verificationMethod: "MOBILE_MONEY_PROVIDER_COLLECTION",
        verificationCompletedAt: now,
        verificationSourcePayoutRequestId: payoutRequestId,
        lastVerificationError: null,
      }, {merge: true});
      return;
    }
  }

  const methodsSnap = await senderMethodsRef.where("type", "==", "MOBILE_MONEY").get();
  const updates = methodsSnap.docs.filter((doc) => {
    const phoneDigits = normalizeDigits(doc.get("phoneNumber") || "");
    const network = normalizeText(doc.get("network") || "");
    const country = normalizeText(doc.get("country") || "");
    if (!phoneDigits || !normalizedPhoneDigits) return false;
    const phoneMatches = phoneDigits === normalizedPhoneDigits;
    const networkMatches = !normalizedNetwork || !network || normalizedNetwork === network;
    const countryMatches = !normalizedCountry || !country || normalizedCountry === country;
    return phoneMatches && networkMatches && countryMatches;
  });

  await Promise.all(updates.map((doc) => doc.ref.set({
    phoneOwnershipVerified: true,
    verificationStatus: "VERIFIED",
    verificationMethod: "MOBILE_MONEY_PROVIDER_COLLECTION",
    verificationCompletedAt: now,
    verificationSourcePayoutRequestId: payoutRequestId,
    lastVerificationError: null,
  }, {merge: true})));
};

const markMobileMoneyMethodVerificationStatus = async (
  senderId: string,
  payoutData: FirebaseFirestore.DocumentData,
  status: "REQUESTED" | "AWAITING_CONFIRMATION" | "FAILED",
  reason?: string
): Promise<void> => {
  const paymentMethodId = asNonEmptyString(payoutData.paymentMethodId);
  if (!paymentMethodId) return;

  const methodRef = db.collection("users").doc(senderId)
    .collection("payment_methods").doc(paymentMethodId);
  const now = admin.firestore.Timestamp.now();
  const updateData: Record<string, unknown> = {
    verificationStatus: status,
    verificationUpdatedAt: now,
    verificationMethod: "MOBILE_MONEY_PROVIDER_COLLECTION",
  };
  if (status === "REQUESTED") {
    updateData.verificationRequestedAt = now;
  }
  if (status === "AWAITING_CONFIRMATION") {
    updateData.verificationAwaitingAt = now;
  }
  if (status === "FAILED") {
    updateData.lastVerificationError = reason || "Verification failed.";
    updateData.phoneOwnershipVerified = false;
  }

  await methodRef.set(updateData, {merge: true});
};

const settleMobileMoneyCashInToWallet = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Promise<void> => {
  const senderId = asNonEmptyString(payoutData.senderId);
  if (!senderId) {
    throw new Error("Missing senderId for cash-in settlement.");
  }
  const amount = Number(payoutData.amount || 0);
  const verificationOnly = payoutData.verificationOnly === true;
  if (!verificationOnly && (!Number.isFinite(amount) || amount <= 0)) {
    throw new Error("Invalid cash-in amount for settlement.");
  }

  let queuedTransferPayoutRef: FirebaseFirestore.DocumentReference | null = null;
  let queuedTransferPayoutData: FirebaseFirestore.DocumentData | null = null;

  await db.runTransaction(async (transaction) => {
    const latestSnap = await transaction.get(payoutRef);
    if (!latestSnap.exists) return;
    const latestData = latestSnap.data() || payoutData;
    if (latestData.type !== "CASH_IN") return;
    if (latestData.cashInSettled === true) return;
    const latestVerificationOnly = latestData.verificationOnly === true;
    const latestAmount = Number(latestData.amount || amount || 0);

    const userRef = db.collection("users").doc(senderId);
    const now = admin.firestore.Timestamp.now();
    const transferIntent = latestData.transferIntent && typeof latestData.transferIntent === "object" ?
      (latestData.transferIntent as Record<string, unknown>) :
      null;
    const transferMode = String(transferIntent?.mode || "").trim().toUpperCase();

    let walletCreditAmount = latestVerificationOnly ? 0 : latestAmount;
    let queuedDirectPayoutId: string | null = null;
    let transferIntentState = "NONE";
    let transferIntentError: string | null = null;

    if (transferMode === "MM_TO_MM" && !latestVerificationOnly) {
      try {
        const recipientInfo = transferIntent?.recipientInfo && typeof transferIntent.recipientInfo === "object" ?
          (transferIntent.recipientInfo as Record<string, unknown>) :
          {};
        const recipientName = asNonEmptyString(
          transferIntent?.recipientName,
          recipientInfo["name"]
        );
        const recipientNetwork = asNonEmptyString(
          transferIntent?.recipientNetwork,
          recipientInfo["network"]
        );
        const recipientPhone = asNonEmptyString(
          transferIntent?.recipientPhone,
          recipientInfo["mobileNumber"],
          recipientInfo["accountNumber"]
        );
        const recipientCountryRaw = asNonEmptyString(
          recipientInfo["country"],
          transferIntent?.recipientCountry
        );

        if (!recipientName || !recipientNetwork || !recipientPhone || !recipientCountryRaw) {
          throw new Error("Transfer intent recipient is missing required details.");
        }

        const canonicalRecipientCountry = assertCountrySupportedForConfiguredProvider(recipientCountryRaw);
        const normalizedRecipient = {
          ...recipientInfo,
          name: recipientName,
          network: recipientNetwork,
          mobileNumber: recipientPhone,
          accountNumber: recipientPhone,
          country: canonicalRecipientCountry || recipientCountryRaw,
        };
        const payoutCurrency = resolveMobileMoneyCurrency(normalizedRecipient);
        if (!payoutCurrency) {
          throw new Error(`Mobile money transfers are not supported for ${normalizedRecipient.country}.`);
        }

        const expectedFingerprint = buildBeneficiaryVerificationFingerprint({
          name: recipientName,
          phone: recipientPhone,
          network: recipientNetwork,
          country: String(normalizedRecipient.country || recipientCountryRaw),
          currency: payoutCurrency,
        });
        const beneficiaryVerificationId = asNonEmptyString(
          transferIntent?.beneficiaryVerificationId,
          latestData.beneficiaryVerificationId
        );
        if (!beneficiaryVerificationId) {
          throw new Error("Transfer intent is missing beneficiary verification id.");
        }

        const transferPayoutRef = db.collection("payout_requests").doc();
        const verificationRef = db.collection("beneficiary_verifications").doc(beneficiaryVerificationId);
        const verificationUsage = await consumeBeneficiaryVerificationInTransaction({
          transaction,
          verificationRef,
          senderId,
          expectedFingerprint,
          payoutRequestId: transferPayoutRef.id,
        });
        const senderTxRef = userRef.collection("transactions").doc();

        transaction.set(senderTxRef, {
          title: "Mobile Money Transfer (Pending)",
          amount: -latestAmount,
          type: "DEBIT",
          status: "PENDING",
          timestamp: now,
          note: `MM to MM transfer to ${recipientName} (${recipientPhone}).`,
          source: "MOBILE_MONEY_TRANSFER",
          payoutRequestId: transferPayoutRef.id,
          collectionRequestId: payoutRef.id,
        });

        const transferPayoutData: FirebaseFirestore.DocumentData = {
          senderId,
          recipientInfo: normalizedRecipient,
          recipientName,
          recipientNetwork,
          recipientPhone,
          amount: latestAmount,
          amountInLocalCurrency: latestAmount,
          currency: payoutCurrency,
          status: "PENDING_PROVIDER",
          type: "BENEFICIARY_TRANSFER",
          source: "MOBILE_MONEY_TRANSFER",
          fundingSource: "EXTERNAL_MOBILE_MONEY",
          fundingPaymentMethodId: asNonEmptyString(latestData.paymentMethodId) || null,
          collectionRequestId: payoutRef.id,
          senderTransactionIds: [senderTxRef.id],
          beneficiaryVerificationId: verificationUsage.verificationId,
          beneficiaryVerificationStatus: verificationUsage.status,
          beneficiaryVerificationMatchLevel: verificationUsage.matchLevel,
          beneficiaryVerificationReasonCode: verificationUsage.reasonCode || null,
          beneficiaryVerificationReasonMessage: verificationUsage.reasonMessage || null,
          beneficiaryVerificationFingerprint: verificationUsage.fingerprint,
          beneficiaryVerificationAmlStatus: verificationUsage.amlStatus,
          beneficiaryVerificationAmlBlocked: verificationUsage.amlBlocked,
          beneficiaryVerificationAmlMatchCount: verificationUsage.amlMatchCount,
          beneficiaryVerificationAmlTopMatchName: verificationUsage.amlTopMatchName,
          beneficiaryVerificationCheckedAt: verificationUsage.checkedAt,
          createdAt: now,
          processedAt: now,
        };
        transaction.set(transferPayoutRef, transferPayoutData);

        queuedTransferPayoutRef = transferPayoutRef;
        queuedTransferPayoutData = transferPayoutData;
        queuedDirectPayoutId = transferPayoutRef.id;
        transferIntentState = "COLLECTION_COMPLETED_PAYOUT_QUEUED";
        walletCreditAmount = 0;
      } catch (error) {
        transferIntentState = "COLLECTION_COMPLETED_PAYOUT_QUEUE_FAILED";
        transferIntentError = parseProviderErrorMessage(error);
        walletCreditAmount = latestAmount;
        functions.logger.error("Failed to queue beneficiary payout after mobile money collection. Falling back to wallet credit.", {
          payoutRequestId: payoutRef.id,
          senderId,
          error,
          transferIntentError,
        });
      }
    }

    if (walletCreditAmount > 0) {
      transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(walletCreditAmount));
    }

    transaction.set(payoutRef, {
      cashInSettled: true,
      cashInSettledAt: now,
      cashInVerificationOnly: latestVerificationOnly,
      walletCreditedAmount: walletCreditAmount,
      transferIntentState,
      transferIntentError,
      transferIntentPayoutRequestId: queuedDirectPayoutId,
      processedAt: now,
    }, {merge: true});
  });

  await markMobileMoneyMethodVerifiedFromCashIn(senderId, payoutData, payoutRef.id);
  if (queuedTransferPayoutRef && queuedTransferPayoutData) {
    const queuedPayoutRef = queuedTransferPayoutRef as unknown as FirebaseFirestore.DocumentReference;
    const queuedPayoutData = queuedTransferPayoutData as unknown as FirebaseFirestore.DocumentData;
    try {
      await processPendingMobileMoneyProviderPayout(queuedPayoutRef, queuedPayoutData);
    } catch (error) {
      functions.logger.error("Queued MM->MM payout processing failed after collection settlement.", {
        payoutRequestId: queuedPayoutRef.id,
        senderId,
        error,
      });
      await queuedPayoutRef.set({
        status: "FAILED",
        providerStatus: "FAILED",
        providerMessage: "Failed to start provider payout after collection completion.",
        errorMessage: "Failed to start provider payout after collection completion.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
    }
  }
};

const refundWalletForFailedMobileMoneyCashOut = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Promise<void> => {
  const senderId = asNonEmptyString(payoutData.senderId);
  if (!senderId) return;
  const refundAmount = Number(payoutData.amount || 0);
  if (!Number.isFinite(refundAmount) || refundAmount <= 0) return;

  const userRef = db.collection("users").doc(senderId);
  await db.runTransaction(async (transaction) => {
    const latestSnap = await transaction.get(payoutRef);
    if (!latestSnap.exists) return;
    const latestData = latestSnap.data() || payoutData;
    if (String(latestData.type || "") !== "CASH_OUT") return;
    if (latestData.refundProcessed === true || latestData.status === "REFUNDED") return;

    const txRef = userRef.collection("transactions").doc();
    const now = admin.firestore.Timestamp.now();
    transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(refundAmount));
    transaction.set(txRef, {
      title: "Mobile Money Withdrawal Reversal",
      amount: refundAmount,
      type: "CREDIT",
      status: "COMPLETED",
      timestamp: now,
      source: "MOBILE_MONEY_REVERSAL",
      note: "Provider payout failed; amount returned to wallet.",
      payoutRequestId: payoutRef.id,
    });
    transaction.set(payoutRef, {
      refundProcessed: true,
      refundAmount,
      refundTransactionId: txRef.id,
      refundReason: "Provider payout failed before settlement.",
      refundedAt: now,
      status: "REFUNDED",
      providerStatus: "FAILED",
      processedAt: now,
    }, {merge: true});
  });
};

const refundWalletForFailedExternalMobileMoneyBeneficiaryTransfer = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
): Promise<void> => {
  const senderId = asNonEmptyString(payoutData.senderId);
  if (!senderId) return;
  const fundingSource = String(payoutData.fundingSource || "").toUpperCase();
  if (fundingSource !== "EXTERNAL_MOBILE_MONEY") return;

  const refundAmount = Number(payoutData.amount || 0);
  if (!Number.isFinite(refundAmount) || refundAmount <= 0) return;

  const userRef = db.collection("users").doc(senderId);
  await db.runTransaction(async (transaction) => {
    const latestSnap = await transaction.get(payoutRef);
    if (!latestSnap.exists) return;
    const latestData = latestSnap.data() || payoutData;
    const latestType = String(latestData.type || "").toUpperCase();
    const latestFundingSource = String(latestData.fundingSource || "").toUpperCase();
    if (latestType !== "BENEFICIARY_TRANSFER" || latestFundingSource !== "EXTERNAL_MOBILE_MONEY") return;
    if (latestData.refundProcessed === true || latestData.status === "REFUNDED") return;

    const txRef = userRef.collection("transactions").doc();
    const now = admin.firestore.Timestamp.now();
    transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(refundAmount));
    transaction.set(txRef, {
      title: "Mobile Money Transfer Reversal",
      amount: refundAmount,
      type: "CREDIT",
      status: "COMPLETED",
      timestamp: now,
      source: "MOBILE_MONEY_REVERSAL",
      note: "Mobile money payout failed after collection; amount credited to wallet.",
      payoutRequestId: payoutRef.id,
    });
    transaction.set(payoutRef, {
      refundProcessed: true,
      refundAmount,
      refundTransactionId: txRef.id,
      refundReason: "Provider payout failed after mobile money collection.",
      refundedAt: now,
      status: "REFUNDED",
      providerStatus: "FAILED",
      processedAt: now,
    }, {merge: true});
  });
};

export const createBeneficiaryVerification = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const senderId = context.auth.uid;
    const payload = (data || {}) as CreateBeneficiaryVerificationRequest;
    if (!payload.recipientBeneficiary) {
      throw new functions.https.HttpsError("invalid-argument", "recipientBeneficiary is required.");
    }

    const prepared = prepareBeneficiaryVerificationInput({
      beneficiary: payload.recipientBeneficiary,
      amount: payload.amount,
      requestedCurrency: payload.currency,
    });
    if (!prepared.nameInput || !prepared.phoneDigits || !prepared.countryInput) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Beneficiary name, phone number, and country are required."
      );
    }

    const decision = await runBeneficiaryVerificationDecision({prepared});
    const now = admin.firestore.Timestamp.now();
    const expiresAt = admin.firestore.Timestamp.fromMillis(
      now.toMillis() + getBeneficiaryVerificationTtlMs()
    );

    const verificationRef = db.collection("beneficiary_verifications").doc();
    await verificationRef.set({
      senderId,
      status: decision.status,
      canProceed: decision.canProceed,
      matchLevel: decision.matchLevel,
      reasonCode: decision.reasonCode,
      reasonMessage: decision.reasonMessage,
      fingerprint: prepared.fingerprint,
      createdAt: now,
      expiresAt,
      consumedAt: null,
      consumedByPayoutRequestId: null,
      request: {
        nameInput: prepared.nameInput,
        accountNumberInput: prepared.accountNumberInput,
        mobileNumberInput: prepared.mobileNumberInput,
        networkInput: prepared.networkInput,
        countryInput: prepared.countryInput,
        countryCanonical: prepared.countryCanonical || null,
        phoneE164: prepared.phoneE164,
        amount: prepared.amount,
        currency: prepared.currency || null,
      },
      provider: decision.provider,
      aml: decision.aml,
      audit: {
        verifiedByUid: senderId,
        appCheckPresent: !!context.app,
        sourceFunction: "createBeneficiaryVerification",
      },
      updatedAt: now,
    });

    return {
      verificationId: verificationRef.id,
      status: decision.status,
      canProceed: decision.canProceed,
      matchLevel: decision.matchLevel,
      reasonCode: decision.reasonCode,
      reasonMessage: decision.reasonMessage,
      expiresAtMs: expiresAt.toMillis(),
      provider: decision.provider,
      aml: decision.aml,
    };
  });

export const requestMobileMoneyMethodVerification = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const senderId = context.auth.uid;
    const payload = (data || {}) as {paymentMethodId?: unknown; verificationLocalAmount?: unknown};
    const paymentMethodId = asNonEmptyString(payload.paymentMethodId);
    if (!paymentMethodId) {
      throw new functions.https.HttpsError("invalid-argument", "paymentMethodId is required.");
    }

    const userRef = db.collection("users").doc(senderId);
    const methodRef = userRef.collection("payment_methods").doc(paymentMethodId);
    const methodSnap = await methodRef.get();
    if (!methodSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Mobile money payment method not found.");
    }
    const methodData = methodSnap.data() || {};
    if (String(methodData.type || "").toUpperCase() !== "MOBILE_MONEY") {
      throw new functions.https.HttpsError("invalid-argument", "Selected method is not mobile money.");
    }

    const phone = asNonEmptyString(methodData.phoneNumber);
    const network = asNonEmptyString(methodData.network);
    const country = asNonEmptyString(methodData.country);
    const localCurrency = asNonEmptyString(methodData.currency)?.toUpperCase() || "USD";
    if (!phone || !network || !country) {
      throw new functions.https.HttpsError("failed-precondition", "Mobile money method is missing required details.");
    }

    const rawLocalAmount = Number(
      payload.verificationLocalAmount ||
      process.env.MOBILE_MONEY_VERIFICATION_LOCAL_AMOUNT ||
      "1"
    );
    const verificationLocalAmount = roundMoney(rawLocalAmount);
    if (!Number.isFinite(verificationLocalAmount) || verificationLocalAmount <= 0) {
      throw new functions.https.HttpsError("invalid-argument", "verificationLocalAmount must be a positive number.");
    }

    const walletCurrency = "USD";
    const now = admin.firestore.Timestamp.now();
    await methodRef.set({
      verificationStatus: "REQUESTED",
      verificationRequestedAt: now,
      verificationUpdatedAt: now,
      verificationMethod: "MOBILE_MONEY_PROVIDER_COLLECTION",
      lastVerificationError: null,
    }, {merge: true});

    const payoutRef = db.collection("payout_requests").doc();
    await payoutRef.set({
      senderId,
      amount: 0,
      currency: walletCurrency,
      phone,
      network,
      country,
      dialCode: asNonEmptyString(methodData.dialCode) || null,
      localCurrency,
      localAmount: verificationLocalAmount,
      paymentMethodId,
      type: "CASH_IN",
      status: "PENDING",
      verificationOnly: true,
      source: "MOBILE_MONEY_VERIFICATION",
      timestamp: now,
      createdAt: now,
    }, {merge: true});

    return {
      success: true,
      payoutRequestId: payoutRef.id,
      verificationStatus: "REQUESTED",
      message: `Verification request started. Approve the ${verificationLocalAmount.toFixed(2)} ${localCurrency} collection prompt on your phone.`,
    };
  });


export const initiateTransfer = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in to transfer.");
    }
    const senderId = context.auth.uid;
    const requestData = data as InitiateTransferRequest;

    if (!requestData.amount || requestData.amount <= 0) {
      throw new functions.https.HttpsError("invalid-argument", "Transfer amount must be positive.");
    }
    if (!requestData.recipientId && !requestData.recipientBeneficiary) {
      throw new functions.https.HttpsError("invalid-argument", "A recipient must be specified.");
    }

    const senderRef = db.collection("users").doc(senderId);
    const platformFee = 0; // You can implement a fee structure here if desired
    const totalDeduction = requestData.amount + platformFee;

    functions.logger.log(`Transfer from ${senderId}: Amount=${requestData.amount}, Type=${requestData.fundingSourceType}`);

    // --- Logic for WALLET transfers ---
    if (requestData.fundingSourceType === "WALLET") {
      const toFiniteNumber = (value: unknown, fallback = 0): number => {
        const parsed = typeof value === "number" ? value : Number(value);
        return Number.isFinite(parsed) ? parsed : fallback;
      };

      let committedDestinationType: "WALLET" | "CARD" | "BANK" = "WALLET";
      let committedSenderTransactionId: string | null = null;
      let committedPayoutRequestId: string | null = null;
      let committedSenderNewBalance: number | null = null;

      try {
        await db.runTransaction(async (transaction) => {
          const userSnap = await transaction.get(senderRef);
          if (!userSnap.exists) {
            throw new functions.https.HttpsError("not-found", "Sender account not found.");
          }

          const userData = (userSnap.data() || {}) as Record<string, unknown>;
          const userWallet = (userData.wallet || {}) as Record<string, unknown>;
          const userBalance = toFiniteNumber(userWallet.balance, 0);
          if (userBalance < totalDeduction) {
            throw new functions.https.HttpsError("failed-precondition", "Insufficient wallet balance.");
          }

          const senderName = asNonEmptyString(userData.name, userData.username) || "A user";
          let recipientName = "a user";
          const recipientId = asNonEmptyString(requestData.recipientId);

          const destinationTypeRaw = asNonEmptyString(requestData.destinationType)?.toUpperCase();
          const destinationType: "WALLET" | "CARD" | "BANK" = destinationTypeRaw === "CARD" || destinationTypeRaw === "BANK" || destinationTypeRaw === "WALLET" ?
            destinationTypeRaw :
            "WALLET";
          committedDestinationType = destinationType;

          if (!recipientId) {
            throw new functions.https.HttpsError("invalid-argument", "A recipient user must be specified for wallet transfers.");
          }

          if (destinationType === "WALLET" && recipientId === senderId) {
            throw new functions.https.HttpsError("invalid-argument", "You cannot transfer to your own wallet.");
          }

          // IMPORTANT: Firestore transactions require all reads before any writes.
          const recipientRef = db.collection("users").doc(recipientId);
          const recipientSnap = await transaction.get(recipientRef);
          if (!recipientSnap.exists) {
            throw new functions.https.HttpsError("not-found", "Recipient account not found.");
          }
          const recipientData = (recipientSnap.data() || {}) as Record<string, unknown>;
          recipientName = asNonEmptyString(recipientData.name, recipientData.username) || "a user";

          let recipientMethodData: FirebaseFirestore.DocumentData | null = null;
          let resolvedRecipientPaymentMethodId: string | null = null;
          if (destinationType !== "WALLET") {
            const requestedMethodId = asNonEmptyString(requestData.recipientPaymentMethodId);
            const requestedExternalAccountId = asNonEmptyString(requestData.recipientExternalAccountId);
            if (!requestedMethodId && !requestedExternalAccountId) {
              throw new functions.https.HttpsError("invalid-argument", "Recipient payout method is required.");
            }

            let methodSnap: FirebaseFirestore.DocumentSnapshot<FirebaseFirestore.DocumentData> | null = null;

            if (requestedMethodId) {
              const methodRef = recipientRef.collection("payment_methods").doc(requestedMethodId);
              const directSnap = await transaction.get(methodRef);
              if (directSnap.exists) {
                methodSnap = directSnap;
                resolvedRecipientPaymentMethodId = directSnap.id;
              }
            }

            if (!methodSnap && requestedExternalAccountId) {
              const byExternalQuery = recipientRef.collection("payment_methods")
                .where("externalAccountId", "==", requestedExternalAccountId)
                .limit(1);
              const byExternalSnap = await transaction.get(byExternalQuery);
              if (!byExternalSnap.empty) {
                methodSnap = byExternalSnap.docs[0];
                resolvedRecipientPaymentMethodId = methodSnap.id;
              }
            }

            if (!methodSnap && requestedExternalAccountId) {
              const byStripeExternalQuery = recipientRef.collection("payment_methods")
                .where("stripeExternalAccountId", "==", requestedExternalAccountId)
                .limit(1);
              const byStripeExternalSnap = await transaction.get(byStripeExternalQuery);
              if (!byStripeExternalSnap.empty) {
                methodSnap = byStripeExternalSnap.docs[0];
                resolvedRecipientPaymentMethodId = methodSnap.id;
              }
            }

            if (!methodSnap) {
              const availableMethodsQuery = recipientRef.collection("payment_methods").limit(10);
              const availableMethodsSnap = await transaction.get(availableMethodsQuery);
              const availableMethodIds = availableMethodsSnap.docs.map((doc) => doc.id);
              functions.logger.warn("Recipient payout method lookup failed.", {
                senderId,
                recipientId,
                requestedMethodId: requestedMethodId || null,
                requestedExternalAccountId: requestedExternalAccountId || null,
                availableMethodIds,
              });
              throw new functions.https.HttpsError(
                "not-found",
                "Recipient payout method not found. Re-select recipient payout method and try again."
              );
            }

            recipientMethodData = methodSnap.data() || {};
            resolvedRecipientPaymentMethodId = resolvedRecipientPaymentMethodId || methodSnap.id;
          }

          const senderCurrency = asNonEmptyString(userWallet.currency)?.toUpperCase() || "USD";
          const updatedSenderBalance = roundMoney(userBalance - totalDeduction);
          committedSenderNewBalance = updatedSenderBalance;
          transaction.set(senderRef, {
            wallet: {
              balance: updatedSenderBalance,
              currency: senderCurrency,
            },
            updatedAt: admin.firestore.Timestamp.now(),
          }, {merge: true});

          if (destinationType === "WALLET") {
            const recipientWallet = (recipientData.wallet || {}) as Record<string, unknown>;
            const recipientBalance = toFiniteNumber(recipientWallet.balance, 0);
            const recipientCurrency = asNonEmptyString(recipientWallet.currency)?.toUpperCase() || senderCurrency;
            const updatedRecipientBalance = roundMoney(recipientBalance + requestData.amount);
            transaction.set(recipientRef, {
              wallet: {
                balance: updatedRecipientBalance,
                currency: recipientCurrency,
              },
              updatedAt: admin.firestore.Timestamp.now(),
            }, {merge: true});

            const recipientTxRef = recipientRef.collection("transactions").doc();
            transaction.set(recipientTxRef, {title: "Received Money", amount: requestData.amount, type: "CREDIT", status: "COMPLETED", timestamp: admin.firestore.Timestamp.now(), note: `From ${senderName}`, source: "WALLET_TRANSFER"});
          }

          if (destinationType !== "WALLET") {
            if (!recipientMethodData) {
              throw new functions.https.HttpsError("internal", "Recipient payout method could not be loaded.");
            }

            const payoutRequestRef = db.collection("payout_requests").doc();
            committedPayoutRequestId = payoutRequestRef.id;
            transaction.set(payoutRequestRef, {
              senderId: senderId,
              recipientId: recipientId,
              recipientName: recipientName,
              destinationType: destinationType,
              paymentMethodId: resolvedRecipientPaymentMethodId,
              paymentMethod: recipientMethodData,
              amount: requestData.amount,
              currency: senderCurrency,
              status: "PENDING",
              source: "WALLET_TRANSFER",
              createdAt: admin.firestore.Timestamp.now(),
            });
          }

          const senderTxRef = senderRef.collection("transactions").doc();
          committedSenderTransactionId = senderTxRef.id;
          const destinationLabel = recipientId ?
            recipientName :
            requestData.recipientBeneficiary?.name;
          const payoutSuffix = destinationType === "WALLET" ? "" : ` (${destinationType})`;
          transaction.set(senderTxRef, {title: "Sent Money", amount: -totalDeduction, type: "DEBIT", status: "COMPLETED", timestamp: admin.firestore.Timestamp.now(), note: `To ${destinationLabel}${payoutSuffix}`, source: "WALLET_TRANSFER"});
        });
        functions.logger.info("Wallet transfer committed.", {
          senderId,
          destinationType: committedDestinationType,
          amount: requestData.amount,
          totalDeduction,
          senderNewBalance: committedSenderNewBalance,
          senderTransactionId: committedSenderTransactionId,
          payoutRequestId: committedPayoutRequestId,
          recipientId: asNonEmptyString(requestData.recipientId) || null,
        });

        const message = committedDestinationType === "WALLET" ?
          "Transfer from wallet successful!" :
          "Transfer submitted. Payout is now processing.";
        return {
          success: true,
          message,
          senderNewBalance: committedSenderNewBalance,
          senderTransactionId: committedSenderTransactionId,
          payoutRequestId: committedPayoutRequestId,
          destinationType: committedDestinationType,
        };
      } catch (error) {
        const rawMessage = error instanceof Error ?
          error.message :
          String(error || "Unknown wallet transfer error");
        const rawLower = rawMessage.toLowerCase();
        functions.logger.error("Wallet transfer transaction failed:", {
          senderId,
          recipientId: asNonEmptyString(requestData.recipientId) || null,
          destinationType: asNonEmptyString(requestData.destinationType) || "WALLET",
          error,
          rawMessage,
        });
        if (error instanceof functions.https.HttpsError) throw error;
        if (rawLower.includes("no document to update")) {
          throw new functions.https.HttpsError("not-found", "Recipient account is no longer available.");
        }
        throw new functions.https.HttpsError("internal", "An internal error occurred during the wallet transfer.");
      }


    // --- COMPLETE LOGIC for MOBILE_MONEY transfers via payment provider ---
    } else if (requestData.fundingSourceType === "MOBILE_MONEY") {
      if (!requestData.recipientBeneficiary) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary details are required for mobile money transfer.");
      }
      const beneficiaryVerificationId = asNonEmptyString(requestData.beneficiaryVerificationId);
      if (!beneficiaryVerificationId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Beneficiary verification is required before mobile money transfer."
        );
      }
      const verificationRef = db.collection("beneficiary_verifications").doc(beneficiaryVerificationId);
      // Your app doesn't have a UI to select funding source for mobile money, so we debit the wallet.
      const userSnap = await senderRef.get();
      const userBalance = userSnap.data()?.wallet?.balance ?? 0;
      if (userBalance < totalDeduction) {
        throw new functions.https.HttpsError("failed-precondition", "Insufficient wallet balance for this transfer.");
      }

      try {
        const recipient = requestData.recipientBeneficiary;
        const canonicalCountry = assertCountrySupportedForConfiguredProvider(recipient.country);
        const normalizedRecipient = {
          ...recipient,
          country: canonicalCountry || recipient.country,
        };
        const normalizedRecipientPhone = asNonEmptyString(
          normalizedRecipient.mobileNumber,
          normalizedRecipient.accountNumber
        );
        if (!normalizedRecipientPhone) {
          throw new functions.https.HttpsError("invalid-argument", "Beneficiary phone number is required.");
        }
        const currency = resolveMobileMoneyCurrency(normalizedRecipient as unknown as Record<string, unknown>);

        if (!currency) {
          throw new functions.https.HttpsError("invalid-argument", `Mobile money transfers are not supported for ${normalizedRecipient.country}.`);
        }
        const expectedFingerprint = buildBeneficiaryVerificationFingerprint({
          name: normalizedRecipient.name,
          phone: normalizedRecipientPhone,
          network: normalizedRecipient.network,
          country: normalizedRecipient.country,
          currency,
        });
        await assertBeneficiaryVerificationReadyForUse({
          verificationRef,
          senderId,
          expectedFingerprint,
        });

        // Direct phone-number destinations are not valid Stripe transfer destinations.
        // Queue the payout request for provider/manual processing instead.
        const payoutRequestRef = db.collection("payout_requests").doc();
        const senderTxRef = senderRef.collection("transactions").doc();

        await db.runTransaction(async (transaction) => {
          const freshSenderSnap = await transaction.get(senderRef);
          const freshBalance = freshSenderSnap.data()?.wallet?.balance ?? 0;
          if (freshBalance < totalDeduction) {
            throw new functions.https.HttpsError("failed-precondition", "Insufficient wallet balance for this transfer.");
          }

          const verificationUsage = await consumeBeneficiaryVerificationInTransaction({
            transaction,
            verificationRef,
            senderId,
            expectedFingerprint,
            payoutRequestId: payoutRequestRef.id,
          });

          transaction.update(senderRef, "wallet.balance", admin.firestore.FieldValue.increment(-totalDeduction));

          transaction.set(senderTxRef, {
            title: "Mobile Money Transfer (Pending)",
            amount: -totalDeduction,
            type: "DEBIT",
            status: "PENDING",
            timestamp: admin.firestore.Timestamp.now(),
            note: `To ${normalizedRecipient.name} (${normalizedRecipientPhone})`,
            source: "MOBILE_MONEY",
            payoutRequestId: payoutRequestRef.id,
          });

          transaction.set(payoutRequestRef, {
            senderId: senderId,
            recipientInfo: normalizedRecipient,
            recipientName: normalizedRecipient.name,
            recipientNetwork: normalizedRecipient.network,
            recipientPhone: normalizedRecipientPhone,
            amount: requestData.amount,
            amountInLocalCurrency: requestData.amount,
            currency: currency,
            status: "PENDING_PROVIDER",
            type: "BENEFICIARY_TRANSFER",
            source: "MOBILE_MONEY_TRANSFER",
            fundingSource: "MOBILE_MONEY",
            beneficiaryVerificationId: verificationUsage.verificationId,
            beneficiaryVerificationStatus: verificationUsage.status,
            beneficiaryVerificationMatchLevel: verificationUsage.matchLevel,
            beneficiaryVerificationReasonCode: verificationUsage.reasonCode || null,
            beneficiaryVerificationReasonMessage: verificationUsage.reasonMessage || null,
            beneficiaryVerificationFingerprint: verificationUsage.fingerprint,
            beneficiaryVerificationAmlStatus: verificationUsage.amlStatus,
            beneficiaryVerificationAmlBlocked: verificationUsage.amlBlocked,
            beneficiaryVerificationAmlMatchCount: verificationUsage.amlMatchCount,
            beneficiaryVerificationAmlTopMatchName: verificationUsage.amlTopMatchName,
            beneficiaryVerificationCheckedAt: verificationUsage.checkedAt,
            senderTransactionIds: [senderTxRef.id],
            createdAt: admin.firestore.Timestamp.now(),
          });
        });

        functions.logger.info("Mobile money transfer queued", {
          senderId,
          payoutRequestId: payoutRequestRef.id,
          senderTransactionIds: [senderTxRef.id],
          fundingSourceType: requestData.fundingSourceType,
          recipientPhone: normalizedRecipientPhone,
          recipientNetwork: normalizedRecipient.network,
          recipientCountry: normalizedRecipient.country,
          beneficiaryVerificationId,
        });

        return {
          success: true,
          message: `Transfer request submitted for ${normalizedRecipient.name}. It is pending payout processing.`,
          payoutRequestId: payoutRequestRef.id,
        };
      } catch (error: unknown) {
        functions.logger.error("Mobile money payout request failed:", error);
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError("internal", "The mobile money transfer request could not be created at this time.");
      }


    // --- Logic for direct MOBILE_MONEY funding (sender MM -> recipient MM) ---
    } else if (requestData.fundingSourceType === "EXTERNAL_MOBILE_MONEY") {
      if (requestData.recipientId) {
        throw new functions.https.HttpsError("invalid-argument", "Direct mobile money funding is only supported for beneficiary transfers.");
      }
      if (!requestData.recipientBeneficiary) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary details are required for this transfer.");
      }
      const beneficiaryVerificationId = asNonEmptyString(requestData.beneficiaryVerificationId);
      if (!beneficiaryVerificationId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Beneficiary verification is required before direct mobile money transfer."
        );
      }
      const verificationRef = db.collection("beneficiary_verifications").doc(beneficiaryVerificationId);
      const fundingMethodId = asNonEmptyString(requestData.fundingPaymentMethodId);
      if (!fundingMethodId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Select a verified mobile money funding source."
        );
      }

      const fundingMethodSnap = await senderRef.collection("payment_methods").doc(fundingMethodId).get();
      if (!fundingMethodSnap.exists) {
        throw new functions.https.HttpsError("not-found", "Selected mobile money funding source was not found.");
      }
      const fundingData = fundingMethodSnap.data() || {};
      const fundingType = String(fundingData.type || "").toUpperCase();
      if (fundingType !== "MOBILE_MONEY") {
        throw new functions.https.HttpsError("failed-precondition", "Selected funding source is not mobile money.");
      }
      const fundingVerified = fundingData.phoneOwnershipVerified === true;
      const fundingVerificationStatus = String(fundingData.verificationStatus || "").toUpperCase();
      if (!fundingVerified || fundingVerificationStatus !== "VERIFIED") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Mobile money funding source is not verified. Complete a successful verification/deposit first."
        );
      }

      const recipient = requestData.recipientBeneficiary;
      const canonicalCountry = assertCountrySupportedForConfiguredProvider(recipient.country);
      const normalizedRecipient = {
        ...recipient,
        country: canonicalCountry || recipient.country,
      };
      const normalizedRecipientPhone = asNonEmptyString(
        normalizedRecipient.mobileNumber,
        normalizedRecipient.accountNumber
      );
      if (!normalizedRecipientPhone) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary phone number is required.");
      }
      const recipientCurrency = resolveMobileMoneyCurrency(normalizedRecipient as unknown as Record<string, unknown>);
      if (!recipientCurrency) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          `Mobile money transfers are not supported for ${normalizedRecipient.country}.`
        );
      }

      const expectedVerificationFingerprint = buildBeneficiaryVerificationFingerprint({
        name: normalizedRecipient.name,
        phone: normalizedRecipientPhone,
        network: normalizedRecipient.network,
        country: normalizedRecipient.country,
        currency: recipientCurrency,
      });
      await assertBeneficiaryVerificationReadyForUse({
        verificationRef,
        senderId,
        expectedFingerprint: expectedVerificationFingerprint,
      });

      const fundingPhone = asNonEmptyString(fundingData.phoneNumber);
      const fundingNetwork = asNonEmptyString(fundingData.network);
      const fundingCountry = asNonEmptyString(fundingData.country);
      const fundingCurrency = asNonEmptyString(fundingData.currency)?.toUpperCase() || "USD";
      if (!fundingPhone || !fundingNetwork || !fundingCountry) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Selected mobile money funding source is missing required phone/network/country details."
        );
      }

      let collectionLocalAmount = roundMoney(requestData.amount);
      if (fundingCurrency !== "USD") {
        try {
          const snapshot = await fetchRawExchangeRateToUsd(fundingCurrency);
          if (Number.isFinite(snapshot.rate) && snapshot.rate > 0) {
            collectionLocalAmount = roundMoney(requestData.amount / snapshot.rate);
          }
        } catch (error) {
          functions.logger.warn("Failed to convert USD amount to funding local currency. Falling back to same numeric amount.", {
            senderId,
            fundingMethodId,
            fundingCurrency,
            error,
          });
          collectionLocalAmount = roundMoney(requestData.amount);
        }
      }
      if (!Number.isFinite(collectionLocalAmount) || collectionLocalAmount <= 0) {
        collectionLocalAmount = roundMoney(requestData.amount);
      }

      const now = admin.firestore.Timestamp.now();
      const collectionRequestRef = db.collection("payout_requests").doc();
      await collectionRequestRef.set({
        senderId,
        amount: requestData.amount,
        currency: "USD",
        phone: fundingPhone,
        network: fundingNetwork,
        country: fundingCountry,
        dialCode: asNonEmptyString(fundingData.dialCode) || null,
        localCurrency: fundingCurrency,
        localAmount: collectionLocalAmount,
        paymentMethodId: fundingMethodId,
        type: "CASH_IN",
        status: "PENDING_PROVIDER",
        verificationOnly: false,
        source: "MOBILE_MONEY_TRANSFER",
        fundingSource: "EXTERNAL_MOBILE_MONEY",
        transferIntent: {
          mode: "MM_TO_MM",
          recipientInfo: normalizedRecipient,
          recipientName: normalizedRecipient.name,
          recipientNetwork: normalizedRecipient.network || null,
          recipientPhone: normalizedRecipientPhone,
          beneficiaryVerificationId,
          beneficiaryVerificationFingerprint: expectedVerificationFingerprint,
          requestedAmount: requestData.amount,
          requestedCurrency: "USD",
          destinationType: "MOBILE_MONEY",
          fundingPaymentMethodId: fundingMethodId,
        },
        createdAt: now,
        processedAt: now,
      });

      functions.logger.info("Direct mobile money funding collection queued.", {
        senderId,
        fundingMethodId,
        collectionRequestId: collectionRequestRef.id,
        recipientPhone: normalizedRecipientPhone,
        recipientNetwork: normalizedRecipient.network || null,
        recipientCountry: normalizedRecipient.country,
      });

      return {
        success: true,
        message: `Collection request sent from your mobile money account. Approve on your phone; payout to ${normalizedRecipient.name} starts automatically after confirmation.`,
        payoutRequestId: collectionRequestRef.id,
        collectionLocalAmount,
        collectionLocalCurrency: fundingCurrency,
      };


    // --- Logic for EXTERNAL_CARD transfers ---
    } else if (requestData.fundingSourceType === "EXTERNAL_CARD") {
      if (requestData.recipientId) {
        throw new functions.https.HttpsError("invalid-argument", "Card funding is not supported for app user transfers.");
      }
      if (!requestData.recipientBeneficiary) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary details are required for this transfer.");
      }
      const beneficiaryVerificationId = asNonEmptyString(requestData.beneficiaryVerificationId);
      if (!beneficiaryVerificationId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Beneficiary verification is required before external card transfer."
        );
      }
      const verificationRef = db.collection("beneficiary_verifications").doc(beneficiaryVerificationId);

      const userSnap = await senderRef.get();
      const userBalance = userSnap.data()?.wallet?.balance ?? 0;
      const prioritizeExternalFunding = requestData.prioritizeExternalFunding === true;
      const walletContribution = prioritizeExternalFunding ? 0 : Math.min(userBalance, totalDeduction);
      const remaining = Number((totalDeduction - walletContribution).toFixed(2));
      const recipient = requestData.recipientBeneficiary;
      const canonicalCountry = assertCountrySupportedForConfiguredProvider(recipient.country);
      const normalizedRecipient = {
        ...recipient,
        country: canonicalCountry || recipient.country,
      };
      const normalizedRecipientPhone = asNonEmptyString(
        normalizedRecipient.mobileNumber,
        normalizedRecipient.accountNumber
      );
      if (!normalizedRecipientPhone) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary phone number is required.");
      }
      const verificationCurrency = resolveMobileMoneyCurrency(normalizedRecipient as unknown as Record<string, unknown>);
      if (!verificationCurrency) {
        throw new functions.https.HttpsError("invalid-argument", `Mobile money transfers are not supported for ${normalizedRecipient.country}.`);
      }
      const expectedVerificationFingerprint = buildBeneficiaryVerificationFingerprint({
        name: normalizedRecipient.name,
        phone: normalizedRecipientPhone,
        network: normalizedRecipient.network,
        country: normalizedRecipient.country,
        currency: verificationCurrency,
      });
      await assertBeneficiaryVerificationReadyForUse({
        verificationRef,
        senderId,
        expectedFingerprint: expectedVerificationFingerprint,
      });
      const senderDataForFees = (userSnap.data() || {}) as Record<string, unknown>;
      const senderStaffFeeExempt = isStaffFeeExempt(
        senderDataForFees,
        (context.auth?.token || {}) as Record<string, unknown>
      );
      const senderCurrency = (userSnap.data()?.wallet?.currency || "USD").toLowerCase();
      let fundingPaymentIntentId: string | null = null;

      if (remaining > 0) {
        const fundingMethodId = requestData.fundingPaymentMethodId;
        if (!fundingMethodId) {
          throw new functions.https.HttpsError("failed-precondition", "Select a funding card to cover the remaining balance.");
        }

        const fundingMethodSnap = await senderRef.collection("payment_methods").doc(fundingMethodId).get();

        // Enhanced error handling for missing payment method
        if (!fundingMethodSnap.exists) {
          functions.logger.error(`Payment method ${fundingMethodId} not found for user ${senderId}`);
          throw new functions.https.HttpsError("not-found", "The selected funding card was not found. Please select another card.");
        }

        const fundingData = fundingMethodSnap.data() || {};

        // Log the payment method data for debugging
        functions.logger.log(`Payment method data: type=${fundingData.type}, hasChargeId=${!!fundingData.chargePaymentMethodId}, hasStripeId=${!!fundingData.stripePaymentMethodId}`);

        const chargePaymentMethodId = fundingData.chargePaymentMethodId || fundingData.stripePaymentMethodId;
        if (!chargePaymentMethodId) {
          // Provide context-aware error messages
          if (fundingData.requiresRelinkForCharges === true) {
            throw new functions.https.HttpsError("failed-precondition", "This card needs to be re-linked before it can fund transfers. Please remove and re-add it.");
          }
          if (fundingData.type === "MOBILE_MONEY") {
            throw new functions.https.HttpsError("failed-precondition", "Mobile Money accounts cannot be used as a funding source. Please use a credit or debit card.");
          }
          if (fundingData.type === "BANK_ACCOUNT") {
            throw new functions.https.HttpsError("failed-precondition", "Bank accounts are not set up for charging. Please use a credit or debit card.");
          }
          functions.logger.error(`Payment method ${fundingMethodId} has no Stripe payment method ID. Data: ${JSON.stringify(fundingData)}`);
          throw new functions.https.HttpsError("failed-precondition", "This card has not been properly configured. Please remove and re-add it.");
        }

        const customerId = userSnap.data()?.paymentCustomerId as string | undefined;
        if (!customerId) {
          functions.logger.error(`No payment customer ID for user ${senderId}`);
          throw new functions.https.HttpsError("failed-precondition", "Your card is not linked to a billing profile. Please re-add the card.");
        }

        const amountToCharge = Math.round(remaining * 100);
        try {
          functions.logger.log(`Attempting charge: amount=${amountToCharge} ${senderCurrency}, customer=${customerId}, paymentMethod=${chargePaymentMethodId}`);

          const fundingIntent = await getStripe().paymentIntents.create({
            amount: amountToCharge,
            currency: senderCurrency,
            customer: customerId,
            payment_method: chargePaymentMethodId,
            confirm: true,
            off_session: true,
            description: `Funding transfer to ${normalizedRecipient.name}`,
            metadata: {senderId, fundingMethodId},
          });
          fundingPaymentIntentId = fundingIntent.id;

          const stripeFxMargin = senderStaffFeeExempt ?
            0 :
            Number.parseFloat(getAppConfig().stripeForexDepositProfitMargin);
          const stripeFxEarnings = roundMoney(remaining * stripeFxMargin);
          if (stripeFxEarnings > 0) {
            await db.runTransaction(async (transaction) => {
              recordPlatformRevenue(transaction, {
                source: "stripeForexEarnings",
                amount: stripeFxEarnings,
                note: "Stripe FX margin on card-funded transfer",
                relatedUserId: senderId,
              });
            });
          }
        } catch (error: unknown) {
          const stripeError = error as Stripe.errors.StripeError;
          functions.logger.error(`Stripe charge failed: code=${stripeError?.code}, message=${stripeError?.message}`, error);

          // Handle specific Stripe error codes
          if (stripeError?.code === "authentication_required") {
            throw new functions.https.HttpsError("failed-precondition", "Additional authentication is required. Please verify with your bank.");
          }
          if (stripeError?.code === "card_declined") {
            throw new functions.https.HttpsError("failed-precondition", "Your card was declined. Please check the card details or try another card.");
          }
          if (stripeError?.code === "expired_card") {
            throw new functions.https.HttpsError("failed-precondition", "Your card has expired. Please update it or use another card.");
          }
          if (stripeError?.code === "lost_card" || stripeError?.code === "stolen_card") {
            throw new functions.https.HttpsError("failed-precondition", "This card has been flagged as lost or stolen. Please use another card.");
          }
          if (stripeError?.code === "processing_error") {
            throw new functions.https.HttpsError("internal", "Payment processing error. Please try again in a moment.");
          }

          // Generic card charge error with specific message if available
          throw new functions.https.HttpsError("failed-precondition", stripeError?.message ? `Card charge failed: ${stripeError.message}` : "Card charge failed. Please try another card.");
        }
      }
      try {
        const currency = verificationCurrency;

        const payoutRequestRef = db.collection("payout_requests").doc();
        const walletTxRef = walletContribution > 0 ? senderRef.collection("transactions").doc() : null;
        const cardTxRef = remaining > 0 ? senderRef.collection("transactions").doc() : null;
        const senderTransactionIds = [walletTxRef?.id, cardTxRef?.id]
          .filter((v): v is string => typeof v === "string" && v.length > 0);

        await db.runTransaction(async (transaction) => {
          const verificationUsage = await consumeBeneficiaryVerificationInTransaction({
            transaction,
            verificationRef,
            senderId,
            expectedFingerprint: expectedVerificationFingerprint,
            payoutRequestId: payoutRequestRef.id,
          });

          if (walletContribution > 0 && walletTxRef) {
            transaction.update(senderRef, "wallet.balance", admin.firestore.FieldValue.increment(-walletContribution));

            transaction.set(walletTxRef, {
              title: "Transfer (wallet portion)",
              amount: -walletContribution,
              type: "DEBIT",
              status: "PENDING",
              timestamp: admin.firestore.Timestamp.now(),
              note: `Wallet contribution for ${normalizedRecipient.name}`,
              source: "MOBILE_MONEY",
              payoutRequestId: payoutRequestRef.id,
            });
          }

          if (remaining > 0 && cardTxRef) {
            transaction.set(cardTxRef, {
              title: "Transfer (card portion)",
              amount: -remaining,
              type: "DEBIT",
              status: "PENDING",
              timestamp: admin.firestore.Timestamp.now(),
              note: `Card funding for ${normalizedRecipient.name}`,
              source: "MOBILE_MONEY",
              payoutRequestId: payoutRequestRef.id,
            });
          }

          transaction.set(payoutRequestRef, {
            senderId: senderId,
            recipientInfo: normalizedRecipient,
            recipientName: normalizedRecipient.name,
            recipientNetwork: normalizedRecipient.network,
            recipientPhone: normalizedRecipientPhone,
            amount: requestData.amount,
            amountWallet: walletContribution,
            amountCharged: remaining,
            currency: currency,
            status: "PENDING_PROVIDER",
            type: "BENEFICIARY_TRANSFER",
            source: "MOBILE_MONEY_TRANSFER",
            fundingSource: "EXTERNAL_CARD",
            fundingPaymentMethodId: requestData.fundingPaymentMethodId || null,
            fundingPaymentIntentId: fundingPaymentIntentId,
            beneficiaryVerificationId: verificationUsage.verificationId,
            beneficiaryVerificationStatus: verificationUsage.status,
            beneficiaryVerificationMatchLevel: verificationUsage.matchLevel,
            beneficiaryVerificationReasonCode: verificationUsage.reasonCode || null,
            beneficiaryVerificationReasonMessage: verificationUsage.reasonMessage || null,
            beneficiaryVerificationFingerprint: verificationUsage.fingerprint,
            beneficiaryVerificationAmlStatus: verificationUsage.amlStatus,
            beneficiaryVerificationAmlBlocked: verificationUsage.amlBlocked,
            beneficiaryVerificationAmlMatchCount: verificationUsage.amlMatchCount,
            beneficiaryVerificationAmlTopMatchName: verificationUsage.amlTopMatchName,
            beneficiaryVerificationCheckedAt: verificationUsage.checkedAt,
            senderTransactionIds,
            createdAt: admin.firestore.Timestamp.now(),
          });
        });

        functions.logger.info("Split-funded mobile money transfer queued", {
          senderId,
          payoutRequestId: payoutRequestRef.id,
          senderTransactionIds,
          walletContribution,
          chargedContribution: remaining,
          fundingSourceType: requestData.fundingSourceType,
          recipientPhone: normalizedRecipientPhone,
          recipientNetwork: normalizedRecipient.network,
          recipientCountry: normalizedRecipient.country,
          beneficiaryVerificationId,
        });

        return {
          success: true,
          message: `Transfer request submitted for ${normalizedRecipient.name}. It is pending payout processing.`,
          payoutRequestId: payoutRequestRef.id,
        };
      } catch (error) {
        functions.logger.error("Split funding transfer failed:", error);
        if (error instanceof functions.https.HttpsError) throw error;

        const stripeError = error as Stripe.errors.StripeError;
        if (stripeError?.code === "insufficient_funds") {
          throw new functions.https.HttpsError("failed-precondition", "Insufficient funds on your card. Please try with a different card.");
        }

        throw new functions.https.HttpsError("internal", "The transfer request could not be completed. Please try again or contact support.");
      }

    // --- Logic for EXTERNAL_BANK transfers (bank -> mobile money beneficiary) ---
    } else if (requestData.fundingSourceType === "EXTERNAL_BANK") {
      if (requestData.recipientId) {
        throw new functions.https.HttpsError("invalid-argument", "Bank funding is not supported for app user transfers.");
      }
      if (!requestData.recipientBeneficiary) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary details are required for this transfer.");
      }

      const beneficiaryVerificationId = asNonEmptyString(requestData.beneficiaryVerificationId);
      if (!beneficiaryVerificationId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Beneficiary verification is required before bank-funded transfer."
        );
      }
      const verificationRef = db.collection("beneficiary_verifications").doc(beneficiaryVerificationId);

      const userSnap = await senderRef.get();
      const senderCurrency = (userSnap.data()?.wallet?.currency || "USD").toLowerCase();
      if (senderCurrency !== "usd") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Bank-funded mobile money transfers are currently available for USD wallets only."
        );
      }

      const recipient = requestData.recipientBeneficiary;
      const canonicalCountry = assertCountrySupportedForConfiguredProvider(recipient.country);
      const normalizedRecipient = {
        ...recipient,
        country: canonicalCountry || recipient.country,
      };
      const normalizedRecipientPhone = asNonEmptyString(
        normalizedRecipient.mobileNumber,
        normalizedRecipient.accountNumber
      );
      if (!normalizedRecipientPhone) {
        throw new functions.https.HttpsError("invalid-argument", "Beneficiary phone number is required.");
      }
      const verificationCurrency = resolveMobileMoneyCurrency(normalizedRecipient as unknown as Record<string, unknown>);
      if (!verificationCurrency) {
        throw new functions.https.HttpsError("invalid-argument", `Mobile money transfers are not supported for ${normalizedRecipient.country}.`);
      }
      const expectedVerificationFingerprint = buildBeneficiaryVerificationFingerprint({
        name: normalizedRecipient.name,
        phone: normalizedRecipientPhone,
        network: normalizedRecipient.network,
        country: normalizedRecipient.country,
        currency: verificationCurrency,
      });
      await assertBeneficiaryVerificationReadyForUse({
        verificationRef,
        senderId,
        expectedFingerprint: expectedVerificationFingerprint,
      });

      const fundingMethodId = asNonEmptyString(requestData.fundingPaymentMethodId);
      if (!fundingMethodId) {
        throw new functions.https.HttpsError("failed-precondition", "Select an ACH-enabled bank account to fund this transfer.");
      }

      const fundingMethodSnap = await senderRef.collection("payment_methods").doc(fundingMethodId).get();
      if (!fundingMethodSnap.exists) {
        throw new functions.https.HttpsError("not-found", "The selected bank account was not found.");
      }

      const fundingData = fundingMethodSnap.data() || {};
      const fundingType = String(fundingData.type || "").toUpperCase();
      if (fundingType !== "BANK") {
        throw new functions.https.HttpsError("failed-precondition", "Selected funding method is not a bank account.");
      }

      const chargeSourceStatus = String(fundingData.chargeSourceStatus || "").trim().toLowerCase();
      const achEnabled = !!asNonEmptyString(fundingData.chargeSourceId) &&
        chargeSourceStatus === "verified";
      if (!achEnabled) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "This bank account must be verified for ACH funding. Re-link and verify it in Payment Methods."
        );
      }

      const chargeSourceId = asNonEmptyString(fundingData.chargeSourceId);
      const chargeCustomerId = asNonEmptyString(fundingData.chargeCustomerId, userSnap.data()?.paymentCustomerId);
      if (!chargeSourceId || !chargeCustomerId) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "This bank account is missing ACH charge details. Re-link the account and try again."
        );
      }

      const amountToCharge = Math.round(totalDeduction * 100);
      if (amountToCharge <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Transfer amount must be greater than zero.");
      }

      const payoutRequestRef = db.collection("payout_requests").doc();
      const senderTxRef = senderRef.collection("transactions").doc();

      let bankCharge: Stripe.Charge;
      try {
        bankCharge = await getStripe().charges.create({
          amount: amountToCharge,
          currency: "usd",
          customer: chargeCustomerId,
          source: chargeSourceId,
          description: `Bank-funded transfer to ${normalizedRecipient.name}`,
          metadata: {
            senderId,
            payoutRequestId: payoutRequestRef.id,
            fundingMethodId,
            beneficiaryVerificationId,
          },
        }, {
          idempotencyKey: `bank_transfer_${payoutRequestRef.id}`,
        });
      } catch (error) {
        const stripeError = error as Stripe.errors.StripeError;
        if (stripeError?.code === "authentication_required") {
          throw new functions.https.HttpsError("failed-precondition", "Additional authentication is required by your bank.");
        }
        if (stripeError?.code === "insufficient_funds") {
          throw new functions.https.HttpsError("failed-precondition", "Insufficient funds in the selected bank account.");
        }
        if ((stripeError?.message || "").toLowerCase().includes("must be verified")) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "This bank account is not verified for ACH debit. Re-link and complete verification in Payment Methods."
          );
        }
        if (stripeError?.message) {
          throw new functions.https.HttpsError("failed-precondition", stripeError.message);
        }
        throw new functions.https.HttpsError("internal", "Bank funding could not be initiated at this time.");
      }

      const bankChargeStatus = String(bankCharge.status || "").toLowerCase();
      if (bankChargeStatus !== "succeeded" && bankChargeStatus !== "pending") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Bank funding did not settle. Please try again or use another funding source."
        );
      }

      await db.runTransaction(async (transaction) => {
        const verificationUsage = await consumeBeneficiaryVerificationInTransaction({
          transaction,
          verificationRef,
          senderId,
          expectedFingerprint: expectedVerificationFingerprint,
          payoutRequestId: payoutRequestRef.id,
        });

        transaction.set(senderTxRef, {
          title: "Transfer (bank funding)",
          amount: -totalDeduction,
          type: "DEBIT",
          status: "PENDING",
          timestamp: admin.firestore.Timestamp.now(),
          note: `Bank funding for ${normalizedRecipient.name}`,
          source: "MOBILE_MONEY",
          payoutRequestId: payoutRequestRef.id,
        });

        const payoutStatus = bankChargeStatus === "succeeded" ?
          "PENDING_PROVIDER" :
          "PENDING_BANK_SETTLEMENT";

        transaction.set(payoutRequestRef, {
          senderId: senderId,
          recipientInfo: normalizedRecipient,
          recipientName: normalizedRecipient.name,
          recipientNetwork: normalizedRecipient.network,
          recipientPhone: normalizedRecipientPhone,
          amount: requestData.amount,
          amountWallet: 0,
          amountCharged: requestData.amount,
          currency: verificationCurrency,
          status: payoutStatus,
          type: "BENEFICIARY_TRANSFER",
          source: "MOBILE_MONEY_TRANSFER",
          fundingSource: "EXTERNAL_BANK",
          fundingPaymentMethodId: fundingMethodId,
          fundingBankChargeId: bankCharge.id,
          fundingBankChargeStatus: bankChargeStatus,
          beneficiaryVerificationId: verificationUsage.verificationId,
          beneficiaryVerificationStatus: verificationUsage.status,
          beneficiaryVerificationMatchLevel: verificationUsage.matchLevel,
          beneficiaryVerificationReasonCode: verificationUsage.reasonCode || null,
          beneficiaryVerificationReasonMessage: verificationUsage.reasonMessage || null,
          beneficiaryVerificationFingerprint: verificationUsage.fingerprint,
          beneficiaryVerificationAmlStatus: verificationUsage.amlStatus,
          beneficiaryVerificationAmlBlocked: verificationUsage.amlBlocked,
          beneficiaryVerificationAmlMatchCount: verificationUsage.amlMatchCount,
          beneficiaryVerificationAmlTopMatchName: verificationUsage.amlTopMatchName,
          beneficiaryVerificationCheckedAt: verificationUsage.checkedAt,
          senderTransactionIds: [senderTxRef.id],
          createdAt: admin.firestore.Timestamp.now(),
        });
      });

      functions.logger.info("Bank-funded mobile money transfer queued", {
        senderId,
        payoutRequestId: payoutRequestRef.id,
        fundingMethodId,
        fundingBankChargeId: bankCharge.id,
        fundingBankChargeStatus: bankChargeStatus,
        recipientPhone: normalizedRecipientPhone,
        recipientNetwork: normalizedRecipient.network,
        recipientCountry: normalizedRecipient.country,
        beneficiaryVerificationId,
      });

      if (bankChargeStatus === "succeeded") {
        return {
          success: true,
          message: `Transfer request submitted for ${normalizedRecipient.name}. It is pending payout processing.`,
          payoutRequestId: payoutRequestRef.id,
        };
      }

      return {
        success: true,
        message: "Bank debit is pending settlement (typically 1-3 business days). Transfer to the recipient will start automatically once settled.",
        payoutRequestId: payoutRequestRef.id,
      };


    // --- Fallback error ---
    } else {
      throw new functions.https.HttpsError("invalid-argument", "Invalid funding source specified.");
    }
  });

// =============================================================================
//  12. PAYOUT PROVIDER SCAFFOLDING (ACCOUNT + ONBOARDING + EXTERNAL ACCOUNT)
// =============================================================================

interface CreateConnectAccountRequest {
  country?: string;
  email?: string;
  businessType?: "individual" | "company";
}

export const createConnectAccount = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const userId = context.auth.uid;
    const userRef = db.collection("users").doc(userId);
    const userSnap = await userRef.get();
    const userData = userSnap.data() || {};
    const stripeSecret = getApiKeys().stripe;
    const expectedLiveMode = (stripeSecret || "").startsWith("sk_live_");

    const existingAccountId = (userData.payoutAccountId || userData.stripeAccountId) as string | undefined;
    if (existingAccountId) {
      let shouldReplaceExistingAccount = false;
      try {
        const existingAccount = await getStripe().accounts.retrieve(existingAccountId) as Stripe.Account;
        const accountLivemode = ((existingAccount as unknown as {livemode?: boolean}).livemode === true);
        const accountType = ((existingAccount as unknown as {type?: string}).type || "").toLowerCase();
        const controllerType = ((existingAccount as unknown as {controller?: {type?: string}}).controller?.type || "").toLowerCase();
        const controllerDashboardType = ((existingAccount as unknown as {controller?: {stripe_dashboard?: {type?: string}}}).controller?.stripe_dashboard?.type || "").toLowerCase();
        const isExpressAccount = accountType === "express" || controllerDashboardType === "express";
        const isPlatformManaged = controllerType ? controllerType === "application" : isExpressAccount;
        const isUsableAccount =
          accountLivemode === expectedLiveMode &&
          isExpressAccount &&
          isPlatformManaged;

        if (isUsableAccount) {
          if (!userData.payoutAccountId && userData.stripeAccountId) {
            await userRef.set({payoutAccountId: userData.stripeAccountId}, {merge: true});
          }
          return {accountId: existingAccountId, alreadyExists: true};
        }

        shouldReplaceExistingAccount = true;
        functions.logger.warn("Existing payout account is not usable; creating replacement account.", {
          userId,
          existingAccountId,
          existingAccountLivemode: accountLivemode,
          expectedLiveMode,
          accountType,
          controllerType,
          controllerDashboardType,
          isExpressAccount,
          isPlatformManaged,
        });
      } catch (error) {
        const stripeError = error as Stripe.errors.StripeError | undefined;
        const rawMessage = stripeError?.message || "";
        const message = rawMessage.toLowerCase();
        const isRecoverableExistingAccountError =
          message.includes("test mode") ||
          message.includes("live mode") ||
          message.includes("a similar object exists") ||
          message.includes("no such account") ||
          message.includes("required permissions") ||
          message.includes("does not have the required permissions");

        if (!isRecoverableExistingAccountError) {
          functions.logger.error("Failed to verify existing payout account.", {
            userId,
            existingAccountId,
            stripeType: stripeError?.type,
            stripeCode: stripeError?.code,
            stripeMessage: stripeError?.message,
            stripeRequestId: stripeError?.requestId,
            error,
          });
          throw new functions.https.HttpsError("internal", "Could not verify existing payout account.");
        }

        shouldReplaceExistingAccount = true;
        functions.logger.warn("Existing payout account is not usable; creating replacement account.", {
          userId,
          existingAccountId,
          stripeMessage: rawMessage,
          expectedLiveMode,
        });
      }

      if (shouldReplaceExistingAccount) {
        const staleAccountUpdates: Record<string, unknown> = {
          payoutAccountId: admin.firestore.FieldValue.delete(),
        };
        if (userData.stripeAccountId === existingAccountId) {
          staleAccountUpdates.stripeAccountId = admin.firestore.FieldValue.delete();
        }
        await userRef.set(staleAccountUpdates, {merge: true});
      }
    }

    const request = data as CreateConnectAccountRequest;
    const country = request?.country || userData.country || "US";
    const email = request?.email || userData.email || undefined;

    try {
      const account = await getStripe().accounts.create({
        type: "express",
        country,
        email,
        business_type: request?.businessType || "individual",
        capabilities: {
          card_payments: {requested: true},
          transfers: {requested: true},
        },
      });

      await userRef.set({payoutAccountId: account.id, stripeAccountId: account.id}, {merge: true});
      return {accountId: account.id, alreadyExists: false};
    } catch (error) {
      const stripeError = error as Stripe.errors.StripeError | undefined;
      functions.logger.error("Failed to create payout account", {
        userId,
        country,
        email,
        stripeType: stripeError?.type,
        stripeCode: stripeError?.code,
        stripeMessage: stripeError?.message,
        stripeRequestId: stripeError?.requestId,
        error,
      });

      if (
        stripeError?.type === "StripeInvalidRequestError" &&
        (stripeError?.message || "").includes("signed up for Connect")
      ) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Stripe Connect is not enabled for this Stripe account. Enable Connect in Stripe Dashboard and try again."
        );
      }

      if (stripeError?.message) {
        throw new functions.https.HttpsError("failed-precondition", stripeError.message);
      }

      throw new functions.https.HttpsError("internal", "Could not create payout account.");
    }
  });

interface CreateOnboardingLinkRequest {
  accountId?: string;
}

export const createConnectOnboardingLink = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const userId = context.auth.uid;
    const userSnap = await db.collection("users").doc(userId).get();
    const accountId = (data as CreateOnboardingLinkRequest)?.accountId ||
      userSnap.data()?.payoutAccountId ||
      userSnap.data()?.stripeAccountId;

    const config = getAppConfig();
    if (!config.stripeConnectReturnUrl || !config.stripeConnectRefreshUrl) {
      throw new functions.https.HttpsError("failed-precondition", "Missing payout return/refresh URLs.");
    }

    if (!accountId) {
      throw new functions.https.HttpsError("failed-precondition", "Payout account not found.");
    }

    try {
      const link = await getStripe().accountLinks.create({
        account: accountId,
        refresh_url: config.stripeConnectRefreshUrl,
        return_url: config.stripeConnectReturnUrl,
        type: "account_onboarding",
      });
      return {url: link.url};
    } catch (error) {
      functions.logger.error("Failed to create setup link", error);
      throw new functions.https.HttpsError("internal", "Could not create setup link.");
    }
  });

interface GetConnectAccountStatusRequest {
  accountId?: string;
}

export const getConnectAccountStatus = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const userId = context.auth.uid;
    const userSnap = await db.collection("users").doc(userId).get();
    const accountId = (data as GetConnectAccountStatusRequest)?.accountId ||
      userSnap.data()?.payoutAccountId ||
      userSnap.data()?.stripeAccountId;

    if (!accountId) {
      return {hasAccount: false, detailsSubmitted: false, payoutsEnabled: false, chargesEnabled: false};
    }

    try {
      const account = await getStripe().accounts.retrieve(accountId);
      const requirements = (account as Stripe.Account).requirements;
      return {
        hasAccount: true,
        detailsSubmitted: (account as Stripe.Account).details_submitted || false,
        payoutsEnabled: (account as Stripe.Account).payouts_enabled || false,
        chargesEnabled: (account as Stripe.Account).charges_enabled || false,
        currentlyDue: requirements?.currently_due || [],
        eventuallyDue: requirements?.eventually_due || [],
      };
    } catch (error) {
      functions.logger.error("Failed to retrieve payout account status", error);
      throw new functions.https.HttpsError("internal", "Could not retrieve payout account status.");
    }
  });

interface AttachExternalAccountRequest {
  accountId?: string;
  paymentMethodId: string;
  externalAccountToken: string;
  chargeExternalAccountToken?: string;
  methodType?: string;
}

interface CreateUsBankAccountSetupIntentRequest {
  accountHolderName?: string;
  email?: string;
}

export const createUsBankAccountSetupIntent = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const userId = context.auth.uid;
    const request = (data || {}) as CreateUsBankAccountSetupIntentRequest;
    const userRef = db.collection("users").doc(userId);
    const userSnap = await userRef.get();
    if (!userSnap.exists) {
      throw new functions.https.HttpsError("not-found", "User profile not found.");
    }

    const userData = userSnap.data() || {};
    let customerId = asNonEmptyString(userData.paymentCustomerId);
    if (!customerId) {
      const customer = await getStripe().customers.create({
        email: asNonEmptyString(request.email, userData.email) || undefined,
        metadata: {userId},
      });
      customerId = customer.id;
      await userRef.set({paymentCustomerId: customerId}, {merge: true});
    }

    try {
      const setupIntent = await getStripe().setupIntents.create({
        customer: customerId,
        payment_method_types: ["us_bank_account"],
        usage: "off_session",
        payment_method_options: {
          us_bank_account: {
            verification_method: "automatic",
          },
        },
        metadata: {
          userId,
          flow: "wallet_us_bank_link",
          accountHolderName: asNonEmptyString(request.accountHolderName) || "unknown",
        },
      });

      if (!setupIntent.client_secret) {
        throw new functions.https.HttpsError("internal", "Stripe setup intent did not return a client secret.");
      }

      return {
        setupIntentId: setupIntent.id,
        clientSecret: setupIntent.client_secret,
        customerId,
      };
    } catch (error) {
      const stripeError = error as Stripe.errors.StripeError | undefined;
      functions.logger.error("Failed to create US bank setup intent", {
        userId,
        customerId,
        error,
      });
      throw new functions.https.HttpsError(
        "failed-precondition",
        stripeError?.message || "Could not initialize US bank linking."
      );
    }
  });

interface AddUsBankAccountFromFinancialConnectionsRequest {
  stripePaymentMethodId?: string;
  label?: string;
  accountHolderName?: string;
  isDefault?: boolean;
}

export const addUsBankAccountFromFinancialConnections = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const userId = context.auth.uid;
    const request = (data || {}) as AddUsBankAccountFromFinancialConnectionsRequest;
    const stripePaymentMethodId = asNonEmptyString(request.stripePaymentMethodId);
    if (!stripePaymentMethodId) {
      throw new functions.https.HttpsError("invalid-argument", "Missing Stripe payment method id.");
    }

    const userRef = db.collection("users").doc(userId);
    const userSnap = await userRef.get();
    if (!userSnap.exists) {
      throw new functions.https.HttpsError("not-found", "User profile not found.");
    }
    const userData = userSnap.data() || {};

    let customerId = asNonEmptyString(userData.paymentCustomerId);
    if (!customerId) {
      const customer = await getStripe().customers.create({
        email: asNonEmptyString(userData.email) || undefined,
        metadata: {userId},
      });
      customerId = customer.id;
      await userRef.set({paymentCustomerId: customerId}, {merge: true});
    }

    let paymentMethod: Stripe.PaymentMethod;
    try {
      paymentMethod = await getStripe().paymentMethods.retrieve(stripePaymentMethodId) as Stripe.PaymentMethod;
    } catch (error) {
      const stripeError = error as Stripe.errors.StripeError | undefined;
      throw new functions.https.HttpsError(
        "failed-precondition",
        stripeError?.message || "Could not retrieve linked bank account from Stripe."
      );
    }

    if (paymentMethod.type !== "us_bank_account" || !paymentMethod.us_bank_account) {
      throw new functions.https.HttpsError("invalid-argument", "Stripe payment method is not a US bank account.");
    }

    const paymentMethodCustomerId = typeof paymentMethod.customer === "string" ?
      paymentMethod.customer :
      paymentMethod.customer?.id;
    if (paymentMethodCustomerId && paymentMethodCustomerId !== customerId) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "This bank payment method belongs to a different customer."
      );
    }

    if (!paymentMethodCustomerId) {
      await getStripe().paymentMethods.attach(stripePaymentMethodId, {customer: customerId});
      paymentMethod = await getStripe().paymentMethods.retrieve(stripePaymentMethodId) as Stripe.PaymentMethod;
    }

    const usBank = paymentMethod.us_bank_account;
    const last4 = asNonEmptyString(usBank?.last4);
    if (!last4) {
      throw new functions.https.HttpsError("failed-precondition", "Linked bank account is missing last4 details.");
    }

    const bankName = asNonEmptyString(
      request.label,
      usBank?.bank_name,
      paymentMethod.billing_details?.name
    ) || "US Bank Account";
    const accountHolderName = asNonEmptyString(
      request.accountHolderName,
      paymentMethod.billing_details?.name
    ) || "Account Holder";

    const methodsRef = userRef.collection("payment_methods");
    const dedupeKey = buildPaymentMethodDedupeKey({
      type: "BANK",
      country: "US",
      last4,
      routingNumber: "norouting",
    });

    if (!dedupeKey) {
      throw new functions.https.HttpsError("internal", "Could not compute bank dedupe key.");
    }

    const existingSnap = await methodsRef.where("type", "==", "BANK").get();
    const hasDuplicate = existingSnap.docs.some((doc) => {
      const existingData = doc.data() as AddPaymentMethodRequest;
      return buildPaymentMethodDedupeKey(existingData) === dedupeKey;
    });
    if (hasDuplicate) {
      throw new functions.https.HttpsError("already-exists", "This bank account is already saved.");
    }

    const shouldBeDefault = request.isDefault === true || existingSnap.empty;
    const payload: AddPaymentMethodRequest = {
      type: "BANK",
      label: bankName,
      bankName,
      accountHolderName,
      accountNumber: `********${last4}`,
      last4,
      country: "US",
      currency: "USD",
      dedupeKey,
      isDefault: shouldBeDefault,
      chargePaymentMethodId: stripePaymentMethodId,
      chargeCustomerId: customerId,
      achDebitEnabled: true,
      requiresRelinkForCharges: false,
      chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
      financialConnectionsLinked: true,
      stripePaymentMethodId,
      externalAccountId: null,
      requiresPayoutSetup: true,
    };

    const paymentMethodDocId = `dedupe_${dedupeKey}`;
    const paymentMethodRef = methodsRef.doc(paymentMethodDocId);
    try {
      await paymentMethodRef.create(payload);
    } catch (error) {
      if (isAlreadyExistsError(error)) {
        throw new functions.https.HttpsError("already-exists", "This bank account is already saved.");
      }
      throw error;
    }

    if (shouldBeDefault) {
      const allMethodsSnap = await methodsRef.get();
      const batch = db.batch();
      allMethodsSnap.docs.forEach((doc) => {
        batch.set(doc.ref, {isDefault: doc.id === paymentMethodDocId}, {merge: true});
      });
      await batch.commit();
    }

    return {
      success: true,
      message: "US bank account linked successfully. ACH funding is enabled.",
      paymentMethodId: paymentMethodDocId,
      last4,
      bankName,
    };
  });

export const attachExternalAccount = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const userId = context.auth.uid;
    const request = data as AttachExternalAccountRequest;
    if (!request?.externalAccountToken || !request?.paymentMethodId) {
      throw new functions.https.HttpsError("invalid-argument", "Missing external account token or payment method id.");
    }

    const userRef = db.collection("users").doc(userId);
    const userSnap = await userRef.get();
    const accountId = request.accountId || userSnap.data()?.payoutAccountId || userSnap.data()?.stripeAccountId;

    const methodSnap = await userRef.collection("payment_methods").doc(request.paymentMethodId).get();
    if (!methodSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Payment method record not found.");
    }
    const methodData = methodSnap.data() || {};
    const isDefault = methodData.isDefault === true;
    const methodType = ((request.methodType || methodData.type || "") as string).toUpperCase();
    const bankCountry = String(methodData.country || "").trim().toUpperCase();
    const chargeToken = asNonEmptyString(request.chargeExternalAccountToken);
    const hasUsAchChargeToken = methodType === "BANK" && bankCountry === "US" && !!chargeToken;
    const allowBankWithoutPayoutAccount = methodType === "BANK";

    if (!accountId && methodType !== "CARD" && !hasUsAchChargeToken && !allowBankWithoutPayoutAccount) {
      throw new functions.https.HttpsError("failed-precondition", "Payout account not found.");
    }

    let externalAccountId: string | null = null;
    let chargePaymentMethodId: string | null = null;
    let chargeSourceId: string | null = null;
    let chargeSourceStatus: string | null = null;
    let achDebitEnabled = false;
    let customerId = userSnap.data()?.paymentCustomerId as string | undefined;

    const ensureCustomerId = async (): Promise<string> => {
      if (customerId) return customerId;
      const customer = await getStripe().customers.create({
        email: userSnap.data()?.email || undefined,
        metadata: {userId},
      });
      customerId = customer.id;
      await userRef.set({paymentCustomerId: customerId}, {merge: true});
      return customerId;
    };

    try {
      if (methodType === "CARD") {
        const resolvedCustomerId = await ensureCustomerId();

        const chargeMethod = await getStripe().paymentMethods.create({
          type: "card",
          card: {token: request.externalAccountToken},
        });

        await getStripe().paymentMethods.attach(chargeMethod.id, {customer: resolvedCustomerId});

        if (isDefault) {
          await getStripe().customers.update(resolvedCustomerId, {
            invoice_settings: {default_payment_method: chargeMethod.id},
          });
        }

        chargePaymentMethodId = chargeMethod.id;
        await userRef.collection("payment_methods").doc(request.paymentMethodId).set({
          chargePaymentMethodId: chargePaymentMethodId,
          requiresRelinkForCharges: false,
          chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
      }

      if (methodType === "BANK") {
        if (bankCountry === "US" && chargeToken) {
          const resolvedCustomerId = await ensureCustomerId();
          const bankSource = await getStripe().customers.createSource(resolvedCustomerId, {
            source: chargeToken,
          });
          const sourceData = bankSource as Stripe.BankAccount;
          chargeSourceId = sourceData.id;
          chargeSourceStatus = (sourceData.status || null) as string | null;
          achDebitEnabled = !chargeSourceId ? false :
            (String(chargeSourceStatus || "").toLowerCase() === "verified");

          await userRef.collection("payment_methods").doc(request.paymentMethodId).set({
            chargeSourceId: chargeSourceId,
            chargeSourceStatus: chargeSourceStatus,
            chargeCustomerId: resolvedCustomerId,
            achDebitEnabled: achDebitEnabled,
            requiresRelinkForCharges: !achDebitEnabled,
            chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
          }, {merge: true});
        } else if (bankCountry === "US") {
          await userRef.collection("payment_methods").doc(request.paymentMethodId).set({
            achDebitEnabled: false,
            requiresRelinkForCharges: true,
            chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
          }, {merge: true});
        }
      }

      // Important: card tokens are single-use. For CARD methods, the token is already consumed
      // by paymentMethods.create() above, so we only attach external payout accounts for non-card methods.
      if (accountId && methodType !== "CARD") {
        const externalAccount = await getStripe().accounts.createExternalAccount(accountId, {
          external_account: request.externalAccountToken,
        });
        externalAccountId = externalAccount.id;

        await userRef.collection("payment_methods").doc(request.paymentMethodId).set({
          externalAccountId: externalAccountId,
          requiresPayoutSetup: false,
          payoutSetupCheckedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
      } else if (!accountId && methodType === "BANK") {
        await userRef.collection("payment_methods").doc(request.paymentMethodId).set({
          requiresPayoutSetup: true,
          payoutSetupCheckedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});

        functions.logger.info("Saved bank method without payout external account attachment.", {
          userId,
          paymentMethodId: request.paymentMethodId,
          methodType,
          bankCountry,
          hasUsAchChargeToken,
        });
      }

      return {externalAccountId, chargePaymentMethodId, chargeSourceId, achDebitEnabled, chargeSourceStatus};
    } catch (error) {
      functions.logger.error("Failed to attach payout account", {
        userId,
        paymentMethodId: request.paymentMethodId,
        methodType,
        hasAccountId: !!accountId,
        error,
      });

      if (error instanceof functions.https.HttpsError) {
        throw error;
      }

      const stripeError = error as Stripe.errors.StripeError | undefined;
      const message = stripeError?.message || "Could not attach payout account.";
      throw new functions.https.HttpsError("failed-precondition", message);
    }
  });

// =============================================================================
//  13. PROCESS PENDING PAYOUT REQUEST (CONNECT)
// =============================================================================

interface ProcessPayoutRequest {
  payoutRequestId: string;
}

const processPayoutRequestDoc = async (payoutRef: FirebaseFirestore.DocumentReference, payoutData: FirebaseFirestore.DocumentData) => {
  if (payoutData.status !== "PENDING") {
    throw new functions.https.HttpsError("failed-precondition", "Payout request is not pending.");
  }

  const recipientId = payoutData.recipientId as string | undefined;
  if (!recipientId) {
    throw new functions.https.HttpsError("failed-precondition", "Recipient is missing.");
  }

  const recipientSnap = await db.collection("users").doc(recipientId).get();
  const payoutAccountId = (recipientSnap.data()?.payoutAccountId || recipientSnap.data()?.stripeAccountId) as string | undefined;
  if (!payoutAccountId) {
    throw new functions.https.HttpsError("failed-precondition", "Recipient payout setup is incomplete.");
  }

  const methodId = payoutData.paymentMethodId as string | undefined;
  if (!methodId) {
    throw new functions.https.HttpsError("failed-precondition", "Recipient payment method missing.");
  }

  const methodSnap = await db.collection("users").doc(recipientId)
    .collection("payment_methods").doc(methodId).get();
  const externalAccountId = (methodSnap.data()?.externalAccountId || methodSnap.data()?.stripeExternalAccountId) as string | undefined;
  if (!externalAccountId) {
    throw new functions.https.HttpsError("failed-precondition", "Recipient external account is not attached.");
  }

  const amount = Number(payoutData.amount || 0);
  const currency = (payoutData.currency || "USD").toLowerCase();
  if (!amount || amount <= 0) {
    throw new functions.https.HttpsError("invalid-argument", "Invalid payout amount.");
  }

  const amountInSmallestUnit = Math.round(amount * 100);

  await payoutRef.set({
    status: "PROCESSING",
    processedAt: admin.firestore.Timestamp.now(),
  }, {merge: true});

  const transfer = await getStripe().transfers.create({
    amount: amountInSmallestUnit,
    currency,
    destination: payoutAccountId,
    metadata: {
      payoutRequestId: payoutRef.id,
      recipientId,
    },
  });

  const payout = await getStripe().payouts.create({
    amount: amountInSmallestUnit,
    currency,
    destination: externalAccountId,
  }, {
    stripeAccount: payoutAccountId,
  });

  await payoutRef.set({
    status: "COMPLETED",
    payoutTransferId: transfer.id,
    payoutId: payout.id,
    processedAt: admin.firestore.Timestamp.now(),
  }, {merge: true});
};

export const processPayoutRequest = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    const request = data as ProcessPayoutRequest;
    if (!request?.payoutRequestId) {
      throw new functions.https.HttpsError("invalid-argument", "Missing payoutRequestId.");
    }

    const payoutRef = db.collection("payout_requests").doc(request.payoutRequestId);
    const payoutSnap = await payoutRef.get();
    if (!payoutSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Payout request not found.");
    }
    try {
      const payoutData = payoutSnap.data() || {};
      await processPayoutRequestDoc(payoutRef, payoutData);

      return {success: true};
    } catch (error) {
      functions.logger.error("Payout processing failed", error);
      await payoutRef.set({
        status: "FAILED",
        errorMessage: "Payout failed.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      throw new functions.https.HttpsError("internal", "Payout failed.");
    }
  });

export const onPayoutRequestCreated = functions.firestore
  .document("payout_requests/{payoutRequestId}")
  .onCreate(async (snap) => {
    const payoutData = snap.data();
    if (payoutData.status !== "PENDING") return null;
    if (payoutData.source !== "WALLET_TRANSFER") return null;
    if (payoutData.destinationType === "WALLET") return null;

    try {
      await processPayoutRequestDoc(snap.ref, payoutData);
    } catch (error) {
      functions.logger.error("Auto payout processing failed", error);
      await snap.ref.set({
        status: "FAILED",
        errorMessage: "Payout failed.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
    }

    return null;
  });

export const onMobileMoneyRequestCreated = functions.firestore
  .document("payout_requests/{payoutRequestId}")
  .onCreate(async (snap) => {
    const data = snap.data();
    if (!data || data.status !== "PENDING") return null;
    if (data.type !== "CASH_IN" && data.type !== "CASH_OUT") return null;

    const userId = data.senderId as string | undefined;
    if (!userId) return null;

    const amount = Number(data.amount || 0);
    const verificationOnly = data.verificationOnly === true;
    const localAmountForVerification = Number(data.localAmount || 0);
    if (!verificationOnly && (!Number.isFinite(amount) || amount <= 0)) return null;
    if (verificationOnly && data.type !== "CASH_IN") {
      await snap.ref.set({
        status: "FAILED",
        errorMessage: "Verification-only mode is only supported for mobile money cash-in.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      await markMobileMoneyMethodVerificationStatus(
        userId,
        data,
        "FAILED",
        "Verification-only mode is only supported for mobile money cash-in."
      );
      return null;
    }
    if (verificationOnly && (!Number.isFinite(localAmountForVerification) || localAmountForVerification <= 0)) {
      await snap.ref.set({
        status: "FAILED",
        errorMessage: "Verification local amount must be greater than zero.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      await markMobileMoneyMethodVerificationStatus(
        userId,
        data,
        "FAILED",
        "Verification local amount must be greater than zero."
      );
      return null;
    }

    const senderSnap = await db.collection("users").doc(userId).get();
    const senderData = (senderSnap.data() || {}) as Record<string, unknown>;
    const senderStaffFeeExempt = isStaffFeeExempt(senderData);

    const mobileMoneyHiddenFeeRate = Number.parseFloat(getAppConfig().mobileMoneyHiddenFeeRate);
    const hiddenFeeBaseAmount = Number.isFinite(amount) && amount > 0 ? amount : 0;
    const hiddenFee = senderStaffFeeExempt ? 0 : roundMoney(hiddenFeeBaseAmount * mobileMoneyHiddenFeeRate);

    const currency = resolveMobileMoneyCurrency(data as Record<string, unknown>);
    if (!currency) {
      await snap.ref.set({
        status: "FAILED",
        errorMessage: "Unsupported mobile money currency.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      if (data.type === "CASH_IN") {
        await markMobileMoneyMethodVerificationStatus(userId, data, "FAILED", "Unsupported mobile money currency.");
      }
      return null;
    }

    const phone = data.phone as string | undefined;
    const country = data.country as string | undefined;
    const network = asNonEmptyString(data.network);
    const paymentMethodId = asNonEmptyString(data.paymentMethodId);
    const senderMethodsRef = db.collection("users").doc(userId).collection("payment_methods");

    let linkedMethodData: FirebaseFirestore.DocumentData | null = null;
    if (paymentMethodId) {
      const linkedMethodSnap = await senderMethodsRef.doc(paymentMethodId).get();
      if (!linkedMethodSnap.exists) {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Selected mobile money payment method was not found.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }
      linkedMethodData = linkedMethodSnap.data() || null;
      const linkedType = String(linkedMethodData?.type || "").toUpperCase();
      if (linkedType !== "MOBILE_MONEY") {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Selected payment method is not a mobile money account.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }

      const linkedPhoneDigits = normalizeDigits(linkedMethodData?.phoneNumber || "");
      const requestPhoneDigits = normalizeDigits(phone || "");
      const linkedNetwork = normalizeText(linkedMethodData?.network || "");
      const requestNetwork = normalizeText(network || "");
      const linkedCountry = normalizeText(linkedMethodData?.country || "");
      const requestCountry = normalizeText(country || "");

      if (!requestPhoneDigits || linkedPhoneDigits !== requestPhoneDigits) {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Mobile money request phone does not match the selected payment method.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }
      if (requestNetwork && linkedNetwork && linkedNetwork !== requestNetwork) {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Mobile money request network does not match the selected payment method.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }
      if (requestCountry && linkedCountry && linkedCountry !== requestCountry) {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Mobile money request country does not match the selected payment method.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }
    }

    if (data.type === "CASH_OUT") {
      const phoneOwnershipVerified = linkedMethodData?.phoneOwnershipVerified === true;
      if (!paymentMethodId || !phoneOwnershipVerified) {
        await snap.ref.set({
          status: "FAILED",
          errorMessage: "Mobile money number is not verified. Complete a confirmed mobile money deposit first.",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
        return null;
      }
    }

    const localAmountRaw = Number(data.localAmount || 0);
    const localCurrency = asNonEmptyString(data.localCurrency, currency)?.toUpperCase() || currency;
    const providerAmountLabel = Number.isFinite(localAmountRaw) && localAmountRaw > 0 ?
      `${roundMoney(localAmountRaw).toFixed(2)} ${localCurrency}` :
      `${roundMoney(amount).toFixed(2)} ${currency}`;
    const walletAmountLabel = verificationOnly ?
      "0.00 USD (verification-only)" :
      `${roundMoney(amount).toFixed(2)} USD`;

    if (data.type === "CASH_IN") {
      await markMobileMoneyMethodVerificationStatus(userId, data, "REQUESTED");
    }

    try {
      if (data.type === "CASH_OUT") {
        await snap.ref.set({
          status: "PENDING_PROVIDER",
          providerMessage: "Cash-out request queued for mobile money provider processing.",
          payoutCurrency: currency,
          hiddenFeeAmount: hiddenFee,
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});

        await db.collection("users").doc(userId).collection("transactions").add({
          title: "Mobile Money Withdrawal (Pending)",
          amount: -amount,
          type: "DEBIT",
          status: "PENDING",
          timestamp: admin.firestore.Timestamp.now(),
          note: `To ${phone || "mobile money"} (${country || "Unknown country"}) - wallet debit ${walletAmountLabel}, provider payout ${providerAmountLabel}.`,
          source: "MOBILE_MONEY",
          payoutRequestId: snap.id,
        });

        await processPendingMobileMoneyProviderPayout(snap.ref, {
          ...data,
          status: "PENDING_PROVIDER",
          type: "CASH_OUT",
          hiddenFeeAmount: hiddenFee,
        });
      }

      if (data.type === "CASH_IN") {
        await snap.ref.set({
          status: "PENDING_PROVIDER",
          providerMessage: verificationOnly ?
            "Verification request queued for mobile money provider confirmation." :
            "Cash-in request queued for mobile money provider confirmation.",
          payoutCurrency: currency,
          hiddenFeeAmount: hiddenFee,
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});

        if (!verificationOnly) {
          await db.collection("users").doc(userId).collection("transactions").add({
            title: "Mobile Money Deposit (Pending)",
            amount: amount,
            type: "CREDIT",
            status: "PENDING",
            timestamp: admin.firestore.Timestamp.now(),
            note: `From ${phone || "mobile money"} (${country || "Unknown country"}) - provider collection ${providerAmountLabel}, wallet credit pending ${walletAmountLabel}.`,
            source: "MOBILE_MONEY",
            payoutRequestId: snap.id,
          });
        }

        await markMobileMoneyMethodVerificationStatus(userId, data, "AWAITING_CONFIRMATION");

        await processPendingMobileMoneyProviderPayout(snap.ref, {
          ...data,
          status: "PENDING_PROVIDER",
          type: "CASH_IN",
          hiddenFeeAmount: hiddenFee,
        });
      }
    } catch (error) {
      functions.logger.error("Mobile money auto processing failed", error);
      await snap.ref.set({
        status: "FAILED",
        errorMessage: "Mobile money processing failed.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      if (data.type === "CASH_IN") {
        await markMobileMoneyMethodVerificationStatus(
          userId,
          data,
          "FAILED",
          "Mobile money verification request failed before provider submission."
        );
      }
    }

    return null;
  });

export const onPendingMobileMoneyProviderRequestCreated = functions.firestore
  .document("payout_requests/{payoutRequestId}")
  .onCreate(async (snap) => {
    const data = snap.data();
    if (!data) return null;
    if (data.status !== "PENDING_PROVIDER") return null;

    const type = String(data.type || "");
    if (type !== "CASH_OUT" && type !== "BENEFICIARY_TRANSFER" && type !== "CASH_IN") return null;

    try {
      await processPendingMobileMoneyProviderPayout(snap.ref, data);
    } catch (error) {
      functions.logger.error("Pending mobile money provider processing failed", error);
      await snap.ref.set({
        status: "FAILED",
        providerStatus: "FAILED",
        errorMessage: "Mobile money provider processing failed.",
        processedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      if (type === "CASH_IN") {
        const senderId = asNonEmptyString(data.senderId);
        if (senderId) {
          await markMobileMoneyMethodVerificationStatus(
            senderId,
            data,
            "FAILED",
            "Mobile money provider processing failed."
          );
        }
      }
    }
    return null;
  });

export const onMobileMoneyPayoutStatusChanged = functions.firestore
  .document("payout_requests/{payoutRequestId}")
  .onUpdate(async (change) => {
    const before = change.before.data() || {};
    const after = change.after.data() || {};

    if (before.status === after.status) return null;

    const type = String(after.type || "");
    if (type !== "CASH_OUT" && type !== "BENEFICIARY_TRANSFER" && type !== "CASH_IN") return null;

    const senderId = after.senderId as string | undefined;
    if (!senderId) return null;

    let nextTxStatus: "COMPLETED" | "FAILED" | null = null;
    if (after.status === "COMPLETED") nextTxStatus = "COMPLETED";
    if (after.status === "FAILED") nextTxStatus = "FAILED";
    if (!nextTxStatus) return null;

    if (type === "CASH_IN" && nextTxStatus === "COMPLETED") {
      await settleMobileMoneyCashInToWallet(change.after.ref, after);
    }
    if (type === "CASH_IN" && nextTxStatus === "FAILED") {
      await markMobileMoneyMethodVerificationStatus(
        senderId,
        after,
        "FAILED",
        asNonEmptyString(after.errorMessage, after.providerMessage) || "Verification failed."
      );
    }
    if (type === "CASH_OUT" && nextTxStatus === "FAILED") {
      await refundWalletForFailedMobileMoneyCashOut(change.after.ref, after);
    }
    if (type === "BENEFICIARY_TRANSFER" && nextTxStatus === "FAILED") {
      await refundWalletForFailedExternalMobileMoneyBeneficiaryTransfer(change.after.ref, after);
    }

    await finalizeSenderTransactionsForPayout(
      senderId,
      change.after.ref,
      after,
      nextTxStatus
    );

    if (nextTxStatus === "COMPLETED") {
      await recordMobileMoneyHiddenFeeRevenueIfNeeded(change.after.ref, after, senderId);
    }
    return null;
  });

export const mobileMoneyProviderWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({error: "Method not allowed. Use POST."});
    return;
  }

  const expectedSecret = process.env.MOBILE_MONEY_PROVIDER_WEBHOOK_SECRET;
  const secretHeaderName = (process.env.MOBILE_MONEY_PROVIDER_WEBHOOK_HEADER || "x-mobile-money-webhook-secret").toLowerCase();
  if (expectedSecret) {
    const providedSecret = req.header(secretHeaderName) || "";
    if (providedSecret !== expectedSecret) {
      res.status(403).json({error: "Invalid webhook secret."});
      return;
    }
  }

  const body = (req.body || {}) as Record<string, unknown>;
  const metadata = (body["metadata"] as Record<string, unknown> | undefined) || {};
  const payoutRequestId = asNonEmptyString(
    body["payoutRequestId"],
    body["payout_request_id"],
    body["payoutId"],
    body["payout_id"],
    metadata["payoutRequestId"],
    metadata["payout_request_id"],
    metadata["payoutId"],
    metadata["payout_id"]
  );

  const providerTransferId = asNonEmptyString(
    body["providerTransferId"],
    body["provider_transfer_id"],
    body["providerTransactionId"],
    body["provider_transaction_id"],
    body["transferId"],
    body["transfer_id"],
    body["reference"],
    body["tx_ref"],
    body["id"]
  );

  const rawStatus = asNonEmptyString(
    body["status"],
    body["transfer_status"],
    body["state"],
    body["result"]
  );
  const normalizedStatus = normalizeMobileMoneyProviderResultStatus(rawStatus);
  const providerMessage = asNonEmptyString(
    body["message"],
    body["detail"],
    body["reason"],
    body["error"],
    body["failureReason"],
    body["failureCode"]
  );

  let payoutRef: FirebaseFirestore.DocumentReference | null = null;
  if (payoutRequestId) {
    payoutRef = db.collection("payout_requests").doc(payoutRequestId);
  } else if (providerTransferId) {
    const snap = await db.collection("payout_requests")
      .where("providerTransferId", "==", providerTransferId)
      .limit(1)
      .get();
    payoutRef = snap.empty ? null : snap.docs[0].ref;
  }

  if (!payoutRef) {
    res.status(404).json({error: "Payout request not found."});
    return;
  }

  const payoutSnap = await payoutRef.get();
  if (!payoutSnap.exists) {
    res.status(404).json({error: "Payout request does not exist."});
    return;
  }

  const payoutData = payoutSnap.data() || {};
  const payoutType = String(payoutData.type || "");
  if (payoutType !== "CASH_OUT" && payoutType !== "BENEFICIARY_TRANSFER" && payoutType !== "CASH_IN") {
    res.status(400).json({error: "Not a mobile money payout request."});
    return;
  }

  await applyMobileMoneyProviderResult(payoutRef, payoutData, {
    status: normalizedStatus,
    providerTransferId,
    providerMessage,
    rawStatus,
  });

  res.status(200).json({
    success: true,
    payoutRequestId: payoutRef.id,
    status: normalizedStatus,
    providerTransferId: providerTransferId || null,
  });
});

const markDepositFailed = async (
  depositRef: FirebaseFirestore.DocumentReference,
  message: string,
  extra: Record<string, unknown> = {}
) => {
  await depositRef.set({
    status: "FAILED",
    errorMessage: message,
    processedAt: admin.firestore.Timestamp.now(),
    ...extra,
  }, {merge: true});
};

const finalizeDepositWalletCredit = async (
  args: {
    depositRef: FirebaseFirestore.DocumentReference;
    userRef: FirebaseFirestore.DocumentReference;
    amount: number;
    title: string;
    note: string;
    source: string;
    metadata?: Record<string, unknown>;
  }
) => {
  const {depositRef, userRef, amount, title, note, source, metadata = {}} = args;
  await db.runTransaction(async (transaction) => {
    const freshDepositSnap = await transaction.get(depositRef);
    if (!freshDepositSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Deposit request not found.");
    }

    const freshDeposit = freshDepositSnap.data() || {};
    if (freshDeposit.walletCredited === true) {
      return;
    }

    const txRef = userRef.collection("transactions").doc();
    const now = admin.firestore.Timestamp.now();

    transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(amount));
    transaction.set(txRef, {
      title,
      amount,
      type: "CREDIT",
      status: "COMPLETED",
      timestamp: now,
      note,
      source,
      depositRequestId: depositRef.id,
    });
    transaction.set(depositRef, {
      status: "COMPLETED",
      walletCredited: true,
      walletCreditTransactionId: txRef.id,
      processedAt: now,
      ...metadata,
    }, {merge: true});
  });
};

const settlePendingBankDepositRequest = async (depositRef: FirebaseFirestore.DocumentReference, data: FirebaseFirestore.DocumentData) => {
  const userId = asNonEmptyString(data.userId);
  const amount = Number(data.amount || 0);
  const bankChargeId = asNonEmptyString(data.bankChargeId);
  if (!userId || !Number.isFinite(amount) || amount <= 0 || !bankChargeId) {
    await markDepositFailed(depositRef, "Invalid pending bank deposit state.");
    return;
  }

  const userRef = db.collection("users").doc(userId);
  const charge = await getStripe().charges.retrieve(bankChargeId);
  const chargeStatus = String((charge as Stripe.Charge).status || "").toLowerCase();

  if (chargeStatus === "succeeded") {
    await finalizeDepositWalletCredit({
      depositRef,
      userRef,
      amount,
      title: "Bank Deposit",
      note: "ACH debit settled from linked bank account",
      source: "BANK_DEPOSIT",
      metadata: {
        bankChargeId: (charge as Stripe.Charge).id,
        bankChargeStatus: chargeStatus,
      },
    });
    return;
  }

  if (chargeStatus === "pending") {
    await depositRef.set({
      status: "PENDING_SETTLEMENT",
      bankChargeStatus: chargeStatus,
      lastStatusCheckAt: admin.firestore.Timestamp.now(),
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});
    return;
  }

  await markDepositFailed(
    depositRef,
    "Bank debit did not settle. Please try another account or card.",
    {
      bankChargeStatus: chargeStatus || "failed",
      bankChargeId: (charge as Stripe.Charge).id,
      lastStatusCheckAt: admin.firestore.Timestamp.now(),
    }
  );
};

export const onDepositRequestCreated = functions.firestore
  .document("deposit_requests/{depositRequestId}")
  .onCreate(async (snap) => {
    const data = snap.data();
    if (!data || data.status !== "PENDING") return null;
    if (data.type !== "DEPOSIT") return null;

    const userId = data.userId as string | undefined;
    const paymentMethodId = data.paymentMethodId as string | undefined;
    const amount = Number(data.amount || 0);

    if (!userId || !paymentMethodId || !Number.isFinite(amount) || amount <= 0) {
      await markDepositFailed(snap.ref, "Invalid deposit request.");
      return null;
    }

    const userRef = db.collection("users").doc(userId);
    const methodRef = userRef.collection("payment_methods").doc(paymentMethodId);

    try {
      const [userSnap, methodSnap] = await Promise.all([userRef.get(), methodRef.get()]);
      if (!userSnap.exists) {
        throw new functions.https.HttpsError("not-found", "User profile not found.");
      }
      if (!methodSnap.exists) {
        throw new functions.https.HttpsError("not-found", "Funding payment method not found.");
      }

      const methodData = methodSnap.data() || {};
      const methodType = String(methodData.type || "").toUpperCase();
      const amountInSmallestUnit = Math.round(amount * 100);
      if (amountInSmallestUnit <= 0) {
        throw new functions.https.HttpsError("invalid-argument", "Deposit amount must be positive.");
      }

      if (methodType === "CARD") {
        const chargePaymentMethodId = (methodData.chargePaymentMethodId || methodData.stripePaymentMethodId) as string | undefined;
        if (!chargePaymentMethodId) {
          await methodRef.set({
            requiresRelinkForCharges: true,
            chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
          }, {merge: true});
          throw new functions.https.HttpsError("failed-precondition", "This card needs to be re-linked before it can be used for deposits.");
        }

        const customerId = userSnap.data()?.paymentCustomerId as string | undefined;
        if (!customerId) {
          throw new functions.https.HttpsError("failed-precondition", "Your card is not linked to a billing profile. Please re-add the card.");
        }

        const rawCurrency = String(data.currency || userSnap.data()?.wallet?.currency || "USD").trim().toLowerCase();
        const currency = /^[a-z]{3}$/.test(rawCurrency) ? rawCurrency : "usd";

        const paymentIntent = await getStripe().paymentIntents.create({
          amount: amountInSmallestUnit,
          currency,
          customer: customerId,
          payment_method: chargePaymentMethodId,
          confirm: true,
          off_session: true,
          description: "Wallet card deposit",
          metadata: {
            userId,
            depositRequestId: snap.id,
          },
        }, {
          idempotencyKey: `deposit_${snap.id}`,
        });

        await finalizeDepositWalletCredit({
          depositRef: snap.ref,
          userRef,
          amount,
          title: "Card Deposit",
          note: "Deposit from linked card",
          source: "CARD_DEPOSIT",
          metadata: {
            paymentIntentId: paymentIntent.id,
            chargePaymentMethodId,
            methodType: "CARD",
          },
        });
        return null;
      }

      if (methodType === "BANK") {
        const chargeSourceStatus = String(methodData.chargeSourceStatus || "").trim().toLowerCase();
        const achEnabled = !!asNonEmptyString(methodData.chargeSourceId) &&
          chargeSourceStatus === "verified";
        if (!achEnabled) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "This bank account must be verified for ACH deposits. Re-link and verify the account, then try again."
          );
        }

        const chargeSourceId = asNonEmptyString(methodData.chargeSourceId);
        const chargeCustomerId = asNonEmptyString(methodData.chargeCustomerId, userSnap.data()?.paymentCustomerId);
        if (!chargeSourceId || !chargeCustomerId) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "This bank account is missing ACH charge details. Re-link the account and try again."
          );
        }

        const rawCurrency = String(data.currency || userSnap.data()?.wallet?.currency || "USD").trim().toLowerCase();
        if (rawCurrency !== "usd") {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "ACH deposits are currently available for USD wallets only."
          );
        }

        await snap.ref.set({
          status: "PROCESSING_BANK",
          methodType: "BANK",
          processedAt: admin.firestore.Timestamp.now(),
        }, {merge: true});

        const bankCharge = await getStripe().charges.create({
          amount: amountInSmallestUnit,
          currency: "usd",
          customer: chargeCustomerId,
          source: chargeSourceId,
          description: "Wallet ACH bank deposit",
          metadata: {
            userId,
            depositRequestId: snap.id,
            paymentMethodId,
          },
        }, {
          idempotencyKey: `bank_deposit_${snap.id}`,
        });

        const chargeStatus = String(bankCharge.status || "").toLowerCase();
        if (chargeStatus === "succeeded") {
          await finalizeDepositWalletCredit({
            depositRef: snap.ref,
            userRef,
            amount,
            title: "Bank Deposit",
            note: "ACH debit settled from linked bank account",
            source: "BANK_DEPOSIT",
            metadata: {
              bankChargeId: bankCharge.id,
              bankChargeStatus: chargeStatus,
              methodType: "BANK",
            },
          });
          return null;
        }

        if (chargeStatus === "pending") {
          await snap.ref.set({
            status: "PENDING_SETTLEMENT",
            methodType: "BANK",
            bankChargeId: bankCharge.id,
            bankChargeStatus: chargeStatus,
            settlementMessage: "ACH debit pending settlement. This can take 1-3 business days.",
            processedAt: admin.firestore.Timestamp.now(),
          }, {merge: true});
          return null;
        }

        await markDepositFailed(
          snap.ref,
          "Bank debit did not settle. Please try another account or card.",
          {
            methodType: "BANK",
            bankChargeId: bankCharge.id,
            bankChargeStatus: chargeStatus,
          }
        );
        return null;
      }

      throw new functions.https.HttpsError(
        "failed-precondition",
        "This funding source is not supported for deposits."
      );
    } catch (error) {
      functions.logger.error("Deposit processing failed", {
        userId,
        paymentMethodId,
        amount,
        error,
      });

      let userMessage = "Deposit processing failed.";
      if (error instanceof functions.https.HttpsError) {
        userMessage = error.message || userMessage;
      } else {
        const stripeError = error as Stripe.errors.StripeError | undefined;
        if (stripeError?.code === "authentication_required") {
          userMessage = "Additional authentication is required by your bank.";
        } else if (stripeError?.code === "card_declined") {
          userMessage = "Your card was declined. Please use another card.";
        } else if (stripeError?.code === "expired_card") {
          userMessage = "Your card has expired. Please use another card.";
        } else if (stripeError?.message) {
          userMessage = stripeError.message;
        }
      }

      await markDepositFailed(snap.ref, userMessage);
    }

    return null;
  });

export const reconcilePendingBankDeposits = functions.pubsub
  .schedule("every 5 minutes")
  .onRun(async () => {
    const pendingSnap = await db.collection("deposit_requests")
      .where("status", "in", ["PENDING_SETTLEMENT", "PROCESSING_BANK"])
      .limit(100)
      .get();

    for (const doc of pendingSnap.docs) {
      const data = doc.data() || {};
      if (String(data.type || "").toUpperCase() !== "DEPOSIT") {
        continue;
      }
      if (String(data.methodType || "").toUpperCase() !== "BANK") {
        continue;
      }

      try {
        await settlePendingBankDepositRequest(doc.ref, data);
      } catch (error) {
        functions.logger.error("Failed to reconcile pending bank deposit", {
          depositRequestId: doc.id,
          error,
        });
        await doc.ref.set({
          lastStatusCheckAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
      }
    }

    return null;
  });

const settlePendingBankFundedTransferRequest = async (
  payoutRef: FirebaseFirestore.DocumentReference,
  payoutData: FirebaseFirestore.DocumentData
) => {
  const senderId = asNonEmptyString(payoutData.senderId);
  const bankChargeId = asNonEmptyString(payoutData.fundingBankChargeId);
  if (!bankChargeId) {
    await payoutRef.set({
      status: "FAILED",
      providerStatus: "FAILED",
      errorMessage: "Missing bank charge reference for settlement.",
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});

    if (senderId) {
      await finalizeSenderTransactionsForPayout(senderId, payoutRef, payoutData, "FAILED");
    }
    return;
  }

  const charge = await getStripe().charges.retrieve(bankChargeId);
  const chargeStatus = String((charge as Stripe.Charge).status || "").toLowerCase();

  if (chargeStatus === "succeeded") {
    await payoutRef.set({
      status: "PENDING_PROVIDER",
      fundingBankChargeStatus: chargeStatus,
      bankSettlementCompletedAt: admin.firestore.Timestamp.now(),
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});

    const refreshed = await payoutRef.get();
    if (refreshed.exists) {
      await processPendingMobileMoneyProviderPayout(payoutRef, refreshed.data() || {});
    }
    return;
  }

  if (chargeStatus === "pending") {
    await payoutRef.set({
      status: "PENDING_BANK_SETTLEMENT",
      fundingBankChargeStatus: chargeStatus,
      lastStatusCheckAt: admin.firestore.Timestamp.now(),
      processedAt: admin.firestore.Timestamp.now(),
    }, {merge: true});
    return;
  }

  const failureMessage = "Bank funding did not settle. Transfer was cancelled.";
  await payoutRef.set({
    status: "FAILED",
    providerStatus: "FAILED",
    errorMessage: failureMessage,
    providerMessage: failureMessage,
    fundingBankChargeStatus: chargeStatus || "failed",
    lastStatusCheckAt: admin.firestore.Timestamp.now(),
    processedAt: admin.firestore.Timestamp.now(),
  }, {merge: true});

  if (senderId) {
    await finalizeSenderTransactionsForPayout(senderId, payoutRef, payoutData, "FAILED");
  }
};

export const reconcilePendingBankFundedTransfers = functions.pubsub
  .schedule("every 5 minutes")
  .onRun(async () => {
    const pendingSnap = await db.collection("payout_requests")
      .where("status", "in", ["PENDING_BANK_SETTLEMENT", "PROCESSING_BANK_SETTLEMENT"])
      .limit(100)
      .get();

    for (const doc of pendingSnap.docs) {
      const data = doc.data() || {};
      if (String(data.type || "").toUpperCase() !== "BENEFICIARY_TRANSFER") {
        continue;
      }
      if (String(data.fundingSource || "").toUpperCase() !== "EXTERNAL_BANK") {
        continue;
      }

      try {
        await settlePendingBankFundedTransferRequest(doc.ref, data);
      } catch (error) {
        functions.logger.error("Failed to reconcile pending bank-funded transfer", {
          payoutRequestId: doc.id,
          error,
        });
        await doc.ref.set({
          lastStatusCheckAt: admin.firestore.Timestamp.now(),
        }, {merge: true});
      }
    }

    return null;
  });

// =============================================================================
//  14. MIGRATION: BACKFILL PAYOUT FIELDS
// =============================================================================

interface BackfillPayoutFieldsRequest {
  userCursor?: string;
  payoutCursor?: string;
  pageSize?: number;
  dryRun?: boolean;
}

export const backfillPayoutFields = functions.runWith({enforceAppCheck: true, timeoutSeconds: 300})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const isAdmin = context.auth.token?.admin === true || context.auth.token?.role === "admin";
    if (!isAdmin) {
      throw new functions.https.HttpsError("permission-denied", "Admin access required.");
    }

    const request = (data || {}) as BackfillPayoutFieldsRequest;
    const pageSize = Math.min(Math.max(request.pageSize || 200, 1), 500);
    const dryRun = request.dryRun === true;

    let usersProcessed = 0;
    let usersUpdated = 0;
    let methodsUpdated = 0;
    let payoutRequestsProcessed = 0;
    let payoutRequestsUpdated = 0;

    let batch = db.batch();
    let batchOps = 0;

    const commitBatch = async () => {
      if (dryRun || batchOps === 0) return;
      await batch.commit();
      batch = db.batch();
      batchOps = 0;
    };

    let userQuery = db.collection("users")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(pageSize);
    if (request.userCursor) {
      userQuery = userQuery.startAfter(request.userCursor);
    }

    const userSnap = await userQuery.get();
    for (const doc of userSnap.docs) {
      usersProcessed += 1;
      const data = doc.data();
      if (!data.payoutAccountId && data.stripeAccountId) {
        usersUpdated += 1;
        if (!dryRun) {
          batch.set(doc.ref, {payoutAccountId: data.stripeAccountId}, {merge: true});
          batchOps += 1;
        }
      }

      const methodsSnap = await doc.ref.collection("payment_methods").get();
      for (const methodDoc of methodsSnap.docs) {
        const methodData = methodDoc.data();
        if (!methodData.externalAccountId && methodData.stripeExternalAccountId) {
          methodsUpdated += 1;
          if (!dryRun) {
            batch.set(methodDoc.ref, {externalAccountId: methodData.stripeExternalAccountId}, {merge: true});
            batchOps += 1;
          }
        }

        if (batchOps >= 400) {
          await commitBatch();
        }
      }
    }

    let payoutQuery = db.collection("payout_requests")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(pageSize);
    if (request.payoutCursor) {
      payoutQuery = payoutQuery.startAfter(request.payoutCursor);
    }

    const payoutSnap = await payoutQuery.get();
    for (const doc of payoutSnap.docs) {
      payoutRequestsProcessed += 1;
      const payoutData = doc.data();
      const updates: Record<string, unknown> = {};
      if (!payoutData.payoutTransferId && payoutData.stripeTransferId) {
        updates.payoutTransferId = payoutData.stripeTransferId;
      }
      if (!payoutData.payoutId && payoutData.stripePayoutId) {
        updates.payoutId = payoutData.stripePayoutId;
      }
      if (Object.keys(updates).length > 0) {
        payoutRequestsUpdated += 1;
        if (!dryRun) {
          batch.set(doc.ref, updates, {merge: true});
          batchOps += 1;
        }
      }

      if (batchOps >= 400) {
        await commitBatch();
      }
    }

    await commitBatch();

    const nextUserCursor = userSnap.docs.length ?
      userSnap.docs[userSnap.docs.length - 1].id :
      null;
    const nextPayoutCursor = payoutSnap.docs.length ?
      payoutSnap.docs[payoutSnap.docs.length - 1].id :
      null;

    return {
      dryRun,
      usersProcessed,
      usersUpdated,
      methodsUpdated,
      payoutRequestsProcessed,
      payoutRequestsUpdated,
      nextUserCursor,
      nextPayoutCursor,
      hasMoreUsers: userSnap.docs.length === pageSize,
      hasMorePayouts: payoutSnap.docs.length === pageSize,
    };
  });

// =============================================================================
//  15. MIGRATION: FLAG CARD METHODS NEEDING CHARGE SETUP
// =============================================================================

interface BackfillChargeMethodsRequest {
  userCursor?: string;
  pageSize?: number;
  dryRun?: boolean;
}

export const backfillChargeMethods = functions.runWith({enforceAppCheck: true, timeoutSeconds: 300})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const isAdmin = context.auth.token?.admin === true || context.auth.token?.role === "admin";
    if (!isAdmin) {
      throw new functions.https.HttpsError("permission-denied", "Admin access required.");
    }

    const request = (data || {}) as BackfillChargeMethodsRequest;
    const pageSize = Math.min(Math.max(request.pageSize || 200, 1), 500);
    const dryRun = request.dryRun === true;

    let usersProcessed = 0;
    let cardMethodsFlagged = 0;

    let batch = db.batch();
    let batchOps = 0;

    const commitBatch = async () => {
      if (dryRun || batchOps === 0) return;
      await batch.commit();
      batch = db.batch();
      batchOps = 0;
    };

    let userQuery = db.collection("users")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(pageSize);
    if (request.userCursor) {
      userQuery = userQuery.startAfter(request.userCursor);
    }

    const userSnap = await userQuery.get();
    for (const doc of userSnap.docs) {
      usersProcessed += 1;
      const methodsSnap = await doc.ref.collection("payment_methods").get();
      for (const methodDoc of methodsSnap.docs) {
        const methodData = methodDoc.data();
        if (methodData.type !== "CARD") continue;

        if (!methodData.chargePaymentMethodId) {
          cardMethodsFlagged += 1;
          if (!dryRun) {
            batch.set(methodDoc.ref, {
              requiresRelinkForCharges: true,
              chargeSetupCheckedAt: admin.firestore.Timestamp.now(),
            }, {merge: true});
            batchOps += 1;
          }
        }

        if (batchOps >= 400) {
          await commitBatch();
        }
      }
    }

    await commitBatch();

    const nextUserCursor = userSnap.docs.length ?
      userSnap.docs[userSnap.docs.length - 1].id :
      null;

    return {
      dryRun,
      usersProcessed,
      cardMethodsFlagged,
      nextUserCursor,
      hasMoreUsers: userSnap.docs.length === pageSize,
    };
  });

// =============================================================================
//  11. PAYMENT METHOD FUNCTION (UPDATED)
// =============================================================================

const MOBILE_MONEY_PHONE_OTP_COLLECTION = "mobile_money_phone_otp_codes";
const MOBILE_MONEY_PHONE_OTP_CODE_LENGTH = 6;
const MOBILE_MONEY_PHONE_OTP_TTL_MS = 10 * 60 * 1000;
const MOBILE_MONEY_PHONE_OTP_RESEND_COOLDOWN_MS = 60 * 1000;
const MOBILE_MONEY_PHONE_OTP_MAX_ATTEMPTS = 5;

const getMobileMoneyPhoneOtpConfig = (): {
  provider: string;
  twilioAccountSid?: string;
  twilioAuthToken?: string;
  twilioVerifyServiceSid?: string;
  codeSecret: string;
} => {
  const provider = String(process.env.MOBILE_MONEY_PHONE_OTP_PROVIDER || "disabled")
    .trim()
    .toLowerCase();
  const twilioAccountSid = asNonEmptyString(process.env.TWILIO_ACCOUNT_SID);
  const twilioAuthToken = asNonEmptyString(process.env.TWILIO_AUTH_TOKEN);
  const twilioVerifyServiceSid = asNonEmptyString(process.env.TWILIO_VERIFY_SERVICE_SID);
  const codeSecret =
    asNonEmptyString(process.env.MOBILE_MONEY_PHONE_OTP_SECRET) ||
    asNonEmptyString(process.env.EMAIL_VERIFICATION_CODE_SECRET) ||
    asNonEmptyString(process.env.ADMIN_PAYOUT_REVERSAL_SECRET) ||
    "volunteersapp-mobile-money-phone-otp-default-secret";
  return {
    provider,
    twilioAccountSid: twilioAccountSid || undefined,
    twilioAuthToken: twilioAuthToken || undefined,
    twilioVerifyServiceSid: twilioVerifyServiceSid || undefined,
    codeSecret,
  };
};

const normalizeMobileMoneyPhoneE164 = (phone: unknown, dialCode?: unknown): string => {
  const rawPhone = String(phone || "").trim();
  const rawDialCode = String(dialCode || "").trim();
  const phoneDigits = normalizeDigits(rawPhone);
  const dialDigits = normalizeDigits(rawDialCode);
  if (!phoneDigits) return "";

  if (rawPhone.startsWith("+")) {
    return `+${phoneDigits}`;
  }
  if (dialDigits) {
    const mergedDigits = phoneDigits.startsWith(dialDigits) ?
      phoneDigits :
      `${dialDigits}${phoneDigits}`;
    return `+${mergedDigits}`;
  }
  return `+${phoneDigits}`;
};

const maskPhoneNumberForOtp = (phone: string): string => {
  const digits = normalizeDigits(phone);
  if (!digits) return "***";
  if (digits.length <= 4) return `***${digits}`;
  return `***${digits.slice(-4)}`;
};

const buildMobileMoneyOtpDocId = (uid: string, phoneDigits: string): string => {
  const config = getMobileMoneyPhoneOtpConfig();
  const seed = `${uid}:${phoneDigits}:${config.codeSecret}`;
  return createHash("sha256").update(seed).digest("hex").slice(0, 48);
};

const getMobileMoneyOtpRef = (
  uid: string,
  phoneDigits: string
): FirebaseFirestore.DocumentReference => {
  const docId = buildMobileMoneyOtpDocId(uid, phoneDigits);
  return db.collection(MOBILE_MONEY_PHONE_OTP_COLLECTION).doc(docId);
};

const sendMobileMoneyPhoneOtpCode = async (phoneE164: string): Promise<void> => {
  const config = getMobileMoneyPhoneOtpConfig();
  if (config.provider !== "twilio_verify") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Mobile money phone OTP is not configured. Set MOBILE_MONEY_PHONE_OTP_PROVIDER=twilio_verify."
    );
  }
  if (!config.twilioAccountSid || !config.twilioAuthToken || !config.twilioVerifyServiceSid) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Twilio Verify is missing TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, or TWILIO_VERIFY_SERVICE_SID."
    );
  }

  const body = new URLSearchParams();
  body.append("To", phoneE164);
  body.append("Channel", "sms");

  try {
    await axios.post(
      `https://verify.twilio.com/v2/Services/${config.twilioVerifyServiceSid}/Verifications`,
      body.toString(),
      {
        timeout: 15000,
        auth: {
          username: config.twilioAccountSid,
          password: config.twilioAuthToken,
        },
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
      }
    );
  } catch (error) {
    const providerMessage = asNonEmptyString(
      (error as {response?: {data?: {message?: unknown}}})?.response?.data?.message
    );
    throw new functions.https.HttpsError(
      "internal",
      providerMessage || "Could not send verification code. Please try again."
    );
  }
};

const verifyMobileMoneyPhoneOtpCode = async (
  phoneE164: string,
  code: string
): Promise<{approved: boolean; status: string}> => {
  const config = getMobileMoneyPhoneOtpConfig();
  if (config.provider !== "twilio_verify") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Mobile money phone OTP is not configured. Set MOBILE_MONEY_PHONE_OTP_PROVIDER=twilio_verify."
    );
  }
  if (!config.twilioAccountSid || !config.twilioAuthToken || !config.twilioVerifyServiceSid) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Twilio Verify is missing TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, or TWILIO_VERIFY_SERVICE_SID."
    );
  }

  const body = new URLSearchParams();
  body.append("To", phoneE164);
  body.append("Code", code);

  try {
    const response = await axios.post(
      `https://verify.twilio.com/v2/Services/${config.twilioVerifyServiceSid}/VerificationCheck`,
      body.toString(),
      {
        timeout: 15000,
        auth: {
          username: config.twilioAccountSid,
          password: config.twilioAuthToken,
        },
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
      }
    );
    const responseData = (response.data || {}) as {status?: unknown};
    const status = String(responseData.status || "").trim().toLowerCase();
    return {
      approved: status === "approved",
      status: status || "unknown",
    };
  } catch (error) {
    const providerMessage = asNonEmptyString(
      (error as {response?: {data?: {message?: unknown}}})?.response?.data?.message
    );
    throw new functions.https.HttpsError(
      "internal",
      providerMessage || "Could not verify the code at this time. Please try again."
    );
  }
};

interface AddPaymentMethodRequest {
  type?: string;
  label?: string;
  cardHolderName?: string;
  cardNumber?: string;
  expiryDate?: string;
  brand?: string;
  last4?: string;
  bankName?: string;
  accountHolderName?: string;
  accountNumber?: string;
  routingNumber?: string;
  phoneNumber?: string;
  network?: string;
  registeredName?: string;
  country?: string;
  dialCode?: string;
  currency?: string;
  isDefault?: boolean;
  [key: string]: unknown;
}

const normalizeMethodType = (value: unknown): string =>
  String(value || "").trim().toUpperCase();

const normalizeText = (value: unknown): string =>
  String(value || "").trim().toLowerCase();

const normalizeDigits = (value: unknown): string =>
  String(value || "").replace(/\D/g, "");

const normalizeExpiry = (value: unknown): string => {
  const raw = String(value || "").trim();
  if (!raw) return "";
  const match = raw.match(/^(\d{1,2})\s*\/\s*(\d{2}|\d{4})$/);
  if (!match) return raw.replace(/\s+/g, "");
  const month = match[1].padStart(2, "0");
  const year = match[2].slice(-2);
  return `${month}-${year}`;
};

const toSafeKeyPart = (value: string): string => {
  const safe = value.replace(/[^a-z0-9_-]/g, "");
  return safe || "na";
};

const buildPaymentMethodDedupeKey = (data: AddPaymentMethodRequest): string | null => {
  const type = normalizeMethodType(data.type);

  if (type === "CARD") {
    const last4 = normalizeDigits(data.last4 || data.cardNumber).slice(-4);
    const expiry = normalizeExpiry(data.expiryDate);
    if (!last4 || !expiry) return null;
    return `card_${toSafeKeyPart(last4)}_${toSafeKeyPart(expiry)}`;
  }

  if (type === "BANK") {
    const last4 = normalizeDigits(data.last4 || data.accountNumber).slice(-4);
    const routing = normalizeDigits(data.routingNumber) || "norouting";
    const country = normalizeText(data.country) || "na";
    if (!last4) return null;
    return `bank_${toSafeKeyPart(country)}_${toSafeKeyPart(routing)}_${toSafeKeyPart(last4)}`;
  }

  if (type === "MOBILE_MONEY") {
    const phone = normalizeDigits(data.phoneNumber);
    const network = normalizeText(data.network) || "na";
    if (!phone) return null;
    return `mobile_${toSafeKeyPart(network)}_${toSafeKeyPart(phone)}`;
  }

  return null;
};

const duplicateMessageForType = (type: string): string => {
  switch (type) {
  case "CARD":
    return "This card is already saved.";
  case "BANK":
    return "This bank account is already saved.";
  case "MOBILE_MONEY":
    return "This mobile money number is already saved.";
  default:
    return "This payment method is already saved.";
  }
};

const savedMessageForType = (type: string): string => {
  switch (type) {
  case "CARD":
    return "Card saved successfully.";
  case "BANK":
    return "Bank account saved successfully.";
  case "MOBILE_MONEY":
    return "Mobile money number saved successfully.";
  default:
    return "Payment method saved successfully.";
  }
};

const missingIdentityMessageForType = (type: string): string => {
  switch (type) {
  case "CARD":
    return "Card details are incomplete. Please provide last4 and expiry date.";
  case "BANK":
    return "Bank account details are incomplete. Please provide account details.";
  case "MOBILE_MONEY":
    return "Mobile money details are incomplete. Please provide a phone number.";
  default:
    return "Payment method data is incomplete.";
  }
};

const isAlreadyExistsError = (error: unknown): boolean => {
  const code = (error as {code?: string | number})?.code;
  if (code === 6 || code === "already-exists") return true;
  const message = String((error as {message?: string})?.message || "");
  return message.includes("ALREADY_EXISTS") || message.includes("already exists");
};

const assertMobileMoneyPhoneOtpVerified = async (params: {
  uid: string;
  phoneNumber?: unknown;
  dialCode?: unknown;
}): Promise<{phoneE164: string; phoneDigits: string; verifiedAtMs: number | null}> => {
  const phoneE164 = normalizeMobileMoneyPhoneE164(params.phoneNumber, params.dialCode);
  const phoneDigits = normalizeDigits(phoneE164);
  if (phoneDigits.length < 8 || phoneDigits.length > 15) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A valid mobile money phone number is required."
    );
  }

  const otpRef = getMobileMoneyOtpRef(params.uid, phoneDigits);
  const otpSnap = await otpRef.get();
  if (!otpSnap.exists) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Verify this mobile money phone number with OTP before saving."
    );
  }

  const otpData = (otpSnap.data() || {}) as Record<string, unknown>;
  const otpStatus = String(otpData.status || "").trim().toUpperCase();
  if (otpStatus !== "VERIFIED") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Verify this mobile money phone number with OTP before saving."
    );
  }

  const verifiedAtMs = toMillisTimestamp(otpData.verifiedAt) || Number(otpData.verifiedAtMs || 0) || null;
  return {phoneE164, phoneDigits, verifiedAtMs};
};

export const addPaymentMethod = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) throw new functions.https.HttpsError("unauthenticated", "You must be logged in to add a payment method.");
    const userId = context.auth.uid;
    const requestData = (data || {}) as AddPaymentMethodRequest;
    const methodType = normalizeMethodType(requestData.type);
    if (!methodType) throw new functions.https.HttpsError("invalid-argument", "Payment method data is incomplete.");

    const normalizedData: AddPaymentMethodRequest = {
      ...requestData,
      type: methodType,
    };

    const dedupeKey = buildPaymentMethodDedupeKey(normalizedData);
    const mustHaveIdentity = methodType === "CARD" || methodType === "BANK" || methodType === "MOBILE_MONEY";
    if (mustHaveIdentity && !dedupeKey) {
      throw new functions.https.HttpsError("invalid-argument", missingIdentityMessageForType(methodType));
    }

    const userRef = db.collection("users").doc(userId);
    const methodsRef = userRef.collection("payment_methods");
    try {
      if (dedupeKey) {
        const existingSnap = await methodsRef.where("type", "==", methodType).get();
        const hasDuplicate = existingSnap.docs.some((doc) => {
          const existingData = doc.data() as AddPaymentMethodRequest;
          return buildPaymentMethodDedupeKey(existingData) === dedupeKey;
        });

        if (hasDuplicate) {
          throw new functions.https.HttpsError("already-exists", duplicateMessageForType(methodType));
        }
      }

      const payload: AddPaymentMethodRequest = {
        ...normalizedData,
        dedupeKey: dedupeKey || null,
      };
      if (methodType === "CARD") {
        payload.requiresRelinkForCharges = true;
        payload.chargeSetupCheckedAt = admin.firestore.Timestamp.now();
      }
      if (methodType === "MOBILE_MONEY") {
        const otpVerification = await assertMobileMoneyPhoneOtpVerified({
          uid: userId,
          phoneNumber: payload.phoneNumber,
          dialCode: payload.dialCode,
        });
        payload.phoneNumber = otpVerification.phoneE164;
        payload.phoneDigits = otpVerification.phoneDigits;
        payload.phoneOtpVerified = true;
        payload.phoneOtpVerifiedAt = otpVerification.verifiedAtMs ?
          admin.firestore.Timestamp.fromMillis(otpVerification.verifiedAtMs) :
          admin.firestore.Timestamp.now();
        payload.phoneOwnershipVerified = payload.phoneOwnershipVerified === true;
        payload.verificationStatus = asNonEmptyString(payload.verificationStatus) || "UNVERIFIED";
        payload.verificationMethod = asNonEmptyString(payload.verificationMethod) || "PENDING_PROVIDER_CONFIRMATION";
        payload.verificationCompletedAt = payload.phoneOwnershipVerified ? (payload.verificationCompletedAt || admin.firestore.Timestamp.now()) : null;
      }

      let paymentMethodId: string;
      if (dedupeKey) {
        const docRef = methodsRef.doc(`dedupe_${dedupeKey}`);
        try {
          await docRef.create(payload);
        } catch (error) {
          if (isAlreadyExistsError(error)) {
            throw new functions.https.HttpsError("already-exists", duplicateMessageForType(methodType));
          }
          throw error;
        }
        paymentMethodId = docRef.id;
      } else {
        const docRef = await methodsRef.add(payload);
        paymentMethodId = docRef.id;
      }

      const message = savedMessageForType(methodType);
      functions.logger.log(`Successfully added payment method of type ${methodType} for user ${userId}.`);
      return {success: true, message, paymentMethodId};
    } catch (error) {
      if (error instanceof functions.https.HttpsError) {
        throw error;
      }
      functions.logger.error(`Failed to add payment method for user ${userId}:`, error);
      throw new functions.https.HttpsError("internal", "Could not save the payment method.");
    }
  });

// =============================================================================
//  16. ADMIN TOOLING: REVERSE MOBILE MONEY PAYOUT REQUESTS TO WALLET
// =============================================================================

interface ReversePayoutRequestsPayload {
  payoutRequestIds: string[];
  allowCompleted?: boolean;
  reason?: string;
}

interface ReversePayoutExecutionResult {
  payoutRequestId: string;
  outcome: "REFUNDED" | "SKIPPED" | "ERROR";
  message: string;
  refundAmount?: number;
}

interface AdminListPayoutRequestsPayload {
  limit?: number;
  statuses?: string[];
  mobileMoneyOnly?: boolean;
}

interface AdminAddAssociatePayload {
  associateEmail?: string;
  associateUid?: string;
  note?: string;
}

interface SupportListUsersPayload {
  query?: string;
  limit?: number;
  cursor?: string;
}

interface SupportGetUserAccountPayload {
  userId?: string;
  verificationEmail?: string;
  verificationPhone?: string;
}

interface AdminMigrateWalletsToUsdPayload {
  dryRun?: boolean;
  limit?: number;
  startAfterUserId?: string;
  userIds?: string[];
  runId?: string;
}

interface WalletUsdMigrationResultItem {
  userId: string;
  outcome: "MIGRATED" | "PREVIEW" | "SKIPPED" | "ERROR";
  reason: string;
  previousCurrency?: string;
  previousBalance?: number;
  newUsdBalance?: number;
  rate?: number;
  rateProvider?: string;
  rateFetchedAtMs?: number;
  auditId?: string;
  migrationRunId?: string;
}

const toMillisTimestamp = (value: unknown): number | null => {
  if (value instanceof admin.firestore.Timestamp) {
    return value.toMillis();
  }
  if (value instanceof Date) {
    return value.getTime();
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  return null;
};

const normalizeStatusFilters = (value: unknown): string[] => {
  if (!Array.isArray(value)) return [];
  return [...new Set(
    value
      .map((item) => String(item || "").trim().toUpperCase())
      .filter((item) => item.length > 0)
  )];
};

const isAdminRoleValue = (value: unknown): boolean => {
  const role = String(value || "").trim().toLowerCase();
  return role === "admin" || role === "owner";
};

const isAssociateRoleValue = (value: unknown): boolean => {
  const role = String(value || "").trim().toLowerCase();
  return role === "associate" || role === "support" || role === "support_associate";
};

const normalizeEmailLower = (value: unknown): string =>
  String(value || "").trim().toLowerCase();

const normalizePhoneDigits = (value: unknown): string =>
  String(value || "").replace(/\D/g, "");

const readUserPhone = (userData: Record<string, unknown>): string =>
  String(userData.phoneNumber || userData.phone || "").trim();

const EMAIL_VERIFICATION_COLLECTION = "email_verification_codes";
const EMAIL_VERIFICATION_CODE_LENGTH = 6;
const EMAIL_VERIFICATION_TTL_MS = 10 * 60 * 1000;
const EMAIL_VERIFICATION_RESEND_COOLDOWN_MS = 2 * 60 * 1000;
const EMAIL_VERIFICATION_MAX_ATTEMPTS = 5;

const getEmailVerificationConfig = (): {
  provider: string;
  resendApiKey?: string;
  fromEmail?: string;
  codeSecret: string;
} => {
  const provider = String(process.env.EMAIL_VERIFICATION_PROVIDER || "disabled")
    .trim()
    .toLowerCase();
  const resendApiKey = asNonEmptyString(process.env.RESEND_API_KEY);
  const fromEmail = asNonEmptyString(process.env.EMAIL_FROM_ADDRESS, process.env.RESEND_FROM_EMAIL);
  const codeSecret =
    asNonEmptyString(process.env.EMAIL_VERIFICATION_CODE_SECRET) ||
    asNonEmptyString(process.env.ADMIN_PAYOUT_REVERSAL_SECRET) ||
    "volunteersapp-email-verification-default-secret";
  return {
    provider,
    resendApiKey: resendApiKey || undefined,
    fromEmail: fromEmail || undefined,
    codeSecret,
  };
};

const maskEmailAddress = (email: string): string => {
  const normalized = String(email || "").trim().toLowerCase();
  const atIndex = normalized.indexOf("@");
  if (atIndex <= 0) return "***";
  const local = normalized.slice(0, atIndex);
  const domain = normalized.slice(atIndex + 1);
  const maskedLocal = local.length <= 2 ?
    `${local.charAt(0)}*` :
    `${local.slice(0, 2)}${"*".repeat(Math.max(local.length - 2, 2))}`;
  return `${maskedLocal}@${domain}`;
};

const generateSixDigitEmailVerificationCode = (): string =>
  String(randomInt(0, 10 ** EMAIL_VERIFICATION_CODE_LENGTH)).padStart(EMAIL_VERIFICATION_CODE_LENGTH, "0");

const hashEmailVerificationCode = (params: {
  uid: string;
  email: string;
  code: string;
}): string => {
  const config = getEmailVerificationConfig();
  const payload = `${params.uid}:${params.email.trim().toLowerCase()}:${params.code}:${config.codeSecret}`;
  return createHash("sha256").update(payload).digest("hex");
};

const sendEmailVerificationCodeMessage = async (params: {
  toEmail: string;
  code: string;
  expiresInMinutes: number;
}): Promise<void> => {
  const config = getEmailVerificationConfig();
  const toEmail = params.toEmail.trim().toLowerCase();
  if (!toEmail) {
    throw new functions.https.HttpsError("failed-precondition", "User email is missing.");
  }

  if (config.provider === "resend") {
    if (!config.resendApiKey || !config.fromEmail) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Resend email provider is enabled but RESEND_API_KEY or EMAIL_FROM_ADDRESS is missing."
      );
    }

    const subject = "Your VolunteersApp verification code";
    const text = `Your verification code is ${params.code}. It expires in ${params.expiresInMinutes} minutes.`;
    const html = `<p>Your verification code is <strong style="font-size:20px;letter-spacing:2px;">${params.code}</strong>.</p>
<p>This code expires in ${params.expiresInMinutes} minutes.</p>`;

    await axios.post(
      "https://api.resend.com/emails",
      {
        from: config.fromEmail,
        to: [toEmail],
        subject,
        text,
        html,
      },
      {
        timeout: 15000,
        headers: {
          "Authorization": `Bearer ${config.resendApiKey}`,
          "Content-Type": "application/json",
        },
      }
    );
    return;
  }

  throw new functions.https.HttpsError(
    "failed-precondition",
    "EMAIL_VERIFICATION_PROVIDER is not configured for 6-digit email codes. Set EMAIL_VERIFICATION_PROVIDER=resend."
  );
};

const requestEmailVerificationCodeCore = async (uid: string): Promise<{
  success: boolean;
  alreadyVerified: boolean;
  cooldownSeconds: number;
  expiresInSeconds: number;
  maskedEmail: string;
}> => {
  const authUser = await admin.auth().getUser(uid);
  const email = asNonEmptyString(authUser.email);
  if (!email) {
    throw new functions.https.HttpsError("failed-precondition", "Your account email is missing.");
  }

  if (authUser.emailVerified) {
    const now = admin.firestore.Timestamp.now();
    await db.collection("users").doc(uid).set({
      emailVerified: true,
      emailVerifiedAt: now,
      updatedAt: now,
    }, {merge: true});
    return {
      success: true,
      alreadyVerified: true,
      cooldownSeconds: 0,
      expiresInSeconds: Math.trunc(EMAIL_VERIFICATION_TTL_MS / 1000),
      maskedEmail: maskEmailAddress(email),
    };
  }

  const codeRef = db.collection(EMAIL_VERIFICATION_COLLECTION).doc(uid);
  const nowMs = Date.now();
  const codeSnap = await codeRef.get();
  if (codeSnap.exists) {
    const existing = (codeSnap.data() || {}) as Record<string, unknown>;
    const sentAtMs = toMillisTimestamp(existing.sentAt) || Number(existing.sentAtMs || 0);
    if (sentAtMs > 0) {
      const elapsedMs = nowMs - sentAtMs;
      const remainingMs = EMAIL_VERIFICATION_RESEND_COOLDOWN_MS - elapsedMs;
      if (remainingMs > 0) {
        const cooldownSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
        throw new functions.https.HttpsError(
          "failed-precondition",
          `Please wait ${cooldownSeconds}s before requesting another code.`,
          {cooldownSeconds}
        );
      }
    }
  }

  const code = generateSixDigitEmailVerificationCode();
  const codeHash = hashEmailVerificationCode({uid, email, code});
  const expiresAtMs = nowMs + EMAIL_VERIFICATION_TTL_MS;
  const expiresInMinutes = Math.max(1, Math.ceil(EMAIL_VERIFICATION_TTL_MS / 60000));
  await sendEmailVerificationCodeMessage({
    toEmail: email,
    code,
    expiresInMinutes,
  });

  const now = admin.firestore.Timestamp.now();
  const expiresAt = admin.firestore.Timestamp.fromMillis(expiresAtMs);
  await codeRef.set({
    uid,
    email: email.trim().toLowerCase(),
    codeHash,
    status: "SENT",
    attempts: 0,
    maxAttempts: EMAIL_VERIFICATION_MAX_ATTEMPTS,
    sentAt: now,
    sentAtMs: nowMs,
    expiresAt,
    expiresAtMs,
    updatedAt: now,
  }, {merge: true});

  await db.collection("users").doc(uid).set({
    verificationEmailLastSentAtMs: nowMs,
    updatedAt: now,
  }, {merge: true});

  return {
    success: true,
    alreadyVerified: false,
    cooldownSeconds: Math.trunc(EMAIL_VERIFICATION_RESEND_COOLDOWN_MS / 1000),
    expiresInSeconds: Math.trunc(EMAIL_VERIFICATION_TTL_MS / 1000),
    maskedEmail: maskEmailAddress(email),
  };
};

const assertSupportCallableAccess = async (
  context: functions.https.CallableContext
): Promise<{uid: string; isAdmin: boolean; role: string}> => {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
  }

  const uid = context.auth.uid;
  const ownerUserId = getAppConfig().ownerUserId;
  const claimRole = context.auth.token?.role;
  const hasAdminClaim = context.auth.token?.admin === true || isAdminRoleValue(claimRole);
  if (hasAdminClaim || (ownerUserId && uid === ownerUserId)) {
    return {uid, isAdmin: true, role: "admin"};
  }
  if (isAssociateRoleValue(claimRole)) {
    return {uid, isAdmin: false, role: String(claimRole || "associate")};
  }

  const userSnap = await db.collection("users").doc(uid).get();
  const userRole = userSnap.data()?.role;
  if (isAdminRoleValue(userRole)) {
    return {uid, isAdmin: true, role: String(userRole || "admin")};
  }
  if (isAssociateRoleValue(userRole)) {
    return {uid, isAdmin: false, role: String(userRole || "associate")};
  }

  throw new functions.https.HttpsError("permission-denied", "Support access required.");
};

const buildSupportUserSummary = (doc: FirebaseFirestore.QueryDocumentSnapshot): Record<string, unknown> => {
  const userData = (doc.data() || {}) as Record<string, unknown>;
  const wallet = (userData.wallet || {}) as Record<string, unknown>;
  return {
    userId: doc.id,
    username: asNonEmptyString(userData.username, userData.name) || "Unknown",
    email: asNonEmptyString(userData.email) || "",
    phone: readUserPhone(userData),
    role: asNonEmptyString(userData.role) || "volunteer",
    walletBalance: Number(wallet.balance || 0),
    walletCurrency: asNonEmptyString(wallet.currency) || "USD",
    profilePictureUrl: asNonEmptyString(userData.profilePictureUrl, userData.profileImageUrl) || null,
    createdAtMs: toMillisTimestamp(userData.createdAt),
    updatedAtMs: toMillisTimestamp(userData.updatedAt),
  };
};

export const bootstrapOwnerSelf = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (_data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const uid = context.auth.uid;
    const callerEmail = normalizeEmailLower(context.auth.token?.email);
    const ownerBootstrapEmail = normalizeEmailLower(getAppConfig().ownerBootstrapEmail);
    if (!ownerBootstrapEmail) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Owner bootstrap is not configured. Set CONFIG_OWNER_BOOTSTRAP_EMAIL."
      );
    }
    if (!callerEmail || callerEmail !== ownerBootstrapEmail) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "This account is not authorized for owner bootstrap."
      );
    }
    if (context.auth.token?.email_verified !== true) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verify your email before requesting owner access."
      );
    }

    const now = admin.firestore.Timestamp.now();
    const userRef = db.collection("users").doc(uid);
    await userRef.set({
      uid,
      email: callerEmail,
      role: "owner",
      userType: "owner",
      isStaff: true,
      supportAccess: true,
      staffAccessScope: "SUPPORT_CONSOLE",
      staffOnboardingStatus: "ACTIVE",
      profileStatus: "active",
      ownerBootstrappedAt: now,
      updatedAt: now,
    }, {merge: true});

    try {
      const authUser = await admin.auth().getUser(uid);
      const existingClaims = authUser.customClaims || {};
      await admin.auth().setCustomUserClaims(uid, {
        ...existingClaims,
        admin: true,
        owner: true,
        role: "owner",
      });
    } catch (error) {
      functions.logger.warn("Failed to set owner custom claims during bootstrap.", {
        uid,
        error,
      });
    }

    return {
      success: true,
      uid,
      role: "owner",
      email: callerEmail,
      ownerUserId: uid,
      message: "Owner access bootstrapped.",
    };
  });

export const ownerGrantAdminByEmail = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }

    const callerUid = context.auth.uid;
    const ownerUserId = getAppConfig().ownerUserId;
    const callerTokenRole = String(context.auth.token?.role || "").trim().toLowerCase();
    let isOwnerCaller =
      context.auth.token?.owner === true ||
      callerTokenRole === "owner" ||
      (ownerUserId ? callerUid === ownerUserId : false);

    if (!isOwnerCaller) {
      const callerSnap = await db.collection("users").doc(callerUid).get();
      const callerData = (callerSnap.data() || {}) as Record<string, unknown>;
      const callerRole = asNonEmptyString(
        callerData.role,
        callerData.userRole,
        callerData.userType
      )?.toLowerCase();
      isOwnerCaller = callerRole === "owner" || (ownerUserId ? callerUid === ownerUserId : false);
    }

    if (!isOwnerCaller) {
      throw new functions.https.HttpsError("permission-denied", "Owner access required.");
    }

    const payload = (data || {}) as {email?: string; targetEmail?: string};
    const targetEmail = normalizeEmailLower(payload.email || payload.targetEmail);
    if (!targetEmail) {
      throw new functions.https.HttpsError("invalid-argument", "Provide a valid email.");
    }

    let targetUid = "";
    const byEmailSnap = await db.collection("users")
      .where("email", "==", targetEmail)
      .limit(1)
      .get();
    if (!byEmailSnap.empty) {
      targetUid = byEmailSnap.docs[0].id;
    } else {
      try {
        const authUser = await admin.auth().getUserByEmail(targetEmail);
        targetUid = authUser.uid;
      } catch {
        // no-op: handled by not-found below
      }
    }

    if (!targetUid) {
      throw new functions.https.HttpsError(
        "not-found",
        "User account not found for the provided email."
      );
    }

    const targetRef = db.collection("users").doc(targetUid);
    const targetSnap = await targetRef.get();
    const targetData = (targetSnap.data() || {}) as Record<string, unknown>;
    const targetRole = String(targetData.role || "").trim().toLowerCase();
    if (targetRole === "owner") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Owner account role cannot be downgraded to admin."
      );
    }

    const now = admin.firestore.Timestamp.now();
    await targetRef.set({
      email: targetEmail,
      role: "admin",
      userRole: "admin",
      userType: "admin",
      staffOnboardingStatus: "ACTIVE",
      grantedByOwnerId: callerUid,
      grantedAdminAt: now,
      updatedAt: now,
    }, {merge: true});

    try {
      const authUser = await admin.auth().getUser(targetUid);
      const existingClaims = authUser.customClaims || {};
      await admin.auth().setCustomUserClaims(targetUid, {
        ...existingClaims,
        admin: true,
        role: "admin",
      });
    } catch (error) {
      functions.logger.warn("Failed to set custom claims for owner-granted admin role.", {
        targetUid,
        error,
      });
    }

    const updatedSnap = await targetRef.get();
    const updatedData = (updatedSnap.data() || {}) as Record<string, unknown>;
    return {
      success: true,
      message: "Admin access granted successfully.",
      admin: {
        userId: targetUid,
        email: asNonEmptyString(updatedData.email) || targetEmail,
        role: asNonEmptyString(updatedData.role) || "admin",
        grantedByOwnerId: asNonEmptyString(updatedData.grantedByOwnerId) || callerUid,
        grantedAdminAtMs: toMillisTimestamp(updatedData.grantedAdminAt),
      },
    };
  });

export const adminAddSupportAssociate = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const adminUid = await assertAdminCallableAccess(context);
    const payload = (data || {}) as AdminAddAssociatePayload;
    const associateUid = asNonEmptyString(payload.associateUid);
    const associateEmail = normalizeEmailLower(payload.associateEmail);
    if (!associateUid && !associateEmail) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Provide associateEmail or associateUid."
      );
    }

    let targetUid = associateUid || "";
    if (!targetUid && associateEmail) {
      const byEmailSnap = await db.collection("users")
        .where("email", "==", associateEmail)
        .limit(1)
        .get();
      if (!byEmailSnap.empty) {
        targetUid = byEmailSnap.docs[0].id;
      } else {
        try {
          const authUser = await admin.auth().getUserByEmail(associateEmail);
          targetUid = authUser.uid;
        } catch {
          // no-op: handled by not-found below
        }
      }
    }

    if (!targetUid) {
      throw new functions.https.HttpsError("not-found", "Associate account not found for the provided email.");
    }

    const targetRef = db.collection("users").doc(targetUid);
    const targetSnap = await targetRef.get();
    const targetData = (targetSnap.data() || {}) as Record<string, unknown>;
    const existingRole = String(targetData.role || "").trim().toLowerCase();
    if (isAdminRoleValue(existingRole)) {
      throw new functions.https.HttpsError("failed-precondition", "Target account already has admin-level access.");
    }

    const now = admin.firestore.Timestamp.now();
    await targetRef.set({
      email: associateEmail || targetData.email || null,
      role: "associate",
      supportAccess: true,
      supportNote: asNonEmptyString(payload.note) || null,
      supportGrantedAt: now,
      supportGrantedBy: adminUid,
      updatedAt: now,
    }, {merge: true});

    try {
      const authUser = await admin.auth().getUser(targetUid);
      const existingClaims = authUser.customClaims || {};
      await admin.auth().setCustomUserClaims(targetUid, {
        ...existingClaims,
        role: "associate",
        associate: true,
      });
    } catch (error) {
      functions.logger.warn("Failed to set custom claims for associate role.", {
        targetUid,
        error,
      });
    }

    const updatedSnap = await targetRef.get();
    const updatedData = (updatedSnap.data() || {}) as Record<string, unknown>;
    return {
      success: true,
      message: "Associate access granted successfully.",
      associate: {
        userId: targetUid,
        username: asNonEmptyString(updatedData.username, updatedData.name) || "Unknown",
        email: asNonEmptyString(updatedData.email) || associateEmail || "",
        role: asNonEmptyString(updatedData.role) || "associate",
        supportGrantedAtMs: toMillisTimestamp(updatedData.supportGrantedAt),
      },
    };
  });

export const supportListUsers = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const access = await assertSupportCallableAccess(context);
    const payload = (data || {}) as SupportListUsersPayload;
    const parsedLimit = Number(payload.limit);
    const limit = Math.min(Math.max(Number.isFinite(parsedLimit) ? Math.trunc(parsedLimit) : 80, 1), 200);
    const cursor = asNonEmptyString(payload.cursor);
    const query = String(payload.query || "").trim();
    const queryLower = query.toLowerCase();
    const queryDigits = normalizePhoneDigits(query);
    const fetchLimit = query ? Math.min(limit * 5, 500) : limit;

    let usersQuery = db.collection("users")
      .orderBy(admin.firestore.FieldPath.documentId())
      .limit(fetchLimit);
    if (cursor) {
      usersQuery = usersQuery.startAfter(cursor);
    }

    const usersSnap = await usersQuery.get();
    let items = usersSnap.docs.map((doc) => buildSupportUserSummary(doc));
    if (query) {
      items = items.filter((item) => {
        const email = normalizeEmailLower(item.email);
        const username = String(item.username || "").toLowerCase();
        const phoneDigits = normalizePhoneDigits(item.phone);
        return email.includes(queryLower) ||
          username.includes(queryLower) ||
          (queryDigits.length > 0 && phoneDigits.includes(queryDigits));
      }).slice(0, limit);
    }

    const nextCursor = usersSnap.docs.length > 0 ?
      usersSnap.docs[usersSnap.docs.length - 1].id :
      null;

    return {
      requestedBy: access.uid,
      isAdmin: access.isAdmin,
      role: access.role,
      items,
      nextCursor,
      hasMore: usersSnap.docs.length === fetchLimit,
    };
  });

export const supportGetUserAccountDetails = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const access = await assertSupportCallableAccess(context);
    const payload = (data || {}) as SupportGetUserAccountPayload;
    const userId = asNonEmptyString(payload.userId);
    if (!userId) {
      throw new functions.https.HttpsError("invalid-argument", "Missing userId.");
    }

    const targetRef = db.collection("users").doc(userId);
    const targetSnap = await targetRef.get();
    if (!targetSnap.exists) {
      throw new functions.https.HttpsError("not-found", "User account not found.");
    }
    const userData = (targetSnap.data() || {}) as Record<string, unknown>;
    const storedEmail = normalizeEmailLower(userData.email);
    const storedPhoneDigits = normalizePhoneDigits(readUserPhone(userData));

    if (!access.isAdmin) {
      const verificationEmail = normalizeEmailLower(payload.verificationEmail);
      const verificationPhoneDigits = normalizePhoneDigits(payload.verificationPhone);
      if (!verificationEmail || !verificationPhoneDigits) {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "Associates must verify both customer email and phone before viewing account details."
        );
      }
      const emailMatches = storedEmail.length > 0 && verificationEmail === storedEmail;
      const phoneMatches = storedPhoneDigits.length > 0 && verificationPhoneDigits === storedPhoneDigits;
      if (!emailMatches || !phoneMatches) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Verification failed. Customer email/phone do not match the selected account."
        );
      }
    }

    const wallet = (userData.wallet || {}) as Record<string, unknown>;
    const txSnap = await targetRef.collection("transactions")
      .orderBy("timestamp", "desc")
      .limit(25)
      .get();
    const transactions = txSnap.docs.map((doc) => {
      const tx = (doc.data() || {}) as Record<string, unknown>;
      return {
        transactionId: doc.id,
        title: asNonEmptyString(tx.title) || "Transaction",
        amount: Number(tx.amount || 0),
        type: asNonEmptyString(tx.type) || "UNKNOWN",
        status: asNonEmptyString(tx.status) || "UNKNOWN",
        source: asNonEmptyString(tx.source) || null,
        note: asNonEmptyString(tx.note) || null,
        timestampMs: toMillisTimestamp(tx.timestamp),
      };
    });

    const complaints: Array<Record<string, unknown>> = [];
    if (storedEmail.length > 0) {
      const [reportsSnake, reportsCamel] = await Promise.all([
        db.collection("user_reports").where("reportedUserEmail", "==", storedEmail).limit(25).get(),
        db.collection("userReports").where("reportedUserEmail", "==", storedEmail).limit(25).get(),
      ]);
      const pushReports = (snap: FirebaseFirestore.QuerySnapshot) => {
        for (const doc of snap.docs) {
          const report = (doc.data() || {}) as Record<string, unknown>;
          complaints.push({
            reportId: doc.id,
            reportedUserName: asNonEmptyString(report.reportedUserName) || null,
            reportedUserEmail: asNonEmptyString(report.reportedUserEmail) || null,
            reasonForReport: asNonEmptyString(report.reasonForReport) || asNonEmptyString(report.reason) || "No reason provided.",
            eventName: asNonEmptyString(report.eventName) || null,
            reportingUserId: asNonEmptyString(report.reportingUserId) || null,
            reportingUserDisplayName: asNonEmptyString(report.reportingUserDisplayName) || null,
            timestampMs: toMillisTimestamp(report.timestamp),
          });
        }
      };
      pushReports(reportsSnake);
      pushReports(reportsCamel);
    }

    complaints.sort((a, b) => Number(b.timestampMs || 0) - Number(a.timestampMs || 0));

    return {
      requestedBy: access.uid,
      isAdmin: access.isAdmin,
      role: access.role,
      requiresVerification: !access.isAdmin,
      verified: true,
      user: {
        userId: targetSnap.id,
        username: asNonEmptyString(userData.username, userData.name) || "Unknown",
        email: asNonEmptyString(userData.email) || "",
        phone: readUserPhone(userData),
        role: asNonEmptyString(userData.role) || "volunteer",
        profilePictureUrl: asNonEmptyString(userData.profilePictureUrl, userData.profileImageUrl) || null,
        walletBalance: Number(wallet.balance || 0),
        walletCurrency: asNonEmptyString(wallet.currency) || "USD",
        payoutAccountId: asNonEmptyString(userData.payoutAccountId, userData.stripeAccountId) || null,
        chargesEnabled: userData.chargesEnabled === true,
        payoutsEnabled: userData.payoutsEnabled === true,
        detailsSubmitted: userData.detailsSubmitted === true,
      },
      transactions,
      complaints: complaints.slice(0, 25),
    };
  });

export const requestEmailVerificationCode = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (_data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    return requestEmailVerificationCodeCore(context.auth.uid);
  });

export const verifyEmailVerificationCode = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const payload = (data || {}) as {code?: unknown; email?: unknown};
    const rawCode = String(payload.code || "").trim();
    if (!/^\d{6}$/.test(rawCode)) {
      throw new functions.https.HttpsError("invalid-argument", "Code must be exactly 6 digits.");
    }

    let uid = context.auth?.uid || "";
    let authUser: admin.auth.UserRecord;
    if (uid) {
      authUser = await admin.auth().getUser(uid);
    } else {
      const providedEmail = normalizeEmailLower(payload.email);
      if (!providedEmail) {
        throw new functions.https.HttpsError(
          "invalid-argument",
          "Email is required when verification session is not active."
        );
      }
      try {
        authUser = await admin.auth().getUserByEmail(providedEmail);
      } catch {
        throw new functions.https.HttpsError("not-found", "No account found for the provided email.");
      }
      uid = authUser.uid;
    }

    const email = asNonEmptyString(authUser.email);
    if (!email) {
      throw new functions.https.HttpsError("failed-precondition", "Your account email is missing.");
    }

    if (authUser.emailVerified) {
      const now = admin.firestore.Timestamp.now();
      await db.collection("users").doc(uid).set({
        emailVerified: true,
        emailVerifiedAt: now,
        updatedAt: now,
      }, {merge: true});
      return {
        success: true,
        alreadyVerified: true,
      };
    }

    const codeRef = db.collection(EMAIL_VERIFICATION_COLLECTION).doc(uid);
    const codeSnap = await codeRef.get();
    if (!codeSnap.exists) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verification code not found. Request a new code."
      );
    }

    const codeData = (codeSnap.data() || {}) as Record<string, unknown>;
    const status = String(codeData.status || "SENT").trim().toUpperCase();
    if (status === "VERIFIED") {
      await admin.auth().updateUser(uid, {emailVerified: true});
      const now = admin.firestore.Timestamp.now();
      await db.collection("users").doc(uid).set({
        emailVerified: true,
        emailVerifiedAt: now,
        updatedAt: now,
      }, {merge: true});
      return {
        success: true,
        alreadyVerified: true,
      };
    }

    if (status === "LOCKED") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Too many invalid attempts. Request a new verification code."
      );
    }

    const nowMs = Date.now();
    const expiresAtMs = toMillisTimestamp(codeData.expiresAt) || Number(codeData.expiresAtMs || 0);
    if (!Number.isFinite(expiresAtMs) || expiresAtMs <= nowMs) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verification code expired. Request a new code."
      );
    }

    const attempts = Math.max(0, Math.trunc(Number(codeData.attempts || 0)));
    const maxAttempts = Math.max(1, Math.trunc(Number(codeData.maxAttempts || EMAIL_VERIFICATION_MAX_ATTEMPTS)));
    if (attempts >= maxAttempts) {
      await codeRef.set({
        status: "LOCKED",
        updatedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});
      throw new functions.https.HttpsError(
        "permission-denied",
        "Too many invalid attempts. Request a new verification code."
      );
    }

    const storedHash = asNonEmptyString(codeData.codeHash);
    if (!storedHash) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verification code state is invalid. Request a new code."
      );
    }

    const incomingHash = hashEmailVerificationCode({
      uid,
      email,
      code: rawCode,
    });

    if (incomingHash !== storedHash) {
      const nextAttempts = attempts + 1;
      const remainingAttempts = Math.max(0, maxAttempts - nextAttempts);
      const shouldLock = nextAttempts >= maxAttempts;
      await codeRef.set({
        attempts: nextAttempts,
        status: shouldLock ? "LOCKED" : status,
        lastAttemptAt: admin.firestore.Timestamp.now(),
        updatedAt: admin.firestore.Timestamp.now(),
      }, {merge: true});

      if (shouldLock) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Too many invalid attempts. Request a new verification code."
        );
      }
      throw new functions.https.HttpsError(
        "permission-denied",
        `Invalid code. ${remainingAttempts} attempt(s) remaining.`
      );
    }

    const now = admin.firestore.Timestamp.now();
    await admin.auth().updateUser(uid, {emailVerified: true});
    await codeRef.set({
      status: "VERIFIED",
      verifiedAt: now,
      verifiedAtMs: nowMs,
      attempts: attempts + 1,
      updatedAt: now,
    }, {merge: true});
    await db.collection("users").doc(uid).set({
      emailVerified: true,
      emailVerifiedAt: now,
      updatedAt: now,
    }, {merge: true});

    return {
      success: true,
      alreadyVerified: false,
    };
  });

export const requestMobileMoneyPhoneOtp = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const uid = context.auth.uid;
    const payload = (data || {}) as {phoneNumber?: unknown; dialCode?: unknown};
    const phoneE164 = normalizeMobileMoneyPhoneE164(payload.phoneNumber, payload.dialCode);
    const phoneDigits = normalizeDigits(phoneE164);
    if (phoneDigits.length < 8 || phoneDigits.length > 15) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Provide a valid phone number in international format."
      );
    }

    const otpRef = getMobileMoneyOtpRef(uid, phoneDigits);
    const nowMs = Date.now();
    const now = admin.firestore.Timestamp.now();
    const existingSnap = await otpRef.get();
    if (existingSnap.exists) {
      const existing = (existingSnap.data() || {}) as Record<string, unknown>;
      const status = String(existing.status || "").trim().toUpperCase();
      if (status === "VERIFIED") {
        return {
          success: true,
          alreadyVerified: true,
          cooldownSeconds: 0,
          expiresInSeconds: Math.trunc(MOBILE_MONEY_PHONE_OTP_TTL_MS / 1000),
          maskedPhone: maskPhoneNumberForOtp(phoneE164),
        };
      }

      const sentAtMs = toMillisTimestamp(existing.sentAt) || Number(existing.sentAtMs || 0);
      if (sentAtMs > 0) {
        const elapsedMs = nowMs - sentAtMs;
        const remainingMs = MOBILE_MONEY_PHONE_OTP_RESEND_COOLDOWN_MS - elapsedMs;
        if (remainingMs > 0) {
          const cooldownSeconds = Math.max(1, Math.ceil(remainingMs / 1000));
          throw new functions.https.HttpsError(
            "failed-precondition",
            `Please wait ${cooldownSeconds}s before requesting another code.`,
            {cooldownSeconds}
          );
        }
      }
    }

    await sendMobileMoneyPhoneOtpCode(phoneE164);

    const expiresAtMs = nowMs + MOBILE_MONEY_PHONE_OTP_TTL_MS;
    await otpRef.set({
      uid,
      phoneNumber: phoneE164,
      phoneDigits,
      status: "SENT",
      attempts: 0,
      maxAttempts: MOBILE_MONEY_PHONE_OTP_MAX_ATTEMPTS,
      sentAt: now,
      sentAtMs: nowMs,
      expiresAt: admin.firestore.Timestamp.fromMillis(expiresAtMs),
      expiresAtMs,
      updatedAt: now,
    }, {merge: true});

    await db.collection("users").doc(uid).set({
      mobileMoneyPhoneOtpLastSentAtMs: nowMs,
      mobileMoneyPhoneOtpLastSentPhone: phoneE164,
      mobileMoneyPhoneOtpLastSentPhoneDigits: phoneDigits,
      updatedAt: now,
    }, {merge: true});

    return {
      success: true,
      alreadyVerified: false,
      cooldownSeconds: Math.trunc(MOBILE_MONEY_PHONE_OTP_RESEND_COOLDOWN_MS / 1000),
      expiresInSeconds: Math.trunc(MOBILE_MONEY_PHONE_OTP_TTL_MS / 1000),
      maskedPhone: maskPhoneNumberForOtp(phoneE164),
    };
  });

export const verifyMobileMoneyPhoneOtp = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const payload = (data || {}) as {phoneNumber?: unknown; dialCode?: unknown; code?: unknown};
    const rawCode = String(payload.code || "").trim();
    if (!new RegExp(`^\\d{${MOBILE_MONEY_PHONE_OTP_CODE_LENGTH}}$`).test(rawCode)) {
      throw new functions.https.HttpsError("invalid-argument", "Code must be exactly 6 digits.");
    }

    const uid = context.auth.uid;
    const phoneE164 = normalizeMobileMoneyPhoneE164(payload.phoneNumber, payload.dialCode);
    const phoneDigits = normalizeDigits(phoneE164);
    if (phoneDigits.length < 8 || phoneDigits.length > 15) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Provide a valid phone number in international format."
      );
    }

    const otpRef = getMobileMoneyOtpRef(uid, phoneDigits);
    const otpSnap = await otpRef.get();
    if (!otpSnap.exists) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verification code not found. Request a new code."
      );
    }

    const otpData = (otpSnap.data() || {}) as Record<string, unknown>;
    const status = String(otpData.status || "SENT").trim().toUpperCase();
    if (status === "VERIFIED") {
      return {
        success: true,
        alreadyVerified: true,
      };
    }
    if (status === "LOCKED") {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Too many invalid attempts. Request a new verification code."
      );
    }

    const nowMs = Date.now();
    const now = admin.firestore.Timestamp.now();
    const expiresAtMs = toMillisTimestamp(otpData.expiresAt) || Number(otpData.expiresAtMs || 0);
    if (!Number.isFinite(expiresAtMs) || expiresAtMs <= nowMs) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Verification code expired. Request a new code."
      );
    }

    const attempts = Math.max(0, Math.trunc(Number(otpData.attempts || 0)));
    const maxAttempts = Math.max(1, Math.trunc(Number(otpData.maxAttempts || MOBILE_MONEY_PHONE_OTP_MAX_ATTEMPTS)));
    if (attempts >= maxAttempts) {
      await otpRef.set({
        status: "LOCKED",
        updatedAt: now,
      }, {merge: true});
      throw new functions.https.HttpsError(
        "permission-denied",
        "Too many invalid attempts. Request a new verification code."
      );
    }

    const verificationResult = await verifyMobileMoneyPhoneOtpCode(phoneE164, rawCode);
    if (!verificationResult.approved) {
      const nextAttempts = attempts + 1;
      const remainingAttempts = Math.max(0, maxAttempts - nextAttempts);
      const shouldLock = nextAttempts >= maxAttempts;
      await otpRef.set({
        attempts: nextAttempts,
        status: shouldLock ? "LOCKED" : "SENT",
        lastAttemptAt: now,
        lastProviderStatus: verificationResult.status,
        updatedAt: now,
      }, {merge: true});

      if (shouldLock) {
        throw new functions.https.HttpsError(
          "permission-denied",
          "Too many invalid attempts. Request a new verification code."
        );
      }
      throw new functions.https.HttpsError(
        "permission-denied",
        `Invalid code. ${remainingAttempts} attempt(s) remaining.`
      );
    }

    await otpRef.set({
      phoneNumber: phoneE164,
      phoneDigits,
      status: "VERIFIED",
      attempts: attempts + 1,
      verifiedAt: now,
      verifiedAtMs: nowMs,
      lastProviderStatus: verificationResult.status,
      updatedAt: now,
    }, {merge: true});
    await db.collection("users").doc(uid).set({
      mobileMoneyPhoneOtpVerifiedAt: now,
      mobileMoneyPhoneOtpVerifiedPhone: phoneE164,
      mobileMoneyPhoneOtpVerifiedPhoneDigits: phoneDigits,
      phoneVerified: true,
      phoneVerifiedAt: now,
      phoneVerifiedNumber: phoneE164,
      phoneVerifiedDigits: phoneDigits,
      phoneVerificationMethod: "TWILIO_VERIFY_SMS",
      updatedAt: now,
    }, {merge: true});

    return {
      success: true,
      alreadyVerified: false,
      maskedPhone: maskPhoneNumberForOtp(phoneE164),
    };
  });

const isMobileMoneyPayoutType = (value: unknown): boolean => {
  const type = String(value || "").trim().toUpperCase();
  return type === "CASH_OUT" || type === "CASH_IN" || type === "BENEFICIARY_TRANSFER";
};

const assertAdminCallableAccess = async (context: functions.https.CallableContext): Promise<string> => {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
  }

  const uid = context.auth.uid;
  const ownerUserId = getAppConfig().ownerUserId;
  const hasAdminClaim = context.auth.token?.admin === true || isAdminRoleValue(context.auth.token?.role);
  if (hasAdminClaim || (ownerUserId && uid === ownerUserId)) {
    return uid;
  }

  const userSnap = await db.collection("users").doc(uid).get();
  if (isAdminRoleValue(userSnap.data()?.role)) {
    return uid;
  }

  throw new functions.https.HttpsError("permission-denied", "Admin access required.");
};

type WalletUsdExchangeRateSnapshot = {
  fromCurrency: string;
  toCurrency: "USD";
  rate: number;
  provider: string;
  fetchedAtMs: number;
  requestUrl: string;
};

const toRecord = (value: unknown): Record<string, unknown> => {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
};

const getWalletUsdMigrationMarker = (userData: Record<string, unknown>): Record<string, unknown> | null => {
  const walletMigration = toRecord(userData.walletMigration);
  const usdOneTime = toRecord(walletMigration.usdOneTime);
  return Object.keys(usdOneTime).length > 0 ? usdOneTime : null;
};

const fetchRawExchangeRateToUsd = async (fromCurrencyRaw: string): Promise<WalletUsdExchangeRateSnapshot> => {
  const fromCurrency = String(fromCurrencyRaw || "USD").trim().toUpperCase();
  if (fromCurrency === "USD") {
    return {
      fromCurrency: "USD",
      toCurrency: "USD",
      rate: 1,
      provider: "IDENTITY",
      fetchedAtMs: Date.now(),
      requestUrl: "local://identity",
    };
  }

  const apiKey = getApiKeys().exchangeRate;
  if (!apiKey) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Exchange rate API key is not configured. Set EXCHANGERATE_API_KEY."
    );
  }

  const requestUrl = `https://v6.exchangerate-api.com/v6/${apiKey}/pair/${fromCurrency}/USD`;
  const response = await axios.get(requestUrl, {timeout: 15000});
  const responseData = response.data as {result?: string; conversion_rate?: number};
  const rate = Number(responseData.conversion_rate || 0);
  if (responseData.result !== "success" || !Number.isFinite(rate) || rate <= 0) {
    throw new Error(`Could not fetch exchange rate for ${fromCurrency} -> USD.`);
  }

  return {
    fromCurrency,
    toCurrency: "USD",
    rate,
    provider: "EXCHANGERATE_API_V6",
    fetchedAtMs: Date.now(),
    requestUrl,
  };
};

export const adminMigrateWalletCurrenciesToUsd = functions.runWith({enforceAppCheck: true, timeoutSeconds: 540})
  .https.onCall(async (data, context) => {
    const adminUid = await assertAdminCallableAccess(context);
    const payload = (data || {}) as AdminMigrateWalletsToUsdPayload;

    const dryRun = payload.dryRun !== false;
    const parsedLimit = Number(payload.limit);
    const limit = Math.min(Math.max(Number.isFinite(parsedLimit) ? Math.trunc(parsedLimit) : 100, 1), 300);
    const startAfterUserId = asNonEmptyString(payload.startAfterUserId);
    const runId = asNonEmptyString(payload.runId) || `wallet_usd_migration_${Date.now()}`;
    const explicitUserIds = [...new Set(
      (payload.userIds || [])
        .map((value) => String(value || "").trim())
        .filter((value) => value.length > 0)
    )].slice(0, limit);

    let userDocs: FirebaseFirestore.DocumentSnapshot[] = [];
    let nextCursor: string | null = null;
    if (explicitUserIds.length > 0) {
      userDocs = await Promise.all(
        explicitUserIds.map((userId) => db.collection("users").doc(userId).get())
      );
    } else {
      let usersQuery = db.collection("users")
        .orderBy(admin.firestore.FieldPath.documentId())
        .limit(limit);
      if (startAfterUserId) {
        usersQuery = usersQuery.startAfter(startAfterUserId);
      }
      const usersSnap = await usersQuery.get();
      userDocs = usersSnap.docs;
      nextCursor = usersSnap.docs.length === limit ?
        usersSnap.docs[usersSnap.docs.length - 1].id :
        null;
    }

    const currenciesToFetch = new Set<string>();
    for (const userDoc of userDocs) {
      if (!userDoc.exists) continue;
      const userData = toRecord(userDoc.data());
      const marker = getWalletUsdMigrationMarker(userData);
      if (marker) continue;

      const wallet = toRecord(userData.wallet);
      const previousCurrency = (asNonEmptyString(wallet.currency) || "USD").toUpperCase();
      const previousBalance = Number(wallet.balance || 0);
      if (previousCurrency !== "USD" && Number.isFinite(previousBalance) && previousBalance !== 0) {
        currenciesToFetch.add(previousCurrency);
      }
    }

    const rateSnapshots = new Map<string, WalletUsdExchangeRateSnapshot>();
    const rateErrors = new Map<string, string>();
    await Promise.all([...currenciesToFetch].map(async (currency) => {
      try {
        const snapshot = await fetchRawExchangeRateToUsd(currency);
        rateSnapshots.set(currency, snapshot);
      } catch (error) {
        rateErrors.set(currency, parseProviderErrorMessage(error));
      }
    }));

    const results: WalletUsdMigrationResultItem[] = [];
    let migratedCount = 0;
    let previewCount = 0;
    let skippedCount = 0;
    let errorCount = 0;

    for (const userDoc of userDocs) {
      const userId = userDoc.id;
      if (!userDoc.exists) {
        skippedCount += 1;
        results.push({
          userId,
          outcome: "SKIPPED",
          reason: "User document not found.",
          migrationRunId: runId,
        });
        continue;
      }

      const userData = toRecord(userDoc.data());
      const marker = getWalletUsdMigrationMarker(userData);
      if (marker) {
        skippedCount += 1;
        results.push({
          userId,
          outcome: "SKIPPED",
          reason: "Wallet USD one-time migration already completed for this user.",
          previousCurrency: (asNonEmptyString(toRecord(userData.wallet).currency) || "USD").toUpperCase(),
          previousBalance: Number(toRecord(userData.wallet).balance || 0),
          migrationRunId: String(marker.runId || ""),
        });
        continue;
      }

      const wallet = toRecord(userData.wallet);
      const previousCurrency = (asNonEmptyString(wallet.currency) || "USD").toUpperCase();
      const previousBalance = Number(wallet.balance || 0);
      if (!Number.isFinite(previousBalance)) {
        errorCount += 1;
        results.push({
          userId,
          outcome: "ERROR",
          reason: "Wallet balance is not a finite number.",
          previousCurrency,
          migrationRunId: runId,
        });
        continue;
      }

      if (previousCurrency === "USD") {
        skippedCount += 1;
        results.push({
          userId,
          outcome: "SKIPPED",
          reason: "Wallet currency is already USD.",
          previousCurrency,
          previousBalance,
          migrationRunId: runId,
        });
        continue;
      }

      const rateSnapshot = previousBalance === 0 ?
        {
          fromCurrency: previousCurrency,
          toCurrency: "USD" as const,
          rate: 1,
          provider: "ZERO_BALANCE_IDENTITY",
          fetchedAtMs: Date.now(),
          requestUrl: "local://zero-balance",
        } :
        rateSnapshots.get(previousCurrency);

      if (!rateSnapshot) {
        errorCount += 1;
        results.push({
          userId,
          outcome: "ERROR",
          reason: rateErrors.get(previousCurrency) || `Missing exchange rate snapshot for ${previousCurrency}.`,
          previousCurrency,
          previousBalance,
          migrationRunId: runId,
        });
        continue;
      }

      const computedUsdBalance = roundMoney(previousBalance * rateSnapshot.rate);
      const auditId = `${runId}_${userId}`;
      if (dryRun) {
        previewCount += 1;
        results.push({
          userId,
          outcome: "PREVIEW",
          reason: "Dry run only. No writes performed.",
          previousCurrency,
          previousBalance,
          newUsdBalance: computedUsdBalance,
          rate: rateSnapshot.rate,
          rateProvider: rateSnapshot.provider,
          rateFetchedAtMs: rateSnapshot.fetchedAtMs,
          auditId,
          migrationRunId: runId,
        });
        continue;
      }

      try {
        let writeOutcome: "MIGRATED" | "SKIPPED" = "MIGRATED";
        await db.runTransaction(async (transaction) => {
          const freshSnap = await transaction.get(userDoc.ref);
          if (!freshSnap.exists) {
            writeOutcome = "SKIPPED";
            return;
          }

          const freshData = toRecord(freshSnap.data());
          const freshMarker = getWalletUsdMigrationMarker(freshData);
          if (freshMarker) {
            writeOutcome = "SKIPPED";
            return;
          }

          const freshWallet = toRecord(freshData.wallet);
          const freshCurrency = (asNonEmptyString(freshWallet.currency) || "USD").toUpperCase();
          const freshBalance = Number(freshWallet.balance || 0);
          if (!Number.isFinite(freshBalance)) {
            throw new Error("Wallet balance is not a finite number at write time.");
          }
          if (freshCurrency === "USD") {
            writeOutcome = "SKIPPED";
            return;
          }

          const freshRateSnapshot = freshBalance === 0 ?
            {
              fromCurrency: freshCurrency,
              toCurrency: "USD" as const,
              rate: 1,
              provider: "ZERO_BALANCE_IDENTITY",
              fetchedAtMs: Date.now(),
              requestUrl: "local://zero-balance",
            } :
            rateSnapshots.get(freshCurrency);
          if (!freshRateSnapshot) {
            throw new Error(`Missing exchange rate snapshot for ${freshCurrency} at write time.`);
          }

          const exactUsdBalance = freshBalance * freshRateSnapshot.rate;
          const newUsdBalance = roundMoney(exactUsdBalance);
          const now = admin.firestore.Timestamp.now();
          const fetchedAtTimestamp = admin.firestore.Timestamp.fromMillis(freshRateSnapshot.fetchedAtMs);
          const roundingDelta = Number((newUsdBalance - exactUsdBalance).toFixed(8));

          transaction.update(userDoc.ref, {
            "wallet.balance": newUsdBalance,
            "wallet.currency": "USD",
            "walletMigration.usdOneTime": {
              runId,
              migratedBy: adminUid,
              migratedAt: now,
              previousCurrency: freshCurrency,
              previousBalance: freshBalance,
              newUsdBalance,
              rate: freshRateSnapshot.rate,
              rateProvider: freshRateSnapshot.provider,
              rateFetchedAt: fetchedAtTimestamp,
              roundingDelta,
              auditId,
            },
          });

          const auditRef = db.collection("wallet_currency_migrations").doc(auditId);
          transaction.set(auditRef, {
            runId,
            userId,
            migratedBy: adminUid,
            migratedAt: now,
            previousCurrency: freshCurrency,
            previousBalance: freshBalance,
            newUsdBalance,
            exactUsdBalance,
            roundingDelta,
            rateSnapshot: {
              fromCurrency: freshRateSnapshot.fromCurrency,
              toCurrency: freshRateSnapshot.toCurrency,
              rate: freshRateSnapshot.rate,
              provider: freshRateSnapshot.provider,
              fetchedAt: fetchedAtTimestamp,
              requestUrl: freshRateSnapshot.requestUrl,
            },
            beforeWallet: {
              balance: freshBalance,
              currency: freshCurrency,
            },
            afterWallet: {
              balance: newUsdBalance,
              currency: "USD",
            },
            oneTimeMigration: true,
          }, {merge: true});
        });

        if (writeOutcome === "MIGRATED") {
          migratedCount += 1;
          results.push({
            userId,
            outcome: "MIGRATED",
            reason: "Wallet converted to USD and audit snapshot recorded.",
            previousCurrency,
            previousBalance,
            newUsdBalance: computedUsdBalance,
            rate: rateSnapshot.rate,
            rateProvider: rateSnapshot.provider,
            rateFetchedAtMs: rateSnapshot.fetchedAtMs,
            auditId,
            migrationRunId: runId,
          });
        } else {
          skippedCount += 1;
          results.push({
            userId,
            outcome: "SKIPPED",
            reason: "User wallet changed before migration commit; no conversion applied.",
            previousCurrency,
            previousBalance,
            migrationRunId: runId,
          });
        }
      } catch (error) {
        errorCount += 1;
        results.push({
          userId,
          outcome: "ERROR",
          reason: parseProviderErrorMessage(error),
          previousCurrency,
          previousBalance,
          migrationRunId: runId,
        });
      }
    }

    functions.logger.info("Wallet USD migration run completed.", {
      runId,
      dryRun,
      requestedBy: adminUid,
      scanned: userDocs.length,
      migratedCount,
      previewCount,
      skippedCount,
      errorCount,
      nextCursor,
    });

    return {
      runId,
      dryRun,
      requestedBy: adminUid,
      scanned: userDocs.length,
      summary: {
        migrated: migratedCount,
        preview: previewCount,
        skipped: skippedCount,
        errors: errorCount,
      },
      nextCursor,
      results,
    };
  });

export const adminListPayoutRequests = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const adminUid = await assertAdminCallableAccess(context);
    const payload = (data || {}) as AdminListPayoutRequestsPayload;
    const parsedLimit = Number(payload.limit);
    const limit = Math.min(Math.max(Number.isFinite(parsedLimit) ? Math.trunc(parsedLimit) : 120, 1), 250);
    const statusFilters = normalizeStatusFilters(payload.statuses);
    const statusFilterSet = new Set(statusFilters);
    const mobileMoneyOnly = payload.mobileMoneyOnly !== false;

    const snap = await db.collection("payout_requests")
      .orderBy("createdAt", "desc")
      .limit(limit)
      .get();

    const reversibleStatuses = new Set<string>([
      "PENDING",
      "PROCESSING",
      "PENDING_BANK_SETTLEMENT",
      "PROCESSING_BANK_SETTLEMENT",
      "PENDING_PROVIDER",
      "PROCESSING_PROVIDER",
      "FAILED",
      "COMPLETED",
    ]);

    const items = snap.docs.map((doc) => {
      const payoutData = doc.data() || {};
      const recipientInfo = (payoutData.recipientInfo || {}) as Record<string, unknown>;
      const status = String(payoutData.status || "UNKNOWN").trim().toUpperCase();
      const type = String(payoutData.type || "UNKNOWN").trim().toUpperCase();
      const isMobileMoneyType = isMobileMoneyPayoutType(type);
      const createdAtMs = toMillisTimestamp(payoutData.createdAt);
      const processedAtMs = toMillisTimestamp(payoutData.processedAt);
      const refunded = payoutData.refundProcessed === true || status === "REFUNDED";
      const reversible = !refunded && reversibleStatuses.has(status);
      const senderTransactionIds = Array.isArray(payoutData.senderTransactionIds) ?
        payoutData.senderTransactionIds.map((value) => String(value || "").trim()).filter((value) => value.length > 0) :
        [];

      return {
        payoutRequestId: doc.id,
        senderId: asNonEmptyString(payoutData.senderId) || "",
        recipientId: asNonEmptyString(payoutData.recipientId) || null,
        type,
        status,
        providerStatus: asNonEmptyString(payoutData.providerStatus) || null,
        providerTransferId: asNonEmptyString(payoutData.providerTransferId) || null,
        providerMessage: asNonEmptyString(payoutData.providerMessage) || null,
        errorMessage: asNonEmptyString(payoutData.errorMessage) || null,
        fundingSourceType: asNonEmptyString(
          payoutData.fundingSourceType,
          payoutData.fundingSource
        ) || null,
        amount: Number(payoutData.amount || 0),
        currency: asNonEmptyString(payoutData.currency, payoutData.localCurrency)?.toUpperCase() || "USD",
        recipientName: asNonEmptyString(
          payoutData.recipientName,
          recipientInfo["name"],
          recipientInfo["registeredName"]
        ) || null,
        recipientPhone: asNonEmptyString(
          payoutData.recipientPhone,
          recipientInfo["mobileNumber"],
          recipientInfo["phone"],
          recipientInfo["accountNumber"]
        ) || null,
        recipientCountry: asNonEmptyString(
          payoutData.recipientCountry,
          payoutData.country,
          recipientInfo["country"]
        ) || null,
        recipientNetwork: asNonEmptyString(
          payoutData.recipientNetwork,
          payoutData.network,
          recipientInfo["network"]
        ) || null,
        senderTransactionIds,
        refundProcessed: refunded,
        refundAmount: Number(payoutData.refundAmount || 0),
        createdAtMs,
        processedAtMs,
        isMobileMoneyType,
        reversible,
      };
    }).filter((item) => {
      if (mobileMoneyOnly && !item.isMobileMoneyType) {
        return false;
      }
      if (statusFilterSet.size > 0 && !statusFilterSet.has(item.status)) {
        return false;
      }
      return true;
    }).map((item) => {
      const {isMobileMoneyType, ...sanitizedItem} = item;
      return isMobileMoneyType ? sanitizedItem : sanitizedItem;
    });

    return {
      requestedBy: adminUid,
      requestedLimit: limit,
      returnedCount: items.length,
      filters: {
        statuses: statusFilters,
        mobileMoneyOnly,
      },
      items,
    };
  });

const reversiblePayoutStatuses = new Set<string>([
  "PENDING",
  "PROCESSING",
  "PENDING_BANK_SETTLEMENT",
  "PROCESSING_BANK_SETTLEMENT",
  "PENDING_PROVIDER",
  "PROCESSING_PROVIDER",
  "FAILED",
]);

const reverseSinglePayoutRequestToWallet = async (
  payoutRequestId: string,
  options: {
    allowCompleted: boolean;
    reason: string;
    performedBy: string;
  }
): Promise<ReversePayoutExecutionResult> => {
  const payoutRef = db.collection("payout_requests").doc(payoutRequestId);
  const payoutSnap = await payoutRef.get();
  if (!payoutSnap.exists) {
    return {
      payoutRequestId,
      outcome: "SKIPPED",
      message: "Payout request not found.",
    };
  }

  const payoutData = payoutSnap.data() || {};
  const payoutType = String(payoutData.type || "").toUpperCase();
  if (payoutType !== "CASH_OUT" && payoutType !== "BENEFICIARY_TRANSFER") {
    return {
      payoutRequestId,
      outcome: "SKIPPED",
      message: `Payout type ${payoutType || "UNKNOWN"} is not eligible for this reversal tool.`,
    };
  }

  const senderId = asNonEmptyString(payoutData.senderId);
  if (!senderId) {
    return {
      payoutRequestId,
      outcome: "ERROR",
      message: "Missing senderId on payout request.",
    };
  }

  const status = String(payoutData.status || "").toUpperCase();
  const canReverse = reversiblePayoutStatuses.has(status) || (options.allowCompleted && status === "COMPLETED");
  if (!canReverse) {
    return {
      payoutRequestId,
      outcome: "SKIPPED",
      message: `Status ${status || "UNKNOWN"} is not reversible for this request.`,
    };
  }

  const refundAmount = Number(payoutData.amount || 0);
  if (!Number.isFinite(refundAmount) || refundAmount <= 0) {
    return {
      payoutRequestId,
      outcome: "ERROR",
      message: "Invalid refund amount on payout request.",
    };
  }

  const userRef = db.collection("users").doc(senderId);
  let alreadyRefunded = false;

  await db.runTransaction(async (transaction) => {
    const freshPayoutSnap = await transaction.get(payoutRef);
    if (!freshPayoutSnap.exists) {
      throw new Error("Payout request disappeared during reversal.");
    }

    const freshPayoutData = freshPayoutSnap.data() || {};
    if (freshPayoutData.refundProcessed === true || String(freshPayoutData.status || "").toUpperCase() === "REFUNDED") {
      alreadyRefunded = true;
      return;
    }

    const txRef = userRef.collection("transactions").doc();
    const now = admin.firestore.Timestamp.now();
    transaction.update(userRef, "wallet.balance", admin.firestore.FieldValue.increment(refundAmount));
    transaction.set(txRef, {
      title: "Mobile Money Transfer Reversal",
      amount: refundAmount,
      type: "CREDIT",
      status: "COMPLETED",
      timestamp: now,
      note: `${options.reason} (payoutRequestId: ${payoutRequestId})`,
      source: "MOBILE_MONEY_REVERSAL",
      payoutRequestId,
    });

    transaction.set(payoutRef, {
      status: "REFUNDED",
      refundProcessed: true,
      refundAmount,
      refundTransactionId: txRef.id,
      refundReason: options.reason,
      refundedBy: options.performedBy,
      refundedAt: now,
      reversedFromStatus: String(freshPayoutData.status || status || "UNKNOWN"),
      providerStatus: "REFUNDED",
      providerMessage: "Funds returned to sender wallet by admin reversal tool.",
      processedAt: now,
    }, {merge: true});
  });

  if (alreadyRefunded) {
    return {
      payoutRequestId,
      outcome: "SKIPPED",
      message: "Already refunded.",
    };
  }

  return {
    payoutRequestId,
    outcome: "REFUNDED",
    message: "Funds returned to sender wallet.",
    refundAmount,
  };
};

const reversePayoutRequestsToWallet = async (
  payload: ReversePayoutRequestsPayload,
  contextData: {performedBy: string}
): Promise<{results: ReversePayoutExecutionResult[]; totals: {requested: number; refunded: number; skipped: number; errors: number; refundedAmount: number}}> => {
  const uniqueIds = [...new Set((payload.payoutRequestIds || []).map((id) => String(id || "").trim()).filter((id) => id.length > 0))];
  const allowCompleted = payload.allowCompleted === true;
  const reason = asNonEmptyString(payload.reason) || "Payout did not settle with provider; returning funds to sender wallet.";
  const performedBy = contextData.performedBy;

  const results: ReversePayoutExecutionResult[] = [];
  let refunded = 0;
  let skipped = 0;
  let errors = 0;
  let refundedAmount = 0;

  for (const payoutRequestId of uniqueIds) {
    try {
      const result = await reverseSinglePayoutRequestToWallet(payoutRequestId, {
        allowCompleted,
        reason,
        performedBy,
      });
      results.push(result);
      if (result.outcome === "REFUNDED") {
        refunded += 1;
        refundedAmount += Number(result.refundAmount || 0);
      } else if (result.outcome === "SKIPPED") {
        skipped += 1;
      } else {
        errors += 1;
      }
    } catch (error) {
      results.push({
        payoutRequestId,
        outcome: "ERROR",
        message: parseProviderErrorMessage(error),
      });
      errors += 1;
    }
  }

  return {
    results,
    totals: {
      requested: uniqueIds.length,
      refunded,
      skipped,
      errors,
      refundedAmount: Number(refundedAmount.toFixed(2)),
    },
  };
};

export const adminReversePayoutRequestsCallable = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const adminUid = await assertAdminCallableAccess(context);
    const payload = (data || {}) as ReversePayoutRequestsPayload;
    if (!Array.isArray(payload.payoutRequestIds) || payload.payoutRequestIds.length === 0) {
      throw new functions.https.HttpsError("invalid-argument", "payoutRequestIds must be a non-empty array.");
    }

    return reversePayoutRequestsToWallet(payload, {
      performedBy: `adminCallable:${adminUid}`,
    });
  });

export const adminReversePayoutRequests = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).json({error: "Method not allowed. Use POST."});
    return;
  }

  const expectedSecret = asNonEmptyString(process.env.ADMIN_PAYOUT_REVERSAL_SECRET);
  if (!expectedSecret) {
    res.status(503).json({error: "ADMIN_PAYOUT_REVERSAL_SECRET is not configured."});
    return;
  }

  const providedSecret = req.header("x-admin-reversal-secret") || "";
  if (providedSecret !== expectedSecret) {
    res.status(403).json({error: "Invalid reversal secret."});
    return;
  }

  const body = (req.body || {}) as ReversePayoutRequestsPayload;
  if (!Array.isArray(body.payoutRequestIds) || body.payoutRequestIds.length === 0) {
    res.status(400).json({error: "payoutRequestIds must be a non-empty array."});
    return;
  }

  const result = await reversePayoutRequestsToWallet(body, {
    performedBy: "adminReversePayoutRequests",
  });
  res.status(200).json(result);
});
