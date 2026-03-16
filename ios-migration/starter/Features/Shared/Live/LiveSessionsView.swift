import SwiftUI

struct LiveSessionsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = LiveSessionsViewModel()

    var body: some View {
        List {
            if !viewModel.tokenPreview.isEmpty {
                Section("Last RTC Token") {
                    Text(viewModel.tokenPreview)
                        .font(.caption)
                        .textSelection(.enabled)
                }
            }

            if viewModel.isLoading && viewModel.sessions.isEmpty {
                ProgressView("Loading live sessions...")
            } else if viewModel.sessions.isEmpty {
                Text("No live sessions found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.sessions) { session in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(session.title ?? "Untitled Session")
                            .font(.headline)
                        Text(session.hostName ?? "Unknown Host")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Status: \((session.status ?? "unknown").uppercased())")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        Button("Request RTC Token") {
                            Task { await viewModel.requestToken(session: session, uid: user.uid) }
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Live")
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
