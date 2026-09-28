/* eslint-disable max-len, quote-props */
import axios from "axios";
import {createHmac} from "crypto";
import * as admin from "firebase-admin";
import {RtcRole, RtcTokenBuilder} from "agora-token";
import * as functions from "firebase-functions/v1";

const getDb = (): admin.firestore.Firestore => admin.firestore();
const AGORA_RECORDING_API_BASE = "https://api.sd-rtn.com";

type LiveAccessPurpose = "live" | "replay";

interface LiveAccessClaims {
  sid: string;
  hostId: string;
  purpose: LiveAccessPurpose;
  file?: string;
  exp: number;
}

interface AgoraRecordingConfig {
  enabled: boolean;
  customerId?: string;
  customerSecret?: string;
  maxIdleTimeSeconds: number;
  resourceExpiredHours: number;
  storageVendor: number;
  storageRegion: number;
  storageBucket?: string;
  storageAccessKey?: string;
  storageSecretKey?: string;
  fileNamePrefix: string[];
  playbackBaseUrl?: string;
}

interface LiveAccessConfig {
  replaySecret?: string;
  shareSecret?: string;
  replayBaseUrl?: string;
  shareBaseUrl?: string;
  replayAccessTtlSeconds: number;
  replayShareTtlSeconds: number;
  shareAccessTtlSeconds: number;
  shareReplayTtlSeconds: number;
}

const getRuntimeConfig = (): Record<string, unknown> => {
  const configFn = (functions as unknown as {config?: () => Record<string, unknown>}).config;
  if (typeof configFn === "function") {
    try {
      return configFn();
    } catch {
      return {};
    }
  }
  return {};
};

const getAgoraAppConfig = () => {
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

const getAgoraRecordingConfig = (): AgoraRecordingConfig => {
  const prefixRaw = String(process.env.AGORA_RECORDING_STORAGE_FILE_NAME_PREFIX || "live_archives").trim();
  const prefixParts = prefixRaw.split("/").map((part) => part.trim()).filter((part) => part.length > 0);
  return {
    enabled: String(process.env.AGORA_RECORDING_ENABLED || "false").trim().toLowerCase() === "true",
    customerId: process.env.AGORA_RECORDING_CUSTOMER_ID,
    customerSecret: process.env.AGORA_RECORDING_CUSTOMER_SECRET,
    maxIdleTimeSeconds: Number(process.env.AGORA_RECORDING_MAX_IDLE_TIME_SECONDS || 600),
    resourceExpiredHours: Number(process.env.AGORA_RECORDING_RESOURCE_EXPIRED_HOURS || 24),
    storageVendor: Number(process.env.AGORA_RECORDING_STORAGE_VENDOR || 6),
    storageRegion: Number(process.env.AGORA_RECORDING_STORAGE_REGION || 0),
    storageBucket: process.env.AGORA_RECORDING_STORAGE_BUCKET,
    storageAccessKey: process.env.AGORA_RECORDING_STORAGE_ACCESS_KEY,
    storageSecretKey: process.env.AGORA_RECORDING_STORAGE_SECRET_KEY,
    fileNamePrefix: prefixParts.length > 0 ? prefixParts : ["live_archives"],
    playbackBaseUrl: process.env.AGORA_RECORDING_PLAYBACK_BASE_URL,
  };
};

const getLiveAccessConfig = (): LiveAccessConfig => {
  const replaySecret = String(process.env.LIVE_REPLAY_ACCESS_SECRET || "").trim() || undefined;
  const shareSecret = String(process.env.LIVE_SHARE_ACCESS_SECRET || "").trim() || replaySecret;
  return {
    replaySecret,
    shareSecret,
    replayBaseUrl: String(process.env.LIVE_REPLAY_ACCESS_BASE_URL || "").trim() || undefined,
    shareBaseUrl: String(process.env.LIVE_SHARE_BASE_URL || "").trim() || undefined,
    replayAccessTtlSeconds: Number(process.env.LIVE_REPLAY_ACCESS_TTL_SECONDS || 900),
    replayShareTtlSeconds: Number(process.env.LIVE_REPLAY_SHARE_TTL_SECONDS || 86400),
    shareAccessTtlSeconds: Number(process.env.LIVE_SHARE_ACCESS_TTL_SECONDS || 21600),
    shareReplayTtlSeconds: Number(process.env.LIVE_SHARE_REPLAY_TTL_SECONDS || 86400),
  };
};

const base64UrlEncode = (value: string | Buffer): string => {
  const buffer = Buffer.isBuffer(value) ? value : Buffer.from(value);
  return buffer.toString("base64url");
};

const signJwt = (payload: Record<string, unknown>, secret: string): string => {
  const header = base64UrlEncode(JSON.stringify({alg: "HS256", typ: "JWT"}));
  const body = base64UrlEncode(JSON.stringify(payload));
  const data = `${header}.${body}`;
  const signature = createHmac("sha256", secret).update(data).digest("base64url");
  return `${data}.${signature}`;
};

const verifyJwt = (token: string, secret: string): Record<string, unknown> | null => {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, payload, signature] = parts;
  const data = `${header}.${payload}`;
  const expected = createHmac("sha256", secret).update(data).digest("base64url");
  if (signature !== expected) return null;
  try {
    const decoded = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as Record<string, unknown>;
    const exp = Number(decoded.exp || 0);
    if (!Number.isFinite(exp) || exp <= Math.floor(Date.now() / 1000)) {
      return null;
    }
    return decoded;
  } catch {
    return null;
  }
};

