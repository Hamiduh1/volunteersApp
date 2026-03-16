import Foundation

enum FirestoreCollection: String, CaseIterable {
    case users
    case organizers
    case employers
    case events
    case jobs
    case applications
    case liveSessions = "live_sessions"
    case joinRequests = "join_requests"
    case chats
    case callSessions = "call_sessions"
    case depositRequests = "deposit_requests"
    case payoutRequests = "payout_requests"
    case marketplaceItems = "marketplace_items"
    case purchaseRequests = "purchase_requests"
    case advertisements
    case garageSales = "garage_sales"
    case garageSalePayments = "garage_sale_payments"
    case appConfig = "app_config"
    case system
    case howToUseTips
    case generalSupportItems = "general_support_items"
    case amlCftContent = "aml_cft_content"
    case userReports = "user_reports"
}

enum FirestoreSubcollection: String {
    case transactions
    case paymentMethods = "payment_methods"
    case beneficiaries
    case invitations
    case chatInvitations = "chat_invitations"
    case blindDateInvitations
    case hostedEvents
    case jokes
    case followers
    case following
    case applications
    case messages
    case callLogs = "call_logs"
    case comments
}

enum FirestoreCollectionGroup: String {
    case applications
    case jokes
}

enum CallableFunction: String, CaseIterable {
    case getAgoraRtcToken
    case requestEmailVerificationCode
    case verifyEmailVerificationCode
    case bootstrapOwnerSelf
    case joinBlindDate
    case acceptBlindDateInvitation
    case declineBlindDateInvitation
    case rejoinBlindDate
    case getSecureExchangeRate
    case createBeneficiaryVerification
    case initiateTransfer
    case payForAgentRole
    case processAgentPayout
    case cashOutAgentEarnings
    case cashOutOwnerRevenue
    case addPaymentMethod
    case attachExternalAccount
    case createUsBankAccountSetupIntent
    case addUsBankAccountFromFinancialConnections
    case requestMobileMoneyMethodVerification
    case createConnectAccount
    case createConnectOnboardingLink
    case getConnectAccountStatus
    case adminAddSupportAssociate
    case supportListUsers
    case supportGetUserAccountDetails
    case adminListPayoutRequests
    case adminReversePayoutRequestsCallable
}

enum StorageFolder: String {
    case profileImages = "profile_images"
    case eventImages = "event_images"
    case blindDateMedia = "blind_date_media"
    case datingImages = "dating_images"
    case jokeImages = "joke_images"
    case jokeVideos = "joke_videos"
    case jokeDocs = "joke_docs"
    case jokeMisc = "joke_misc"
    case ads
    case garageSales = "garage_sales"
    case marketplaceImages = "marketplace_images"
}

enum FirestorePathBuilder {
    static func user(_ uid: String) -> String {
        "\(FirestoreCollection.users.rawValue)/\(uid)"
    }

    static func organizer(_ uid: String) -> String {
        "\(FirestoreCollection.organizers.rawValue)/\(uid)"
    }

    static func employer(_ uid: String) -> String {
        "\(FirestoreCollection.employers.rawValue)/\(uid)"
    }

    static func event(_ eventId: String) -> String {
        "\(FirestoreCollection.events.rawValue)/\(eventId)"
    }

    static func eventApplication(eventId: String, applicationId: String) -> String {
        "\(FirestoreCollection.events.rawValue)/\(eventId)/\(FirestoreSubcollection.applications.rawValue)/\(applicationId)"
    }

    static func job(_ jobId: String) -> String {
        "\(FirestoreCollection.jobs.rawValue)/\(jobId)"
    }

    static func jobApplication(jobId: String, volunteerUid: String) -> String {
        "\(FirestoreCollection.jobs.rawValue)/\(jobId)/\(FirestoreSubcollection.applications.rawValue)/\(volunteerUid)"
    }

    static func rootApplication(_ applicationId: String) -> String {
        "\(FirestoreCollection.applications.rawValue)/\(applicationId)"
    }
}
