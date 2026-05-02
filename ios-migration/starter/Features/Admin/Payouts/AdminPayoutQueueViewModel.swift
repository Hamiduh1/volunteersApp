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
    @Published var query = ""
    @Published var reversalReason = "Admin dashboard reversal: payout did not settle with provider."
    @Published private(set) var items: [AdminPayoutRequestRecord] = []
    @Published var selectedIds: Set<String> = []
    @Published var isLoading = false
    @Published var isReversing = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()
    private let minimumReasonLength = 12

    var filteredItems: [AdminPayoutRequestRecord] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return items }
        return items.filter { item in
            item.requesterName.lowercased().contains(cleanQuery)
                || item.requesterId.lowercased().contains(cleanQuery)
                || item.status.lowercased().contains(cleanQuery)
                || item.currency.lowercased().contains(cleanQuery)
                || (item.destinationLabel?.lowercased().contains(cleanQuery) ?? false)
        }
    }

    var cleanedReason: String {
        reversalReason.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var canReverse: Bool {
        !selectedIds.isEmpty
            && !isReversing
            && cleanedReason.count >= minimumReasonLength
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.listPayoutRequests(statuses: activeFilter.statuses)
            let validIds = Set(items.map(\.id))
            selectedIds = selectedIds.intersection(validIds)
            statusMessage = items.isEmpty ? "No payout requests in this filter." : "Loaded \(items.count) payout request(s)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    func selectVisible() {
        let visibleIds = Set(filteredItems.map(\.id))
        selectedIds.formUnion(visibleIds)
    }

    func clearSelection() {
        selectedIds = []
    }

    func reverseSelected() async {
        guard !selectedIds.isEmpty else {
            errorMessage = "Select at least one payout request."
            return
        }
        guard cleanedReason.count >= minimumReasonLength else {
            errorMessage = "Provide a reversal reason with at least \(minimumReasonLength) characters."
            return
        }

        isReversing = true
        errorMessage = nil
        statusMessage = nil
        defer { isReversing = false }

        do {
            let allowCompleted = items.contains { item in
                selectedIds.contains(item.id) && item.status.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "COMPLETED"
            }
            let response = try await repository.reversePayoutRequests(
                ids: Array(selectedIds),
                reason: cleanedReason,
                allowCompleted: allowCompleted
            )
            let totals = (response["totals"] as? [String: Any]) ?? [:]
            let refunded = Int((totals["refunded"] as? NSNumber)?.intValue ?? 0)
            let skipped = Int((totals["skipped"] as? NSNumber)?.intValue ?? 0)
            let errors = Int((totals["errors"] as? NSNumber)?.intValue ?? 0)
            statusMessage = "Reversal complete. Refunded \(refunded), Skipped \(skipped), Errors \(errors)."
            selectedIds = []
            await refresh()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
