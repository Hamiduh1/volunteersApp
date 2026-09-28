# Afriex Supported Countries Refresh - 2026-09-06

## Sources

- [Documentation index](https://docs.afriex.com/llms.txt)
- [Supported Currencies](https://docs.afriex.com/api-reference/supported-currencies)
- [Supported Countries](https://docs.afriex.com/api-reference/supported-currencies#supported-countries)
- [SWIFT Coverage](https://docs.afriex.com/api-reference/supported-currencies#swift-coverage)

The documentation index was read before reconciling the coverage pages.

## Coverage

| Rail | Provider catalog | VolunteersApp release policy |
| --- | --- | --- |
| Mobile Money payout | 23 countries | Recipient creation and delivery remain the 19-country UAT-approved scope. |
| Local Bank (`BANK_ACCOUNT`) | 45 countries | Route and settlement currency are available; production use needs the rail-specific allowlist. |
| USD SWIFT | 100 countries | Used only for non-local-bank destinations and behind its independent production gate. |

### Mobile Money catalog (23)

`BJ`, `BW`, `CM`, `CG`, `CI`, `EG`, `ET`, `GA`, `GM`, `GH`, `GN`, `GW`, `KE`,
`MG`, `MW`, `MZ`, `PK`, `RW`, `SN`, `SL`, `TZ`, `UG`, `ZM`

### Mobile Money UAT/account scope (19)

`BJ`, `BW`, `CM`, `CG`, `CI`, `ET`, `GH`, `GM`, `GN`, `KE`, `MG`, `MW`, `MZ`,
`RW`, `SN`, `SL`, `TZ`, `UG`, `ZM`

`EG`, `GA`, `GW`, and `PK` remain catalog-only until Afriex enables recipient
verification and production delivery for this workspace.

### Local Bank catalog (45)

- Africa: `CM`, `CI`, `EG`, `ET`, `GH`, `KE`, `NG`, `RW`, `SN`, `ZA`, `UG`
- Americas: `US`
- Europe: `AT`, `BE`, `BG`, `HR`, `CY`, `CZ`, `DK`, `EE`, `FI`, `FR`, `DE`, `GR`, `HU`, `IE`, `IT`, `LV`, `LT`, `LU`, `MT`, `NL`, `NO`, `PL`, `PT`, `RO`, `SK`, `SI`, `ES`, `SE`, `UA`, `GB`
- Asia: `CN`, `IN`, `PK`

## Implementation safeguards

- Android and Cloud Functions share the full local-bank and SWIFT catalogs.
- Unknown local-bank fees are required admin configuration, not zero-dollar fees.
- New Android recipient requests submit trimmed identity and E.164 phone fields
  consistently at the top level and in the nested recipient object.
- Production country allowlists and rail flags still control execution.
- VolunteersApp remains transaction-only and does not hold customer funds.