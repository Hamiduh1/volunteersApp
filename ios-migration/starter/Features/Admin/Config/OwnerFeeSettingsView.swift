import SwiftUI

struct OwnerFeeSettingsView: View {
    @StateObject private var viewModel = OwnerFeeSettingsViewModel()

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

                feesCard

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
        .navigationTitle("Fee Settings")
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

<<<<<<< HEAD
        var accent: Color {
            switch self {
            case .success: return .green
            case .warning: return .orange
            case .error: return .red
            case .neutral: return .blue
=======
            AdminRoleResponsibilitiesSection(
                screenKey: AdminRoleResponsibilitiesScreenKey.FEE_SETTINGS,
                fallbackGuide: AdminRoleResponsibilitiesGuide(
                    roleTitle: "Pricing Configuration Admin",
                    mission: "Set fee controls that balance growth, trust, and margin.",
                    responsibilities: [
                        "Update fees only after policy and financial impact review.",
                        "Keep values non-negative and within approved ranges.",
                        "Coordinate fee updates with support and release notes.",
                        "Monitor post-change effects on disputes and conversion."
                    ],
                    escalationRule: "Escalate emergency pricing rollback requests to owner."
                )
            )

            Section("Fees") {
                TextField("Blind Date Fee (USD)", text: $viewModel.blindDateFeeUsd)
                    .keyboardType(.decimalPad)
                TextField("Agent Authorization Fee (USD)", text: $viewModel.agentAuthorizationFeeUsd)
                    .keyboardType(.decimalPad)
                TextField("Sponsored Ad Fee (USD)", text: $viewModel.adPostFeeUsd)
                    .keyboardType(.decimalPad)
                TextField("Forex Profit Margin", text: $viewModel.forexProfitMargin)
                    .keyboardType(.decimalPad)
                TextField("Stripe Forex Deposit Margin", text: $viewModel.stripeForexDepositProfitMargin)
                    .keyboardType(.decimalPad)
                TextField("Mobile Money Hidden Fee Rate", text: $viewModel.mobileMoneyHiddenFeeRate)
                    .keyboardType(.decimalPad)
                TextField("Event Ticket Owner Fee Rate", text: $viewModel.eventTicketOwnerFeeRate)
                    .keyboardType(.decimalPad)
                TextField("Marketplace Platinum Fee Rate", text: $viewModel.marketplacePlatinumFeeRate)
                    .keyboardType(.decimalPad)
                TextField("Garage Sale Fee Rate", text: $viewModel.garageSaleFeeRate)
                    .keyboardType(.decimalPad)
>>>>>>> cc9d9a7 (iOS admin dashboard updates: country analytics, role responsibilities, fee config parity)
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
        card(tint: .purple) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "slider.horizontal.3")
                    .font(.headline.weight(.semibold))
                    .foregroundStyle(.purple)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(Color.purple.opacity(0.14)))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Fee Settings")
                        .font(.headline.weight(.semibold))
                    Text("Update platform fee configuration. Changes apply immediately to new operations.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var feesCard: some View {
        card(tint: .purple) {
            Text("Fees")
                .font(.headline.weight(.semibold))

            numberField("Blind Date Fee (USD)", text: $viewModel.blindDateFeeUsd, icon: "ticket.fill")
                .keyboardType(.decimalPad)
            numberField("Agent Authorization Fee (USD)", text: $viewModel.agentAuthorizationFeeUsd, icon: "person.badge.key.fill")
                .keyboardType(.decimalPad)

            Divider().opacity(0.6)

            numberField("Forex Profit Margin (0–1)", text: $viewModel.forexProfitMargin, icon: "percent")
                .keyboardType(.decimalPad)
            numberField("Stripe Forex Deposit Margin (0–1)", text: $viewModel.stripeForexDepositProfitMargin, icon: "creditcard.fill")
                .keyboardType(.decimalPad)
            numberField("Mobile Money Hidden Fee Rate (0–1)", text: $viewModel.mobileMoneyHiddenFeeRate, icon: "wave.3.right")
                .keyboardType(.decimalPad)
        }
    }

    @ViewBuilder
    private var actionsCard: some View {
        card(tint: .purple) {
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
                    Text("Save Fee Settings")
                        .font(.subheadline.weight(.bold))
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(.purple)
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
