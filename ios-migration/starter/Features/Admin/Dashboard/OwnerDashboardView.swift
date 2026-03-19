import SwiftUI

struct OwnerDashboardView: View {
    private enum SectionAnchor {
        static let dashboard = "owner_dashboard_home"
        static let totals = "owner_totals"
        static let sources = "owner_sources"
        static let operations = "owner_operations"
        static let window = "owner_window"
        static let transactions = "owner_transactions"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = OwnerDashboardViewModel()

    private var totalEarnings: Double {
        if viewModel.summary.totalCollected > 0 { return viewModel.summary.totalCollected }
        return [
            viewModel.summary.stripeForexEarnings,
            viewModel.summary.mobileMoneyHiddenFee,
            viewModel.summary.blindDateFees,
            viewModel.summary.agentAuthorizationFees,
            viewModel.summary.agentCashoutOwnerShare,
            viewModel.summary.otherIncome
        ].reduce(0, +)
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 14) {
                        dashboardHomeCard(proxy: proxy)
                            .id(SectionAnchor.dashboard)

                        if let status = viewModel.statusMessage, !status.isEmpty {
                            Text(status)
                                .font(.subheadline)
                                .foregroundStyle(.green)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Text("Total Earnings")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(currency(totalEarnings))
                                .font(.largeTitle.weight(.bold))
                            Text("Balance: \(currency(viewModel.summary.balance))")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("\(viewModel.activeWindow.label) net revenue: \(currency(viewModel.filteredNetRevenue))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(Color.blue.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .id(SectionAnchor.totals)

                        GroupBox("Revenue Sources") {
                            VStack(spacing: 8) {
                                metricRow("Stripe FX", viewModel.summary.stripeForexEarnings)
                                metricRow("Mobile Hidden Fee", viewModel.summary.mobileMoneyHiddenFee)
                                metricRow("Blind Date Fees", viewModel.summary.blindDateFees)
                                metricRow("Agent Authorization", viewModel.summary.agentAuthorizationFees)
                                metricRow("Agent Cashout Share", viewModel.summary.agentCashoutOwnerShare)
                                metricRow("Other Income", viewModel.summary.otherIncome)
                            }
                            .padding(.top, 4)
                        }
                        .id(SectionAnchor.sources)

                        GroupBox("Operations") {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Revenue events: \(viewModel.summary.transactionCount)")
                                Text("Last update: \(dateText(viewModel.summary.lastUpdate))")
                                    .foregroundStyle(.secondary)
                                Button {
                                    Task { await viewModel.cashOut() }
                                } label: {
                                    if viewModel.isCashingOut {
                                        ProgressView()
                                    } else {
                                        Text("Cash Out Owner Revenue")
                                    }
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(viewModel.summary.balance <= 0 || viewModel.isCashingOut)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .id(SectionAnchor.operations)

                        GroupBox("Transaction Window") {
                            VStack(alignment: .leading, spacing: 8) {
                                Picker("Range", selection: $viewModel.activeWindow) {
                                    ForEach(OwnerRevenueWindow.allCases) { window in
                                        Text(window.label).tag(window)
                                    }
                                }
                                .pickerStyle(.segmented)
                                Text("Showing \(viewModel.filteredTransactions.count) transaction(s)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .id(SectionAnchor.window)

                        GroupBox("Recent Revenue Transactions") {
                            if viewModel.isLoading && viewModel.filteredTransactions.isEmpty {
                                ProgressView("Loading revenue transactions...")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else if viewModel.filteredTransactions.isEmpty {
                                Text("No revenue transactions yet.")
                                    .foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                VStack(spacing: 8) {
                                    ForEach(viewModel.filteredTransactions.prefix(12)) { tx in
                                        HStack {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(tx.source.capitalized)
                                                    .font(.headline)
                                                if let note = tx.note, !note.isEmpty {
                                                    Text(note)
                                                        .font(.caption)
                                                        .foregroundStyle(.secondary)
                                                }
                                                Text(dateText(tx.createdAt))
                                                    .font(.caption2)
                                                    .foregroundStyle(.secondary)
                                            }
                                            Spacer()
                                            Text(currency(tx.amount))
                                                .fontWeight(.semibold)
                                                .foregroundStyle(tx.amount >= 0 ? .green : .red)
                                        }
                                        .padding(.vertical, 4)
                                    }
                                }
                            }
                        }
                        .id(SectionAnchor.transactions)
                    }
                    .padding()
                }
            }
                .navigationTitle("Owner Dashboard")
                .task { await viewModel.refresh() }
                .refreshable { await viewModel.refresh() }
                .alert("Error", isPresented: Binding(
                    get: { viewModel.errorMessage != nil },
                    set: { if !$0 { viewModel.errorMessage = nil } }
                )) {
                    Button("OK", role: .cancel) {}
                } message: {
                    Text(viewModel.errorMessage ?? "Unknown error")
                }
            }
        }
    }

    @ViewBuilder
    private func dashboardHomeCard(proxy: ScrollViewProxy) -> some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 10) {
                Text("Dashboard Home")
                    .font(.headline)

                Text("Android-style owner command center with quick navigation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 8) {
                    dashboardMetric(value: currency(totalEarnings), label: "Total")
                    dashboardMetric(value: currency(viewModel.summary.balance), label: "Balance")
                    dashboardMetric(value: "\(viewModel.summary.transactionCount)", label: "Events")
                }

                HStack(spacing: 10) {
                    dashboardTile(title: "Refresh", icon: "arrow.clockwise.circle.fill") {
                        Task { await viewModel.refresh() }
                    }
                    dashboardTile(title: "Cash Out", icon: "banknote.fill") {
                        Task { await viewModel.cashOut() }
                    }
                    .disabled(viewModel.summary.balance <= 0 || viewModel.isCashingOut)
                }

                HStack(spacing: 8) {
                    dashboardQuickButton("Totals") {
                        withAnimation { proxy.scrollTo(SectionAnchor.totals, anchor: .top) }
                    }
                    dashboardQuickButton("Sources") {
                        withAnimation { proxy.scrollTo(SectionAnchor.sources, anchor: .top) }
                    }
                    dashboardQuickButton("Ops") {
                        withAnimation { proxy.scrollTo(SectionAnchor.operations, anchor: .top) }
                    }
                    dashboardQuickButton("Tx") {
                        withAnimation { proxy.scrollTo(SectionAnchor.transactions, anchor: .top) }
                    }
                }

                HStack(spacing: 8) {
                    NavigationLink {
                        AdminControlsHubView(user: user)
                    } label: {
                        Label("Admin Controls", systemImage: "switch.2")
                            .font(.caption.weight(.semibold))
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        SupportConsoleView(user: user)
                    } label: {
                        Label("Support Console", systemImage: "person.2.badge.gearshape")
                            .font(.caption.weight(.semibold))
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        GlobalWalletHomeView(user: user)
                    } label: {
                        Label("Wallet", systemImage: "wallet.pass.fill")
                            .font(.caption.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.weight(.bold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func dashboardTile(title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundStyle(.blue)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(.secondarySystemGroupedBackground))
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.bordered)
            .font(.caption.weight(.semibold))
    }

    @ViewBuilder
    private func metricRow(_ label: String, _ value: Double) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(currency(value))
                .fontWeight(.semibold)
        }
    }

    private func currency(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        f.currencyCode = "USD"
        return f.string(from: NSNumber(value: value)) ?? "$0.00"
    }

    private func dateText(_ date: Date?) -> String {
        guard let date else { return "--" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
