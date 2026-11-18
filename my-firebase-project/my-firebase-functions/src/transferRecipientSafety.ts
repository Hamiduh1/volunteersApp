/**
 * Pure helpers for Send Money recipient verification state.
 * Keep outage / pending decisions testable without Firebase.
 */

export const MM_PENDING_ROUTE_VALIDATION = "PENDING_AFRIEX_PAYOUT_VALIDATION";
export const MM_PENDING_VERIFICATION_STATUS = "PENDING_AFRIEX_ROUTE_RESOLUTION";

export type MobileMoneyRecipientVerificationFields = {
  verificationStatus: string;
  isVerified: boolean;
  accountRouteVerified: boolean;
  providerRouteValidation: string;
  providerVerifiedAtMs: number | null;
};

/**
 * When Afriex resolve is down (`accountRouteVerified=false`), persist the
 * recipient as pending — never as verified — so quote/send stay blocked.
 */
export const mobileMoneyRecipientVerificationFields = (params: {
  accountRouteVerified: boolean;
  accountNameVerified: boolean;
  nowMs?: number;
}): MobileMoneyRecipientVerificationFields => {
  if (!params.accountRouteVerified) {
    return {
      verificationStatus: MM_PENDING_VERIFICATION_STATUS,
      isVerified: false,
      accountRouteVerified: false,
      providerRouteValidation: MM_PENDING_ROUTE_VALIDATION,
      providerVerifiedAtMs: null,
    };
  }
  return {
    verificationStatus: params.accountNameVerified ?
      "VERIFIED_AFRIEX_NAME_CONFIRMED" :
      "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED",
    isVerified: true,
    accountRouteVerified: true,
    providerRouteValidation: "AFRIEX_RESOLVED",
    providerVerifiedAtMs: params.nowMs ?? Date.now(),
  };
};

export const normalizeRecipientTypeToken = (raw: unknown): string =>
  String(raw || "MOBILE_MONEY")
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "_");

/** True when a saved MM recipient must not fund a quote/send yet. */
export const hasPendingMobileMoneyRoute = (stored: {
  type?: unknown;
  recipientType?: unknown;
  deliveryRoute?: unknown;
  accountRouteVerified?: unknown;
  providerRouteValidation?: unknown;
  verificationStatus?: unknown;
}): boolean => {
  const type = normalizeRecipientTypeToken(
    stored.type || stored.recipientType || stored.deliveryRoute || "MOBILE_MONEY"
  );
  if (type !== "MOBILE_MONEY" && type !== "MOBILEMONEY") return false;
  if (stored.accountRouteVerified === false) return true;
  const routeValidation = String(stored.providerRouteValidation || "").trim().toUpperCase();
  if (routeValidation === MM_PENDING_ROUTE_VALIDATION) return true;
  const status = String(stored.verificationStatus || "").trim().toUpperCase();
  return status === MM_PENDING_VERIFICATION_STATUS || status === "PENDING_PROVIDER_VERIFICATION";
};

/**
 * Transfer/send must reject resolve outages for every MM corridor.
 * Recipient registration may still save a pending draft.
 */
export const shouldRejectMobileMoneyTransferOnResolveOutage = (params: {
  accountRouteVerified: boolean;
  isRecipientRegistration: boolean;
}): boolean => {
  if (params.accountRouteVerified) return false;
  return !params.isRecipientRegistration;
};
