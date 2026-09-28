import Foundation
import Combine

@MainActor
final class OrganizerWalletViewModel: ObservableObject {
    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var trackedEventIncome = 0.0
    @Published private(set) var incomeSourceNote: String?
    @Published private(set) var transactions: [WalletTransactionRecord] = []
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OrganizerWalletRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let summaryValue = repository.fetchSummary(uid: uid)
            async let transactionsValue = repository.fetchTransactions(uid: uid, limit: 10)
            async let trackedIncomeValue = repository.fetchTrackedEventIncome(uid: uid)
            summary = try await summaryValue
            transactions = try await transactionsValue
            trackedEventIncome = try await trackedIncomeValue

            if summary.balance <= 0 && trackedEventIncome > 0 {
                incomeSourceNote = "Showing tracked income from approved applications while wallet ledger settlement syncs."
            } else {
                incomeSourceNote = nil
            }

            statusMessage = transactions.isEmpty
                ? "Wallet loaded. No transactions found."
                : "Wallet loaded with \(transactions.count) transactions."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

