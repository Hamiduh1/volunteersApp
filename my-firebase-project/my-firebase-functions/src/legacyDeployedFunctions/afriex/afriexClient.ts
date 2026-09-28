// @ts-nocheck
import axios, {AxiosError} from "axios";
import {createPublicKey, createVerify, KeyObject, verify as cryptoVerify} from "crypto";
import {defineString} from "firebase-functions/params";

// Declare the production routing values so Firebase deploys the intended
// Afriex environment rather than relying on an inherited function revision.
const afriexEnvironmentParam = defineString("AFRIEX_ENV", {default: "sandbox"});
const afriexLiveApiBaseUrlParam = defineString("AFRIEX_LIVE_API_BASE_URL", {default: ""});
const afriexLiveApiKeyParam = defineString("AFRIEX_LIVE_API_KEY", {default: ""});
const afriexLiveWebhookPublicKeyParam = defineString("AFRIEX_LIVE_WEBHOOK_PUBLIC_KEY", {default: ""});

export type AfriexLiveMode = "transaction_only" | "hosted_wallet";
export type AfriexEnvironment = "sandbox" | "live";

export type AfriexInstitutionSummary = {
  institutionCode: string | null;
  institutionName: string | null;
};

export type AfriexHttpProbe = {
  ok: boolean;
  statusCode: number | null;
  method: string;
  path: string;
  errorCode: string | null;
  friendlyMessage: string | null;
  durationMs: number | null;
};

export type AfriexLiveModeDiagnostics = {
  product: {
    liveMode: AfriexLiveMode;
    endCustomerCustody: boolean;
    description: string;
  };
  config: {
    apiBaseUrl: string;
    isSandbox: boolean;
    apiKeyConfigured: boolean;
    apiVersion: string | null;
    webhookPublicKeyConfigured: boolean;
    authHeader: string;
    timeoutMs: number;
    inScopeCountryCode: string;
    inScopeChannel: string;
    inScopeCurrency: string;
  };
  probes: {
    authentication: AfriexHttpProbe;
    businessBalance: AfriexHttpProbe & {balances?: Array<{currency: string; amount: unknown}>};
    ugMobileMoneyInstitutions: AfriexHttpProbe & {
      institutionCount: number;
      institutions: AfriexInstitutionSummary[];
    };
  };
  phase0: {
    ready: boolean;
    blockers: string[];
    hints: string[];
  };
  checkedAtMs: number;
};

const firstNonEmpty = (...values: Array<string | undefined | null>): string => {
  for (const value of values) {
    const trimmed = String(value || "").trim();
    if (trimmed) return trimmed;
  }
  return "";
};

const unwrapAfriexData = (raw: unknown): Record<string, unknown> => {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  const data = raw as Record<string, unknown>;
  if (data.data && typeof data.data === "object" && !Array.isArray(data.data)) {
    return data.data as Record<string, unknown>;
  }
  return data;
};

const institutionArrayKeys = [
  "institutions",
  "items",
  "results",
  "records",
  "banks",
  "providers",
  "paymentMethods",
  "payment_methods",
  "paymentMethodInstitutions",
  "payment_method_institutions",
];

const filterObjectRows = (value: unknown): Record<string, unknown>[] => {
  if (!Array.isArray(value)) return [];
  return value.filter(
    (entry) => entry && typeof entry === "object" && !Array.isArray(entry)
  ) as Record<string, unknown>[];
};

const findInstitutionRows = (record: Record<string, unknown>): Record<string, unknown>[] => {
  for (const key of institutionArrayKeys) {
    const rows = filterObjectRows(record[key]);
    if (rows.length > 0) return rows;
  }
  return [];
};

const extractInstitutionRows = (raw: unknown): Record<string, unknown>[] => {
  const directRows = filterObjectRows(raw);
  if (directRows.length > 0) return directRows;

  if (!raw || typeof raw !== "object") return [];
  const data = raw as Record<string, unknown>;

  const topLevelRows = findInstitutionRows(data);
  if (topLevelRows.length > 0) return topLevelRows;

  for (const key of ["data", "result", "response", "payload"]) {
    const nested = data[key];
    const nestedRows = filterObjectRows(nested);
    if (nestedRows.length > 0) return nestedRows;

    if (nested && typeof nested === "object" && !Array.isArray(nested)) {
      const rows = findInstitutionRows(nested as Record<string, unknown>);
      if (rows.length > 0) return rows;
    }
  }
  return [];
};

export const resolveAfriexLiveMode = (): AfriexLiveMode => {
  const raw = firstNonEmpty(
    process.env.AFRIEX_LIVE_MODE,
    process.env.AFRIEX_PRODUCT_MODE
  ).toLowerCase();
  if (raw === "hosted_wallet" || raw === "hosted" || raw === "wallet") {
    return "hosted_wallet";
  }
  return "transaction_only";
};

export const resolveAfriexEnvironment = (): AfriexEnvironment => {
  const explicit = firstNonEmpty(
    process.env.AFRIEX_ENV,
    afriexEnvironmentParam.value()
  ).toLowerCase();
  if (["live", "production", "prod"].includes(explicit)) return "live";
  if (["sandbox", "test", "testing"].includes(explicit)) return "sandbox";

  // Preserve the legacy URL-only configuration when no explicit environment exists.
  const configuredUrl = firstNonEmpty(
    process.env.AFRIEX_API_BASE_URL,
    process.env.AFRIEX_PROVIDER_URL,
    process.env.MOBILE_MONEY_PROVIDER_API_BASE_URL,
    process.env.MOBILE_MONEY_PROVIDER_URL
  ).toLowerCase();
  return configuredUrl.includes("sandbox.") ? "sandbox" : "live";
};

