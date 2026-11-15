/* eslint-disable max-len */
import axios from "axios";
import {createHash} from "crypto";
import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v1";
import {APP_ASSISTANT_GUIDE} from "./appAssistantGuide.js";

const getDb = (): admin.firestore.Firestore => admin.firestore();

const DEFAULT_SUPPORT_EMAIL = "support@softsolutionstech.com";
const DEFAULT_MODEL = "gemini-3.8-flash";
const MAX_QUESTION_CHARS = 1000;
const MAX_HISTORY_TURNS = 10;
const MAX_HISTORY_TURN_CHARS = 2000;
const HOURLY_QUESTION_LIMIT = 40;
const DAILY_QUESTION_LIMIT = 200;
const ESCALATION_DEDUPE_MS = 24 * 60 * 60 * 1000;
const FAQ_CACHE_TTL_MS = 10 * 60 * 1000;

interface AssistantConfig {
  enabled: boolean;
  model: string;
  location: string;
  projectId: string;
  supportEmail: string;
  resendApiKey?: string;
  fromEmail?: string;
}

interface HistoryTurn {
  role: "user" | "model";
  text: string;
}

interface AssistantReply {
  answer: string;
  answered: boolean;
}

interface EscalationResult {
  ticketId: string;
  emailSent: boolean;
  alreadyEscalated: boolean;
}

const cleanString = (value: unknown, maxLength: number): string => {
  if (typeof value !== "string") return "";
  return value.replace(/\s+/g, " ").trim().slice(0, maxLength);
};