const parseLiveAccessClaims = (token: string, purpose: LiveAccessPurpose): LiveAccessClaims | null => {
  const config = getLiveAccessConfig();
  const secret = purpose === "live" ? config.shareSecret : config.replaySecret;
  if (!secret) return null;
  const decoded = verifyJwt(token, secret);
  if (!decoded) return null;
  const sid = String(decoded.sid || decoded.sessionId || "").trim();
  const hostId = String(decoded.hostId || "").trim();
  const claimPurpose = String(decoded.purpose || "").trim().toLowerCase();
  const exp = Number(decoded.exp || 0);
  if (!sid || !hostId || claimPurpose !== purpose || !Number.isFinite(exp)) {
    return null;
  }
  return {
    sid,
    hostId,
    purpose,
    file: typeof decoded.file === "string" ? decoded.file : undefined,
    exp,
  };
};

const buildShareUrl = (sessionId: string, hostId: string, token: string): string => {
  const config = getLiveAccessConfig();
  const base = (config.shareBaseUrl || "https://softsolutionstech.com/live").replace(/\/$/, "");
  const params = new URLSearchParams({
    sessionId,
    hostId,
    token,
  });
  return `${base}?${params.toString()}`;
};

const buildReplayPlaybackUrl = (sessionId: string, filePath: string, token: string): string => {
  const accessConfig = getLiveAccessConfig();
  const recordingConfig = getAgoraRecordingConfig();
  const base = (accessConfig.replayBaseUrl || recordingConfig.playbackBaseUrl || "").replace(/\/$/, "");
  const normalizedFile = filePath.replace(/^\/+/, "");
  if (!base) {
    return `${normalizedFile}?token=${encodeURIComponent(token)}`;
  }
  return `${base}/${normalizedFile}?token=${encodeURIComponent(token)}`;
};

const stableRecordingUid = (sessionId: string): number => {
  let hash = 0;
  for (let i = 0; i < sessionId.length; i += 1) {
    hash = ((hash << 5) - hash) + sessionId.charCodeAt(i);
    hash |= 0;
  }
  const positive = Math.abs(hash);
  return positive === 0 ? 900001 : (positive % 900000) + 100000;
};

const buildAgoraRtcToken = (channelName: string, uid: number, role: "publisher" | "subscriber"): string => {
  const {appId, appCertificate, ttlSeconds} = getAgoraAppConfig();
  if (!appId || !appCertificate) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Agora credentials are not configured."
    );
  }
  const rtcRole = role === "subscriber" ? RtcRole.SUBSCRIBER : RtcRole.PUBLISHER;
  return RtcTokenBuilder.buildTokenWithUid(
    appId,
    appCertificate,
    channelName,
    uid,
    rtcRole,
    ttlSeconds,
    ttlSeconds
  );
};

const agoraRecordingAuthHeader = (): string => {
  const config = getAgoraRecordingConfig();
  if (!config.customerId || !config.customerSecret) {
    throw new Error("Agora recording credentials are not configured.");
  }
  return `Basic ${Buffer.from(`${config.customerId}:${config.customerSecret}`).toString("base64")}`;
};

const isLiveStatus = (status: unknown): boolean => {
  const normalized = String(status || "live").trim().toLowerCase();
  return normalized === "live" || normalized === "active";
};

