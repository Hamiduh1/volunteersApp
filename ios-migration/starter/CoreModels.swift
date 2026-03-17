import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

enum AppUserRole: String, Codable {
    case user
    case volunteer
    case agent
    case organizer
    case employer
    case owner
    case admin
    case associate
    case support
    case supportAssociate = "support_associate"
    case unknown

    init(rawRole: String?) {
        guard let rawRole else {
            self = .unknown
            return
        }
        let normalized = rawRole
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "-", with: "_")
        self = AppUserRole(rawValue: normalized) ?? .unknown
    }
}

enum ApplicationStatus: String, Codable, CaseIterable {
    case pending = "PENDING"
    case viewed = "VIEWED"
    case approved = "APPROVED"
    case accepted = "ACCEPTED"
    case attended = "ATTENDED"
    case completed = "COMPLETED"
    case rejected = "REJECTED"
    case rejectedByEmployer = "REJECTED_BY_EMPLOYER"
    case withdrawn = "WITHDRAWN"
    case waitlisted = "WAITLISTED"
    case unknown = "UNKNOWN"

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = (try? container.decode(String.self)) ?? ""
        let normalized = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
        self = ApplicationStatus(rawValue: normalized) ?? .unknown
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

extension ApplicationStatus {
    var displayTitle: String {
        rawValue
            .replacingOccurrences(of: "_", with: " ")
            .lowercased()
            .split(separator: " ")
            .map { $0.capitalized }
            .joined(separator: " ")
    }

    var isPendingLike: Bool {
        self == .pending || self == .viewed
    }

    var isApprovedLike: Bool {
        self == .approved || self == .accepted || self == .attended || self == .completed
    }

    var isRejectedLike: Bool {
        self == .rejected || self == .rejectedByEmployer || self == .withdrawn
    }
}

struct AppUser: Codable, Identifiable {
    @DocumentID var id: String?
    var uid: String?
    var username: String?
    var name: String?
    var email: String?
    var phoneNumber: String?
    var profileImageUrl: String?
    var userRole: String?
    var role: String?
    var fcmToken: String?
    var wallet: [String: AnyCodable]?
}

struct EventRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var description: String?
    var category: String?
    var eventDateTime: Timestamp?
    var locationName: String?
    var locationAddress: String?
    var payment: Double?
    var eventFee: Double?
    var volunteerLimit: Int?
    var participantsCount: Int?
    var requirements: String?
    var contactInfo: String?
    var status: String?
    var organizerId: String?
    var organizerUid: String?
    var organizerName: String?
    var createdAt: Timestamp?
    var lastUpdatedAt: Timestamp?
}

struct JobRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var organizationName: String?
    var jobTitle: String?
    var employerUid: String?
    var employerId: String?
    var employerName: String?
    var description: String?
    var responsibilities: [String]?
    var locationString: String?
    var locationName: String?
    var locationIsRemote: Bool?
    var category: String?
    var jobType: String?
    var date: String?
    var time: String?
    var volunteersNeeded: Int?
    var postedDate: Timestamp?
    var applicationDeadline: Timestamp?
    var status: String?
    var requiredSkills: [String]?
    var preferredSkills: [String]?
    var salaryOrCompensation: String?
    var totalSlots: Int?
    var slotsFilled: Int?
    var applicantsCount: Int?
}

struct EventApplicationRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var applicationId: String?
    var eventId: String?
    var volunteerId: String?
    var volunteerUid: String?
    var userId: String?
    var volunteerName: String?
    var volunteerEmail: String?
    var organizerId: String?
    var organizerUid: String?
    var status: ApplicationStatus?
    var appliedAt: Timestamp?
    var appliedDate: Timestamp?
    var lastUpdatedAt: Timestamp?
}

