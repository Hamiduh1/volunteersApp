import SwiftUI

struct OrganizerWalletView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerWalletViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Available Balance")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("\(viewModel.summary.currency) \(String(format: "%.2f", viewModel.summary.balance))")
                        .font(.title2.bold())
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal)

                if viewModel.isLoading {
                    Spacer()
                    ProgressView("Loading wallet...")
                    Spacer()
                } else if viewModel.transactions.isEmpty {
                    Spacer()
                    Text("No transactions found.")
                        .foregroundStyle(.secondary)
                    Spacer()
                } else {
                    List(viewModel.transactions) { tx in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(tx.title).font(.headline)
                            HStack {
                                Text(tx.type).font(.caption).foregroundStyle(.secondary)
                                Spacer()
                                Text(String(format: "%.2f", tx.amount))
                                    .font(.subheadline.weight(.semibold))
                            }
                            if let note = tx.note, !note.isEmpty {
                                Text(note).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
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
    }
}
