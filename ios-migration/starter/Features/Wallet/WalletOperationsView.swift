import SwiftUI

struct WalletOperationsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel: WalletOperationsViewModel

    init(user: AppSessionUser, initialTab: WalletOperationsTab = .mobileMoney) {
        self.user = user
        _viewModel = StateObject(wrappedValue: WalletOperationsViewModel(initialTab: initialTab))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                balanceCard
                tabCard
                if viewModel.selectedTab == .mobileMoney {
                    mobileMoneyCard
                } else {
                    agentCard
                }
                actionCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Wallet Operations")
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
    private var balanceCard: some View {
        WalletOpsCard(background: .blue.opacity(0.92)) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Wallet Balance")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.8))
                Text(balanceText)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
            }
        }
    }

    @ViewBuilder
    private var tabCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Flow")
                    .font(.headline)
                Picker("Tab", selection: $viewModel.selectedTab) {
                    ForEach(WalletOperationsTab.allCases) { tab in
                        Text(tab.title).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
            }
        }
    }

    @ViewBuilder
    private var mobileMoneyCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Mobile Money")
                    .font(.headline)

                Picker("Action", selection: $viewModel.mobileMoneyFlow) {
                    ForEach(MobileMoneyFlowType.allCases) { flow in
                        Text(flow.title).tag(flow)
                    }
                }
                .pickerStyle(.segmented)

                TextField("Amount", text: $viewModel.mobileAmountText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)

                if viewModel.mobileMoneyMethods.isEmpty {
                    Text("No verified mobile money methods found. Add one in Payment Methods.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Picker("Mobile method", selection: $viewModel.selectedMobileMethodId) {
                        ForEach(viewModel.mobileMoneyMethods) { method in
                            Text(mobileMethodLabel(method)).tag(viewModel.methodIdentifier(method))
                        }
                    }
                }

                if let selected = viewModel.selectedMobileMethod {
                    Text("Network: \((selected.network ?? selected.brand ?? "Mobile Money"))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("Phone: \(selected.phoneNumber ?? "Not set")")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var agentCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Agent")
                    .font(.headline)

                Text("Authorize to run cash-in/cash-out and generate payout codes.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Button("Authorize Agent Role") {
                    Task { await viewModel.authorizeAgent() }
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isWorking)

                Divider()

                TextField("Withdrawal Amount", text: $viewModel.agentAmountText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)

                if let fee = viewModel.currentAgentFeePreview {
                    Text("Fee: \(String(format: "%.2f", fee.fee)) (\(String(format: "%.3f", fee.rate * 100))%)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("Total debit: \(String(format: "%.2f", fee.totalDebit))")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                Button("Generate Withdrawal Code") {
                    Task { await viewModel.generateWithdrawalCode() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canGenerateCode)

                if let code = viewModel.generatedCodeRecord {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Generated Code")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(code.code)
                            .font(.title3.monospacedDigit().weight(.bold))
                        Text("Expires: \(code.expiresAt.formatted(date: .abbreviated, time: .shortened))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 4)
                }

                Divider()

                TextField("Payout Secret Code", text: $viewModel.payoutSecretCode)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                Button("Complete Agent Payout") {
                    Task { await viewModel.completeAgentPayout() }
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.canCompletePayout)

                Button("Cash Out Agent Earnings") {
                    Task { await viewModel.cashOutAgentEarnings() }
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isWorking)
            }
        }
    }

    @ViewBuilder
    private var actionCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 8) {
                if viewModel.isLoading {
                    ProgressView("Loading wallet operations...")
                }
                if viewModel.isWorking {
                    ProgressView("Processing...")
                }
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }
        }
    }

    private var balanceText: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = viewModel.summary.currency
        return formatter.string(from: NSNumber(value: viewModel.summary.balance))
            ?? "\(viewModel.summary.currency) \(String(format: "%.2f", viewModel.summary.balance))"
    }

    private func mobileMethodLabel(_ method: PaymentMethodRecord) -> String {
        let network = method.network ?? method.brand ?? "Mobile Money"
        let phone = method.phoneNumber ?? "No phone"
        return "\(network) - \(phone)"
    }
}

private struct WalletOpsCard<Content: View>: View {
    private let background: Color
    @ViewBuilder private var content: Content

    init(background: Color = Color(.secondarySystemBackground), @ViewBuilder content: () -> Content) {
        self.background = background
        self.content = content()
    }

    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
