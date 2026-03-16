import SwiftUI

struct GlobalWalletHomeView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = GlobalWalletHomeViewModel()

    var body: some View {
        NavigationStack {
            List {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Available Balance")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(balanceText)
                            .font(.largeTitle.weight(.bold))
                    }
                    .padding(.vertical, 4)

                    NavigationLink("Send Money") {
                        WalletTransactView(user: user)
                    }
                    NavigationLink("Transaction History") {
                        WalletTransactionHistoryView(user: user)
                    }
                    NavigationLink("Payment Methods & Payout") {
                        PaymentMethodsView(user: user)
                    }
                }

                Section("Recent Transactions") {
                    if viewModel.isLoading && viewModel.transactions.isEmpty {
                        ProgressView("Loading wallet...")
                    } else if viewModel.transactions.isEmpty {
                        Text("No transactions yet.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.transactions) { tx in
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
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
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
            }
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
}