export const resolveAfriexApiBaseUrl = (): string => {
  const environment = resolveAfriexEnvironment();
  const configured = environment === "live" ?
    firstNonEmpty(
      process.env.AFRIEX_LIVE_API_BASE_URL,
      afriexLiveApiBaseUrlParam.value(),
      process.env.MOBILE_MONEY_PROVIDER_LIVE_API_BASE_URL
    ) :
    firstNonEmpty(
      process.env.AFRIEX_SANDBOX_API_BASE_URL,
      process.env.MOBILE_MONEY_PROVIDER_SANDBOX_API_BASE_URL,
      process.env.AFRIEX_API_BASE_URL,
      process.env.AFRIEX_PROVIDER_URL,
      process.env.MOBILE_MONEY_PROVIDER_API_BASE_URL,
      process.env.MOBILE_MONEY_PROVIDER_URL
    );
  if (!configured) return "";
  try {
    const url = new URL(configured);
    if (/\/api\/v1(?:\/.*)?$/i.test(url.pathname)) {
      url.pathname = url.pathname.replace(/\/api\/v1(?:\/.*)?$/i, "/api/v1");
    } else if (!/\/api\/v1$/i.test(url.pathname)) {
      url.pathname = `${url.pathname.replace(/\/+$/, "")}/api/v1`;
    }
    url.search = "";
    url.hash = "";
    return url.toString().replace(/\/+$/, "");
  } catch {
    const trimmed = configured.replace(/\/+$/, "");
    const match = trimmed.match(/^(.*\/api\/v1)(?:\/.*)?$/i);
    return match ? match[1] : `${trimmed}/api/v1`;
  }
};

export const buildAfriexApiUrl = (apiBaseUrl: string, apiPath: string): string => {
  const normalizedPath = apiPath.startsWith("/") ? apiPath : `/${apiPath}`;
  return `${apiBaseUrl.replace(/\/+$/, "")}${normalizedPath}`;
};

export type AfriexRuntimeConfig = {
  environment: AfriexEnvironment;
  apiBaseUrl: string;
  apiKey: string;
  apiVersion: string;
  authHeader: string;
  keyPrefix: string | undefined;
  timeoutMs: number;
  isSandbox: boolean;
  liveMode: AfriexLiveMode;
  webhookPublicKey: string;
  inScopeCountryCode: string;
  inScopeChannel: string;
  inScopeCurrency: string;
  headers: Record<string, string>;
};

export const resolveAfriexRuntimeConfig = (): AfriexRuntimeConfig => {
  const environment = resolveAfriexEnvironment();
  const apiBaseUrl = resolveAfriexApiBaseUrl();
  const apiKey = environment === "live" ?
    firstNonEmpty(
      process.env.AFRIEX_LIVE_API_KEY,
      afriexLiveApiKeyParam.value(),
      process.env.MOBILE_MONEY_PROVIDER_LIVE_API_KEY
    ) :
    firstNonEmpty(
      process.env.AFRIEX_SANDBOX_API_KEY,
      process.env.MOBILE_MONEY_PROVIDER_SANDBOX_API_KEY,
      process.env.AFRIEX_API_KEY,
      process.env.MOBILE_MONEY_PROVIDER_API_KEY
    );
  const apiVersion = firstNonEmpty(process.env.AFRIEX_API_VERSION);
  const authHeader = environment === "live" ?
    firstNonEmpty(
      process.env.AFRIEX_LIVE_AUTH_HEADER,
      process.env.MOBILE_MONEY_PROVIDER_LIVE_AUTH_HEADER,
      "x-api-key"
    ) :
    firstNonEmpty(
      process.env.AFRIEX_SANDBOX_AUTH_HEADER,
      process.env.MOBILE_MONEY_PROVIDER_SANDBOX_AUTH_HEADER,
      process.env.AFRIEX_AUTH_HEADER,
      process.env.MOBILE_MONEY_PROVIDER_AUTH_HEADER,
      "x-api-key"
    );
  const keyPrefix = environment === "live" ?
    process.env.AFRIEX_LIVE_API_KEY_PREFIX ??
      process.env.MOBILE_MONEY_PROVIDER_LIVE_API_KEY_PREFIX :
    process.env.AFRIEX_SANDBOX_API_KEY_PREFIX ??
      process.env.MOBILE_MONEY_PROVIDER_SANDBOX_API_KEY_PREFIX ??
      process.env.AFRIEX_API_KEY_PREFIX ??
      process.env.MOBILE_MONEY_PROVIDER_API_KEY_PREFIX;
  const timeoutRaw = Number(
    process.env.AFRIEX_TIMEOUT_MS ||
    process.env.MOBILE_MONEY_PROVIDER_TIMEOUT_MS ||
    30000
  );
  const timeoutMs = Number.isFinite(timeoutRaw) && timeoutRaw > 0 ? timeoutRaw : 30000;
  const resolvedApiKey = keyPrefix === undefined ?
    apiKey :
    (keyPrefix === "" ? apiKey : `${keyPrefix} ${apiKey}`);

  const lowerBase = apiBaseUrl.toLowerCase();
  const isSandbox = environment === "sandbox";

  if (environment === "live" && lowerBase.includes("sandbox.")) {
    throw new Error("Afriex live mode cannot use a sandbox API URL.");
  }

  return {
    environment,
    apiBaseUrl,
    apiKey,
    apiVersion,
    authHeader,
    keyPrefix,
    timeoutMs,
    isSandbox,
    liveMode: resolveAfriexLiveMode(),
    webhookPublicKey: environment === "live" ?
      firstNonEmpty(
        process.env.AFRIEX_LIVE_WEBHOOK_PUBLIC_KEY,
        afriexLiveWebhookPublicKeyParam.value(),
        process.env.MOBILE_MONEY_PROVIDER_LIVE_WEBHOOK_PUBLIC_KEY
      ) :
      firstNonEmpty(
        process.env.AFRIEX_SANDBOX_WEBHOOK_PUBLIC_KEY,
        process.env.MOBILE_MONEY_PROVIDER_SANDBOX_WEBHOOK_PUBLIC_KEY,
        process.env.AFRIEX_WEBHOOK_PUBLIC_KEY,
        process.env.MOBILE_MONEY_PROVIDER_WEBHOOK_PUBLIC_KEY
      ),
    inScopeCountryCode: firstNonEmpty(
      process.env.AFRIEX_IN_SCOPE_COUNTRY,
      process.env.MOBILE_MONEY_AFRIEX_ACCESS_PROBE_COUNTRY
    ) || "UG",
    inScopeChannel: firstNonEmpty(process.env.AFRIEX_IN_SCOPE_CHANNEL) || "MOBILE_MONEY",
    inScopeCurrency: firstNonEmpty(process.env.AFRIEX_IN_SCOPE_CURRENCY) || "UGX",
    headers: {
      "Content-Type": "application/json",
      [authHeader]: resolvedApiKey,
      ...(apiVersion ? {"x-api-version": apiVersion} : {}),
    },
  };
};

