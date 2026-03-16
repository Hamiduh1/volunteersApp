import SwiftUI

struct CommunityAlertsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel: CommunityAlertsViewModel

    init(user: AppSessionUser) {
        self.user = user
        _viewModel = StateObject(wrappedValue: CommunityAlertsViewModel(uid: user.uid))
    }

    var body: some View {
        Group {
            if viewModel.isLoading && viewModel.alerts.isEmpty {
                ProgressView("Loading alerts...")
            } else if viewModel.alerts.isEmpty {
                ContentUnavailableView(
                    "No Alerts",
                    systemImage: "bell.slash",
                    description: Text(viewModel.statusMessage ?? "You're all caught up.")
                )
            } else {
                List(viewModel.alerts) { alert in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(alert.title)
                            .font(.headline)
                        Text(alert.description)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        HStack {
                            Text(alert.source.replacingOccurrences(of: "_", with: " ").capitalized)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                            Spacer()
                            Text(alert.timestamp, style: .relative)
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Community Alerts")
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