struct JobApplicationRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var applicationId: String?
    var jobId: String?
    var jobTitle: String?
    var userId: String?
    var volunteerUid: String?
    var volunteerName: String?
    var volunteerEmail: String?
    var employerUid: String?
    var employerId: String?
    var status: ApplicationStatus?
    var appliedAt: Timestamp?
    var appliedDate: Timestamp?
    var lastUpdatedAt: Timestamp?
}

struct WalletSummary {
    let balance: Double
    let currency: String
}

struct WalletTransactionRecord: Identifiable {
    let id: String
    let title: String
    let type: String
    let amount: Double
    let status: String
    let source: String?
    let createdAt: Date?
    let note: String?
}

struct OrganizerDashboardSnapshot {
    let organizerName: String
    let canGoLive: Bool
    let eventCount: Int
    let totalVolunteers: Int
    let totalEarnings: Double
}

struct OrganizerSummaryEventItem: Identifiable {
    let id: String
    let eventId: String
    let title: String
    let date: Date?
    let volunteerLimit: Int
    let appliedCount: Int
    let pendingCount: Int
    let approvedCount: Int
    let rejectedCount: Int
    let totalEarnings: Double
}

struct OrganizerProfileSetupRecord {
    let uid: String
    let name: String
    let email: String
    let organizationName: String
    let bio: String
    let location: String
    let profileImageUrl: String?
}

struct EmployerProfileSetupRecord {
    let uid: String
    let name: String
    let email: String
    let organizationName: String
    let contactEmail: String
    let description: String
    let profileImageUrl: String?
}

struct ChatConversationRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var participants: [String]?
    var otherParticipantId: String?
    var otherParticipantName: String?
    var otherParticipantProfilePicUrl: String?
    var lastMessage: String?
    var lastMessageText: String?
    var lastMessageTimestamp: Timestamp?
    var lastCallType: String?
    var lastCallStatus: String?
    var lastCallTimestamp: Timestamp?
    var lastCallInitiatorId: String?
    var lastCallReceiverId: String?
}

struct ChatMessageRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var senderId: String?
    var text: String?
    var timestamp: Timestamp?
}

struct UserInvitationRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var senderId: String?
    var senderName: String?
    var inviterName: String?
    var senderEmail: String?
    var senderProfileImageUrl: String?
    var status: String?
    var source: String?
    var context: String?
    var timestamp: Timestamp?
}

struct LiveSessionRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var agoraChannelName: String?
    var hostId: String?
    var hostName: String?
    var title: String?
    var status: String?
    var createdAt: Timestamp?
}

struct PaymentMethodRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var type: String?
    var brand: String?
    var last4: String?
    var holderName: String?
    var status: String?
    var bankName: String?
    var network: String?
    var country: String?
    var phoneNumber: String?
    var externalAccountId: String?
    var stripePaymentMethodId: String?
    var chargeSourceId: String?
    var achDebitEnabled: Bool?
    var achCreditEnabled: Bool?
}

struct PayoutSetupStatusRecord {
    let hasAccount: Bool
    let detailsSubmitted: Bool
    let payoutsEnabled: Bool
    let chargesEnabled: Bool
}

struct CallLogRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var chatId: String?
    var callerId: String?
    var receiverId: String?
    var peerUid: String?
    var peerName: String?
    var type: String? // audio | video
    var callType: String?
    var direction: String? // incoming | outgoing
    var status: String? // dialed | received | missed | rejected
    var startedAt: Timestamp?
    var endedAt: Timestamp?
    var durationSec: Int?
    var durationSeconds: Int?
}

struct AppConfigTextRecord: Codable {
    var title: String?
    var content: String?
    var updatedAt: Timestamp?
}

struct FAQItemRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var question: String?
    var answer: String?
    var tags: [String]?
}

struct SupportItemRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var value: String?
    var type: String?
    var isActive: Bool?

    init(
        id: String? = nil,
        title: String? = nil,
        value: String? = nil,
        type: String? = nil,
        isActive: Bool? = nil
    ) {
        self.id = id
        self.title = title
        self.value = value
        self.type = type
        self.isActive = isActive
    }
}

