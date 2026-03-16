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