const mapAxiosProbeError = (error: unknown): Pick<AfriexHttpProbe, "ok" | "statusCode" | "errorCode" | "friendlyMessage"> => {
  if (!axios.isAxiosError(error)) {
    return {
      ok: false,
      statusCode: null,
      errorCode: "UNKNOWN_ERROR",
      friendlyMessage: error instanceof Error ? error.message : "Unknown Afriex request error.",
    };
  }
  const axiosError = error as AxiosError;
  const statusCode = axiosError.response?.status ?? null;
  const body = unwrapAfriexData(axiosError.response?.data);
  return {
    ok: false,
    statusCode,
    errorCode: firstNonEmpty(
      body.code as string | undefined,
      body.errorCode as string | undefined,
      body.error as string | undefined
    ) || (statusCode === 401 ? "UNAUTHORIZED" : "HTTP_ERROR"),
    friendlyMessage: firstNonEmpty(
      body.friendlyMessage as string | undefined,
      body.details && typeof body.details === "object" ?
        firstNonEmpty(
          (body.details as Record<string, unknown>).friendlyMessage as string | undefined,
          (body.details as Record<string, unknown>).errorMessage as string | undefined
        ) :
        undefined,
      body.message as string | undefined,
      body.error as string | undefined,
      axiosError.message
    ) || "Afriex request failed.",
  };
};

export const probeAfriexAuthentication = async (
  runtime: AfriexRuntimeConfig
): Promise<AfriexHttpProbe> => {
  const path = "/customer";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const startedAt = Date.now();
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: {page: 0, limit: 1},
      validateStatus: () => true,
    });
    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: response.status === 401 ? "UNAUTHORIZED" : null,
      friendlyMessage: response.status === 401 ?
        "Afriex rejected the API key (401)." :
        (response.status >= 200 && response.status < 300 ?
          "Afriex authentication probe succeeded." :
          `Afriex authentication probe returned HTTP ${response.status}.`),
      durationMs: Date.now() - startedAt,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

const extractAfriexBalanceRows = (raw: unknown): Record<string, unknown>[] => {
  if (Array.isArray(raw)) {
    return raw.filter((entry) => entry && typeof entry === "object" && !Array.isArray(entry)) as Record<string, unknown>[];
  }
  if (!raw || typeof raw !== "object") return [];

  const data = raw as Record<string, unknown>;
  const nested = data.data;
  const candidates = [
    data.balances,
    data.balance,
    data.items,
    data.results,
    nested && typeof nested === "object" && !Array.isArray(nested) ?
      (nested as Record<string, unknown>).balances :
      undefined,
    nested && typeof nested === "object" && !Array.isArray(nested) ?
      (nested as Record<string, unknown>).balance :
      undefined,
  ];

  for (const candidate of candidates) {
    if (Array.isArray(candidate)) {
      const rows = candidate.filter(
        (entry) => entry && typeof entry === "object" && !Array.isArray(entry)
      ) as Record<string, unknown>[];
      if (rows.length > 0) return rows;
    }
    if (candidate && typeof candidate === "object" && !Array.isArray(candidate)) {
      const objectRows = Object.entries(candidate as Record<string, unknown>).map(([currency, amount]) => ({
        currency,
        amount,
        available: amount,
      }));
      if (objectRows.length > 0) return objectRows;
    }
  }

  const currencyKeyed = Object.entries(data).filter(([key, value]) => {
    if (["data", "status", "message", "code", "friendlyMessage"].includes(key)) return false;
    return typeof value === "number" || typeof value === "string";
  }).map(([currency, amount]) => ({
    currency,
    amount,
    available: amount,
  }));
  return currencyKeyed;
};

const formatAfriexResponseError = (statusCode: number, raw: unknown, fallback: string): string => {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return `${fallback} (HTTP ${statusCode}).`;
  }
  const body = raw as Record<string, unknown>;
  const details = body.details && typeof body.details === "object" && !Array.isArray(body.details) ?
    (body.details as Record<string, unknown>) :
    undefined;
  return firstNonEmpty(
    details?.friendlyMessage as string | undefined,
    details?.errorMessage as string | undefined,
    body.friendlyMessage as string | undefined,
    body.error as string | undefined,
    body.message as string | undefined
  ) || `${fallback} (HTTP ${statusCode}).`;
};

export const fetchAfriexBusinessBalance = async (
  runtime: AfriexRuntimeConfig,
  currencies: string[] = ["USD", "UGX"]
): Promise<AfriexHttpProbe & {balances?: Array<{currency: string; amount: unknown}>}> => {
  const path = "/org/balance";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const startedAt = Date.now();
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: {currencies: currencies.join(",")},
      validateStatus: () => true,
    });
    const responseBody = response.data;
    const body = unwrapAfriexData(responseBody);
    const balanceRows = extractAfriexBalanceRows(body.balances ?? body);
    const balances = balanceRows.map((row) => ({
      currency: firstNonEmpty(row.currency as string, row.code as string) || "unknown",
      amount: row.amount ?? row.available ?? row.balance ?? row.availableBalance ?? null,
    })).filter((entry) => entry.currency !== "unknown");

    const dedupedBalances: Array<{currency: string; amount: unknown}> = [];
    const seen = new Set<string>();
    for (const entry of balances) {
      const key = entry.currency.toUpperCase();
      if (seen.has(key)) continue;
      seen.add(key);
      dedupedBalances.push(entry);
    }

    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: response.status >= 400 ?
        firstNonEmpty(
          (responseBody as Record<string, unknown>)?.code as string | undefined
        ) || "BALANCE_READ_FAILED" :
        null,
      friendlyMessage: response.status >= 200 && response.status < 300 ?
        `Business wallet balance read succeeded (${dedupedBalances.length} currency row(s)).` :
        formatAfriexResponseError(response.status, responseBody, "Business wallet balance read failed"),
      durationMs: Date.now() - startedAt,
      balances: dedupedBalances.length > 0 ? dedupedBalances : undefined,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

