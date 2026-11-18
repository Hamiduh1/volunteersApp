"use strict";

const {describe, it} = require("node:test");
const assert = require("node:assert/strict");
const {
  mobileMoneyRecipientVerificationFields,
  hasPendingMobileMoneyRoute,
  shouldRejectMobileMoneyTransferOnResolveOutage,
  MM_PENDING_ROUTE_VALIDATION,
  MM_PENDING_VERIFICATION_STATUS,
} = require("../lib/transferRecipientSafety.js");

describe("mobileMoneyRecipientVerificationFields", () => {
  it("marks outage saves as pending, not verified", () => {
    const fields = mobileMoneyRecipientVerificationFields({
      accountRouteVerified: false,
      accountNameVerified: false,
      nowMs: 1,
    });
    assert.equal(fields.isVerified, false);
    assert.equal(fields.accountRouteVerified, false);
    assert.equal(fields.verificationStatus, MM_PENDING_VERIFICATION_STATUS);
    assert.equal(fields.providerRouteValidation, MM_PENDING_ROUTE_VALIDATION);
    assert.equal(fields.providerVerifiedAtMs, null);
  });

  it("marks successful resolve as verified", () => {
    const fields = mobileMoneyRecipientVerificationFields({
      accountRouteVerified: true,
      accountNameVerified: true,
      nowMs: 42,
    });
    assert.equal(fields.isVerified, true);
    assert.equal(fields.accountRouteVerified, true);
    assert.equal(fields.verificationStatus, "VERIFIED_AFRIEX_NAME_CONFIRMED");
    assert.equal(fields.providerRouteValidation, "AFRIEX_RESOLVED");
    assert.equal(fields.providerVerifiedAtMs, 42);
  });
});

describe("hasPendingMobileMoneyRoute", () => {
  it("blocks quote/send for pending MM recipients", () => {
    assert.equal(
      hasPendingMobileMoneyRoute({
        type: "MOBILE_MONEY",
        accountRouteVerified: false,
        providerRouteValidation: MM_PENDING_ROUTE_VALIDATION,
      }),
      true
    );
  });

  it("allows verified MM recipients", () => {
    assert.equal(
      hasPendingMobileMoneyRoute({
        type: "MOBILE_MONEY",
        accountRouteVerified: true,
        providerRouteValidation: "AFRIEX_RESOLVED",
        verificationStatus: "VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED",
      }),
      false
    );
  });

  it("ignores non-MM types", () => {
    assert.equal(
      hasPendingMobileMoneyRoute({
        type: "SWIFT_BANK",
        accountRouteVerified: false,
      }),
      false
    );
  });
});

describe("shouldRejectMobileMoneyTransferOnResolveOutage", () => {
  it("rejects transfer/send during outage", () => {
    assert.equal(
      shouldRejectMobileMoneyTransferOnResolveOutage({
        accountRouteVerified: false,
        isRecipientRegistration: false,
      }),
      true
    );
  });

  it("allows registration to continue as pending during outage", () => {
    assert.equal(
      shouldRejectMobileMoneyTransferOnResolveOutage({
        accountRouteVerified: false,
        isRecipientRegistration: true,
      }),
      false
    );
  });
});
