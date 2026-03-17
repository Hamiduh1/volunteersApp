import SwiftUI

struct GlobalWalletHomeView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = GlobalWalletHomeViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                heroBalanceCard
                quickActionCard
                servicesCard
                recentTransactionsCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Wallet")
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
    private var heroBalanceCard: some View {
        CardContainer(background: .blue.opacity(0.92)) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Total Balance (\(viewModel.summary.currency))")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.8))
                Text(balanceText)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                if viewModel.pendingDepositCount > 0 {
                    Label("Pending deposits: \(viewModel.pendingDepositCount)", systemImage: "clock.badge.exclamationmark")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.white)
                    Text("Card deposits are usually quick. ACH bank deposits can take 1-3 business days.")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.86))
                }
            }
        }
    }

    @ViewBuilder
    private var quickActionCard: some View {
        CardContainer {
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    NavigationLink {
                        WalletTransactView(user: user)
                    } label: {
                        quickActionButton(
                            title: "Send",
                            subtitle: "Wallet/Card/Bank",
                            icon: "paperplane.fill"
                        )
                    }

                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        quickActionButton(
                            title: "History",
                            subtitle: "Receipts",
                            icon: "clock.arrow.circlepath"
                        )
                    }
                }

                HStack(spacing: 10) {
                    NavigationLink {
                        WalletOperationsView(user: user, initialTab: .mobileMoney)
                    } label: {
                        quickActionButton(
                            title: "Mobile Money",
                            subtitle: "Cash In / Cash Out",
                            icon: "iphone.gen3.radiowaves.left.and.right"
                        )
                    }

                    NavigationLink {
                        WalletOperationsView(user: user, initialTab: .agent)
                    } label: {
                        quickActionButton(
                            title: "Agent",
                            subtitle: "Codes / Cashout",
                            icon: "person.badge.shield.checkmark"
                        )
                    }
                }

                NavigationLink {
                    PaymentMethodsView(user: user)
                } label: {
                    HStack {
                        Label("Payment Methods & Payout", systemImage: "creditcard.and.123")
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .font(.subheadline.weight(.semibold))
                }
            }
        }
    }

    @ViewBuilder
    private var servicesCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 8) {
                Text("Services")
                    .font(.headline)
                NavigationLink {
                    WalletTransactView(user: user)
                } label: {
                    serviceRow(
                        title: "Send Money",
                        subtitle: "App user wallet, card, bank, or beneficiary mobile money",
                        icon: "paperplane"
                    )
                }
                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .mobileMoney)
                } label: {
                    serviceRow(
                        title: "Mobile Money",
                        subtitle: "Deposit and withdraw with linked mobile money",
                        icon: "iphone.gen3.radiowaves.left.and.right"
                    )
                }
                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .agent)
                } label: {
                    serviceRow(
                        title: "Agent Portal",
                        subtitle: "Authorize, generate payout code, and complete agent payout",
                        icon: "person.badge.shield.checkmark"
                    )
                }
                NavigationLink {
                    WalletTransactionHistoryView(user: user)
                } label: {
                    serviceRow(
                        title: "Transaction History",
                        subtitle: "Track receipts by all, deposit, withdrawals, and mobile money",
                        icon: "list.bullet.rectangle"
                    )
                }
                NavigationLink {
                    PaymentMethodsView(user: user)
                } label: {
                    serviceRow(
                        title: "Payment Methods",
                        subtitle: "Manage cards, banks, mobile money, and payout setup",
                        icon: "wallet.pass"
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var recentTransactionsCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text("Recent Transactions")
                    .font(.headline)

                if viewModel.isLoading && viewModel.transactions.isEmpty {
                    ProgressView("Loading wallet...")
                } else if viewModel.transactions.isEmpty {
                    Text("No transactions yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.transactions.prefix(10)) { tx in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(tx.title)
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                Text(formattedAmount(tx.amount))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(tx.amount >= 0 ? .green : .red)
                            }
                            Text("\(tx.type.uppercased()) - \(tx.status)")
                                .font(.caption)
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
                        .padding(.vertical, 3)
                    }
                }

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var balanceText: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = viewModel.summary.currency
        return formatter.string(from: NSNumber(value: viewModel.summary.balance))
            ?? "\(viewModel.summary.currency) \(String(format: "%.2f", viewModel.summary.balance))"
    }

    private func formattedAmount(_ amount: Double) -> String {
        let sign = amount >= 0 ? "+" : "-"
        return "\(sign)\(String(format: "%.2f", abs(amount)))"
    }

    @ViewBuilder
    private func quickActionButton(title: String, subtitle: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: icon)
                .font(.title3)
            Text(title)
                .font(.subheadline.weight(.bold))
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 82, alignment: .leading)
        .padding(10)
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private func serviceRow(title: String, subtitle: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct CardContainer<Content: View>: View {
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
