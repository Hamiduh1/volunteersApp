import SwiftUI

struct NotificationSettingsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel: NotificationSettingsViewModel

    init(user: AppSessionUser) {
        self.user = user
        _viewModel = StateObject(wrappedValue: NotificationSettingsViewModel(uid: user.uid))
    }

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if viewModel.isLoading && viewModel.settings == nil {
                ProgressView("Loading notification settings...")
            } else if let settings = viewModel.settings {
                Section("Notification Permissions") {
                    ForEach(NotificationSettingField.allCases) { field in
                        VStack(alignment: .leading, spacing: 6) {
                            Toggle(
                                isOn: Binding(
                                    get: { settings.value(for: field) },
                                    set: { enabled in
                                        Task { await viewModel.toggle(field, enabled: enabled) }
                                    }
                                )
                            ) {
                                Text(field.title)
                                    .font(.headline)
                            }
                            Text(field.description)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                }
            } else {
                Text("Notification settings unavailable.")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Notification Settings")
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