enum NotificationSettingField: String, CaseIterable, Identifiable {
    case newFollowers
    case jokesPosts
    case liveStreams
    case eventReminders
    case appUpdates

    var id: String { rawValue }

    var title: String {
        switch self {
        case .newFollowers:
            return "New Followers"
        case .jokesPosts:
            return "New MindLoom Posts"
        case .liveStreams:
            return "Live Streams"
        case .eventReminders:
            return "Event Reminders"
        case .appUpdates:
            return "App Updates"
        }
    }

    var description: String {
        switch self {
        case .newFollowers:
            return "Be notified when someone starts following your profile."
        case .jokesPosts:
            return "Get alerts when creators you follow post new MindLoom content."
        case .liveStreams:
            return "Get notified when users you follow go live."
        case .eventReminders:
            return "Stay updated on upcoming events you joined."
        case .appUpdates:
            return "Receive app improvement and feature release announcements."
        }
    }
}

struct NotificationSettingsRecord {
    var newFollowers: Bool = true
    var jokesPosts: Bool = true
    var liveStreams: Bool = true
    var eventReminders: Bool = true
    var appUpdates: Bool = false

    func value(for field: NotificationSettingField) -> Bool {
        switch field {
        case .newFollowers:
            return newFollowers
        case .jokesPosts:
            return jokesPosts
        case .liveStreams:
            return liveStreams
        case .eventReminders:
            return eventReminders
        case .appUpdates:
            return appUpdates
        }
    }

    mutating func set(_ field: NotificationSettingField, enabled: Bool) {
        switch field {
        case .newFollowers:
            newFollowers = enabled
        case .jokesPosts:
            jokesPosts = enabled
        case .liveStreams:
            liveStreams = enabled
        case .eventReminders:
            eventReminders = enabled
        case .appUpdates:
            appUpdates = enabled
        }
    }
}

struct CommunityAlertRecord: Identifiable {
    let id: String
    let title: String
    let description: String
    let source: String
    let imageUrl: String?
    let timestamp: Date
}

enum DatingGender: String, CaseIterable, Identifiable {
    case male = "MALE"
    case female = "FEMALE"
    case other = "OTHER"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .male:
            return "Male"
        case .female:
            return "Female"
        case .other:
            return "Other"
        }
    }
}

enum DatingLookingFor: String, CaseIterable, Identifiable {
    case men = "MEN"
    case women = "WOMEN"
    case everyone = "EVERYONE"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .men:
            return "Men"
        case .women:
            return "Women"
        case .everyone:
            return "Everyone"
        }
    }
}

enum BlindDateUserStatusRecord: String {
    case notJoined = "NotJoined"
    case active = "Active"
    case matched = "Matched"
    case expired = "Expired"
}

struct DatingProfileRecord: Identifiable {
    let id: String
    let uid: String
    let name: String
    let bio: String
    let gender: String
    let lookingFor: String
    let phone: String
    let country: String
    let imageUrls: [String]
    let createdAt: Date?
}

struct BlindDateProfileRecord: Identifiable {
    let id: String
    let userId: String
    let name: String
    let gender: String
    let profilePictureUrl: String
    let media: [String]
    let bio: String
    let postedAt: Date?
    let status: String
}

enum BlindDateInviteDirectionRecord: String {
    case received = "RECEIVED"
    case sent = "SENT"
}

struct BlindDateInvitationRecord: Identifiable {
    let id: String
    let senderId: String
    let senderName: String
    let senderProfilePictureUrl: String
    let recipientId: String
    let recipientName: String
    let recipientProfilePictureUrl: String
    let status: String
    let sentAt: Date?
    let updatedAt: Date?
    let respondedAt: Date?
}