export const listAfriexInstitutions = async (
  runtime: AfriexRuntimeConfig,
  params: {channel: string; countryCode: string}
): Promise<AfriexHttpProbe & {institutionCount: number; institutions: AfriexInstitutionSummary[]}> => {
  const path = "/payment-method/institution";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const requestParams = {
    channel: params.channel,
    countryCode: params.countryCode,
  };
  const startedAt = Date.now();
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: requestParams,
      validateStatus: () => true,
    });
    const rows = extractInstitutionRows(response.data);
    const institutions = rows.map((row) => {
      const institution = row.institution as Record<string, unknown> | undefined;
      const bank = row.bank as Record<string, unknown> | undefined;
      return {
        institutionCode: firstNonEmpty(
          row.institutionCode as string,
          row.code as string,
          row.providerCode as string,
          row.networkCode as string,
          row.bankCode as string,
          row.sortCode as string,
          row.routingCode as string,
          row.bic as string,
          row.swiftCode as string,
          row.id as string,
          row.uuid as string,
          institution?.institutionCode as string,
          institution?.code as string,
          institution?.providerCode as string,
          institution?.bankCode as string,
          bank?.institutionCode as string,
          bank?.code as string,
          bank?.bankCode as string,
          bank?.sortCode as string
        ) || null,
        institutionName: firstNonEmpty(
          row.institutionName as string,
          row.name as string,
          row.provider as string,
          row.bankName as string,
          row.displayName as string,
          row.label as string,
          row.description as string,
          institution?.institutionName as string,
          institution?.name as string,
          institution?.bankName as string,
          institution?.displayName as string,
          bank?.institutionName as string,
          bank?.name as string,
          bank?.bankName as string,
          bank?.displayName as string
        ) || null,
      };
    }).filter((entry) => entry.institutionCode || entry.institutionName);

    const ok = response.status >= 200 && response.status < 300;
    let friendlyMessage: string | null = ok ?
      `Listed ${institutions.length} institution(s) for ${params.countryCode} ${params.channel}.` :
      `Institution list returned HTTP ${response.status}.`;
    if (ok && institutions.length === 0) {
      friendlyMessage =
        `No supported institutions were returned for ${params.countryCode} ${params.channel}. ` +
        "If this is sandbox, confirm the bank institution list is enabled for this account.";
    }
    if (!ok) {
      const body = unwrapAfriexData(response.data);
      friendlyMessage = firstNonEmpty(
        body.friendlyMessage as string | undefined,
        body.message as string | undefined,
        body.error as string | undefined,
        body.details && typeof body.details === "object" ?
          firstNonEmpty(
            (body.details as Record<string, unknown>).friendlyMessage as string | undefined,
            (body.details as Record<string, unknown>).message as string | undefined,
            (body.details as Record<string, unknown>).errorMessage as string | undefined
          ) :
          undefined,
        typeof response.data === "string" ? response.data : undefined,
        friendlyMessage
      );
    }

    return {
      ok,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: ok ? null : "INSTITUTION_LIST_FAILED",
      friendlyMessage,
      durationMs: Date.now() - startedAt,
      institutionCount: institutions.length,
      institutions: institutions.slice(0, 20),
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      institutionCount: 0,
      institutions: [],
      ...mapped,
    };
  }
};

