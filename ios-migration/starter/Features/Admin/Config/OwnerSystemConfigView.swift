import SwiftUI

struct OwnerSystemConfigView: View {
    @StateObject private var viewModel = OwnerSystemConfigViewModel()

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            if let validation = viewModel.validationMessage, !validation.isEmpty {
                Section {
                    Text(validation)
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }

            Section("Flags") {
                Toggle("Maintenance Mode", isOn: $viewModel.maintenanceMode)
                Toggle("Allow New Signups", isOn: $viewModel.allowNewSignups)
                Toggle("Enable Blind Date", isOn: $viewModel.enableBlindDate)
                Toggle("Enable Live Streams", isOn: $viewModel.enableLiveStreams)
            }

            Section("Limits") {
                TextField("Max Upload (MB)", text: $viewModel.maxUploadMb)
                    .keyboardType(.numberPad)
                HStack {
                    Text("Quick Presets")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("10") { viewModel.setUploadPreset(10) }
                    Button("25") { viewModel.setUploadPreset(25) }
                    Button("100") { viewModel.setUploadPreset(100) }
                }
            }

            Section("Actions") {
                Button {
                    Task { await viewModel.save() }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save System Config")
                    }
                }
                .disabled(!viewModel.canSave)

                Button("Restore Last Loaded Values") {
                    viewModel.restoreLastLoaded()
                }
                .disabled(!viewModel.hasUnsavedChanges || viewModel.isSaving || viewModel.isLoading)

                Button("Apply Recommended Defaults") {
                    viewModel.applyDefaults()
                }
                .disabled(viewModel.isSaving || viewModel.isLoading)
            }

            if viewModel.maintenanceMode {
                Section {
                    Text("Maintenance mode is ON. Non-owner users may be blocked from key actions.")
                        .font(.footnote)
                        .foregroundStyle(.orange)
                }
            }

            if viewModel.hasUnsavedChanges {
                Section {
                    Text("You have unsaved changes.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("System Config")
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
