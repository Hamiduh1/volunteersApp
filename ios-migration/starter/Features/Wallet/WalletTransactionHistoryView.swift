import SwiftUI

struct WalletTransactionHistoryView: View {
    private enum SectionAnchor {
        static let dashboard = "history_dashboard"
        static let filters = "history_filters"
        static let receipts = "history_receipts"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = WalletTransactionHistoryViewModel()

    var body: some View {
        ScrollViewReader { proxy in
            List {
                Section {
                    dashboardHomeCard(proxy: proxy)
                        .id(SectionAnchor.dashboard)
                }

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Picker("Filter", selection: $viewModel.filter) {
                        ForEach(WalletTransactionFilter.allCases) { option in
                            Text(filterTitle(option)).tag(option)
                        }
                    }
                    .pickerStyle(.segmented)
                    .id(SectionAnchor.filters)
                }

                Section("Receipts & Activity") {
                    if viewModel.isLoading && viewModel.filteredItems.isEmpty {
                        ProgressView("Loading transactions...")
                    } else if viewModel.filteredItems.isEmpty {
                        Text(emptyStateMessage)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.filteredItems) { tx in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(tx.title)
                                        .font(.headline)
                                    Spacer()
                                    Text(formattedAmount(tx.amount))
                                        .fontWeight(.semibold)
                                        .foregroundStyle(tx.amount >= 0 ? .green : .red)
                                }
                                Text("\(tx.type.uppercased()) - \(tx.status)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                if let note = tx.note, !note.isEmpty {
                                    Text(note)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                if let date = tx.createdAt {
                                    Text(date.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                .id(SectionAnchor.receipts)
            }
            .navigationTitle("Receipts & Activity")
            .task { await viewModel.refresh(uid: user.uid) }
            .refreshable { await viewModel.refresh(uid: user.uid) }
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
    private func dashboardHomeCard(proxy: ScrollViewProxy) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)

            Text("Android-style transaction history navigation.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 8) {
                dashboardMetric(value: "\(viewModel.items.count)", label: "All")
                dashboardMetric(value: "\(depositCount)", label: "Deposit")
                dashboardMetric(value: "\(withdrawalCount)", label: "Withdraw")
                dashboardMetric(value: "\(mobileMoneyCount)", label: "Mobile")
            }

            HStack(spacing: 8) {
                filterButton("All", filter: .all)
                filterButton("Deposit", filter: .deposit)
                filterButton("Withdraws", filter: .withdrawals)
                filterButton("Mobile", filter: .mobileMoney)
            }

            HStack(spacing: 8) {
                Button("Filters") {
                    withAnimation { proxy.scrollTo(SectionAnchor.filters, anchor: .top) }
                }
                .buttonStyle(.bordered)

                Button("Receipts") {
                    withAnimation { proxy.scrollTo(SectionAnchor.receipts, anchor: .top) }
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(.vertical, 2)
    }

    private var depositCount: Int {
        viewModel.items.filter(viewModel.isDeposit).count
    }

    private var withdrawalCount: Int {
        viewModel.items.filter(viewModel.isWithdrawal).count
    }

    private var mobileMoneyCount: Int {
        viewModel.items.filter(viewModel.isMobileMoney).count
    }

    private var emptyStateMessage: String {
        switch viewModel.filter {
        case .all:
            return "No activity yet."
        case .deposit:
            return "No deposits yet."
        case .withdrawals:
            return "No withdrawals yet."
        case .mobileMoney:
            return "No mobile money activity."
        }
    }

    private func filterTitle(_ option: WalletTransactionFilter) -> String {
        switch option {
        case .all:
            return "All (\(viewModel.items.count))"
        case .deposit:
            return "Deposit (\(depositCount))"
        case .withdrawals:
            return "Withdraw (\(withdrawalCount))"
        case .mobileMoney:
            return "Mobile (\(mobileMoneyCount))"
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private func filterButton(_ title: String, filter: WalletTransactionFilter) -> some View {
        Button(title) {
            viewModel.filter = filter
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.small)
        .tint(viewModel.filter == filter ? .blue : .gray)
    }

    private func formattedAmount(_ amount: Double) -> String {
        let sign = amount >= 0 ? "+" : "-"
        return "\(sign)\(String(format: "%.2f", abs(amount)))"
    }
}
