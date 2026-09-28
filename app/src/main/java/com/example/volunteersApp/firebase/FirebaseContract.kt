package com.example.volunteersApp.firebase

/**
 * Firestore / Storage / callable names aligned with iOS `FirebaseContract.swift`
 * in `_ios_clean_worktree/ios-migration/starter/`. Keep string values in sync with that file
 * and deployed Cloud Functions; do not change iOS from Android.
 */

object FirestoreCollection {
    const val USERS = "users"
    const val WALLETS = "wallets"
    const val ORGANIZERS = "organizers"
    const val EMPLOYERS = "employers"
    const val EVENTS = "events"
    const val JOBS = "jobs"
    /** Swift `jobPosts` — same raw value as iOS `FirestoreCollection.jobPosts`. */
    const val JOB_POSTS = "jobPosts"
    const val APPLICATIONS = "applications"
    const val EVENT_APPLICATIONS = "event_applications"
    const val LIVE_SESSIONS = "live_sessions"
    const val JOIN_REQUESTS = "join_requests"
    const val CHATS = "chats"
    const val CALL_SESSIONS = "call_sessions"
    const val DEPOSIT_REQUESTS = "deposit_requests"
    const val PAYOUT_REQUESTS = "payout_requests"
    const val PROVIDER_TRANSACTIONS = "provider_transactions"
    const val PROVIDER_WEBHOOKS = "provider_webhooks"
    const val MARKETPLACE_ITEMS = "marketplace_items"
    const val PURCHASE_REQUESTS = "purchase_requests"
    const val ADVERTISEMENTS = "advertisements"
    const val GARAGE_SALES = "garage_sales"
    const val GARAGE_SHOP_PROFILES = "garage_shop_profiles"
    const val GARAGE_SALE_PAYMENTS = "garage_sale_payments"
    const val APP_CONFIG = "app_config"
    /** Legacy Android camelCase config root (e.g. privacy policy); not in iOS enum. */
    const val APP_CONFIG_LEGACY_CAMEL = "appConfig"
    const val SYSTEM = "system"
    const val SETTINGS = "settings"
    const val HOW_TO_USE_TIPS = "howToUseTips"
    /** Matches iOS `FirestoreCollection.galleryUploads` (Firestore metadata collection). */
    const val GALLERY_UPLOADS = "galleryUploads"
    const val GENERAL_SUPPORT_ITEMS = "general_support_items"
    const val AML_CFT_CONTENT = "aml_cft_content"
    const val USER_REPORTS = "user_reports"
    const val USER_REPORTS_LEGACY = "userReports"
    const val BLIND_DATE_PROFILES = "blindDateProfiles"
    const val BLIND_DATE_INVITATIONS = "blindDateInvitations"
    const val BLIND_DATE_SENT_INVITATIONS = "blindDateSentInvitations"
    const val DATING_PROFILES = "dating_profiles"
    /** App store / ratings; not in iOS starter `FirebaseContract.swift` — keep path stable. */
    const val REVIEWS = "reviews"
}

