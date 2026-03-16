import Foundation
import Combine

@MainActor
final class OrganizerWalletViewModel: ObservableObject {
    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var transactions: [WalletTransactionRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = OrganizerWalletRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryValue = repository.fetchSummary(uid: uid)
            async let transactionsValue = repository.fetchTransactions(uid: uid)
            summary = try await summaryValue
            transactions = try await transactionsValue
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
