import SwiftUI

struct PrivacyPolicyView: View {
    @StateObject private var viewModel = PrivacyPolicyViewModel()

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.record == nil {
                ProgressView("Loading privacy policy...")
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(viewModel.record?.title ?? "Privacy Policy")
                            .font(.title3.bold())

                        Text(viewModel.record?.content ?? "No privacy policy content found yet.")
                            .font(.body)
                            .foregroundStyle(.secondary)

                        if let updatedAt = viewModel.record?.updatedAt?.dateValue() {
                            Text("Updated \(updatedAt.formatted(date: .abbreviated, time: .omitted))")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("Privacy")
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