object FirestoreSubcollection {
    const val TRANSACTIONS = "transactions"
    const val PAYMENT_METHODS = "payment_methods"
    const val BENEFICIARIES = "beneficiaries"
    const val INVITATIONS = "invitations"
    const val CHAT_INVITATIONS = "chat_invitations"
    const val BLIND_DATE_INVITATIONS = "blindDateInvitations"
    const val BLIND_DATE_SENT_INVITATIONS = "blindDateSentInvitations"
    const val HOSTED_EVENTS = "hostedEvents"
    const val JOKES = "jokes"
    const val FOLLOWERS = "followers"
    const val FOLLOWING = "following"
    const val APPLICATIONS = "applications"
    /** Legacy event applications subcollection per iOS `FirestoreSubcollection.eventApplications`. */
    const val EVENT_APPLICATIONS = "event_applications"
    const val MESSAGES = "messages"
    const val CALL_LOGS = "call_logs"
    /** Per-user call log feed for Social Inbox Calls tab (iOS CallsRepository primary path). */
    const val CALL_HISTORY = "call_history"
    /** Tombstones for hidden call-history rows (iOS parity). */
    const val CALL_HISTORY_HIDDEN = "call_history_hidden"
    /** Per-user incoming call signaling mirror (CallSessionViewModel listens here). */
    const val INCOMING_CALL_SESSIONS = "incoming_call_sessions"
    const val COMMENTS = "comments"
    /**
     * User-scoped settings at `users/{uid}/settings/...`.
     * Same segment as top-level [FirestoreCollection.SETTINGS] but different path role.
     */
    const val SETTINGS = "settings"
    /** Live session engagement; not in iOS starter enum — path used by Android + backend. */
    const val LIKES = "likes"
    /** Live session viewers heartbeat collection (iOS parity). */
    const val VIEWERS = "viewers"
    /** Host-blocked viewers for a live session (iOS parity). */
    const val BLOCKED_USERS = "blocked_users"
    /** Android jokes “saved” list; not declared on iOS starter contract — keep path stable. */
    const val SAVED_JOKES = "saved_jokes"
}

object FirestoreCollectionGroup {
    const val APPLICATIONS = "applications"
    const val JOKES = "jokes"
    const val INVITATIONS = "invitations"
}

object CallableFunction {
    const val GET_AGORA_RTC_TOKEN = "getAgoraRtcToken"
    const val CREATE_CALL_SESSION = "createCallSession"
    const val REQUEST_EMAIL_VERIFICATION_CODE = "requestEmailVerificationCode"
    const val VERIFY_EMAIL_VERIFICATION_CODE = "verifyEmailVerificationCode"
    const val BOOTSTRAP_OWNER_SELF = "bootstrapOwnerSelf"
    const val ENSURE_DATING_ELIGIBILITY = "ensureDatingEligibility"
    const val JOIN_BLIND_DATE = "joinBlindDate"
    const val ACCEPT_BLIND_DATE_INVITATION = "acceptBlindDateInvitation"
    const val DECLINE_BLIND_DATE_INVITATION = "declineBlindDateInvitation"
    const val REJOIN_BLIND_DATE = "rejoinBlindDate"
    const val GET_SECURE_EXCHANGE_RATE = "getSecureExchangeRate"
    const val CREATE_BENEFICIARY_VERIFICATION = "createBeneficiaryVerification"
    const val APPLY_APPROVED_BENEFICIARY_VERIFICATION = "applyApprovedBeneficiaryVerification"
    const val INITIATE_TRANSFER = "initiateTransfer"
    const val SEND_TO_APP_USER = "sendToAppUser"
    // Transfer quote / receipt / Afriex poll (iOS GlobalWalletRepository parity; callables only — no Afriex REST from app)
    const val GET_WALLET_TRANSFER_QUOTE = "getWalletTransferQuote"
    const val GET_WALLET_TRANSFER_RECEIPT = "getWalletTransferReceipt"
    const val SEND_WALLET_TRANSFER_RECEIPT = "sendWalletTransferReceipt"
    const val POLL_AFRIEX_TRANSACTION_STATUS = "pollAfriexTransactionStatus"
    const val GET_MOBILE_MONEY_SUPPORTED_COUNTRIES = "getMobileMoneySupportedCountries"
    const val ENSURE_AFRIEX_CUSTOMER = "ensureAfriexCustomer"
    const val VERIFY_AFRIEX_CUSTOMER = "verifyAfriexCustomer"
    const val GET_AFRIEX_INSTITUTIONS = "getAfriexInstitutions"
    const val RESOLVE_AFRIEX_ACCOUNT = "resolveAfriexAccount"
    const val SEARCH_RECIPIENT_ADDRESS = "searchRecipientAddress"
    const val SAVE_VERIFIED_BENEFICIARY = "saveVerifiedBeneficiary"
    const val CREATE_BANK_RECIPIENT_VERIFICATION = "createBankRecipientVerification"
    const val APPLY_APPROVED_BANK_RECIPIENT_VERIFICATION = "applyApprovedBankRecipientVerification"
    const val GET_AFRIEX_RATES = "getAfriexRates"
    const val PAY_FOR_AGENT_ROLE = "payForAgentRole"
    const val PROCESS_AGENT_PAYOUT = "processAgentPayout"
    const val CASH_OUT_AGENT_EARNINGS = "cashOutAgentEarnings"
    const val CASH_OUT_OWNER_REVENUE = "cashOutOwnerRevenue"
    const val SYNC_AFRIEX_BUSINESS_WALLET_MIRROR = "syncAfriexBusinessWalletMirror"
    const val SYNC_STRIPE_PLATFORM_INCOME_MIRROR = "syncStripePlatformIncomeMirror"
    const val TOPUP_AFRIEX_SANDBOX_BUSINESS_WALLET = "topupAfriexSandboxBusinessWallet"
    const val CREATE_AFRIEX_SANDBOX_CHECKOUT_SESSION = "createAfriexSandboxCheckoutSession"
    const val OWNER_SAVE_FEE_SETTINGS = "ownerSaveFeeSettings"
    const val OWNER_SAVE_SYSTEM_CONFIG = "ownerSaveSystemConfig"
    const val OWNER_GRANT_ADMIN_BY_EMAIL = "ownerGrantAdminByEmail"
    const val ADD_PAYMENT_METHOD = "addPaymentMethod"
    const val SAVE_APP_USER_RECEIVE_ROUTE = "saveAppUserReceiveRoute"
    const val ATTACH_EXTERNAL_ACCOUNT = "attachExternalAccount"
    const val CREATE_US_BANK_ACCOUNT_SETUP_INTENT = "createUsBankAccountSetupIntent"
    const val ADD_US_BANK_ACCOUNT_FROM_FINANCIAL_CONNECTIONS = "addUsBankAccountFromFinancialConnections"
    const val REQUEST_MOBILE_MONEY_METHOD_VERIFICATION = "requestMobileMoneyMethodVerification"
    const val CREATE_CONNECT_ACCOUNT = "createConnectAccount"
    const val CREATE_CONNECT_ONBOARDING_LINK = "createConnectOnboardingLink"
    const val GET_CONNECT_ACCOUNT_STATUS = "getConnectAccountStatus"
    const val ADMIN_ADD_SUPPORT_ASSOCIATE = "adminAddSupportAssociate"
    const val SUPPORT_LIST_USERS = "supportListUsers"
    const val SUPPORT_GET_USER_ACCOUNT_DETAILS = "supportGetUserAccountDetails"
    const val ADMIN_LIST_PAYOUT_REQUESTS = "adminListPayoutRequests"
    const val ADMIN_REVERSE_PAYOUT_REQUESTS_CALLABLE = "adminReversePayoutRequestsCallable"

