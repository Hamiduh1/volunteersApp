import SwiftUI

struct OwnerFeeSettingsView: View {
    @StateObject private var viewModel = OwnerFeeSettingsViewModel()

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
            }

            Section("Actions") {
                Button {
                    Task { await viewModel.save() }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save Fee Settings")
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

            if viewModel.hasUnsavedChanges {
                Section {
                    Text("You have unsaved changes.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Fee Settings")
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