const isEndedStatus = (status: unknown): boolean => {
  return String(status || "").trim().toLowerCase() === "ended";
};

const userFollowsHost = async (viewerId: string, hostId: string): Promise<boolean> => {
  const snap = await getDb().collection("users").doc(viewerId).collection("following").doc(hostId).get();
  return snap.exists;
};

const hasAcceptedJoinRequest = async (sessionId: string, userId: string): Promise<boolean> => {
  const snap = await getDb().collection("join_requests").doc(`${sessionId}_${userId}`).get();
  return String(snap.data()?.status || "").trim().toLowerCase() === "accepted";
};

const isBlockedViewer = async (sessionId: string, userId: string): Promise<boolean> => {
  const blockedDoc = await getDb().collection("live_sessions").doc(sessionId).collection("blocked_users").doc(userId).get();
  return blockedDoc.exists;
};

const shareTokenGrantsAccess = (
  shareAccessToken: string,
  sessionId: string,
  session: admin.firestore.DocumentData,
  purpose: LiveAccessPurpose
): boolean => {
  const claims = parseLiveAccessClaims(shareAccessToken, purpose);
  if (!claims || claims.sid !== sessionId) return false;
  const hostId = String(session.hostId || session.hostUid || "").trim();
  return claims.hostId === hostId;
};
const canUserWatchLiveSession = async (
  sessionId: string,
  session: admin.firestore.DocumentData,
  userId: string,
  shareAccessToken?: string
): Promise<boolean> => {
  const hostId = String(session.hostId || session.hostUid || "").trim();
  if (!hostId) return false;
  if (userId === hostId) return true;
  if (await isBlockedViewer(sessionId, userId)) return false;
  if (shareAccessToken) {
    if (shareTokenGrantsAccess(shareAccessToken, sessionId, session, "live")) {
      return true;
    }
  }
  if (!isLiveStatus(session.status)) {
    return false;
  }

  const mode = String(session.viewAccessMode || "public").trim().toLowerCase();
  switch (mode) {
  case "public":
    return true;
  case "followers_only":
    return userFollowsHost(userId, hostId);
  case "invite_only":
    return hasAcceptedJoinRequest(sessionId, userId);
  case "accepted_event_volunteers": {
    const sourceId = String(session.sourceId || session.linkedEventId || "").trim();
    if (!sourceId) return false;
    const applicationSnap = await getDb().collectionGroup("applications")
      .where("eventId", "==", sourceId)
      .where("volunteerId", "==", userId)
      .limit(5)
      .get();
    return applicationSnap.docs.some((doc) => {
      const status = String(doc.data()?.status || "").trim().toLowerCase();
      return status === "accepted" || status === "approved";
    });
  }
  default:
    return false;
  }
};

const canUserPublishToStage = async (
  sessionId: string,
  session: admin.firestore.DocumentData,
  userId: string
): Promise<boolean> => {
  const hostId = String(session.hostId || session.hostUid || "").trim();
  if (userId === hostId) return true;
  if (await isBlockedViewer(sessionId, userId)) return false;

  const acceptedVolunteerIds = Array.isArray(session.acceptedVolunteerIds) ?
    session.acceptedVolunteerIds.map((value) => String(value)) :
    [];
  const mode = String(session.stageAccessMode || "host_only").trim().toLowerCase();
  switch (mode) {
  case "host_only":
    return false;
  case "request_to_join":
  case "approved_volunteers":
    return acceptedVolunteerIds.includes(userId) || await hasAcceptedJoinRequest(sessionId, userId);
  case "open_to_accepted_volunteers":
    return acceptedVolunteerIds.includes(userId) || await hasAcceptedJoinRequest(sessionId, userId);
  default:
    return false;
  }
};

const canUserAccessReplay = async (
  sessionId: string,
  session: admin.firestore.DocumentData,
  userId: string,
  shareAccessToken?: string
): Promise<boolean> => {
  const hostId = String(session.hostId || session.hostUid || "").trim();
  if (userId === hostId) return true;
  if (await isBlockedViewer(sessionId, userId)) return false;
  if (shareAccessToken) {
    if (shareTokenGrantsAccess(shareAccessToken, sessionId, session, "replay")) {
      return true;
    }
  }
  const visibility = String(session.replayVisibility || "owner_only").trim().toLowerCase();
  switch (visibility) {
  case "public":
    return true;
  case "followers_only":
    return userFollowsHost(userId, hostId);
  case "shared_link":
    return Boolean(
      shareAccessToken &&
      shareTokenGrantsAccess(shareAccessToken, sessionId, session, "replay")
    );
  case "owner_only":
  default:
    return false;
  }
};

