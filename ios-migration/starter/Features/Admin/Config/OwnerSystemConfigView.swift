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

            Section("Flags") {
                Toggle("Maintenance Mode", isOn: $viewModel.maintenanceMode)
                Toggle("Allow New Signups", isOn: $viewModel.allowNewSignups)
                Toggle("Enable Blind Date", isOn: $viewModel.enableBlindDate)
                Toggle("Enable Live Streams", isOn: $viewModel.enableLiveStreams)
            }

            Section("Limits") {
                TextField("Max Upload (MB)", text: $viewModel.maxUploadMb)
                    .keyboardType(.numberPad)
            }

            Section {
                Button {
                    Task { await viewModel.save() }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save System Config")
                    }
                }
                .disabled(viewModel.isSaving)
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
