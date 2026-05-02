import SwiftUI

struct OwnerSystemConfigView: View {
    @StateObject private var viewModel = OwnerSystemConfigViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard

                if let error = viewModel.errorMessage, !error.isEmpty {
                    banner(tone: .error, message: error)
                } else if let validation = viewModel.validationMessage, !validation.isEmpty {
                    banner(tone: .warning, message: validation)
                } else if let status = viewModel.statusMessage, !status.isEmpty {
                    banner(tone: .success, message: status)
                }

                if viewModel.maintenanceMode {
                    banner(
                        tone: .warning,
                        message: "Maintenance mode is ON. Non-owner users may be blocked from key actions."
                    )
                }

                flagsCard
                limitsCard
                actionsCard

                if viewModel.hasUnsavedChanges {
                    banner(tone: .neutral, message: "You have unsaved changes.")
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(
            LinearGradient(
                colors: [
                    Color(.systemGroupedBackground),
                    Color(.secondarySystemGroupedBackground).opacity(0.38)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .navigationTitle("System Config")
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
    }

    private enum BannerTone {
        case success
        case warning
        case error
        case neutral

        var icon: String {
            switch self {
            case .success: return "checkmark.seal.fill"
            case .warning: return "exclamationmark.triangle.fill"
            case .error: return "xmark.octagon.fill"
            case .neutral: return "info.circle.fill"
            }
        }

        var accent: Color {
            switch self {
            case .success: return .green
            case .warning: return .orange
            case .error: return .red
            case .neutral: return .blue
            }
        }

        var fill: Color { accent.opacity(0.10) }
        var stroke: Color { accent.opacity(0.20) }
    }

    @ViewBuilder
    private func banner(tone: BannerTone, message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: tone.icon)
                .foregroundStyle(tone.accent)
            Text(message)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(tone.fill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(tone.stroke, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var heroCard: some View {
        card(tint: .blue) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "server.rack")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.blue)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color.blue.opacity(0.14)))
                VStack(alignment: .leading, spacing: 4) {
                    Text("System Config")
                        .font(.headline.weight(.semibold))
                    Text("Toggle platform availability and safety limits. Changes apply immediately.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var flagsCard: some View {
        card(tint: .blue) {
            Text("Flags")
                .font(.headline.weight(.semibold))

            Toggle("Maintenance Mode", isOn: $viewModel.maintenanceMode)
            Toggle("Allow New Signups", isOn: $viewModel.allowNewSignups)
            Toggle("Enable Blind Date", isOn: $viewModel.enableBlindDate)
            Toggle("Enable Live Streams", isOn: $viewModel.enableLiveStreams)
        }
    }

    @ViewBuilder
    private var limitsCard: some View {
        card(tint: .blue) {
            Text("Limits")
                .font(.headline.weight(.semibold))

            numberField("Max Upload (MB)", text: $viewModel.maxUploadMb, icon: "arrow.up.doc")
                .keyboardType(.numberPad)

            HStack(spacing: 8) {
                Text("Quick Presets")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("10") { viewModel.setUploadPreset(10) }.buttonStyle(.bordered)
                Button("25") { viewModel.setUploadPreset(25) }.buttonStyle(.bordered)
                Button("100") { viewModel.setUploadPreset(100) }.buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private var actionsCard: some View {
        card(tint: .blue) {
            Text("Actions")
                .font(.headline.weight(.semibold))

            Button {
                Task { await viewModel.save() }
            } label: {
                if viewModel.isSaving {
                    ProgressView()
                        .tint(.white)
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Save System Config")
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(.blue)
            .disabled(!viewModel.canSave)

            Button("Restore Last Loaded Values") {
                viewModel.restoreLastLoaded()
            }
            .buttonStyle(.bordered)
            .disabled(!viewModel.hasUnsavedChanges || viewModel.isSaving || viewModel.isLoading)

            Button("Apply Recommended Defaults") {
                viewModel.applyDefaults()
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.isSaving || viewModel.isLoading)
        }
    }

    @ViewBuilder
    private func numberField(_ title: String, text: Binding<String>, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundStyle(.secondary)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            TextField(title, text: text)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    @ViewBuilder
    private func card<Content: View>(tint: Color, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.systemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(tint.opacity(0.14), lineWidth: 1)
        )
    }
}
