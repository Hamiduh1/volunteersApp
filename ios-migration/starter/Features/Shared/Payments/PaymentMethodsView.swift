import SwiftUI

struct PaymentMethodsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = PaymentMethodsViewModel()

    var body: some View {
        List {
            Section("Payout Setup") {
                if let payout = viewModel.payoutStatus {
                    statusRow("Has account", payout.hasAccount)
                    statusRow("Details submitted", payout.detailsSubmitted)
                    statusRow("Payouts enabled", payout.payoutsEnabled)
                    statusRow("Charges enabled", payout.chargesEnabled)
                } else if viewModel.isLoading {
                    ProgressView("Loading payout status...")
                } else {
                    Text("Payout setup status unavailable.")
                        .foregroundStyle(.secondary)
                }

                Button("Create Connect Account") {
                    Task { await viewModel.createConnectAccount() }
                }
                .buttonStyle(.bordered)

                Button("Create Onboarding Link") {
                    Task { await viewModel.createOnboardingLink() }
                }
                .buttonStyle(.borderedProminent)

                if !viewModel.onboardingUrl.isEmpty,
                   let url = URL(string: viewModel.onboardingUrl) {
                    Link("Open Onboarding", destination: url)
                        .font(.subheadline)
                }
            }

            Section("Saved Payment Methods") {
                if viewModel.methods.isEmpty {
                    Text("No payment methods found.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.methods) { method in
                        VStack(alignment: .leading, spacing: 4) {
                            Text((method.brand ?? method.type ?? "Method").capitalized)
                                .font(.headline)
                            Text("•••• \(method.last4 ?? "0000")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            if let holder = method.holderName, !holder.isEmpty {
                                Text(holder)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Payments")
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

    @ViewBuilder
    private func statusRow(_ title: String, _ value: Bool) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value ? "Yes" : "No")
                .foregroundStyle(value ? .green : .secondary)
        }
    }
}