export const topupAfriexSandboxBusinessWallet = async (
  runtime: AfriexRuntimeConfig,
  params: {amount: number; currency: string}
): Promise<AfriexHttpProbe> => {
  if (!runtime.isSandbox) {
    return {
      ok: false,
      statusCode: null,
      method: "POST",
      path: "/org/balance/topup",
      errorCode: "PRODUCTION_BLOCKED",
      friendlyMessage: "Sandbox top-up is blocked outside sandbox Afriex environments.",
      durationMs: 0,
    };
  }

  const amount = Number(params.amount);
  const currency = String(params.currency || "").trim().toUpperCase();
  if (!Number.isFinite(amount) || amount <= 0 || !/^[A-Z]{3}$/.test(currency)) {
    return {
      ok: false,
      statusCode: null,
      method: "POST",
      path: "/org/balance/topup",
      errorCode: "INVALID_TOPUP_INPUT",
      friendlyMessage: "Sandbox top-up requires a positive amount and a three-letter ISO currency code.",
      durationMs: 0,
    };
  }

  const path = "/org/balance/topup";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const startedAt = Date.now();
  try {
    const response = await axios.post(
      url,
      {amount, currency},
      {
        headers: runtime.headers,
        timeout: runtime.timeoutMs,
        validateStatus: () => true,
      }
    );
    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "POST",
      path,
      errorCode: response.status === 403 ? "FORBIDDEN" : (response.status >= 400 ? "TOPUP_FAILED" : null),
      friendlyMessage: response.status >= 200 && response.status < 300 ?
        `Sandbox business wallet topped up with ${amount} ${currency}.` :
        formatAfriexResponseError(response.status, response.data, "Sandbox top-up failed"),
      durationMs: Date.now() - startedAt,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "POST",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

export type AfriexBusinessWalletBalanceEntry = {
  currency: string;
  amount: number | null;
  rawAmount: unknown;
};

export type AfriexBusinessWalletMirrorRecord = {
  provider: "afriex";
  liveMode: AfriexLiveMode;
  endCustomerCustody: boolean;
  isSandbox: boolean;
  apiBaseUrl: string;
  inScopeCountryCode: string;
  inScopeChannel: string;
  inScopeCurrency: string;
  balances: AfriexBusinessWalletBalanceEntry[];
  lastSyncedAtMs: number;
  lastSyncedByUid: string | null;
  lastSyncStatus: "ok" | "error";
  lastSyncMessage: string | null;
  lastTopUpAtMs: number | null;
  lastTopUpAmount: number | null;
  lastTopUpCurrency: string | null;
};

export const AFRIEX_BUSINESS_WALLET_MIRROR_DOC_ID = "afriex_business_wallet";

const parseBalanceAmount = (value: unknown): number | null => {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
};

export const normalizeAfriexBusinessWalletBalances = (
  balanceProbe: AfriexHttpProbe & {balances?: Array<{currency: string; amount: unknown}>}
): AfriexBusinessWalletBalanceEntry[] => {
  const rows = balanceProbe.balances || [];
  return rows.map((row) => ({
    currency: row.currency,
    amount: parseBalanceAmount(row.amount),
    rawAmount: row.amount ?? null,
  }));
};

export const buildAfriexBusinessWalletMirrorRecord = (params: {
  runtime: AfriexRuntimeConfig;
  balanceProbe: AfriexHttpProbe & {balances?: Array<{currency: string; amount: unknown}>};
  syncedByUid?: string | null;
  topupMeta?: {amount: number; currency: string; atMs: number} | null;
  previous?: Partial<AfriexBusinessWalletMirrorRecord> | null;
}): AfriexBusinessWalletMirrorRecord => {
  const nowMs = Date.now();
  const balances = normalizeAfriexBusinessWalletBalances(params.balanceProbe);
  const previous = params.previous || null;
  return {
    provider: "afriex",
    liveMode: params.runtime.liveMode,
    endCustomerCustody: params.runtime.liveMode === "hosted_wallet",
    isSandbox: params.runtime.isSandbox,
    apiBaseUrl: params.runtime.apiBaseUrl,
    inScopeCountryCode: params.runtime.inScopeCountryCode,
    inScopeChannel: params.runtime.inScopeChannel,
    inScopeCurrency: params.runtime.inScopeCurrency,
    balances,
    lastSyncedAtMs: nowMs,
    lastSyncedByUid: params.syncedByUid || null,
    lastSyncStatus: params.balanceProbe.ok ? "ok" : "error",
    lastSyncMessage: params.balanceProbe.friendlyMessage,
    lastTopUpAtMs: params.topupMeta?.atMs ?? previous?.lastTopUpAtMs ?? null,
    lastTopUpAmount: params.topupMeta?.amount ?? previous?.lastTopUpAmount ?? null,
    lastTopUpCurrency: params.topupMeta?.currency ?? previous?.lastTopUpCurrency ?? null,
  };
};

export const syncAfriexBusinessWalletMirrorState = async (params: {
  runtime?: AfriexRuntimeConfig;
  syncedByUid?: string | null;
  topupMeta?: {amount: number; currency: string; atMs: number} | null;
  previous?: Partial<AfriexBusinessWalletMirrorRecord> | null;
}): Promise<{
  mirror: AfriexBusinessWalletMirrorRecord;
  balanceProbe: AfriexHttpProbe & {balances?: Array<{currency: string; amount: unknown}>};
}> => {
  const runtime = params.runtime || resolveAfriexRuntimeConfig();
  const balanceProbe = await fetchAfriexBusinessBalance(runtime, ["USD", runtime.inScopeCurrency]);
  const mirror = buildAfriexBusinessWalletMirrorRecord({
    runtime,
    balanceProbe,
    syncedByUid: params.syncedByUid,
    topupMeta: params.topupMeta,
    previous: params.previous,
  });
  return {mirror, balanceProbe};
};

export type AfriexInstitutionCodeResolution = {
  bankName: string | null;
  countryCode: string;
  codeType: "swift_code" | "routing_number";
  searchTerm: string;
};

export const resolveAfriexInstitutionCode = async (
  runtime: AfriexRuntimeConfig,
  params: {
    searchTerm: string;
    countryCode: string;
    codeType: "swift_code" | "routing_number";
  }
): Promise<AfriexHttpProbe & {resolved?: AfriexInstitutionCodeResolution}> => {
  const path = "/payment-method/institution/codes";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const requestParams: Record<string, string> = {
    searchTerm: params.searchTerm,
    country: params.countryCode,
    codeType: params.codeType,
  };
  const startedAt = Date.now();

  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: requestParams,
      validateStatus: () => true,
    });
    const body = unwrapAfriexData(response.data);
    const resolved: AfriexInstitutionCodeResolution = {
      bankName: firstNonEmpty(
        body.bankName as string,
        body.institutionName as string,
        body.name as string
      ) || null,
      countryCode: params.countryCode,
      codeType: params.codeType,
      searchTerm: params.searchTerm,
    };
    const ok = response.status >= 200 && response.status < 300;
    return {
      ok,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: ok ?
        null :
        firstNonEmpty((response.data as Record<string, unknown>)?.code as string | undefined) ||
          "INSTITUTION_CODE_RESOLVE_FAILED",
      friendlyMessage: ok ?
        (resolved.bankName ?
          `Resolved bank: ${resolved.bankName}.` :
          "No bank was found for that code.") :
        formatAfriexResponseError(response.status, response.data, "Institution code resolve failed"),
      durationMs: Date.now() - startedAt,
      resolved: ok ? resolved : undefined,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      ...mapped,
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      friendlyMessage: mapped.friendlyMessage || "Institution code resolve failed.",
    };
  }
};

export type AfriexResolvedAccount = {
  recipientName: string | null;
  institutionName: string | null;
  institutionCode: string | null;
  channel: string;
  countryCode: string;
  accountNumber: string | null;
};

export type AfriexRatesResult = {
  rates: Record<string, Record<string, string>>;
  updatedAtMs: number | null;
  fromSymbols: string;
  toSymbols: string;
};