    // Android app also calls these deployed functions (not in iOS `CallableFunction` enum).
    const val CREATE_AGENT_PAYOUT_CODE = "createAgentPayoutCode"
    const val PROCESS_AGENT_CASH_IN = "processAgentCashIn"
    const val REQUEST_MOBILE_MONEY_CASH_IN = "requestMobileMoneyCashIn"
    const val REQUEST_MOBILE_MONEY_CASH_OUT = "requestMobileMoneyCashOut"
    const val REQUEST_MOBILE_MONEY_PHONE_OTP = "requestMobileMoneyPhoneOtp"
    const val VERIFY_MOBILE_MONEY_PHONE_OTP = "verifyMobileMoneyPhoneOtp"
    const val POST_SPONSORED_AD = "postSponsoredAd"
    const val DELETE_PAYMENT_METHOD = "deletePaymentMethod"
    const val SET_DEFAULT_PAYMENT_METHOD = "setDefaultPaymentMethod"
    const val REQUEST_EXTERNAL_DEPOSIT = "requestExternalDeposit"
    const val GET_RECIPIENT_PAYOUT_METHODS = "getRecipientPayoutMethods"
    const val GET_VOLUNTEER_LISTINGS = "getVolunteerListings"
    const val APPLY_FOR_EVENT = "applyForEvent"
    const val APPLY_FOR_JOB = "applyForJob"
    const val DELETE_ORGANIZER_EVENT = "deleteOrganizerEvent"
    const val DELETE_EMPLOYER_JOB = "deleteEmployerJob"
    const val REQUEST_MARKETPLACE_PURCHASE = "requestMarketplacePurchase"
    const val REQUEST_GARAGE_SALE_PURCHASE = "requestGarageSalePurchase"
    const val ADMIN_LIST_STAFF_ACCOUNTS = "adminListStaffAccounts"
    const val ADMIN_UPDATE_STAFF_ACCOUNT_STATUS = "adminUpdateStaffAccountStatus"
    const val ADMIN_BACKFILL_MIND_LOOM_COMMENTS_COUNT = "adminBackfillMindLoomCommentsCount"

