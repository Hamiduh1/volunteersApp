import Foundation
import Combine

enum OwnerKycRoleFilter: String, CaseIterable, Identifiable {
    case all
    case volunteer
    case organizer
    case employer
    case admin
    case owner

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class OwnerKYCReviewViewModel: ObservableObject {
    @Published private(set) var items: [OwnerKYCRecord] = []
    @Published var query = ""
    @Published var roleFilter: OwnerKycRoleFilter = .all
    @Published var unverifiedOnly = false
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    var filteredItems: [OwnerKYCRecord] {
        let byRole: [OwnerKYCRecord]
        switch roleFilter {
        case .all:
            byRole = items
        default:
            byRole = items.filter { $0.role.lowercased() == roleFilter.rawValue }
        }

        let byVerification = unverifiedOnly ? byRole.filter { !$0.emailVerified } : byRole
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return byVerification }
        return byVerification.filter { item in
            item.name.lowercased().contains(cleanQuery)
                || item.email.lowercased().contains(cleanQuery)
                || item.role.lowercased().contains(cleanQuery)
                || item.profileStatus.lowercased().contains(cleanQuery)
        }
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchKycUsers()
            if items.isEmpty {
                statusMessage = "No KYC records available."
            }
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

