import SwiftUI

struct OwnerKYCReviewView: View {
    @StateObject private var viewModel = OwnerKYCReviewViewModel()

    var body: some View {
        List {
            if viewModel.isLoading && viewModel.items.isEmpty {
                ProgressView("Loading KYC records...")
            } else if viewModel.items.isEmpty {
                Text("No KYC records found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.name)
                            .font(.headline)
                        Text(item.email.isEmpty ? "No email" : item.email)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("Role: \(item.role)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("Email verified: \(item.emailVerified ? "Yes" : "No")")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text("Profile status: \(item.profileStatus)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let updatedAt = item.updatedAt {
                            Text(updatedAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("KYC Review")
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
