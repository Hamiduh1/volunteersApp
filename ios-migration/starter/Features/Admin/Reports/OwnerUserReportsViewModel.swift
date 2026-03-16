import Foundation
import Combine

enum OwnerUserReportsFilter: String, CaseIterable, Identifiable {
    case all
    case userReports = "user_reports"
    case userReportsCamel = "userReports"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: return "All"
        case .userReports: return "user_reports"
        case .userReportsCamel: return "userReports"
        }
    }
}

@MainActor
final class OwnerUserReportsViewModel: ObservableObject {
    @Published private(set) var reports: [OwnerUserReportRecord] = []
    @Published var query = ""
    @Published var activeFilter: OwnerUserReportsFilter = .all
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    var filteredReports: [OwnerUserReportRecord] {
        let bySource: [OwnerUserReportRecord]
        switch activeFilter {
        case .all:
            bySource = reports
        case .userReports:
            bySource = reports.filter { $0.sourceCollection == OwnerUserReportsFilter.userReports.rawValue }
        case .userReportsCamel:
            bySource = reports.filter { $0.sourceCollection == OwnerUserReportsFilter.userReportsCamel.rawValue }
        }

        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return bySource }
        return bySource.filter { report in
            report.reportedUserName.lowercased().contains(cleanQuery)
                || (report.reportedUserEmail?.lowercased().contains(cleanQuery) ?? false)
                || report.reason.lowercased().contains(cleanQuery)
                || (report.reportingUserDisplayName?.lowercased().contains(cleanQuery) ?? false)
                || (report.reportingUserId?.lowercased().contains(cleanQuery) ?? false)
                || (report.eventName?.lowercased().contains(cleanQuery) ?? false)
        }
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            reports = try await repository.fetchUserReports()
            if reports.isEmpty {
                statusMessage = "No reports available."
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
