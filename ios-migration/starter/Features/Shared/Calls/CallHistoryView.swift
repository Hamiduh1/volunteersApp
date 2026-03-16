import SwiftUI

struct CallHistoryView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = CallHistoryViewModel()

    var body: some View {
        List {
            if viewModel.isLoading && viewModel.callLogs.isEmpty {
                ProgressView("Loading call history...")
            } else if viewModel.callLogs.isEmpty {
                Text("No call records yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.callLogs) { call in
                    HStack(spacing: 10) {
                        Image(systemName: iconName(for: call))
                            .foregroundStyle(.secondary)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(call.peerName ?? "Unknown user")
                                .font(.headline)
                            Text(summary(for: call))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let startedAt = call.startedAt?.dateValue() {
                            Text(startedAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Calls")
        .task { await viewModel.refresh(user: user) }
        .refreshable { await viewModel.refresh(user: user) }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    private func iconName(for call: CallLogRecord) -> String {
        let isVideo = (call.type ?? "").lowercased() == "video"
        if isVideo { return "video" }
        return "phone"
    }

    private func summary(for call: CallLogRecord) -> String {
        let direction = call.direction ?? "unknown"
        let status = call.status ?? "unknown"
        return "\(direction.capitalized) • \(status.capitalized)"
    }
}
