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

            Section("Fees") {
                TextField("Blind Date Fee (USD)", text: $viewModel.blindDateFeeUsd)
                    .keyboardType(.decimalPad)
                TextField("Agent Authorization Fee (USD)", text: $viewModel.agentAuthorizationFeeUsd)
                    .keyboardType(.decimalPad)
                TextField("Forex Profit Margin", text: $viewModel.forexProfitMargin)
                    .keyboardType(.decimalPad)
                TextField("Stripe Forex Deposit Margin", text: $viewModel.stripeForexDepositProfitMargin)
                    .keyboardType(.decimalPad)
                TextField("Mobile Money Hidden Fee Rate", text: $viewModel.mobileMoneyHiddenFeeRate)
                    .keyboardType(.decimalPad)
            }

            Section {
                Button {
                    Task { await viewModel.save() }
                } label: {
                    if viewModel.isSaving {
                        ProgressView()
                    } else {
                        Text("Save Fee Settings")
                    }
                }
                .disabled(viewModel.isSaving)
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
