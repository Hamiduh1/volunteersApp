# In-app AI Assistant (backend + Android handoff)

## What changed

The assistant now runs on the backend instead of on the phone.

- **`askAppAssistant`** (callable, App Check enforced, sign-in required) answers questions with
  Gemini on Vertex AI. The model is grounded in a written guide to every area of the app
  (`src/appAssistantGuide.ts`) plus the FAQ documents in `howToUseTips`, and is told the
  member's screen, platform, role and country.
- When the model cannot answer confidently, the backend creates a ticket in `user_reports`
  (category `AI Support`, visible in the Support Console) and emails the support inbox via
  Resend with the member's name, email, phone, UID, screen and the conversation. The reply-to
  is the member's email, so support can answer directly.
- **`submitAIAssistantEscalation`** (callable) lets the member forward a question on request,
  for example after tapping "Didn't help? Send to support". This function was called by iOS
  before but never existed on the backend, so escalation emails were never sent.
- The same question from the same member is only emailed once per 24 hours.
- Each member can ask 40 questions per hour and 200 per day. Over the limit, the callable
  returns `resource-exhausted`.

Android's current assistant calls Firebase Vertex AI directly with `gemini-2.0-flash`
(`VertexViewModel.kt`). Google shut that model down on June 1, 2026, so the Android
assistant is most likely failing today. Moving Android to `askAppAssistant` fixes this and
gives both apps the same answers.

## Backend setup (before deploy)

1. Enable the Vertex AI API (`aiplatform.googleapis.com`) on the Firebase project.
2. The functions service account needs the `Vertex AI User` role. The App Engine default
   service account already has it via Editor.
3. Add to the functions `.env` (see `.env.example`):
   - `AI_ASSISTANT_ENABLED=true`
   - `AI_ASSISTANT_MODEL=gemini-3.8-flash`
   - `AI_ASSISTANT_LOCATION=global`
   - `SUPPORT_EMAIL_ADDRESS=support@softsolutionstech.com`
   - `RESEND_API_KEY` and `EMAIL_FROM_ADDRESS` (already used for verification emails)
4. Deploy: `firebase deploy --only functions:askAppAssistant,functions:submitAIAssistantEscalation`

To update what the assistant knows, edit `src/appAssistantGuide.ts` (screens, steps, rules)
or add FAQ documents to `howToUseTips` (`question`, `answer`). FAQ changes are picked up
within 10 minutes without a deploy.

## Callable contracts

### `askAppAssistant`

Request:

```json
{
  "question": "How do I add a SWIFT bank?",
  "screen": "Payment Methods",
  "platform": "android",
  "history": [
    {"role": "user", "text": "previous question"},
    {"role": "model", "text": "previous answer"}
  ]
}
```

- `question`: required, up to 1000 characters.
- `history`: optional, the last 10 turns at most, oldest first. Do not include the welcome
  message.

Response:

```json
{
  "answer": "Markdown text (bold and numbered lists)",
  "answered": true,
  "escalated": false,
  "ticketId": "ai_…",
  "emailSent": true,
  "supportEmail": "support@softsolutionstech.com"
}
```

- `answered: false` and `escalated: true` mean the question was already sent to support. The
  `answer` text tells the member that support will reply by email.
- `ticketId`, `emailSent` and `supportEmail` are only present when `escalated` is true.

Errors: `unauthenticated`, `invalid-argument` (empty question), `resource-exhausted` (rate
limit), `unavailable` (assistant disabled or model call failed).

### `submitAIAssistantEscalation`

Request: `{question, screen, platform, aiAnswer, history, note}`. Only `question` is required.
Contact details are read on the server from the signed-in account, so do not send them.

Response: `{success, reportStored, emailSent, alreadyEscalated, ticketId, supportEmail}`.

## Android changes

Files are in `app/src/main/java/com/example/volunteersApp/`.

### `VertexViewModel.kt`

Replace the on-device `Firebase.vertexAI.generativeModel(modelName = "gemini-2.0-flash")` call
with the callable:

- Add `"askAppAssistant"` and `"submitAIAssistantEscalation"` to the App Check preflight set
  in `firebase/FunctionsClient.kt` (`APP_CHECK_PREFLIGHT_REQUIRED`).
- `generate(question, screen, history)` should call
  `FunctionsClient.callData("askAppAssistant", mapOf(...))` and read `answer`, `answered`,
  `escalated` and `emailSent` from the result map.
- Add `requestSupport(question, aiAnswer, history)` that calls
  `submitAIAssistantEscalation`.
- The Firebase Vertex AI client dependency can be removed once nothing else uses it.

### `ui/profile/AiAssistantScreen.kt`

- Keep the common-questions `DropdownMenu`. Do not show FAQ questions as text or chips
  elsewhere on the screen. Selecting a dropdown item should send it right away.
- Remove `buildInAppOnlyPrompt`. The backend now owns the system instructions, so send the
  member's raw question.
- Send the last 10 turns of `conversation` as `history` (`fromUser` → `"user"`, otherwise
  `"model"`).
- Render answers as Markdown (bold and numbered lists).
- Show a typing indicator while waiting.
- When `escalated` is true, show "Sent to support. They'll reply by email." under the answer
  (or "Saved for the support team" when `emailSent` is false).
- Under the latest normal answer, show a "Didn't help? Send to support" text button that
  calls `requestSupport`.
- On `resource-exhausted`, tell the member to try again later and offer the support button.
- If the call fails with any other error, call `submitAIAssistantEscalation` so the question
  still reaches support. If that also fails, offer a `mailto:support@softsolutionstech.com`
  link.

## iOS reference

iOS already uses both callables:

- `Features/Shared/Support/AIAssistantView.swift`
- `Features/Shared/Support/AIAssistantViewModel.swift`
- `Core/Repositories/SupportSubmissionsRepository.swift` (`askAppAssistant`,
  `submitAIAssistantEscalation`)
