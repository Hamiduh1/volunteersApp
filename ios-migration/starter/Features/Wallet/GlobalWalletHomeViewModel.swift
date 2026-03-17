import Foundation
import Combine

@MainActor
final class GlobalWalletHomeViewModel: ObservableObject {
    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var transactions: [WalletTransactionRecord] = []
    @Published private(set) var pendingDepositCount: Int = 0
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchSummary(uid: uid)
            async let txTask = repository.fetchTransactions(uid: uid, limit: 20)
            async let pendingTask = repository.fetchPendingDepositCount(uid: uid)
            summary = try await summaryTask
            transactions = try await txTask
            pendingDepositCount = try await pendingTask
            statusMessage = "Wallet loaded. \(transactions.count) recent transactions."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

