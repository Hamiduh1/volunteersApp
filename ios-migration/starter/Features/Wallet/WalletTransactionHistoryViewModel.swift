import Foundation
import Combine

enum WalletTransactionFilter: String, CaseIterable, Identifiable {
    case all
    case credit
    case debit

    var id: String { rawValue }

    var title: String {
        rawValue.capitalized
    }
}

@MainActor
final class WalletTransactionHistoryViewModel: ObservableObject {
    @Published private(set) var items: [WalletTransactionRecord] = []
    @Published var filter: WalletTransactionFilter = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    var filteredItems: [WalletTransactionRecord] {
        switch filter {
        case .all:
            return items
        case .credit:
            return items.filter { $0.amount > 0 || $0.type.lowercased().contains("credit") }
        case .debit:
            return items.filter { $0.amount < 0 || $0.type.lowercased().contains("debit") }
        }
    }

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchTransactions(uid: uid, limit: 120)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