struct BlindDateTimelineItemRecord: Identifiable {
    let id: String
    let direction: BlindDateInviteDirectionRecord
    let otherUserId: String
    let otherUserName: String
    let status: String
    let sentAt: Date?
    let updatedAt: Date?
}

struct OwnerRevenueSummaryRecord {
    let totalCollected: Double
    let balance: Double
    let stripeForexEarnings: Double
    let mobileMoneyHiddenFee: Double
    let blindDateFees: Double
    let agentAuthorizationFees: Double
    let agentCashoutOwnerShare: Double
    let otherIncome: Double
    let transactionCount: Int
    let lastUpdate: Date?
}

struct OwnerRevenueTransactionRecord: Identifiable {
    let id: String
    let source: String
    let amount: Double
    let note: String?
    let createdAt: Date?
}

struct AdminPayoutRequestRecord: Identifiable {
    let id: String
    let requesterId: String
    let requesterName: String
    let amount: Double
    let currency: String
    let status: String
    let destinationLabel: String?
    let createdAt: Date?
}

struct SupportUserSummaryRecord: Identifiable {
    let id: String
    let username: String
    let email: String
    let phone: String
    let role: String
    let walletBalance: Double
    let walletCurrency: String
}

struct SupportComplaintRecord: Identifiable {
    let id: String
    let reason: String
    let eventName: String?
    let reportedEmail: String?
    let reporterDisplayName: String?
    let timestamp: Date?
}

struct SupportAccountTransactionRecord: Identifiable {
    let id: String
    let title: String
    let amount: Double
    let type: String
    let status: String
    let source: String?
    let note: String?
    let timestamp: Date?
}

struct SupportAccountDetailsRecord {
    let userId: String
    let username: String
    let email: String
    let phone: String
    let role: String
    let walletBalance: Double
    let walletCurrency: String
    let payoutsEnabled: Bool
    let chargesEnabled: Bool
    let detailsSubmitted: Bool
    let complaints: [SupportComplaintRecord]
    let transactions: [SupportAccountTransactionRecord]
}

struct OwnerUserReportRecord: Identifiable {
    let id: String
    let sourceCollection: String
    let reportedUserName: String
    let reportedUserEmail: String?
    let eventName: String?
    let reason: String
    let reportingUserDisplayName: String?
    let reportingUserId: String?
    let timestamp: Date?
}

struct OwnerKYCRecord: Identifiable {
    let id: String
    let name: String
    let email: String
    let role: String
    let emailVerified: Bool
    let profileStatus: String
    let updatedAt: Date?
}

struct OwnerFeeSettingsRecord {
    let blindDateFeeUsd: Double
    let agentAuthorizationFeeUsd: Double
    let forexProfitMargin: Double
    let stripeForexDepositProfitMargin: Double
    let mobileMoneyHiddenFeeRate: Double
}

struct OwnerSystemConfigRecord {
    let maintenanceMode: Bool
    let allowNewSignups: Bool
    let enableBlindDate: Bool
    let enableLiveStreams: Bool
    let maxUploadMb: Int
}

struct MindLoomPostRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var authorId: String?
    var authorName: String?
    var authorProfileUrl: String?
    var text: String?
    var mediaUrl: String?
    var mediaType: String?
    var likes: [String]?
    var commentsCount: Int?
    var timestamp: Timestamp?
}

struct MindLoomCommentRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var authorId: String?
    var authorName: String?
    var authorProfileUrl: String?
    var text: String?
    var isOwnerResponse: Bool?
    var timestamp: Timestamp?
}

struct MindLoomProfileSummary {
    let authorId: String
    let authorName: String
    let authorEmail: String
    let authorProfileUrl: String?
    let followersCount: Int
    let followingCount: Int
    let likesCount: Int
}

struct MarketplaceItemRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var description: String?
    var price: Double?
    var category: String?
    var sellerName: String?
    var sellerId: String?
    var sellerPhone: String?
    var imageUrls: [String]?
    var locationName: String?
    var countryCode: String?
    var latitude: Double?
    var longitude: Double?
    var status: String?
    var timestamp: Timestamp?
}