    // Agent customer portal (iOS parity)
    const val AGENT_LOOKUP_CUSTOMER_BY_PHONE = "agentLookupCustomerByPhone"
    const val AGENT_START_CUSTOMER_WITHDRAWAL = "agentStartCustomerWithdrawal"
    const val AGENT_VERIFY_CUSTOMER_WITHDRAWAL_OTP = "agentVerifyCustomerWithdrawalOtp"
    const val AGENT_REQUEST_CUSTOMER_WITHDRAWAL_APPROVAL = "agentRequestCustomerWithdrawalApproval"
    const val AGENT_GET_CUSTOMER_WITHDRAWAL_SESSION = "agentGetCustomerWithdrawalSession"
    const val AGENT_CONFIRM_CUSTOMER_CASH_HANDOVER = "agentConfirmCustomerCashHandover"
    const val AGENT_CANCEL_CUSTOMER_WITHDRAWAL = "agentCancelCustomerWithdrawal"
    const val AGENT_PROCESS_CUSTOMER_DEPOSIT_BY_PHONE = "agentProcessCustomerDepositByPhone"

    // Live sessions (iOS `FirebaseContract.swift` parity)
    const val CREATE_LIVE_SHARE_ACCESS_LINK = "createLiveShareAccessLink"
    const val CREATE_LIVE_REPLAY_ACCESS_LINK = "createLiveReplayAccessLink"

    // Social inbox push (iOS SessionManager parity)
    const val REGISTER_USER_PUSH_TOKENS = "registerUserPushTokens"
    const val GET_CONVERSATION_MESSAGING_AVAILABILITY = "getConversationMessagingAvailability"
    const val GET_CONVERSATION_CALL_AVAILABILITY = "getConversationCallAvailability"
    const val GET_SOCIAL_INBOX_PUSH_DIAGNOSTICS = "getSocialInboxPushDiagnostics"
}

/** Hosted web + app deep links for live/replay (iOS `LiveRepository` parity). */
object LiveWebContract {
    const val SHARE_HOST = "https://softsolutionstech.com"
    const val LIVE_WEB_PATH = "/live"
    const val APP_DEEP_LINK = "volunteersapp://live"
}

object StorageFolder {
    const val PROFILE_IMAGES = "profile_images"
    const val EVENT_IMAGES = "event_images"
    const val BLIND_DATE_MEDIA = "blind_date_media"
    const val DATING_IMAGES = "dating_images"
    const val GALLERY_UPLOADS = "gallery_uploads"
    const val JOKE_IMAGES = "joke_images"
    const val JOKE_VIDEOS = "joke_videos"
    const val JOKE_DOCS = "joke_docs"
    const val JOKE_MISC = "joke_misc"
    const val ADS = "ads"
    const val GARAGE_SALES = "garage_sales"
    const val MARKETPLACE_IMAGES = "marketplace_images"
    const val CHAT_MEDIA = "chat_media"
    /** Matches iOS Storage path `chat_attachments/{chatId}/{messageId}.ext`. */
    const val CHAT_ATTACHMENTS = "chat_attachments"

    /** `chat_attachments/{chatId}/{messageId}.{ext}` — e.g. ext `jpg` or `mp4`. */
    fun chatAttachment(chatId: String, messageId: String, ext: String): String {
        val clean = ext.trim().removePrefix(".")
        return "$CHAT_ATTACHMENTS/$chatId/$messageId.$clean"
    }

