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
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    var filteredItems: [WalletTransactionRecord] {
        switch filter {
        case .all:
            return items
        case .credit:
            return items.filter(isCredit)
        case .debit:
            return items.filter(isDebit)
        }
    }

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            items = try await repository.fetchTransactions(uid: uid, limit: 120)
            statusMessage = items.isEmpty ? "No transactions found." : "Loaded \(items.count) transactions."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func isCredit(_ transaction: WalletTransactionRecord) -> Bool {
        if transaction.amount > 0 { return true }
        let normalizedType = transaction.type.lowercased()
        let normalizedStatus = transaction.status.lowercased()
        return normalizedType.contains("credit")
            || normalizedType.contains("deposit")
            || normalizedType.contains("receive")
            || normalizedType.contains("refund")
            || normalizedStatus.contains("credit")
    }

    private func isDebit(_ transaction: WalletTransactionRecord) -> Bool {
        if transaction.amount < 0 { return true }
        let normalizedType = transaction.type.lowercased()
        let normalizedStatus = transaction.status.lowercased()
        return normalizedType.contains("debit")
            || normalizedType.contains("withdraw")
            || normalizedType.contains("cashout")
            || normalizedType.contains("sent")
            || normalizedStatus.contains("debit")
    }
}
