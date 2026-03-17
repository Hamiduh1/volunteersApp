import Foundation
import Combine

enum WalletTransactionFilter: String, CaseIterable, Identifiable {
    case all
    case deposit
    case withdrawals
    case mobileMoney

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all:
            return "All"
        case .deposit:
            return "Deposit"
        case .withdrawals:
            return "Withdrawals"
        case .mobileMoney:
            return "Mobile Money"
        }
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
        case .deposit:
            return items.filter(isDeposit)
        case .withdrawals:
            return items.filter(isWithdrawal)
        case .mobileMoney:
            return items.filter(isMobileMoney)
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
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func isDeposit(_ transaction: WalletTransactionRecord) -> Bool {
        if isMobileMoney(transaction) { return false }
        let normalizedTitle = transaction.title.lowercased()
        let normalizedType = transaction.type.lowercased()
        let normalizedSource = (transaction.source ?? "").lowercased()
        return normalizedTitle.contains("deposit")
            || normalizedType.contains("credit")
            || normalizedType.contains("deposit")
            || normalizedType.contains("receive")
            || normalizedType.contains("refund")
            || normalizedSource.contains("deposit")
    }

    private func isWithdrawal(_ transaction: WalletTransactionRecord) -> Bool {
        if isMobileMoney(transaction) { return false }
        let normalizedTitle = transaction.title.lowercased()
        let normalizedType = transaction.type.lowercased()
        let normalizedSource = (transaction.source ?? "").lowercased()
        return normalizedTitle.contains("withdraw")
            || normalizedTitle.contains("cash-out")
            || normalizedTitle.contains("cash out")
            || normalizedType.contains("debit")
            || normalizedType.contains("withdraw")
            || normalizedType.contains("cashout")
            || normalizedType.contains("sent")
            || normalizedSource.contains("withdraw")
    }

    private func isMobileMoney(_ transaction: WalletTransactionRecord) -> Bool {
        let normalizedTitle = transaction.title.lowercased()
        let normalizedType = transaction.type.lowercased()
        let normalizedSource = (transaction.source ?? "").lowercased()
        let normalizedNote = (transaction.note ?? "").lowercased()
        return normalizedSource.contains("mobile")
            || normalizedSource.contains("beneficiary")
            || normalizedTitle.contains("mobile money")
            || normalizedType.contains("mobile")
            || normalizedNote.contains("mobile money")
    }
}