export const enforceLiveRtcTokenAccess = async (params: {
  sessionId: string;
  userId: string;
  requestedRole: "publisher" | "subscriber";
  shareAccessToken?: string;
  liveData: admin.firestore.DocumentData;
}): Promise<void> => {
  const {sessionId, userId, requestedRole, shareAccessToken, liveData} = params;
  if (!isLiveStatus(liveData.status)) {
    throw new functions.https.HttpsError("failed-precondition", "This live session has ended.");
  }

  const canWatch = await canUserWatchLiveSession(sessionId, liveData, userId, shareAccessToken);
  if (!canWatch) {
    const accessMode = String(liveData.viewAccessMode || "public").trim().toLowerCase();
    const message = accessMode === "followers_only" ?
      "This live stream is available to followers of the host." :
      accessMode === "invite_only" ?
        "This live stream requires an approved invite or a valid share link." :
        accessMode === "accepted_event_volunteers" ?
          "This live stream is limited to accepted volunteers for the linked event." :
          "You do not have access to this live session.";
    throw new functions.https.HttpsError("permission-denied", message);
  }

  if (requestedRole === "publisher") {
    const canPublish = await canUserPublishToStage(sessionId, liveData, userId);
    if (!canPublish) {
      throw new functions.https.HttpsError("permission-denied", "You are not allowed to publish to this stage.");
    }
  }
};

const runtimeArchiveRef = (sessionId: string) =>
  getDb().collection("live_sessions").doc(sessionId).collection("server_archive").doc("runtime");

