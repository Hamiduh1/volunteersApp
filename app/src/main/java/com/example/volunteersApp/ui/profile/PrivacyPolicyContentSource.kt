package com.example.volunteersApp.ui.profile

const val PRIVACY_POLICY_LAST_UPDATED = "April 13, 2026"

val DEFAULT_PRIVACY_POLICY_TEXT = """
Volunteers App (LVCA) collects and uses personal information to operate community, messaging, media, wallet, dating, and support features.

What we collect
- Account details such as your name, email address, phone number, profile photo, country, organization details, and account role.
- Profile and community content you choose to create or upload, including posts, comments, invitations, saved items, support requests, and marketplace or event details.
- Media and device data when you use camera, microphone, photo library, live streaming, voice/video calling, or file upload features.
- Location information when you grant location access for maps, nearby features, or location-aware experiences.
- Wallet, transfer, payout, and compliance records needed to process payments, mobile money transactions, fraud checks, sanctions screening, disputes, chargebacks, and legally required reporting.
- Technical and security data such as device identifiers, app integrity signals, IP address, crash logs, authentication events, and usage diagnostics.

How we use data
- To create and manage accounts, sign users in securely, and personalize the in-app experience.
- To enable volunteering, organizer, employer, chat, dating, live media, marketplace, and wallet functionality.
- To process deposits, withdrawals, transfers, mobile money payouts, refunds, reconciliations, and related customer support.
- To monitor abuse, investigate fraud, enforce platform rules, and protect users, staff, and platform systems.
- To comply with financial, regulatory, tax, anti-money-laundering, sanctions, safety, and legal obligations.
- To improve reliability, performance, accessibility, and product quality.

Camera, microphone, and media
- Camera access is used only for features that need image or video capture, such as profile photos, posts, marketplace content, dating profiles, or live/video experiences.
- Microphone access is used only for live audio, calling, streaming, and similar communication features.
- If you do not grant a permission, related features may be unavailable, but the rest of the app should continue to work where possible.

How we share data
- With service providers that help us run the platform, such as cloud hosting, authentication, messaging, storage, mapping, communications, fraud prevention, compliance, and payment/payout providers.
- With payment and payout partners when required to complete wallet, card, bank, or mobile money transactions.
- With law enforcement, regulators, courts, or other parties when required by law or when necessary to protect rights, safety, users, or platform integrity.
- With other users only through the content and profile information you choose to make visible inside the app.

Data retention
- We keep data only for as long as needed to provide the service, maintain security, resolve disputes, satisfy record-keeping duties, and comply with legal or regulatory obligations.
- Some records, especially payment, support, moderation, and compliance records, may be retained for longer where required by law or risk controls.

Your choices
- You can update parts of your profile and account information inside the app.
- You can manage certain privacy preferences from the Privacy & Security area of the app.
- You can request help, account updates, or deletion-related support by contacting us.

Children and age limits
- Volunteers App is not intended for children under 13.
- Certain features, including dating-related features, may require users to be 18+.

Contact us
- Email: support@softsolutionstech.com
- Website: https://softsolutionstech.com

By using Volunteers App, you acknowledge this Privacy Policy and consent to the handling of data described here.
""".trimIndent()

fun isPrivacyPolicyComprehensive(text: String): Boolean {
    val normalized = text.lowercase()
    val requiredMarkers = listOf(
        "camera",
        "microphone",
        "location",
        "wallet",
        "payment",
        "retention",
        "contact",
        "support@"
    )
    return requiredMarkers.all { marker -> normalized.contains(marker) }
}
