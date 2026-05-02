import Foundation
import Combine

enum OwnerRevenueWindow: String, CaseIterable, Identifiable {
    case last7Days
    case last30Days
    case all

    var id: String { rawValue }

    var label: String {
        switch self {
        case .last7Days: return "7D"
        case .last30Days: return "30D"
        case .all: return "All"
        }
    }
}

@MainActor
final class OwnerDashboardViewModel: ObservableObject {
    @Published private(set) var summary = OwnerRevenueSummaryRecord(
        totalCollected: 0,
        balance: 0,
        stripeForexEarnings: 0,
        mobileMoneyHiddenFee: 0,
        blindDateFees: 0,
        eventTicketOwnerFee: 0,
        agentAuthorizationFees: 0,
        agentCashoutOwnerShare: 0,
        marketplacePlatinumFee: 0,
        garageSaleFee: 0,
        otherIncome: 0,
        transactionCount: 0,
        lastUpdate: nil
    )
    @Published private(set) var transactions: [OwnerRevenueTransactionRecord] = []
    @Published var activeWindow: OwnerRevenueWindow = .last30Days
    @Published var isLoading = false
    @Published var isCashingOut = false
    @Published var adminGrantEmail = ""
    @Published var isGrantingAdmin = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    var filteredTransactions: [OwnerRevenueTransactionRecord] {
        switch activeWindow {
        case .all:
            return transactions
        case .last7Days:
            return filterTransactions(days: 7)
        case .last30Days:
            return filterTransactions(days: 30)
        }
    }

    var filteredNetRevenue: Double {
        filteredTransactions.reduce(0) { $0 + $1.amount }
    }

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
            errorMessage = AppErrorMapper.message(from: error)
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
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func grantAdminAccess(ownerId: String) async {
        let cleanEmail = adminGrantEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty else {
            errorMessage = "Admin email is required."
            return
        }
        guard cleanEmail.contains("@") else {
            errorMessage = "Enter a valid email address."
            return
        }

        isGrantingAdmin = true
        errorMessage = nil
        defer { isGrantingAdmin = false }

        do {
            statusMessage = try await repository.grantAdminAccess(ownerId: ownerId, email: cleanEmail)
            adminGrantEmail = ""
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func filterTransactions(days: Int) -> [OwnerRevenueTransactionRecord] {
        guard let cutoff = Calendar.current.date(byAdding: .day, value: -days, to: Date()) else {
            return transactions
        }
        return transactions.filter { tx in
            guard let createdAt = tx.createdAt else { return false }
            return createdAt >= cutoff
        }
    }
}
