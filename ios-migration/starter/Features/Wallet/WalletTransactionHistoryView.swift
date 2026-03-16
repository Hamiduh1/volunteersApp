import SwiftUI

struct WalletTransactionHistoryView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = WalletTransactionHistoryViewModel()

    var body: some View {
        List {
            Section {
                Picker("Filter", selection: $viewModel.filter) {
                    ForEach(WalletTransactionFilter.allCases) { option in
                        Text(option.title).tag(option)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("Transactions") {
                if viewModel.isLoading && viewModel.filteredItems.isEmpty {
                    ProgressView("Loading transactions...")
                } else if viewModel.filteredItems.isEmpty {
                    Text("No transactions found.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.filteredItems) { tx in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(tx.title)
                                    .font(.headline)
                                Spacer()
                                Text(String(format: "%.2f", tx.amount))
                                    .fontWeight(.semibold)
                                    .foregroundStyle(tx.amount >= 0 ? .green : .red)
                            }
                            Text("\(tx.type.uppercased()) • \(tx.status)")
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
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("History")
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
