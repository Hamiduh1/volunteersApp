import Foundation
import Combine

@MainActor
final class OwnerDashboardViewModel: ObservableObject {
    @Published private(set) var summary = OwnerRevenueSummaryRecord(
        totalCollected: 0,
        balance: 0,
        stripeForexEarnings: 0,
        mobileMoneyHiddenFee: 0,
        blindDateFees: 0,
        agentAuthorizationFees: 0,
        agentCashoutOwnerShare: 0,
        otherIncome: 0,
        transactionCount: 0,
        lastUpdate: nil
    )
    @Published private(set) var transactions: [OwnerRevenueTransactionRecord] = []
    @Published var isLoading = false
    @Published var isCashingOut = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchRevenueSummary()
            async let txTask = repository.fetchRevenueTransactions()
            summary = try await summaryTask
            transactions = try await txTask
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func cashOut() async {
        isCashingOut = true
        errorMessage = nil
        defer { isCashingOut = false }

        do {
            statusMessage = try await repository.cashOutOwnerRevenue()
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
