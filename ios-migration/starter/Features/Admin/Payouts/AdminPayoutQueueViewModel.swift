import Foundation
import Combine

enum AdminPayoutFilter: String, CaseIterable, Identifiable {
    case open
    case failed
    case completed
    case refunded
    case all

    var id: String { rawValue }

    var label: String {
        rawValue.capitalized
    }

    var statuses: [String]? {
        switch self {
        case .open: return ["PENDING", "PROCESSING", "PENDING_PROVIDER", "PROCESSING_PROVIDER"]
        case .failed: return ["FAILED"]
        case .completed: return ["COMPLETED"]
        case .refunded: return ["REFUNDED"]
        case .all: return nil
        }
    }
}

@MainActor
final class AdminPayoutQueueViewModel: ObservableObject {
    @Published var activeFilter: AdminPayoutFilter = .open
    @Published private(set) var items: [AdminPayoutRequestRecord] = []
    @Published var selectedIds: Set<String> = []
    @Published var isLoading = false
    @Published var isReversing = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.listPayoutRequests(statuses: activeFilter.statuses)
            let validIds = Set(items.map(\.id))
            selectedIds = selectedIds.intersection(validIds)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    func reverseSelected() async {
        guard !selectedIds.isEmpty else {
            errorMessage = "Select at least one payout request."
            return
        }

        isReversing = true
        errorMessage = nil
        defer { isReversing = false }

        do {
            let response = try await repository.reversePayoutRequests(
                ids: Array(selectedIds),
                reason: "Admin dashboard reversal: payout did not settle with provider."
            )
            let totals = (response["totals"] as? [String: Any]) ?? [:]
            let refunded = Int((totals["refunded"] as? NSNumber)?.intValue ?? 0)
            let skipped = Int((totals["skipped"] as? NSNumber)?.intValue ?? 0)
            let errors = Int((totals["errors"] as? NSNumber)?.intValue ?? 0)
            statusMessage = "Reversal complete. Refunded \(refunded), Skipped \(skipped), Errors \(errors)."
            selectedIds = []
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