const startLiveArchiveRecording = async (sessionId: string, session: admin.firestore.DocumentData): Promise<void> => {
  const recordingConfig = getAgoraRecordingConfig();
  if (!recordingConfig.enabled) {
    functions.logger.info("Live archive recording disabled.", {sessionId});
    return;
  }
  const {appId} = getAgoraAppConfig();
  if (!appId || !recordingConfig.customerId || !recordingConfig.customerSecret ||
      !recordingConfig.storageBucket || !recordingConfig.storageAccessKey || !recordingConfig.storageSecretKey) {
    functions.logger.warn("Live archive recording skipped due to missing config.", {sessionId});
    return;
  }

  const channelName = String(session.agoraChannelName || session.channelName || "").trim();
  if (!channelName) {
    functions.logger.warn("Live archive recording skipped: missing channel name.", {sessionId});
    return;
  }

  const runtimeRef = runtimeArchiveRef(sessionId);
  const runtimeSnap = await runtimeRef.get();
  if (runtimeSnap.exists && runtimeSnap.data()?.recordingSid) {
    return;
  }

  const recordingUid = stableRecordingUid(sessionId);
  const authHeader = agoraRecordingAuthHeader();
  const acquireResponse = await axios.post<{resourceId?: string}>(
    `${AGORA_RECORDING_API_BASE}/v1/apps/${appId}/cloud_recording/acquire`,
    {
      cname: channelName,
      uid: String(recordingUid),
      clientRequest: {
        resourceExpiredHour: recordingConfig.resourceExpiredHours,
      },
    },
    {headers: {Authorization: authHeader, "Content-Type": "application/json"}}
  );
  const resourceId = String(acquireResponse.data?.resourceId || "").trim();
  if (!resourceId) {
    throw new Error("Agora acquire did not return a resourceId.");
  }

  const recordingToken = buildAgoraRtcToken(channelName, recordingUid, "subscriber");
  const filePrefix = [...recordingConfig.fileNamePrefix, sessionId];
  const startResponse = await axios.post<{sid?: string}>(
    `${AGORA_RECORDING_API_BASE}/v1/apps/${appId}/cloud_recording/resourceid/${resourceId}/mode/mix/start`,
    {
      uid: String(recordingUid),
      cname: channelName,
      clientRequest: {
        token: recordingToken,
        recordingConfig: {
          channelType: 1,
          streamTypes: 2,
          maxIdleTime: recordingConfig.maxIdleTimeSeconds,
          subscribeUidGroup: 0,
        },
        recordingFileConfig: {
          avFileType: ["hls", "mp4"],
        },
        storageConfig: {
          vendor: recordingConfig.storageVendor,
          region: recordingConfig.storageRegion,
          bucket: recordingConfig.storageBucket,
          accessKey: recordingConfig.storageAccessKey,
          secretKey: recordingConfig.storageSecretKey,
          fileNamePrefix: filePrefix,
        },
      },
    },
    {headers: {Authorization: authHeader, "Content-Type": "application/json"}}
  );

  const recordingSid = String(startResponse.data?.sid || "").trim();
  await runtimeRef.set({
    resourceId,
    recordingSid,
    recordingUid,
    channelName,
    objectPrefix: filePrefix.join("/"),
    startedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, {merge: true});

  await getDb().collection("live_sessions").doc(sessionId).set({
    archiveStatus: "processing",
    archiveObjectPrefix: filePrefix.join("/"),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, {merge: true});
};

const extractPrimaryArchiveFile = (stopPayload: unknown, objectPrefix: string): string | null => {
  const payload = stopPayload as {
    serverResponse?: {
      fileList?: Array<{fileName?: string; trackType?: string}>;
      extensionServiceState?: {payload?: {uploadingStatus?: string}};
    };
  };
  const fileList = payload.serverResponse?.fileList || [];
  const preferred = fileList.find((file) => {
    const name = String(file.fileName || "");
    return name.endsWith(".m3u8") || name.endsWith(".mp4");
  }) || fileList[0];
  const fileName = String(preferred?.fileName || "").trim();
  if (!fileName) return null;
  if (fileName.includes("/")) return fileName;
  return `${objectPrefix}/${fileName}`.replace(/\/+/g, "/");
};

const finalizeLiveArchiveRecording = async (sessionId: string, session: admin.firestore.DocumentData): Promise<void> => {
  const recordingConfig = getAgoraRecordingConfig();
  const runtimeRef = runtimeArchiveRef(sessionId);
  const runtimeSnap = await runtimeRef.get();
  const runtime = runtimeSnap.data() || {};
  if (runtime.finalizedAt) {
    return;
  }
  if (!runtime.resourceId || !runtime.recordingSid) {
    await getDb().collection("live_sessions").doc(sessionId).set({
      archiveStatus: "failed",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});
    return;
  }

  if (!recordingConfig.enabled) {
    await getDb().collection("live_sessions").doc(sessionId).set({
      archiveStatus: "unavailable",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});
    return;
  }

  const {appId} = getAgoraAppConfig();
  if (!appId || !recordingConfig.customerId || !recordingConfig.customerSecret) {
    await getDb().collection("live_sessions").doc(sessionId).set({
      archiveStatus: "failed",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});
    return;
  }

  const channelName = String(runtime.channelName || session.agoraChannelName || session.channelName || "").trim();
  const recordingUid = Number(runtime.recordingUid || stableRecordingUid(sessionId));
  const authHeader = agoraRecordingAuthHeader();
  let stopPayload: unknown = null;
  try {
    const stopResponse = await axios.post(
      `${AGORA_RECORDING_API_BASE}/v1/apps/${appId}/cloud_recording/resourceid/${runtime.resourceId}/sid/${runtime.recordingSid}/mode/mix/stop`,
      {
        cname: channelName,
        uid: String(recordingUid),
        clientRequest: {
          async_stop: false,
        },
      },
      {headers: {Authorization: authHeader, "Content-Type": "application/json"}}
    );
    stopPayload = stopResponse.data;
  } catch (error) {
    functions.logger.error("Failed to stop Agora archive recording.", {sessionId, error});
    await getDb().collection("live_sessions").doc(sessionId).set({
      archiveStatus: "failed",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, {merge: true});
    return;
  }

  const objectPrefix = String(runtime.objectPrefix || recordingConfig.fileNamePrefix.join("/"));
  const primaryFile = extractPrimaryArchiveFile(stopPayload, objectPrefix);
  const playbackBase = recordingConfig.playbackBaseUrl || "";
  const privatePlaybackUrl = primaryFile && playbackBase ?
    `${playbackBase.replace(/\/$/, "")}/${primaryFile.replace(/^\/+/, "")}` :
    null;

  await runtimeRef.set({
    primaryFile: primaryFile || null,
    playbackUrl: privatePlaybackUrl,
    stopPayload,
    finalizedAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, {merge: true});

  await getDb().collection("live_sessions").doc(sessionId).set({
    archiveStatus: primaryFile ? "ready" : "failed",
    archivePrimaryFile: primaryFile || null,
    archiveObjectPrefix: objectPrefix,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    endTime: session.endTime || admin.firestore.FieldValue.serverTimestamp(),
    endedAt: session.endedAt || admin.firestore.FieldValue.serverTimestamp(),
  }, {merge: true});
};

export const createLiveShareAccessLink = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const sessionId = String(data?.sessionId || "").trim();
    if (!sessionId) {
      throw new functions.https.HttpsError("invalid-argument", "sessionId is required.");
    }
    const sessionSnap = await getDb().collection("live_sessions").doc(sessionId).get();
    if (!sessionSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Live session not found.");
    }
    const session = sessionSnap.data() || {};
    const hostId = String(session.hostId || session.hostUid || "").trim();
    if (!hostId) {
      throw new functions.https.HttpsError("failed-precondition", "Live session is missing host metadata.");
    }
    if (context.auth.uid !== hostId) {
      throw new functions.https.HttpsError("permission-denied", "Only the host can create live share links.");
    }
    if (!isLiveStatus(session.status)) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Live share links are only available while the session is live."
      );
    }

    const accessConfig = getLiveAccessConfig();
    if (!accessConfig.shareSecret) {
      throw new functions.https.HttpsError("failed-precondition", "Live share access secret is not configured.");
    }
    const ttlSeconds = accessConfig.shareAccessTtlSeconds;
    const exp = Math.floor(Date.now() / 1000) + ttlSeconds;
    const token = signJwt({
      sid: sessionId,
      hostId,
      purpose: "live",
      exp,
    }, accessConfig.shareSecret);
    const url = buildShareUrl(sessionId, hostId, token);
    return {
      url,
      shareUrl: url,
      accessUrl: url,
      token,
      expiresAt: exp,
      expiresInSeconds: ttlSeconds,
    };
  });

