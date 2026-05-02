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

struct OwnerCountryRevenueTotal: Identifiable {
    var id: String { country }
    let country: String
    let total: Double
}

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
    @Published private(set) var countrySourceTotals: [String: [String: Double]] = [:]
    @Published var activeWindow: OwnerRevenueWindow = .last30Days
    @Published var selectedCountry = ""
    @Published var countrySearchText = ""
    @Published var isLoading = false
    @Published var isCountryAnalyticsLoading = false
    @Published var isCashingOut = false
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

    var availableCountries: [String] {
        countrySourceTotals.keys.sorted { lhs, rhs in
            if lhs.caseInsensitiveCompare("Unknown") == .orderedSame { return false }
            if rhs.caseInsensitiveCompare("Unknown") == .orderedSame { return true }
            return lhs.localizedCaseInsensitiveCompare(rhs) == .orderedAscending
        }
    }

    var filteredCountryOptions: [String] {
        let query = countrySearchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return availableCountries }
        return availableCountries.filter { country in
            country.localizedCaseInsensitiveContains(query)
        }
    }

    var selectedCountryTotal: Double {
        (countrySourceTotals[selectedCountry] ?? [:]).values.reduce(0, +)
    }

    var selectedCountrySourceBreakdown: [(source: String, amount: Double)] {
        (countrySourceTotals[selectedCountry] ?? [:])
            .map { (source: $0.key, amount: $0.value) }
            .sorted { $0.amount > $1.amount }
    }

    var topCountries: [OwnerCountryRevenueTotal] {
        countrySourceTotals.map { country, sources in
            OwnerCountryRevenueTotal(country: country, total: sources.values.reduce(0, +))
        }
        .sorted { lhs, rhs in
            if lhs.total == rhs.total {
                return lhs.country.localizedCaseInsensitiveCompare(rhs.country) == .orderedAscending
            }
            return lhs.total > rhs.total
        }
        .prefix(8)
        .map { $0 }
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchRevenueSummary()
            async let txTask = repository.fetchRevenueTransactions(limit: 250)
            summary = try await summaryTask
            transactions = try await txTask
            await rebuildCountryAnalytics(from: transactions)
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

    func selectCountry(_ country: String) {
        guard countrySourceTotals[country] != nil else { return }
        selectedCountry = country
        countrySearchText = country
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

    private func rebuildCountryAnalytics(from transactions: [OwnerRevenueTransactionRecord]) async {
        isCountryAnalyticsLoading = true
        defer { isCountryAnalyticsLoading = false }

        let relatedUserIds = transactions.compactMap { tx in
            tx.relatedUserId?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        }
        var countriesByUserId: [String: String] = [:]

        if !relatedUserIds.isEmpty {
            do {
                countriesByUserId = try await repository.fetchUserCountries(userIds: relatedUserIds)
            } catch {
                countriesByUserId = [:]
            }
        }

        var grouped: [String: [String: Double]] = [:]
        for tx in transactions {
            let source = tx.source.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty ?? "otherIncome"
            let country: String
            if let userId = tx.relatedUserId?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty {
                country = countriesByUserId[userId] ?? "Unknown"
            } else {
                country = "Unknown"
            }

            var sourceTotals = grouped[country] ?? [:]
            sourceTotals[source, default: 0] += tx.amount
            grouped[country] = sourceTotals
        }

        countrySourceTotals = grouped

        if selectedCountry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || grouped[selectedCountry] == nil {
            if let first = topCountries.first?.country {
                selectedCountry = first
                countrySearchText = first
            } else {
                selectedCountry = ""
            }
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