export const resolveAfriexPaymentMethodAccount = async (
  runtime: AfriexRuntimeConfig,
  params: {
    channel: string;
    countryCode: string;
    accountNumber: string;
    institutionCode?: string;
  }
): Promise<AfriexHttpProbe & {resolved?: AfriexResolvedAccount}> => {
  const path = "/payment-method/resolve";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const requestParams: Record<string, string> = {
    channel: params.channel,
    countryCode: params.countryCode,
    accountNumber: params.accountNumber,
  };
  if (params.institutionCode) {
    requestParams.institutionCode = params.institutionCode;
  }
  const startedAt = Date.now();
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: requestParams,
      validateStatus: () => true,
    });
    const body = unwrapAfriexData(response.data);
    const resolved: AfriexResolvedAccount = {
      recipientName: firstNonEmpty(body.recipientName as string, body.accountName as string) || null,
      institutionName: firstNonEmpty(body.institutionName as string) || null,
      institutionCode: firstNonEmpty(
        body.institutionCode as string,
        params.institutionCode
      ) || null,
      channel: params.channel,
      countryCode: params.countryCode,
      accountNumber: params.accountNumber,
    };
    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: response.status >= 400 ?
        firstNonEmpty((response.data as Record<string, unknown>)?.code as string | undefined) ||
        "RESOLVE_FAILED" :
        null,
      friendlyMessage: response.status >= 200 && response.status < 300 ?
        `Resolved account holder: ${resolved.recipientName || "unknown"}.` :
        formatAfriexResponseError(response.status, response.data, "Account resolve failed"),
      durationMs: Date.now() - startedAt,
      resolved: response.status >= 200 && response.status < 300 ? resolved : undefined,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

export const fetchAfriexRates = async (
  runtime: AfriexRuntimeConfig,
  params?: {fromSymbols?: string; toSymbols?: string}
): Promise<AfriexHttpProbe & {rates?: AfriexRatesResult}> => {
  const path = "/org/rates";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const fromSymbols = firstNonEmpty(params?.fromSymbols) || "USD";
  const toSymbols = firstNonEmpty(params?.toSymbols) || runtime.inScopeCurrency;
  const startedAt = Date.now();
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      params: {fromSymbols, toSymbols},
      validateStatus: () => true,
    });
    const body = unwrapAfriexData(response.data);
    const ratesRaw = (body.rates && typeof body.rates === "object" && !Array.isArray(body.rates)) ?
      (body.rates as Record<string, Record<string, string>>) :
      {};
    const updatedAtMs = typeof body.updatedAt === "number" ? body.updatedAt : null;
    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: response.status >= 400 ? "RATES_FAILED" : null,
      friendlyMessage: response.status >= 200 && response.status < 300 ?
        `Fetched Afriex rates for ${fromSymbols} → ${toSymbols}.` :
        formatAfriexResponseError(response.status, response.data, "Rates request failed"),
      durationMs: Date.now() - startedAt,
      rates: response.status >= 200 && response.status < 300 ?
        {rates: ratesRaw, updatedAtMs, fromSymbols, toSymbols} :
        undefined,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

