import Foundation
import FirebaseFirestore
import FirebaseFirestoreSwift

enum AppUserRole: String, Codable {
    case user
    case volunteer
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
    var employerUid: String?
    var employerId: String?
    var employerName: String?
    var description: String?
    var responsibilities: [String]?
    var locationString: String?
    var locationIsRemote: Bool?
    var category: String?
    var jobType: String?
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
    let createdAt: Date?
    let note: String?
}

struct ChatConversationRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var participants: [String]?
    var lastMessage: String?
    var lastMessageTimestamp: Timestamp?
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
    var senderEmail: String?
    var status: String?
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
}

struct PayoutSetupStatusRecord {
    let hasAccount: Bool
    let detailsSubmitted: Bool
    let payoutsEnabled: Bool
    let chargesEnabled: Bool
}

struct CallLogRecord: Codable, Identifiable {
    @DocumentID var id: String?
    var peerUid: String?
    var peerName: String?
    var type: String? // audio | video
    var direction: String? // incoming | outgoing
    var status: String? // dialed | received | missed | rejected
    var startedAt: Timestamp?
    var endedAt: Timestamp?
    var durationSec: Int?
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
    var targetUrl: String?
    var ownerId: String?
    var timestamp: Timestamp?
}

struct GarageSaleMediaRecord: Codable {
    var url: String?
    var type: String?
    var name: String?
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
