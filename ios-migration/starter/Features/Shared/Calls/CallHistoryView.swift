import SwiftUI

struct CallHistoryView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = CallHistoryViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                TextField("Search name, status, type", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Picker("Filter", selection: $viewModel.selectedFilter) {
                    ForEach(CallHistoryFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                Picker("Direction", selection: $viewModel.directionFilter) {
                    ForEach(CallDirectionFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.menu)
                Text("Showing \(viewModel.filteredCallLogs.count) of \(viewModel.callLogs.count) calls")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if viewModel.isLoading && viewModel.filteredCallLogs.isEmpty {
                ProgressView("Loading call history...")
            } else if viewModel.filteredCallLogs.isEmpty {
                Text("No call records yet.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredCallLogs) { call in
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
        let isMissed = (call.status ?? "").lowercased() == "missed"
        if isVideo { return isMissed ? "video.slash" : "video" }
        return isMissed ? "phone.down.fill" : "phone"
    }

    private func summary(for call: CallLogRecord) -> String {
        let direction = (call.direction ?? "unknown")
            .replacingOccurrences(of: "_", with: " ")
            .capitalized
        let status = (call.status ?? "unknown")
            .replacingOccurrences(of: "_", with: " ")
            .capitalized
        return "\(direction) - \(status)"
    }
}