export const createAfriexCustomer = async (
  runtime: AfriexRuntimeConfig,
  params: {
    fullName: string;
    email: string;
    phone: string;
    countryCode: string;
    meta?: Record<string, unknown>;
  }
): Promise<AfriexHttpProbe & {customerId?: string}> => {
  const path = "/customer";
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const payload = {
    fullName: params.fullName,
    email: params.email,
    phone: params.phone,
    countryCode: params.countryCode,
    meta: {
      autoProvisionedBy: "VolunteersApp",
      liveMode: runtime.liveMode,
      ...(params.meta || {}),
    },
  };
  const startedAt = Date.now();
  try {
    const response = await axios.post(url, payload, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      validateStatus: () => true,
    });
    const body = unwrapAfriexData(response.data);
    const customerId = firstNonEmpty(body.customerId as string, body.id as string) || undefined;
    const conflictCustomerId = firstNonEmpty(
      body.details && typeof body.details === "object" ?
        ((body.details as Record<string, unknown>).data as Record<string, unknown> | undefined)?.customerId as string | undefined :
        undefined,
      body.data && typeof body.data === "object" ?
        (body.data as Record<string, unknown>).customerId as string | undefined :
        undefined
    ) || undefined;

    const ok = (response.status >= 200 && response.status < 300 && !!customerId) ||
      (!!conflictCustomerId && (response.status === 400 || response.status === 409));

    return {
      ok,
      statusCode: response.status,
      method: "POST",
      path,
      errorCode: ok ? null : firstNonEmpty((response.data as Record<string, unknown>)?.code as string | undefined) || "CUSTOMER_CREATE_FAILED",
      friendlyMessage: ok ?
        `Afriex customer ready (${customerId || conflictCustomerId}).` :
        formatAfriexResponseError(response.status, response.data, "Customer create failed"),
      durationMs: Date.now() - startedAt,
      customerId: customerId || conflictCustomerId,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "POST",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

const normalizePemOrBase64PublicKey = (raw: string): string => {
  const trimmed = raw.trim().replace(/\\n/g, "\n");
  if (trimmed.includes("BEGIN PUBLIC KEY") || trimmed.includes("BEGIN RSA PUBLIC KEY")) {
    return trimmed;
  }
  const compact = trimmed.replace(/\s+/g, "");
  const lines = compact.match(/.{1,64}/g) || [compact];
  return `-----BEGIN PUBLIC KEY-----\n${lines.join("\n")}\n-----END PUBLIC KEY-----`;
};

export const loadAfriexWebhookPublicKey = (rawKey?: string): KeyObject | null => {
  const environment = resolveAfriexEnvironment();
  const source = rawKey || (environment === "live" ?
    firstNonEmpty(
      process.env.AFRIEX_LIVE_WEBHOOK_PUBLIC_KEY,
      afriexLiveWebhookPublicKeyParam.value(),
      process.env.MOBILE_MONEY_PROVIDER_LIVE_WEBHOOK_PUBLIC_KEY
    ) :
    firstNonEmpty(
      process.env.AFRIEX_SANDBOX_WEBHOOK_PUBLIC_KEY,
      process.env.MOBILE_MONEY_PROVIDER_SANDBOX_WEBHOOK_PUBLIC_KEY,
      process.env.AFRIEX_WEBHOOK_PUBLIC_KEY,
      process.env.MOBILE_MONEY_PROVIDER_WEBHOOK_PUBLIC_KEY
    ));
  if (!source) return null;
  try {
    return createPublicKey(normalizePemOrBase64PublicKey(source));
  } catch {
    return null;
  }
};

/**
 * Afriex partner docs specify RSA-SHA256 over the raw body (`x-webhook-signature`, base64).
 * Sandbox dashboard keys are often EC SPKI (P-384) — try RSA then ECDSA variants.
 * Also accept common header prefixes (sha256=, v1=).
 */
export const verifyAfriexWebhookSignature = (params: {
  rawBody: Buffer;
  signatureHeader: string | undefined | null;
  publicKey?: KeyObject | null;
}): {ok: boolean; algorithm: string | null; reason: string} => {
  let signature = String(params.signatureHeader || "").trim();
  if (!signature) {
    return {ok: false, algorithm: null, reason: "Missing x-webhook-signature header."};
  }
  // Strip common prefixes / quoting that dashboards may add.
  signature = signature
    .replace(/^sha256=/i, "")
    .replace(/^sha384=/i, "")
    .replace(/^v1[,=]/i, "")
    .replace(/^["']|["']$/g, "")
    .trim();

  const publicKey = params.publicKey === undefined ?
    loadAfriexWebhookPublicKey() :
    params.publicKey;
  if (!publicKey) {
    return {ok: false, algorithm: null, reason: "Afriex webhook public key is not configured."};
  }

  let signatureBytes: Buffer;
  try {
    signatureBytes = Buffer.from(signature, "base64");
  } catch {
    return {ok: false, algorithm: null, reason: "Webhook signature is not valid base64."};
  }
  if (!signatureBytes.length) {
    return {ok: false, algorithm: null, reason: "Webhook signature decoded to empty bytes."};
  }

  const bodies: Buffer[] = [params.rawBody];
  // Some gateways re-encode UTF-8; try trimmed UTF-8 bytes as a secondary candidate.
  const asUtf8 = params.rawBody.toString("utf8");
  const trimmedUtf8 = asUtf8.trim();
  if (trimmedUtf8 && trimmedUtf8 !== asUtf8) {
    bodies.push(Buffer.from(trimmedUtf8, "utf8"));
  }

  for (const body of bodies) {
    const attempts: Array<{algorithm: string; run: () => boolean}> = [
      {
        algorithm: "RSA-SHA256",
        run: () => {
          // Afriex docs: verifier.verify(publicKey, signature, "base64")
          const verifier = createVerify("RSA-SHA256");
          verifier.update(body);
          verifier.end();
          return verifier.verify(publicKey, signature, "base64");
        },
      },
      {
        algorithm: "RSA-SHA256:bytes",
        run: () => {
          const verifier = createVerify("RSA-SHA256");
          verifier.update(body);
          verifier.end();
          return verifier.verify(publicKey, signatureBytes);
        },
      },
      {
        algorithm: "SHA256",
        run: () => cryptoVerify("SHA256", body, publicKey, signatureBytes),
      },
      {
        algorithm: "SHA384",
        run: () => cryptoVerify("SHA384", body, publicKey, signatureBytes),
      },
      {
        algorithm: "SHA512",
        run: () => cryptoVerify("SHA512", body, publicKey, signatureBytes),
      },
    ];

    for (const attempt of attempts) {
      try {
        if (attempt.run()) {
          return {ok: true, algorithm: attempt.algorithm, reason: "Signature verified."};
        }
      } catch {
        // Try next algorithm — key type may not match this verifier.
      }
    }
  }

  return {
    ok: false,
    algorithm: null,
    reason: "Webhook signature verification failed for configured public key.",
  };
};

export type AfriexTransactionSnapshot = {
  transactionId: string | null;
  status: string | null;
  type: string | null;
  customerId: string | null;
  sourceAmount: string | null;
  sourceCurrency: string | null;
  destinationAmount: string | null;
  destinationCurrency: string | null;
  meta: Record<string, unknown>;
  raw: Record<string, unknown>;
};

export const getAfriexTransaction = async (
  runtime: AfriexRuntimeConfig,
  transactionId: string
): Promise<AfriexHttpProbe & {transaction?: AfriexTransactionSnapshot}> => {
  const cleanId = String(transactionId || "").trim();
  const path = `/transaction/${encodeURIComponent(cleanId)}`;
  const url = buildAfriexApiUrl(runtime.apiBaseUrl, path);
  const startedAt = Date.now();
  if (!cleanId) {
    return {
      ok: false,
      statusCode: null,
      method: "GET",
      path: "/transaction/{id}",
      errorCode: "INVALID_ARGUMENT",
      friendlyMessage: "transactionId is required.",
      durationMs: 0,
    };
  }
  try {
    const response = await axios.get(url, {
      headers: runtime.headers,
      timeout: runtime.timeoutMs,
      validateStatus: () => true,
    });
    const body = unwrapAfriexData(response.data);
    const meta = body.meta && typeof body.meta === "object" && !Array.isArray(body.meta) ?
      (body.meta as Record<string, unknown>) :
      {};
    const transaction: AfriexTransactionSnapshot = {
      transactionId: firstNonEmpty(body.transactionId as string, body.id as string, cleanId) || null,
      status: firstNonEmpty(body.status as string) || null,
      type: firstNonEmpty(body.type as string) || null,
      customerId: firstNonEmpty(body.customerId as string) || null,
      sourceAmount: body.sourceAmount != null ? String(body.sourceAmount) : null,
      sourceCurrency: firstNonEmpty(body.sourceCurrency as string) || null,
      destinationAmount: body.destinationAmount != null ? String(body.destinationAmount) : null,
      destinationCurrency: firstNonEmpty(body.destinationCurrency as string) || null,
      meta,
      raw: body,
    };
    return {
      ok: response.status >= 200 && response.status < 300,
      statusCode: response.status,
      method: "GET",
      path,
      errorCode: response.status >= 400 ? "TRANSACTION_GET_FAILED" : null,
      friendlyMessage: response.status >= 200 && response.status < 300 ?
        `Fetched Afriex transaction ${transaction.transactionId}.` :
        formatAfriexResponseError(response.status, response.data, "Transaction lookup failed"),
      durationMs: Date.now() - startedAt,
      transaction: response.status >= 200 && response.status < 300 ? transaction : undefined,
    };
  } catch (error) {
    const mapped = mapAxiosProbeError(error);
    return {
      method: "GET",
      path,
      durationMs: Date.now() - startedAt,
      ...mapped,
    };
  }
};

export const buildAfriexLiveModeDiagnostics = async (
  options?: {countryCode?: string; channel?: string}
): Promise<AfriexLiveModeDiagnostics> => {
  const runtime = resolveAfriexRuntimeConfig();
  const countryCode = firstNonEmpty(options?.countryCode) || runtime.inScopeCountryCode;
  const channel = firstNonEmpty(options?.channel) || runtime.inScopeChannel;
  const blockers: string[] = [];
  const hints: string[] = [];

  if (!runtime.apiBaseUrl) {
    blockers.push(runtime.isSandbox ?
      "Set AFRIEX_SANDBOX_API_BASE_URL or a legacy sandbox API base URL." :
      "Set AFRIEX_LIVE_API_BASE_URL to https://api.afriex.com."
    );
  }
  if (!runtime.apiKey) {
    blockers.push(runtime.isSandbox ?
      "Set AFRIEX_SANDBOX_API_KEY or a legacy sandbox API key." :
      "Set AFRIEX_LIVE_API_KEY from the approved Afriex production dashboard."
    );
  }
  if (!runtime.webhookPublicKey) {
    blockers.push(runtime.isSandbox ?
      "Set AFRIEX_SANDBOX_WEBHOOK_PUBLIC_KEY for webhook signature verification." :
      "Set AFRIEX_LIVE_WEBHOOK_PUBLIC_KEY before accepting production webhooks."
    );
  } else {
    hints.push("Webhook public key is configured — implement RSA-SHA256 verification on raw body in Phase 2.");
  }

  const authentication = (!runtime.apiBaseUrl || !runtime.apiKey) ?
    {
      ok: false,
      statusCode: null,
      method: "GET",
      path: "/customer",
      errorCode: "CONFIG_MISSING",
      friendlyMessage: "Afriex API base URL or API key is missing.",
      durationMs: null,
    } :
    await probeAfriexAuthentication(runtime);

  if (!authentication.ok && runtime.apiKey) {
    blockers.push(`Afriex authentication probe failed (${authentication.statusCode || "no response"}).`);
  }

  const businessBalance = (!runtime.apiBaseUrl || !runtime.apiKey || !authentication.ok) ?
    {
      ok: false,
      statusCode: null,
      method: "GET",
      path: "/org/balance",
      errorCode: "SKIPPED",
      friendlyMessage: "Balance probe skipped until authentication succeeds.",
      durationMs: null,
    } :
    await fetchAfriexBusinessBalance(runtime, ["USD", runtime.inScopeCurrency]);

  const ugMobileMoneyInstitutions = (!runtime.apiBaseUrl || !runtime.apiKey || !authentication.ok) ?
    {
      ok: false,
      statusCode: null,
      method: "GET",
      path: "/payment-method/institution",
      errorCode: "SKIPPED",
      friendlyMessage: "Institution probe skipped until authentication succeeds.",
      durationMs: null,
      institutionCount: 0,
      institutions: [],
    } :
    await listAfriexInstitutions(runtime, {channel, countryCode});

  if (!ugMobileMoneyInstitutions.ok) {
    blockers.push(`${countryCode} ${channel} institution list failed or returned zero providers.`);
  } else if (ugMobileMoneyInstitutions.institutionCount === 0) {
    blockers.push(`Afriex returned zero ${channel} institutions for ${countryCode}.`);
  } else {
    hints.push(`${countryCode} corridor ready for institution caching (SET-04/05 analog).`);
  }

  if (runtime.liveMode === "transaction_only") {
    hints.push("Transaction-only mode: disburse from Afriex business wallet; no end-customer custody UI.");
  }

  if (runtime.isSandbox) {
    hints.push("Sandbox detected — use POST /org/balance/topup (owner callable) before payout UAT.");
    hints.push("Sandbox settlements take ~1–2 minutes; use meta.reference containing 'fail' to force FAILED.");
  }

  const ready =
    blockers.length === 0 &&
    authentication.ok &&
    ugMobileMoneyInstitutions.ok &&
    ugMobileMoneyInstitutions.institutionCount > 0;

  return {
    product: {
      liveMode: runtime.liveMode,
      endCustomerCustody: runtime.liveMode === "hosted_wallet",
      description: runtime.liveMode === "transaction_only" ?
        "Direct send/payout from Afriex business wallet. Customers are KYC records only; no stored customer balance." :
        "Provider-hosted customer wallet with balance, top-up, and withdraw UX.",
    },
    config: {
      apiBaseUrl: runtime.apiBaseUrl,
      isSandbox: runtime.isSandbox,
      apiKeyConfigured: !!runtime.apiKey,
      apiVersion: runtime.apiVersion || null,
      webhookPublicKeyConfigured: !!runtime.webhookPublicKey,
      authHeader: runtime.authHeader,
      timeoutMs: runtime.timeoutMs,
      inScopeCountryCode: countryCode,
      inScopeChannel: channel,
      inScopeCurrency: runtime.inScopeCurrency,
    },
    probes: {
      authentication,
      businessBalance,
      ugMobileMoneyInstitutions,
    },
    phase0: {
      ready,
      blockers,
      hints,
    },
    checkedAtMs: Date.now(),
  };
};