const firstString = (...values: unknown[]): string => {
  for (const value of values) {
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
};

const resolveConfig = (): AssistantConfig => {
  const projectId = firstString(
    process.env.GCLOUD_PROJECT,
    process.env.GCP_PROJECT,
    admin.app().options.projectId
  );
  return {
    enabled: String(process.env.AI_ASSISTANT_ENABLED || "true").trim().toLowerCase() !== "false",
    model: firstString(process.env.AI_ASSISTANT_MODEL) || DEFAULT_MODEL,
    location: firstString(process.env.AI_ASSISTANT_LOCATION) || "global",
    projectId,
    supportEmail: firstString(process.env.SUPPORT_EMAIL_ADDRESS) || DEFAULT_SUPPORT_EMAIL,
    resendApiKey: firstString(process.env.RESEND_API_KEY) || undefined,
    fromEmail: firstString(process.env.EMAIL_FROM_ADDRESS, process.env.RESEND_FROM_EMAIL) || undefined,
  };
};

let cachedAccessToken: {token: string; expiresAtMs: number} | null = null;

const getGoogleAccessToken = async (): Promise<string> => {
  if (cachedAccessToken && cachedAccessToken.expiresAtMs - Date.now() > 60_000) {
    return cachedAccessToken.token;
  }
  const token = await admin.credential.applicationDefault().getAccessToken();
  cachedAccessToken = {
    token: token.access_token,
    expiresAtMs: Date.now() + token.expires_in * 1000,
  };
  return token.access_token;
};

let cachedFaq: {text: string; loadedAtMs: number} | null = null;

const loadFaqText = async (): Promise<string> => {
  if (cachedFaq && Date.now() - cachedFaq.loadedAtMs < FAQ_CACHE_TTL_MS) {
    return cachedFaq.text;
  }
  try {
    const snapshot = await getDb().collection("howToUseTips").limit(80).get();
    const lines = snapshot.docs.flatMap((doc) => {
      const question = cleanString(doc.get("question"), 300);
      const answer = cleanString(doc.get("answer"), 1500);
      return question && answer ? [`Q: ${question}\nA: ${answer}`] : [];
    });
    cachedFaq = {text: lines.join("\n\n"), loadedAtMs: Date.now()};
  } catch (error) {
    functions.logger.warn("AI assistant could not load FAQ", {error});
    cachedFaq = {text: "", loadedAtMs: Date.now()};
  }
  return cachedFaq.text;
};

const loadUserProfile = async (uid: string): Promise<{
  name: string;
  email: string;
  phone: string;
  role: string;
  country: string;
}> => {
  const [userSnap, authUser] = await Promise.all([
    getDb().collection("users").doc(uid).get(),
    admin.auth().getUser(uid).catch(() => null),
  ]);
  const data = userSnap.data() || {};
  return {
    name: firstString(data.name, data.displayName, data.fullName, authUser?.displayName),
    email: firstString(authUser?.email, data.email),
    phone: firstString(authUser?.phoneNumber, data.phoneNumber, data.phone),
    role: firstString(data.role, data.userRole, data.userType),
    country: firstString(data.country, data.countryCode),
  };
};

const parseHistory = (raw: unknown): HistoryTurn[] => {
  if (!Array.isArray(raw)) return [];
  return raw
    .slice(-MAX_HISTORY_TURNS)
    .flatMap((entry): HistoryTurn[] => {
      if (!entry || typeof entry !== "object") return [];
      const record = entry as Record<string, unknown>;
      const text = cleanString(record.text, MAX_HISTORY_TURN_CHARS);
      if (!text) return [];
      return [{role: record.role === "user" ? "user" : "model", text}];
    });
};

const enforceRateLimit = async (uid: string): Promise<void> => {
  const ref = getDb().collection("ai_assistant_usage").doc(uid);
  await getDb().runTransaction(async (transaction) => {
    const snap = await transaction.get(ref);
    const data = snap.data() || {};
    const now = Date.now();
    const hourStartMs = typeof data.hourStartMs === "number" && now - data.hourStartMs < 60 * 60 * 1000 ?
      data.hourStartMs : now;
    const dayStartMs = typeof data.dayStartMs === "number" && now - data.dayStartMs < 24 * 60 * 60 * 1000 ?
      data.dayStartMs : now;
    const hourCount = hourStartMs === data.hourStartMs ? Number(data.hourCount || 0) : 0;
    const dayCount = dayStartMs === data.dayStartMs ? Number(data.dayCount || 0) : 0;
    if (hourCount >= HOURLY_QUESTION_LIMIT || dayCount >= DAILY_QUESTION_LIMIT) {
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "You have asked a lot of questions recently. Please try again later or email support."
      );
    }
    transaction.set(ref, {
      hourStartMs,
      hourCount: hourCount + 1,
      dayStartMs,
      dayCount: dayCount + 1,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });
};

const buildSystemInstruction = (params: {
  faqText: string;
  supportEmail: string;
  screen: string;
  platform: string;
  role: string;
  country: string;
}): string => `You are the in-app help assistant for Volunteers App, built by Soft Solutions Tech.
You help members use the app: accounts and profiles, events, jobs, organizer and employer tools, Send Money and payment methods, marketplace, ads and garage sales, community, chat and calls, live streaming, dating, safety and support.

Rules:
- Answer only from the APP GUIDE and FAQ below. Never invent screens, buttons, fees, limits, countries or timelines. If a detail is not covered, say you are not sure.
- Give short, practical answers: one or two sentences, then numbered steps that use the exact screen and button names from the guide. Use simple Markdown (bold for button names, numbered lists). No headings, no tables.
- Reply in the same language the member writes in.
- Never ask for passwords, one-time codes, full card or bank numbers. If the member shares them, tell them not to share them.
- You cannot see or change the member's account, payments or data. For anything that needs a person (a stuck or missing payment, refund, dispute, account access or deletion, a bug or error message you cannot explain, a safety or abuse report), say that the support team will follow up by email.
- Set "answered" to false when the guide and FAQ do not cover the question, when you are not confident, or when the member needs the support team. Set it to true only when your answer fully resolves the question from the guide.
- Questions unrelated to the app: politely say you can only help with Volunteers App, and set "answered" to true.
- Support contact: ${params.supportEmail}.

Member context (use it to tailor steps; do not repeat it back):
- Screen they opened the assistant from: ${params.screen || "unknown"}
- Platform: ${params.platform || "unknown"}
- Role: ${params.role || "unknown"}
- Country: ${params.country || "unknown"}

APP GUIDE:
${APP_ASSISTANT_GUIDE}

FAQ:
${params.faqText || "(none)"}`;

const vertexEndpoint = (config: AssistantConfig): string => {
  const host = config.location === "global" ?
    "aiplatform.googleapis.com" :
    `${config.location}-aiplatform.googleapis.com`;
  return `https://${host}/v1/projects/${config.projectId}/locations/${config.location}` +
    `/publishers/google/models/${config.model}:generateContent`;
};

const generateAssistantReply = async (params: {
  config: AssistantConfig;
  systemInstruction: string;
  history: HistoryTurn[];
  question: string;
}): Promise<AssistantReply> => {
  const accessToken = await getGoogleAccessToken();
  const contents = [
    ...params.history.map((turn) => ({role: turn.role, parts: [{text: turn.text}]})),
    {role: "user", parts: [{text: params.question}]},
  ];
  const response = await axios.post<{
    candidates?: Array<{content?: {parts?: Array<{text?: string}>}}>;
  }>(
    vertexEndpoint(params.config),
    {
      systemInstruction: {parts: [{text: params.systemInstruction}]},
      contents,
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: {
          type: "OBJECT",
          properties: {
            answer: {type: "STRING"},
            answered: {type: "BOOLEAN"},
          },
          required: ["answer", "answered"],
        },
        maxOutputTokens: 8192,
      },
    },
    {
      timeout: 45_000,
      headers: {
        "Authorization": `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
    }
  );

  const parts = response.data?.candidates?.[0]?.content?.parts;
  const text = Array.isArray(parts) ?
    parts.map((part: {text?: string}) => part.text || "").join("").trim() :
    "";
  if (!text) {
    return {answer: "", answered: false};
  }
  try {
    const parsed = JSON.parse(text) as {answer?: unknown; answered?: unknown};
    const answer = typeof parsed.answer === "string" ? parsed.answer.trim() : "";
    return {answer, answered: parsed.answered === true && answer.length > 0};
  } catch {
    return {answer: text, answered: false};
  }
};

const escapeHtml = (value: string): string =>
  value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");

const sendSupportEmail = async (params: {
  config: AssistantConfig;
  subject: string;
  lines: string[];
  replyTo?: string;
}): Promise<boolean> => {
  const {config} = params;
  if (!config.resendApiKey || !config.fromEmail) {
    functions.logger.warn("AI assistant escalation email skipped: RESEND_API_KEY or EMAIL_FROM_ADDRESS is missing.");
    return false;
  }
  try {
    await axios.post(
      "https://api.resend.com/emails",
      {
        from: config.fromEmail,
        to: [config.supportEmail],
        ...(params.replyTo ? {reply_to: params.replyTo} : {}),
        subject: params.subject,
        text: params.lines.join("\n"),
        html: params.lines.map((line) => line ? `<p>${escapeHtml(line)}</p>` : "<br>").join(""),
      },
      {
        timeout: 15_000,
        headers: {
          "Authorization": `Bearer ${config.resendApiKey}`,
          "Content-Type": "application/json",
        },
      }
    );
    return true;
  } catch (error) {
    functions.logger.error("AI assistant escalation email failed", {error});
    return false;
  }
};

const escalateToSupport = async (params: {
  uid: string;
  question: string;
  screen: string;
  platform: string;
  aiAnswer: string;
  transcript: HistoryTurn[];
  memberNote: string;
  trigger: "ai_unanswered" | "member_request";
}): Promise<EscalationResult> => {
  const config = resolveConfig();
  const profile = await loadUserProfile(params.uid);
  const questionKey = createHash("sha256")
    .update(`${params.uid}:${params.question.toLowerCase()}`)
    .digest("hex");
  const reportId = `ai_${questionKey.slice(0, 40)}`;
  const reportRef = getDb().collection("user_reports").doc(reportId);

  const existing = await reportRef.get();
  const lastEscalatedMs = existing.get("createdAtMs");
  if (existing.exists && typeof lastEscalatedMs === "number" &&
    Date.now() - lastEscalatedMs < ESCALATION_DEDUPE_MS) {
    return {ticketId: reportId, emailSent: existing.get("emailSent") === true, alreadyEscalated: true};
  }

  const transcriptLines = params.transcript.map((turn) =>
    `${turn.role === "user" ? "Member" : "Assistant"}: ${turn.text}`);
  const nowMs = Date.now();
  const title = `AI Assistant question - ${params.screen || "App Help"}`;
  const description = [
    `Question: ${params.question}`,
    params.memberNote ? `Member note: ${params.memberNote}` : "",
    params.aiAnswer ? `Assistant reply: ${params.aiAnswer}` : "",
  ].filter(Boolean).join("\n\n");

  await reportRef.set({
    reportedByUid: params.uid,
    reportingUserId: params.uid,
    reportingUserDisplayName: profile.name || profile.email || "Member",
    reportedUserName: "AI Assistant question",
    title,
    description,
    reason: description,
    category: "AI Support",
    status: "open",
    source: `ai_assistant_${params.platform || "app"}`,
    trigger: params.trigger,
    question: params.question,
    screen: params.screen,
    aiAnswer: params.aiAnswer,
    transcript: transcriptLines.slice(-MAX_HISTORY_TURNS * 2),
    contactName: profile.name,
    contactEmail: profile.email,
    contactPhone: profile.phone,
    emailSent: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    createdAtMs: nowMs,
  });

  const emailSent = await sendSupportEmail({
    config,
    subject: `[AI Assistant] ${params.question.slice(0, 90)}`,
    replyTo: profile.email || undefined,
    lines: [
      "A member needs help that the in-app AI assistant could not resolve.",
      "",
      `Question: ${params.question}`,
      params.memberNote ? `Member note: ${params.memberNote}` : "",
      `Screen: ${params.screen || "Unknown"}`,
      `Platform: ${params.platform || "Unknown"}`,
      "",
      `Name: ${profile.name || "Not provided"}`,
      `Email: ${profile.email || "Not provided"}`,
      `Phone: ${profile.phone || "Not provided"}`,
      `Role: ${profile.role || "Unknown"}`,
      `Country: ${profile.country || "Unknown"}`,
      `UID: ${params.uid}`,
      `Ticket: user_reports/${reportId}`,
      "",
      params.aiAnswer ? `Assistant reply: ${params.aiAnswer}` : "",
      transcriptLines.length ? "Conversation:" : "",
      ...transcriptLines,
    ].filter((line, index, all) => line !== "" || all[index - 1] !== ""),
  });
  if (emailSent) {
    await reportRef.set({emailSent: true}, {merge: true});
  }
  return {ticketId: reportId, emailSent, alreadyEscalated: false};
};

/**
 * Answers a member's question about the app with Gemini on Vertex AI, grounded in
 * the app guide and the `howToUseTips` FAQ. Questions it cannot answer become a
 * support ticket that is emailed to the support inbox.
 */
export const askAppAssistant = functions
  .runWith({enforceAppCheck: true, timeoutSeconds: 60, memory: "512MB"})
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError("unauthenticated", "Please sign in to use the assistant.");
    }
    const request = (data || {}) as Record<string, unknown>;
    const question = cleanString(request.question, MAX_QUESTION_CHARS);
    if (!question) {
      throw new functions.https.HttpsError("invalid-argument", "Type a question first.");
    }
    const screen = cleanString(request.screen, 80);
    const platform = cleanString(request.platform, 20).toLowerCase();
    const history = parseHistory(request.history);

    const config = resolveConfig();
    if (!config.enabled || !config.projectId) {
      throw new functions.https.HttpsError("unavailable", "The assistant is not available right now.");
    }
    await enforceRateLimit(uid);

    const [profile, faqText] = await Promise.all([loadUserProfile(uid), loadFaqText()]);
    let reply: AssistantReply;
    try {
      reply = await generateAssistantReply({
        config,
        systemInstruction: buildSystemInstruction({
          faqText,
          supportEmail: config.supportEmail,
          screen,
          platform,
          role: profile.role,
          country: profile.country,
        }),
        history,
        question,
      });
    } catch (error: unknown) {
      const axiosError = error as {
        isAxiosError?: boolean;
        response?: {status?: number; data?: unknown};
      };
      const status = axiosError?.isAxiosError ? axiosError.response?.status : undefined;
      functions.logger.error("AI assistant model call failed", {
        status,
        model: config.model,
        body: axiosError?.isAxiosError ? axiosError.response?.data : undefined,
      });
      throw new functions.https.HttpsError("unavailable", "The assistant could not answer right now. Please try again.");
    }

    if (reply.answered) {
      return {answer: reply.answer, answered: true, escalated: false};
    }

    const escalation = await escalateToSupport({
      uid,
      question,
      screen,
      platform,
      aiAnswer: reply.answer,
      transcript: [...history, {role: "user", text: question}],
      memberNote: "",
      trigger: "ai_unanswered",
    });
    const contact = profile.email ? ` at ${profile.email}` : "";
    const answer = [
      reply.answer,
      `I've sent this to our support team (${config.supportEmail}), and they will reply${contact}.`,
    ].filter(Boolean).join("\n\n");
    return {
      answer,
      answered: false,
      escalated: true,
      ticketId: escalation.ticketId,
      emailSent: escalation.emailSent,
      supportEmail: config.supportEmail,
    };
  });

/**
 * Sends the member's question (and recent conversation) to the support inbox on request,
 * for example when an answer did not help.
 */
export const submitAIAssistantEscalation = functions
  .runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    const uid = context.auth?.uid;
    if (!uid) {
      throw new functions.https.HttpsError("unauthenticated", "Please sign in again.");
    }
    const request = (data || {}) as Record<string, unknown>;
    const question = cleanString(request.question, MAX_QUESTION_CHARS);
    if (!question) {
      throw new functions.https.HttpsError("invalid-argument", "Question is required.");
    }
    const config = resolveConfig();
    const result = await escalateToSupport({
      uid,
      question,
      screen: cleanString(request.screen ?? request.contextTitle, 80),
      platform: cleanString(request.platform, 20).toLowerCase(),
      aiAnswer: cleanString(request.aiAnswer, MAX_HISTORY_TURN_CHARS),
      transcript: parseHistory(request.history),
      memberNote: cleanString(request.note, MAX_QUESTION_CHARS),
      trigger: "member_request",
    });
    return {
      success: true,
      reportStored: true,
      emailSent: result.emailSent,
      alreadyEscalated: result.alreadyEscalated,
      ticketId: result.ticketId,
      supportEmail: config.supportEmail,
    };
  });
