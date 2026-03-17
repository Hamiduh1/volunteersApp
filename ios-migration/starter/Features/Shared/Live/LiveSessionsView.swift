import SwiftUI

struct LiveSessionsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = LiveSessionsViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if let tokenStatus = viewModel.tokenStatusMessage, !tokenStatus.isEmpty {
                Section {
                    Text(tokenStatus)
                        .font(.footnote)
                        .foregroundStyle(.green)
                }
            }

            if !viewModel.tokenPreview.isEmpty {
                Section("Last RTC Token") {
                    Text(viewModel.tokenPreview)
                        .font(.caption)
                        .textSelection(.enabled)
                    Button("Clear Token") {
                        viewModel.clearTokenPreview()
                    }
                    .buttonStyle(.bordered)
                }
            }

            Section("Filters") {
                TextField("Search title, host, status, channel", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Picker("Status", selection: $viewModel.statusFilter) {
                    ForEach(LiveSessionStatusFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                Text("Showing \(viewModel.filteredSessions.count) of \(viewModel.sessions.count) sessions | Live: \(viewModel.liveCount)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if viewModel.canHostLive(user: user) {
                Section("Go Live") {
                    TextField("Session title (optional)", text: $viewModel.hostSessionTitle)
                    if let active = viewModel.activeHostedSession(for: user.uid) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("You are live")
                                .font(.caption)
                                .foregroundStyle(.green)
                            Text(active.title ?? "Untitled Session")
                                .font(.subheadline.weight(.semibold))
                        }
                        Button("End My Live Session") {
                            Task { await viewModel.endLiveSession(session: active, user: user) }
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                        .disabled(viewModel.isHostOperationInProgress)
                    } else {
                        Button("Start Live Session") {
                            Task { await viewModel.startLiveSession(user: user) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isHostOperationInProgress)
                    }
                    if viewModel.isHostOperationInProgress {
                        ProgressView()
                            .controlSize(.small)
                    }
                }
            }

            if viewModel.isLoading && viewModel.filteredSessions.isEmpty {
                ProgressView("Loading live sessions...")
            } else if viewModel.filteredSessions.isEmpty {
                Text("No live sessions found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredSessions) { session in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(session.title ?? "Untitled Session")
                            .font(.headline)
                        Text(session.hostName ?? "Unknown Host")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("Status: \((session.status ?? "unknown").uppercased())")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let channel = session.agoraChannelName, !channel.isEmpty {
                            Text("Channel: \(channel)")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        if let createdAt = session.createdAt?.dateValue() {
                            Text(createdAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }

                        Button("Request RTC Token") {
                            Task { await viewModel.requestToken(session: session, uid: user.uid) }
                        }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.isRequestingToken)
                        .overlay(alignment: .trailing) {
                            if viewModel.isRequestingToken(for: session) {
                                ProgressView()
                                    .controlSize(.small)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("Live")
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
}

