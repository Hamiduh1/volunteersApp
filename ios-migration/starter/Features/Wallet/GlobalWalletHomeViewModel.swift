import Foundation
import Combine

@MainActor
final class GlobalWalletHomeViewModel: ObservableObject {
    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var transactions: [WalletTransactionRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchSummary(uid: uid)
            async let txTask = repository.fetchTransactions(uid: uid, limit: 20)
            summary = try await summaryTask
            transactions = try await txTask
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