export const createLiveReplayAccessLink = functions.runWith({enforceAppCheck: true})
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError("unauthenticated", "You must be logged in.");
    }
    const sessionId = String(data?.sessionId || "").trim();
    if (!sessionId) {
      throw new functions.https.HttpsError("invalid-argument", "sessionId is required.");
    }
    const sessionSnap = await getDb().collection("live_sessions").doc(sessionId).get();
    if (!sessionSnap.exists) {
      throw new functions.https.HttpsError("not-found", "Live session not found.");
    }
    const session = sessionSnap.data() || {};
    const hostId = String(session.hostId || session.hostUid || "").trim();
    if (!hostId) {
      throw new functions.https.HttpsError("failed-precondition", "Live session is missing host metadata.");
    }
    if (String(session.archiveStatus || "").trim().toLowerCase() !== "ready") {
      throw new functions.https.HttpsError("failed-precondition", "Replay is not ready yet.");
    }

    const runtimeSnap = await runtimeArchiveRef(sessionId).get();
    const runtime = runtimeSnap.data() || {};
    const primaryFile = String(runtime.primaryFile || session.archivePrimaryFile || "").trim();
    if (!primaryFile) {
      throw new functions.https.HttpsError("failed-precondition", "Replay file is not available.");
    }

    const accessConfig = getLiveAccessConfig();
    if (!accessConfig.replaySecret) {
      throw new functions.https.HttpsError("failed-precondition", "Replay access secret is not configured.");
    }

    const requestedPurpose = String(data?.purpose || "playback").trim().toLowerCase();
    const shareAccessToken = String(data?.shareAccessToken || "").trim() || undefined;
    const ttlSeconds = requestedPurpose === "share" ?
      accessConfig.replayShareTtlSeconds :
      accessConfig.replayAccessTtlSeconds;
    const exp = Math.floor(Date.now() / 1000) + ttlSeconds;
    const token = signJwt({
      sid: sessionId,
      hostId,
      purpose: "replay",
      file: primaryFile,
      exp,
    }, accessConfig.replaySecret);

    const allowed = await canUserAccessReplay(
      sessionId,
      session,
      context.auth.uid,
      shareAccessToken
    );
    if (!allowed) {
      throw new functions.https.HttpsError("permission-denied", "You do not have access to this replay.");
    }

    const playbackUrl = buildReplayPlaybackUrl(sessionId, primaryFile, token);
    return {
      url: playbackUrl,
      playbackUrl,
      accessUrl: playbackUrl,
      token,
      expiresAt: exp,
      expiresInSeconds: ttlSeconds,
    };
  });