    /** `profile_images/{userId}/{fileName}` — matches typical Storage rules. */
    fun profileImage(userId: String, fileName: String): String = "$PROFILE_IMAGES/$userId/$fileName"

    /** `event_images/{organizerUid}/{eventId}/{fileName}` */
    fun eventImage(organizerUid: String, eventId: String, fileName: String): String =
        "$EVENT_IMAGES/$organizerUid/$eventId/$fileName"

    /** `gallery_uploads/{userId}/{fileName}` */
    fun galleryUpload(userId: String, fileName: String): String = "$GALLERY_UPLOADS/$userId/$fileName"

    /** `marketplace_images/{sellerUid}/{fileName}` */
    fun marketplaceImage(sellerUid: String, fileName: String): String =
        "$MARKETPLACE_IMAGES/$sellerUid/$fileName"

    /** `chat_media/{uid}/{fileName}` */
    fun chatMedia(uid: String, fileName: String): String = "$CHAT_MEDIA/$uid/$fileName"

    /** `dating_images/{uid}/{fileName}` */
    fun datingImage(uid: String, fileName: String): String = "$DATING_IMAGES/$uid/$fileName"

    /** `blind_date_media/{uid}/{suffix}` */
    fun blindDateMedia(uid: String, suffix: String): String = "$BLIND_DATE_MEDIA/$uid/$suffix"

    /** `ads/{uid}/{objectId}` — objectId is usually a UUID; extension may be added by metadata. */
    fun adUpload(uid: String, objectId: String): String = "$ADS/$uid/$objectId"

    /** `garage_sales/{uid}/{objectId}` */
    fun garageSaleUpload(uid: String, objectId: String): String = "$GARAGE_SALES/$uid/$objectId"

    /**
     * `{subfolder}/{uid}/{leaf}` where [subfolder] is one of [JOKE_IMAGES], [JOKE_VIDEOS], etc.
     */
    fun jokeMedia(subfolder: String, uid: String, leaf: String): String = "$subfolder/$uid/$leaf"
}

/** Well-known documents under [FirestoreCollection.APP_CONFIG]. */
object FirestoreAppConfigDocument {
    const val FEE_SETTINGS = "fee_settings"
    const val SYSTEM_CONFIG = "system_config"
    const val TERMS_AND_CONDITIONS = "terms_and_conditions"
    const val PRIVACY_POLICY = "privacy_policy"
}

/** Well-known documents under [FirestoreCollection.SYSTEM]. */
object FirestoreSystemDocument {
    const val PLATFORM_REVENUE = "platform_revenue"
    const val AFRIEX_BUSINESS_WALLET = "afriex_business_wallet"
    const val STRIPE_PLATFORM_INCOME_MIRROR = "stripe_platform_income_mirror"
}

/** Documents under `users/{uid}/settings/`. */
object FirestoreUserSettingsDocument {
    const val NOTIFICATIONS = "notifications"
}

object FirestorePathBuilder {
    fun user(uid: String): String = "${FirestoreCollection.USERS}/$uid"
    fun organizer(uid: String): String = "${FirestoreCollection.ORGANIZERS}/$uid"
    fun employer(uid: String): String = "${FirestoreCollection.EMPLOYERS}/$uid"
    fun event(eventId: String): String = "${FirestoreCollection.EVENTS}/$eventId"
    fun eventApplication(eventId: String, applicationId: String): String =
        "${FirestoreCollection.EVENTS}/$eventId/${FirestoreSubcollection.APPLICATIONS}/$applicationId"

    fun job(jobId: String): String = "${FirestoreCollection.JOBS}/$jobId"
    fun jobApplication(jobId: String, volunteerUid: String): String =
        "${FirestoreCollection.JOBS}/$jobId/${FirestoreSubcollection.APPLICATIONS}/$volunteerUid"

    fun rootApplication(applicationId: String): String =
        "${FirestoreCollection.APPLICATIONS}/$applicationId"
}
