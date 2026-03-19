import SwiftUI

struct OrganizerWalletView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerWalletViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                balanceCard
                dashboardHomeCard
                recentTransactionsCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Organizer Wallet")
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

    @ViewBuilder
    private var balanceCard: some View {
        OrganizerWalletCard(background: .blue.opacity(0.92)) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Available Balance")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.82))
                Text("\(viewModel.summary.currency) \(String(format: "%.2f", viewModel.summary.balance))")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                if viewModel.trackedEventIncome > 0 {
                    Text("Tracked Event Income: \(viewModel.summary.currency) \(String(format: "%.2f", viewModel.trackedEventIncome))")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.92))
                }
                if let note = viewModel.incomeSourceNote, !note.isEmpty {
                    Text(note)
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.86))
                }
            }
        }
    }

    @ViewBuilder
    private var dashboardHomeCard: some View {
        OrganizerWalletCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Dashboard Home")
                    .font(.headline)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.green)
                }

                HStack(spacing: 10) {
                    NavigationLink {
                        WalletTransactView(user: user)
                    } label: {
                        dashboardTile(
                            title: "Withdraw / Transact",
                            subtitle: "Send, withdraw, and transfer",
                            icon: "arrow.left.arrow.right.circle.fill"
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        dashboardTile(
                            title: "Full History",
                            subtitle: "View all transaction records",
                            icon: "clock.arrow.circlepath"
                        )
                    }
                    .buttonStyle(.plain)
                }

                NavigationLink {
                    PaymentMethodsView(user: user)
                } label: {
                    actionRow(
                        title: "Payment Methods",
                        subtitle: "Cards, banks, and payout setup",
                        icon: "creditcard.fill"
                    )
                }

                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .mobileMoney)
                } label: {
                    actionRow(
                        title: "Mobile Money Operations",
                        subtitle: "Cash in / cash out workflow",
                        icon: "iphone.gen3.radiowaves.left.and.right"
                    )
                }

                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .agent)
                } label: {
                    actionRow(
                        title: "Agent Portal",
                        subtitle: "Agent code and payout operations",
                        icon: "person.badge.shield.checkmark.fill"
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var recentTransactionsCard: some View {
        OrganizerWalletCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Recent Transactions")
                    .font(.headline)

                if viewModel.isLoading && viewModel.transactions.isEmpty {
                    ProgressView("Loading wallet...")
                } else if viewModel.transactions.isEmpty {
                    Text("No transactions found.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.transactions.prefix(10)) { tx in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tx.title)
                                .font(.headline)
                            HStack {
                                Text(tx.type)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Text(String(format: "%.2f", tx.amount))
                                    .font(.subheadline.weight(.semibold))
                            }
                            if let note = tx.note, !note.isEmpty {
                                Text(note)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 3)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func actionRow(title: String, subtitle: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func dashboardTile(title: String, subtitle: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.headline)
                .foregroundStyle(.blue)
                .frame(width: 30, height: 30)
                .background(Circle().fill(Color.blue.opacity(0.14)))
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }
}

private struct OrganizerWalletCard<Content: View>: View {
    private let background: Color
    @ViewBuilder private var content: Content

    init(background: Color = Color(.secondarySystemBackground), @ViewBuilder content: () -> Content) {
        self.background = background
        self.content = content()
    }

    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
