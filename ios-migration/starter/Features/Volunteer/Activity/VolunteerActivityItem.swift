import Foundation

enum VolunteerActivityType: String, CaseIterable, Identifiable {
    case all
    case event
    case job

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: return "All"
        case .event: return "Events"
        case .job: return "Jobs"
        }
    }
}

struct VolunteerActivityItem: Identifiable {
    let id: String
    let type: VolunteerActivityType
    let referenceId: String
    let title: String
    let subtitle: String
    let status: ApplicationStatus
    let appliedAt: Date?
}
