# Marketplace Stripe charge model

**Decision: destination charges on the platform account are intentional** for the Marketplace, and they match paid events and Seller earnings.

## How the money moves

1. The app calls `requestMarketplacePurchase`. The server re-reads the listing price and seller from Firestore (never from the client),
   verifies the seller's Stripe Connect Express account (`resolveVerifiedStripeConnectDestination(..., "MARKETPLACE")`), and computes the
   platform fee from runtime fee settings.
2. The platform creates a **Stripe Checkout Session** on the platform account. The PaymentIntent is created with
   `transfer_data.destination = <seller's connected account>` and `application_fee_amount = <platform fee>` (see `startStripeCommerceCheckout`).
3. The buyer pays the platform. Stripe moves the seller share to the connected account's Stripe balance; the platform keeps the application fee.
   Stripe pays out to the seller's bank/card on the seller's own payout schedule. Nothing is credited to an in-app wallet.
4. The Stripe webhook (`finalizeStripeCommerceOrder`) is the only fulfillment authority. It marks the listing `SOLD`, writes the private
   `marketplace_orders/{orderId}` projection and receipts. The seller receipt status is `STRIPE_SETTLEMENT_PENDING`.

Same pattern as:

- **Paid events**: Checkout on the platform, `transfer_data.destination` = organizer, fee as `application_fee_amount`.
- **Garage sales / Seller earnings**: Seller earnings docs describe the same Connect balance + Stripe payout schedule.

## Liability

The platform is the **merchant of record** for these charges: the buyer's statement shows the platform, disputes/chargebacks are raised against the
platform's charge, and the platform decides refunds. Sellers are not direct (on-behalf-of / direct) charge merchants.

## Refunds

Refunds are created on the platform PaymentIntent with:

- `reverse_transfer: true` when the PaymentIntent has a destination, so the seller's share is pulled back from the connected account;
- `refund_application_fee: true` when an application fee was taken, so the platform returns its fee.

This applies to:

- automatic refunds when payment lands on an unavailable listing (`refundStripeCommerceCheckout`, `refundUnfulfillableMarketplaceOrder`);
- post-payment refunds approved by the seller or platform (`refundPaidMarketplaceCommerceOrder`, used by `respondMarketplaceOrderHelp` and
  `resolveMarketplaceOrder`).

## Known limits

- If a seller's connected balance is insufficient to cover `reverse_transfer`, Stripe may fail the refund; the order stays unchanged and the failure
  is recorded in `stripe_commerce_refunds` as `RECONCILIATION_REQUIRED` for platform follow-up.
- Platform-revenue ledger entries written at sale time are not reversed automatically on refund (Stripe returns the fee to the buyer via
  `refund_application_fee`).
