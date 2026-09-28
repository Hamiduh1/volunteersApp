# Afriex Supported Currencies & Payment Rails

Source of truth (mirror): [Supported Currencies & Payment Rails](https://docs.afriex.com/api-reference/supported-currencies)  
Also: [API reference copy](https://docs.afriex.com/api-reference/supported-currencies) · Docs index: [llms.txt](https://docs.afriex.com/llms.txt)

**Synced into VolunteersApp:** `CountryMetadata.kt` (Android) and Afriex corridor helpers in `my-firebase-functions/src/index.ts`.  
Apps never call Afriex REST — Cloud Functions own channel/country checks.

## Rail reference (API `channel`)

| Rail | API `channel` | Role in VolunteersApp (txn-only) |
| --- | --- | --- |
| Bank Account | `BANK_ACCOUNT` | Beneficiary bank payout / receive routes |
| Virtual Account | `VIRTUAL_BANK_ACCOUNT` | Deposit / collection (prod-only VA) |
| Pool Account | `POOL_ACCOUNT` | Coming soon (Afriex) |
| Mobile Money | `MOBILE_MONEY` | Primary send + funding verification |
| SWIFT | `SWIFT` | USD international wire — **100 countries** (see [SWIFT coverage](#swift-coverage-100-countries)); consumer Bank loop uses when local `BANK_ACCOUNT` unavailable |
| Interac | `INTERAC` | Canada — coming soon |
| UPI | `UPI` | India payout — later |
| WeChat / Alipay | `WE_CHAT` / `ALIPAY` | China — later |
| Crypto | `CRYPTO` | Deposit only (USDC/USDT) — later |
| Paybill / Till | `PAYBILL_TILL` | Kenya payout option — later |

Legend used below:

| Marker | Meaning |
| --- | --- |
| **Live** | Available for product use now |
| **Coming soon** | Listed by Afriex but not ready — surface as Coming soon in UI; do not treat as live |
| **—** | Not offered for that direction |

---

## Africa

| Country | ISO2 | Currency | Deposit rails | Payout rails |
| --- | --- | --- | --- | --- |
| Benin | BJ | XOF | MM **Live** | MM **Live** |
| Botswana | BW | BWP | — | MM **Live** |
| Burkina Faso | BF | XOF | MM **Coming soon** | MM **Coming soon** |
| Cameroon | CM | XAF | MM **Live** | Bank **Live** · MM **Live** |
| Central African Republic | CF | XAF | — | MM **Coming soon** |
| Republic of the Congo | CG | XAF | MM **Coming soon** | MM **Live** |
| Côte d'Ivoire | CI | XOF | MM **Live** | Bank **Live** · MM **Live** |
| DR Congo | CD | CDF | — | MM **Coming soon** |
| Egypt | EG | EGP | — | Bank **Live** · MM **Live** |
| Ethiopia | ET | ETB | MM **Live** | Bank **Live** · MM **Live** |
| Gabon | GA | XAF | MM **Coming soon** | MM **Live** |
| Gambia | GM | GMD | — | MM **Live** |
| Ghana | GH | GHS | MM **Coming soon** · VA **Coming soon** | Bank **Live** · MM **Live** |
| Guinea | GN | GNF | — | MM **Live** |
| Guinea-Bissau | GW | XOF | — | MM **Live** |
| Kenya | KE | KES | MM **Live** · VA **Live** | Bank **Live** · MM **Live** · Paybill **Live** |
| Madagascar | MG | MGA | — | MM **Live** |
| Malawi | MW | MWK | MM **Coming soon** | MM **Live** |
| Mali | ML | XOF | — | MM **Coming soon** |
| Morocco | MA | MAD | — | MM **Coming soon** |
| Mozambique | MZ | MZN | MM **Coming soon** | MM **Live** |
| Nigeria | NG | NGN | VA **Live** | Bank **Live** *(no MM payout)* |
| Rwanda | RW | RWF | MM **Coming soon** | Bank **Live** · MM **Live** |
| Senegal | SN | XOF | — | Bank **Live** · MM **Live** |
| Sierra Leone | SL | SLE | MM **Coming soon** | MM **Live** |
| South Africa | ZA | ZAR | — | Bank **Live** |
| South Sudan | SS | SSP | — | MM **Coming soon** |
| Tanzania | TZ | TZS | MM **Live** | MM **Live** |
| Togo | TG | XOF | — | MM **Coming soon** |
| Uganda | UG | UGX | MM **Live** | Bank **Live** · MM **Live** |
| Zambia | ZM | ZMW | MM **Coming soon** | MM **Live** |

## Americas

| Country | ISO2 | Currency | Deposit rails | Payout rails |
| --- | --- | --- | --- | --- |
| Canada | CA | CAD | Interac **Coming soon** | Interac **Coming soon** · SWIFT **Live** |
| United States | US | USD | VA **Live** | Bank **Live** · SWIFT **Live** |

> USD `SWIFT` payouts route to **100 countries** globally — not just the US. See [SWIFT coverage](#swift-coverage-100-countries).

## Europe (local bank payouts live; VA deposits coming soon)

EUR bank payouts use **SEPA** (valid IBAN required). GBP payouts use **Faster Payments**.

| Coverage | ISO2 list |
| --- | --- |
| Local bank **Live** (`BANK_ACCOUNT`) | AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GR, HU, IE, IT, LV, LT, LU, MT, NL, NO, PL, PT, RO, SK, SI, ES, SE, UA (EUR), GB (GBP) |
| VA deposit **Coming soon** | Same EUR countries + GB |
| SWIFT **Live** (USD) | All local-bank countries above **plus** GI, LI, CH, RU |

## Asia & Pacific

| Country | ISO2 | Currency | Deposit | Payout |
| --- | --- | --- | --- | --- |
| China | CN | CNY | — | Bank · WeChat · Alipay · SWIFT (**Live**) |
| India | IN | INR | — | Bank · UPI · SWIFT (**Live**) |
| Pakistan | PK | PKR | — | Bank · MM · SWIFT (**Live**) |

Additional **SWIFT-only** payout countries in this region (no local bank rail in Afriex table): AU, BD, HK, ID, JP, KR, KW, MY, NZ, PH, QA, SA, SG, TH, TR, AE, VN.

> CNY `BANK_ACCOUNT` payouts require the recipient's **phone number**, **ID type**, and **ID number** in addition to standard bank details.

## Digital assets

| Asset | Deposit | Payout |
| --- | --- | --- |
| USDC / USDT | Crypto **Live** (100+ countries, production) | — |

---

## SWIFT coverage (100 countries)

Source: [Afriex Supported Currencies — SWIFT Coverage](https://docs.afriex.com/api-reference/supported-currencies)

USD `SWIFT` payouts (`channel: SWIFT`) are available to **100 countries**. SWIFT is the default choice for international USD disbursements when no local `BANK_ACCOUNT` rail is listed.

**Required fields:** recipient email, recipient address, SWIFT/BIC code, transaction invoice.

**Institution lookup:** `GET /payment-method/institution?channel=SWIFT&countryCode={iso2}`

### Africa (36)

DZ, BJ, BW, BF, CM, CF, CG, CI, CD, EG, ET, GA, GM, GH, GN, GW, KE, MG, MW, ML, MA, MZ, NA, NE, NG, RW, SN, SL, ZA, SS, TZ, TG, TN, UG, ZM, ZW

### Americas (10)

AR, BR, CA, CL, CO, HT, MX, PE, US, UY

### Europe (34)

AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GI, GR, HU, IE, IT, LV, LI, LT, LU, MT, NL, NO, PL, PT, RO, RU, SK, SI, ES, SE, CH, UA, GB

### Asia & Pacific (20)

AU, BD, CN, HK, ID, IN, JP, KR, KW, MY, NZ, PH, PK, QA, SA, SG, TH, TR, AE, VN

---

## Local bank payout coverage (`BANK_ACCOUNT`)

Distinct from SWIFT. Use local bank when the recipient country supports `BANK_ACCOUNT` in the Afriex table.

| Region | Live local bank ISO2 |
| --- | --- |
| Africa (11) | CM, CI, EG, ET, GH, KE, NG, RW, SN, ZA, UG |
| Americas (1) | US |
| Europe (30) | AT, BE, BG, HR, CY, CZ, DK, EE, FI, FR, DE, GR, HU, IE, IT, LV, LT, LU, MT, NL, NO, PL, PT, RO, SK, SI, ES, SE, UA, GB |
| Asia (3) | CN, IN, PK |

**VolunteersApp Bank Account loop:** prefer **Local Bank** when `localBankPayoutLiveIso2` includes the recipient country; use **SWIFT Bank** for SWIFT-covered countries without local bank.

---

## Provider catalog vs account entitlement (2026-09-06)

Afriex's provider catalog and the VolunteersApp account entitlement are
intentionally separate:

- Provider-documented Mobile Money payout coverage is 23 countries: `BJ`, `BW`,
  `CM`, `CG`, `CI`, `EG`, `ET`, `GA`, `GM`, `GH`, `GN`, `GW`, `KE`, `MG`, `MW`,
  `MZ`, `PK`, `RW`, `SN`, `SL`, `TZ`, `UG`, `ZM`.
- Add Recipient and Mobile Money delivery remain limited to the 19-country
  UAT-backed account scope. `EG`, `GA`, `GW`, and `PK` are provider-catalog
  entries only until Afriex confirms account entitlement and route resolution.
- Provider-documented local `BANK_ACCOUNT` coverage is 45 countries. Android
  and Cloud Functions use it for routing and settlement currency, but production
  settlement remains gated by the configured local-bank allowlist.
- USD `SWIFT` remains the 100-country catalog and has its own production flag
  and country allowlist.
- The provider catalog is not a commercial fee schedule. The five contracted
  Schedule 1 bank corridors keep their fixed fees; other local-bank rows need
  an admin-entered provider fee before they are saved to fee settings.
## VolunteersApp product mapping (transaction-only)

| Flow | Use live countries from |
| --- | --- |
| Add Mobile Money (funding / deposit verify) | MM **deposit Live** only; MM deposit **Coming soon** shown as Coming soon |
| Send Money → Mobile Money beneficiary | MM **payout Live**; MM payout **Coming soon** labeled Coming soon |
| Send Money → Bank beneficiary | **Local** `BANK_ACCOUNT` where live; **SWIFT** for 100-country USD coverage when local bank unavailable |
| Wallet balance deposit UI | Coming soon (product policy) even where Afriex VA exists |
| SWIFT consumer lane (Bank loop) | SWIFT registration in Send Money when local bank not available; full 100-country list in JSON |
| UPI / Interac / Crypto lanes | Coming soon in consumer app |

### Live MM deposit (cash-in / verify)

`BJ`, `CM`, `CI`, `ET`, `KE`, `TZ`, `UG`

### Coming soon MM deposit

`BF`, `CG`, `GA`, `GH`, `MW`, `MZ`, `RW`, `SL`, `ZM` (+ GH VA, US/NG VA are separate channels)

### Live MM payout

`BJ`, `BW`, `CM`, `CG`, `CI`, `EG`, `ET`, `GA`, `GM`, `GH`, `GN`, `GW`, `KE`, `MG`, `MW`, `MZ`, `RW`, `SN`, `SL`, `TZ`, `UG`, `ZM`, `PK`

### VolunteersApp UAT mobile-money scope (19 corridors)

The public Afriex rail catalog is broader than this partner's submitted scope.
For Add Recipient and Mobile Money delivery, Android, iOS, and Cloud Functions
use exactly this UAT-backed set: `BJ`, `BW`, `CM`, `CG`, `CI`, `ET`, `GH`, `GM`,
`GN`, `KE`, `MG`, `MW`, `MZ`, `RW`, `SN`, `SL`, `TZ`, `UG`, and `ZM`.

Recipient registration eligibility must match this same set. In particular,
Botswana (`BW`) and Gambia (`GM`) must not be hidden by a separate account-
resolution list. Provider-listed public corridors outside the submitted scope,
including `EG`, `GA`, `GW`, and `PK`, remain unavailable until Afriex confirms
the applicable production entitlement.

An eligible corridor only permits the app to request provider verification. A
recipient is saved only after Afriex confirms the selected route. A temporary
`/payment-method/resolve` failure saves no recipient and moves no funds.

### Coming soon MM payout

`BF`, `CF`, `CD`, `ML`, `MA`, `SS`, `TG`

### Live local bank payout (`BANK_ACCOUNT`)

Africa: `CM`, `CI`, `EG`, `ET`, `GH`, `KE`, `NG`, `RW`, `SN`, `ZA`, `UG`  
Americas: `US`  
Europe: `AT`, `BE`, `BG`, `HR`, `CY`, `CZ`, `DK`, `EE`, `FI`, `FR`, `DE`, `GR`, `HU`, `IE`, `IT`, `LV`, `LT`, `LU`, `MT`, `NL`, `NO`, `PL`, `PT`, `RO`, `SK`, `SI`, `ES`, `SE`, `UA`, `GB`  
Asia: `CN`, `IN`, `PK`

### Live SWIFT payout (USD, 100 countries)

See `swiftPayoutLiveIso2` and `swiftPayoutByRegion` in [`afriex_supported_currencies.json`](./afriex_supported_currencies.json).

Africa (36): `DZ`, `BJ`, `BW`, `BF`, `CM`, `CF`, `CG`, `CI`, `CD`, `EG`, `ET`, `GA`, `GM`, `GH`, `GN`, `GW`, `KE`, `MG`, `MW`, `ML`, `MA`, `MZ`, `NA`, `NE`, `NG`, `RW`, `SN`, `SL`, `ZA`, `SS`, `TZ`, `TG`, `TN`, `UG`, `ZM`, `ZW`

Americas (10): `AR`, `BR`, `CA`, `CL`, `CO`, `HT`, `MX`, `PE`, `US`, `UY`

Europe (34): `AT`, `BE`, `BG`, `HR`, `CY`, `CZ`, `DK`, `EE`, `FI`, `FR`, `DE`, `GI`, `GR`, `HU`, `IE`, `IT`, `LV`, `LI`, `LT`, `LU`, `MT`, `NL`, `NO`, `PL`, `PT`, `RO`, `RU`, `SK`, `SI`, `ES`, `SE`, `CH`, `UA`, `GB`

Asia & Pacific (20): `AU`, `BD`, `CN`, `HK`, `ID`, `IN`, `JP`, `KR`, `KW`, `MY`, `NZ`, `PH`, `PK`, `QA`, `SA`, `SG`, `TH`, `TR`, `AE`, `VN`

## Machine-readable catalog

See [`afriex_supported_currencies.json`](./afriex_supported_currencies.json) for ISO2 + rail availability used by engineers to keep Android/Functions in sync.

Key JSON fields:

| Field | Purpose |
| --- | --- |
| `localBankPayoutLiveIso2` | Local `BANK_ACCOUNT` payout countries |
| `swiftPayoutLiveIso2` | Full 100-country USD SWIFT list |
| `swiftPayoutByRegion` | SWIFT countries grouped by region |
| `swiftPayoutRequirements` | Required SWIFT beneficiary / invoice fields |
| `countries[]` | Per-country deposit/payout rail status |

### Crosscheck status (2026-09-06)

Catalog synced from [Afriex Supported Currencies](https://docs.afriex.com/api-reference/supported-currencies).

Parity check targets: JSON ↔ Android `CountryMetadata.kt` ↔ Functions ISO sets for MM deposit/payout live+coming-soon, **local bank payout live**, and **SWIFT 100-country list**.

**Android synced (2026-09-06):** `CountryMetadata.kt` (`afriexSwiftPayoutLiveIsos`, `afriexBankPayoutLiveIsos`, availability helpers), `TransferCorridorFeeCatalog.kt`, Owner dashboard corridor card, fee settings SWIFT rows.

**Cloud Functions:** import ISO sets from [`afriex_supported_currencies.json`](./afriex_supported_currencies.json) (`swiftPayoutLiveIso2`, `localBankPayoutLiveIso2`) in `my-firebase-functions` when that package is deployed.

Loopholes closed in prior pass:
- Afriex **Coming soon** payouts blocked even when `MOBILE_MONEY_PROVIDER_MODE=MANUAL`
- MM deposit verification / cash-in require **live deposit** corridors
- Android blocks Add-MM save and beneficiary proceed for non-live corridors
- Congo ISO/currency corrected to CG / XAF (not DRC)

Intentionally **not** consumer-shipped yet (documented Coming soon / later): Interac, UPI, WeChat/Alipay standalone lanes, Crypto deposits UI, Pool Accounts, Europe VA deposits, Kenya Paybill/Till dedicated UI.

**SWIFT** is supported in the Bank Account send loop (SWIFT Bank registration) for countries in `swiftPayoutLiveIso2`; local bank remains preferred where `localBankPayoutLiveIso2` applies.

### Production activation refresh (2026-09-28)

Source: [Afriex Supported Currencies & Payment Rails](https://docs.afriex.com/api-reference/supported-currencies#supported-countries) and [SWIFT Coverage](https://docs.afriex.com/api-reference/supported-currencies#swift-coverage).

- Mobile-money deposits are live only for `BJ`, `CM`, `CI`, `ET`, `KE`, `TZ`, and `UG`. Android exposes these as **Funding and receive: live**.
- Payout-only mobile-money corridors remain usable as receive routes but never become a funding source until Afriex documents their deposit rail as live.
- Provider-listed payout routes outside this product's approved 19-country scope (`EG`, `GA`, `GW`, and `PK`) are visible as **Coming soon**, not selectable. This preserves the account-entitlement boundary while avoiding a misleading empty country list.
- Deposit rows marked coming soon by Afriex, and countries with no current mobile-money deposit rail, remain disabled and are never sent to collection or OTP funding flows.
- Production SWIFT is enabled only with `AFRIEX_SWIFT_PAYOUTS_PRODUCTION_ENABLED=true` and the existing 100-country `AFRIEX_SWIFT_PAYOUT_COUNTRIES` allowlist. The SWIFT form must collect a valid BIC, recipient email, recipient address, and transaction invoice; settlement is USD.