struct AdvertisementRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var description: String?
    var sponsor: String?
    var ownerPhone: String?
    var mediaUrls: [String]?
    var media: [GarageSaleMediaRecord]?
    var targetUrl: String?
    var ownerId: String?
    var timestamp: Timestamp?
}

struct GarageSaleMediaRecord: Codable {
    var url: String?
    var type: String?
    var name: String?
}

enum CommunityAttachmentType: String, CaseIterable, Identifiable {
    case image
    case video
    case document

    var id: String { rawValue }

    var storageType: String { rawValue }

    var contentType: String {
        switch self {
        case .image:
            return "image/jpeg"
        case .video:
            return "video/mp4"
        case .document:
            return "application/pdf"
        }
    }
}

struct CommunityAttachmentDraft: Identifiable, Equatable {
    let id: UUID
    let type: CommunityAttachmentType
    let data: Data
    let fileName: String
    let contentType: String

    init(
        id: UUID = UUID(),
        type: CommunityAttachmentType,
        data: Data,
        fileName: String,
        contentType: String? = nil
    ) {
        self.id = id
        self.type = type
        self.data = data
        self.fileName = fileName
        self.contentType = contentType ?? type.contentType
    }
}

struct GarageSaleRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var title: String?
    var description: String?
    var contactName: String?
    var contactPhone: String?
    var contactEmail: String?
    var address: String?
    var city: String?
    var state: String?
    var postalCode: String?
    var latitude: Double?
    var longitude: Double?
    var media: [GarageSaleMediaRecord]?
    var ownerId: String?
    var timestamp: Timestamp?
}

struct GarageSalePaymentRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var buyerId: String?
    var sellerId: String?
    var garageSaleId: String?
    var amount: Double?
    var status: String?
    var timestamp: Timestamp?
}

struct GalleryUploadRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var name: String?
    var imageUrl: String?
    var imagePathInStorage: String?
    var uploaderId: String?
    var eventId: String?
    var timestamp: Timestamp?
}

struct BeneficiaryRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var name: String?
    var country: String?
    var network: String?
    var phone: String?
    var accountLast4: String?
    var type: String?
    var verificationStatus: String?
}

struct WalletQuoteRecord {
    let rate: Double
    let recipientAmount: Double
    let sourceAmount: Double
    let sourceCurrency: String
    let targetCurrency: String
}

struct AgentCashOutFeeRecord {
    let rate: Double
    let fee: Double
    let totalDebit: Double
}

struct AgentWithdrawalCodeRecord {
    let code: String
    let expiresAt: Date
    let fee: AgentCashOutFeeRecord
}

struct UserProfileRecord {
    let uid: String
    let email: String
    let name: String
    let username: String
    let phoneNumber: String
    let profileImageUrl: String?
    let role: String
    let isEmailVerified: Bool
}

// Firestore dictionaries with mixed values (wallet/settings style documents).
struct AnyCodable: Codable {
    let value: Any

    init(_ value: Any) {
        self.value = value
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let v = try? container.decode(String.self) { value = v; return }
        if let v = try? container.decode(Int.self) { value = v; return }
        if let v = try? container.decode(Double.self) { value = v; return }
        if let v = try? container.decode(Bool.self) { value = v; return }
        if let v = try? container.decode([String: AnyCodable].self) { value = v; return }
        if let v = try? container.decode([AnyCodable].self) { value = v; return }
        value = NSNull()
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let v as String: try container.encode(v)
        case let v as Int: try container.encode(v)
        case let v as Double: try container.encode(v)
        case let v as Bool: try container.encode(v)
        case let v as [String: AnyCodable]: try container.encode(v)
        case let v as [AnyCodable]: try container.encode(v)
        default: try container.encodeNil()
        }
    }
}
