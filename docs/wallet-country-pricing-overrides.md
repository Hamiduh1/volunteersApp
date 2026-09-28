# Wallet Country Pricing Overrides

`app_config/fee_settings.countryPaymentOverrides` holds optional destination-country adjustments. Keys are ISO-3166 alpha-2 country codes. Unspecified values inherit the global fee settings.

```json
{
  "countryPaymentOverrides": {
    "GH": {
      "topUpFixedUsd": 0.25,
      "topUpRate": 0.01,
      "forexProfitMargin": 0.015,
      "stripeForexDepositProfitMargin": 0.0075
    }
  }
}
```

- `topUpFixedUsd` and `topUpRate` apply only to card- and ACH-funded sends.
- `forexProfitMargin` applies to the transfer quote for the destination country.
- `stripeForexDepositProfitMargin` applies to the recorded card-funding FX margin.
- Schedule 1 locks the provider commercial for its five BANK and eighteen MOBILE_MONEY payout corridors. An owner markup may still be configured separately.
- A SWIFT quote always uses the fixed 0.25% provider rate; only the `CC_SWIFT` owner-fee override may add to it. Local-bank commercials are not reused for SWIFT.
- Mobile-money collection fees are not inferred from mobile-money payout fees. They require separately agreed collection commercials.
- Values are non-negative; rate values must be between `0` and `1`.
