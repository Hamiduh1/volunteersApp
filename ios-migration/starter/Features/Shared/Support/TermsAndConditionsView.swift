import SwiftUI

struct TermsAndConditionsView: View {
    @StateObject private var viewModel = TermsAndConditionsViewModel()

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.record == nil {
                ProgressView("Loading terms...")
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if let notice = viewModel.noticeMessage, !notice.isEmpty {
                            Text(notice)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }

                        Text(viewModel.record?.title ?? "Terms and Conditions")
                            .font(.title3.bold())

                        Text(viewModel.record?.content ?? "No terms content found yet.")
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
        .navigationTitle("Terms")
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
