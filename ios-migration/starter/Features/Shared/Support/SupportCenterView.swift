import SwiftUI

struct SupportCenterView: View {
    @StateObject private var viewModel = SupportCenterViewModel()

    var body: some View {
        List {
            if viewModel.isLoading && viewModel.items.isEmpty {
                ProgressView("Loading support...")
            } else {
                ForEach(viewModel.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title ?? "Support")
                            .font(.headline)
                        Text(item.value ?? "")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Support")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
        .alert("Notice", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Loaded fallback support items because remote support data was unavailable.")
        }
    }
}