export const resolveLiveShareAccess = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  if (req.method === "OPTIONS") {
    res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");
    res.status(204).send("");
    return;
  }
  if (req.method !== "GET" && req.method !== "POST") {
    res.status(405).json({error: "Method not allowed"});
    return;
  }

  const token = String(
    req.query.token ||
      req.query.accessToken ||
      ((req.body as Record<string, unknown> | undefined)?.token as string | undefined) ||
      ""
  ).trim();
  if (!token) {
    res.status(400).json({valid: false, error: "token is required"});
    return;
  }

  const liveClaims = parseLiveAccessClaims(token, "live");
  const replayClaims = parseLiveAccessClaims(token, "replay");
  const claims = liveClaims || replayClaims;
  if (!claims) {
    res.status(401).json({valid: false, error: "Invalid or expired token"});
    return;
  }

  const sessionSnap = await getDb().collection("live_sessions").doc(claims.sid).get();
  if (!sessionSnap.exists) {
    res.status(404).json({valid: false, error: "Session not found"});
    return;
  }
  const session = sessionSnap.data() || {};
  const hostId = String(session.hostId || session.hostUid || "").trim();
  if (hostId && hostId !== claims.hostId) {
    res.status(401).json({valid: false, error: "Token host mismatch"});
    return;
  }

  res.status(200).json({
    valid: true,
    sessionId: claims.sid,
    hostId: claims.hostId,
    purpose: claims.purpose,
    accessType: claims.purpose,
    title: session.title || "Live Session",
    status: session.status || "live",
    archiveStatus: session.archiveStatus || null,
    replayReady: String(session.archiveStatus || "").trim().toLowerCase() === "ready",
    sharePath: session.sharePath || null,
    shareUrl: session.shareUrl || null,
  });
});

export const onLiveSessionCreatedStartArchive = functions.firestore
  .document("live_sessions/{sessionId}")
  .onCreate(async (snap, context) => {
    const session = snap.data();
    if (!session || !isLiveStatus(session.status)) {
      return undefined;
    }
    try {
      await startLiveArchiveRecording(context.params.sessionId as string, session);
    } catch (error) {
      functions.logger.error("Failed to start live archive recording.", {
        sessionId: context.params.sessionId,
        error,
      });
      await getDb().collection("live_sessions").doc(context.params.sessionId as string).set({
        archiveStatus: "failed",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, {merge: true});
    }
    return undefined;
  });

export const onLiveSessionEndedFinalizeArchive = functions.firestore
  .document("live_sessions/{sessionId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();
    if (!before || !after) return undefined;
    const wasLive = isLiveStatus(before.status);
    const nowEnded = isEndedStatus(after.status);
    if (!wasLive || !nowEnded) return undefined;
    try {
      await finalizeLiveArchiveRecording(context.params.sessionId as string, after);
    } catch (error) {
      functions.logger.error("Failed to finalize live archive recording.", {
        sessionId: context.params.sessionId,
        error,
      });
      await change.after.ref.set({
        archiveStatus: "failed",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, {merge: true});
    }
    return undefined;
  });

export const reconcileStaleLiveSessions = functions.pubsub
  .schedule("every 15 minutes")
  .onRun(async () => {
    const staleThresholdMs = 30 * 60 * 1000;
    const cutoff = admin.firestore.Timestamp.fromDate(new Date(Date.now() - staleThresholdMs));
    const staleSnap = await getDb().collection("live_sessions")
      .where("status", "in", ["live", "active", "LIVE", "ACTIVE"])
      .where("updatedAt", "<", cutoff)
      .limit(25)
      .get();

    for (const doc of staleSnap.docs) {
      const data = doc.data();
      const hostId = String(data.hostId || data.hostUid || "").trim();
      let shouldEnd = true;
      if (hostId) {
        const viewerSnap = await doc.ref.collection("viewers").doc(hostId).get();
        const hostSeen = viewerSnap.get("lastSeenAt") as admin.firestore.Timestamp | undefined;
        if (hostSeen && hostSeen.toMillis() > Date.now() - staleThresholdMs) {
          shouldEnd = false;
        }
      }
      if (!shouldEnd) continue;

      await doc.ref.set({
        status: "ended",
        endedAt: admin.firestore.FieldValue.serverTimestamp(),
        endTime: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        endedReason: "stale_reconcile",
      }, {merge: true});
    }
    return null;
  });
