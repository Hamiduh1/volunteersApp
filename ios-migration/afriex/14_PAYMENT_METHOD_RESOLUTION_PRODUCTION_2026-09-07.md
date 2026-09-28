# Payment Method Resolution Production Reconciliation - 2026-09-07

## Sources

- [Afriex documentation index](https://docs.afriex.com/llms.txt)
- [Resolve Payment Method](https://docs.afriex.com/api-reference/endpoint/payment-methods/resolve)
- Afriex support response received 2026-09-07

## Confirmed behavior

Afriex confirmed that every agreed VolunteersApp Mobile Money corridor supports
account/route validation through `GET /api/v1/payment-method/resolve`.
Recipient-name enquiry is a separate corridor capability:

- `NG` and `GH`: Afriex can return `recipientName` or `accountName`; Functions
  compare it with the entered name and require correction or confirmation when
  it differs.
- All other Mobile Money corridors, including `KE`: Afriex validates the phone,
  country, and route but intentionally returns no account-holder name. Functions
  accept the validated route and retain the sender-confirmed recipient name.

The Mobile Money request is:

```text
GET /api/v1/payment-method/resolve
channel=MOBILE_MONEY
accountNumber=+E164MobileNumber
countryCode=ISO2
```

The selected provider is checked against Afriex's live institution list before
resolution. `institutionCode` is used to validate and save the selected route,
but is not sent to the documented Mobile Money resolve request.

## Product safeguards

- Android and iOS may show all 19 agreed payout countries.
- Functions never save a recipient until route validation succeeds.
- Missing `recipientName` outside `NG` and `GH` is expected, not an error.
- Such recipients are stored as `VERIFIED_AFRIEX_ROUTE_CUSTOMER_CONFIRMED`,
  with `accountNameVerified=false` and the customer-confirmed name clearly
  recorded as the confirmation source.
- No quote, collection, payout, or customer-held balance is created when route
  validation fails.

## Failure interpretation

| Result | Meaning | Action |
| --- | --- | --- |
| `401` | The production key is invalid, revoked, or malformed. | Confirm the configured live key in Afriex Developer > API keys. |
| `403` | The key lacks an endpoint permission. | Correct the key permission with Afriex. |
| Successful response without a name outside `NG`/`GH` | Expected route-only validation. | Let the user confirm the entered name and save the verified route. |
| Generic provider-unavailable response | Afriex could not complete route validation at that time. It is not fixed by adding name-enquiry fields or requesting extra permissions. | Retry later; if persistent, send Afriex the redacted timestamp, ISO2 country, provider code, and HTTP status. |

Afriex confirmed there are no additional Business API permissions or payload
parameters that enable recipient-name enquiry outside `NG` and `GH`.

## Public-documentation difference

The public resolver enum does not currently list `BW`, `GM`, and `SL`, but
Afriex's direct production guidance confirms route validation for the agreed
19-corridor UAT scope. Keep the direct provider confirmation with release
evidence and do not treat lack of a returned name as route failure.

## Evidence to retain

Record only redacted evidence in [99_SIGN_OFF.md](./99_SIGN_OFF.md): the
provider timestamp, ISO2 country, selected provider code, response status,
route-validation result, and whether a name was returned. Do not store API
keys, full phone numbers, recipient names, or webhook secrets.
