import SwiftUI

struct OwnerDashboardView: View {
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
            ScrollView {
                VStack(spacing: 14) {
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
                }
                .padding()
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
