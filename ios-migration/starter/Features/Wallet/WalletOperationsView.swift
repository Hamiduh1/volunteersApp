import SwiftUI

struct WalletOperationsView: View {
    private enum SectionAnchor {
        static let dashboard = "wallet_ops_dashboard"
        static let flow = "wallet_ops_flow"
        static let mobileDashboard = "wallet_ops_mobile_dashboard"
        static let mobileAction = "wallet_ops_mobile_action"
        static let mobileMethod = "wallet_ops_mobile_method"
        static let mobileSubmit = "wallet_ops_mobile_submit"
        static let agent = "wallet_ops_agent"
        static let status = "wallet_ops_status"
    }

    let user: AppSessionUser
    @StateObject private var viewModel: WalletOperationsViewModel

    init(user: AppSessionUser, initialTab: WalletOperationsTab = .mobileMoney) {
        self.user = user
        _viewModel = StateObject(wrappedValue: WalletOperationsViewModel(initialTab: initialTab))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 14) {
                    balanceCard
                    dashboardHomeCard(proxy: proxy)
                        .id(SectionAnchor.dashboard)
                    tabCard
                        .id(SectionAnchor.flow)
                    if viewModel.selectedTab == .mobileMoney {
                        mobileMoneyDashboardCard(proxy: proxy)
                            .id(SectionAnchor.mobileDashboard)
                        mobileMoneyActionCard
                            .id(SectionAnchor.mobileAction)
                        mobileMoneyMethodCard
                            .id(SectionAnchor.mobileMethod)
                        mobileMoneySubmitCard
                            .id(SectionAnchor.mobileSubmit)
                    } else {
                        agentCard
                            .id(SectionAnchor.agent)
                    }
                    actionCard
                        .id(SectionAnchor.status)
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
    }

    @ViewBuilder
    private func dashboardHomeCard(proxy: ScrollViewProxy) -> some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Dashboard Home")
                    .font(.headline)

                Text("Android-style agent and mobile money navigation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    laneTile(
                        title: "Mobile Money",
                        subtitle: "Cash in / cash out",
                        icon: "iphone.gen3.radiowaves.left.and.right",
                        selected: viewModel.selectedTab == .mobileMoney
                    ) {
                        viewModel.selectedTab = .mobileMoney
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileDashboard, anchor: .top) }
                    }

                    laneTile(
                        title: "Agent Portal",
                        subtitle: "Code and payout flow",
                        icon: "person.badge.shield.checkmark.fill",
                        selected: viewModel.selectedTab == .agent
                    ) {
                        viewModel.selectedTab = .agent
                        withAnimation { proxy.scrollTo(SectionAnchor.agent, anchor: .top) }
                    }
                }

                if viewModel.selectedTab == .agent {
                    Text("Agent Quick Actions")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    HStack(spacing: 8) {
                        Button("Authorize") {
                            Task { await viewModel.authorizeAgent() }
                        }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.isWorking)

                        Button("Generate Code") {
                            withAnimation { proxy.scrollTo(SectionAnchor.agent, anchor: .top) }
                        }
                        .buttonStyle(.bordered)

                        Button("Status") {
                            withAnimation { proxy.scrollTo(SectionAnchor.status, anchor: .top) }
                        }
                        .buttonStyle(.bordered)
                    }
                }

                HStack(spacing: 8) {
                    Button("Flow") {
                        withAnimation { proxy.scrollTo(SectionAnchor.flow, anchor: .top) }
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        Label("History", systemImage: "clock.arrow.circlepath")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                }
            }
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
    private func mobileMoneyDashboardCard(proxy: ScrollViewProxy) -> some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Mobile Money Dashboard Home")
                    .font(.headline)

                Text("Android-style mobile money navigation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 8) {
                    laneTile(
                        title: "Cash In",
                        subtitle: "Deposit to wallet",
                        icon: "arrow.down.circle.fill",
                        selected: viewModel.mobileMoneyFlow == .cashIn
                    ) {
                        viewModel.mobileMoneyFlow = .cashIn
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileAction, anchor: .top) }
                    }

                    laneTile(
                        title: "Cash Out",
                        subtitle: "Withdraw from wallet",
                        icon: "arrow.up.circle.fill",
                        selected: viewModel.mobileMoneyFlow == .cashOut
                    ) {
                        viewModel.mobileMoneyFlow = .cashOut
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileAction, anchor: .top) }
                    }
                }

                HStack(spacing: 8) {
                    Button("Action") {
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileAction, anchor: .top) }
                    }
                    .buttonStyle(.bordered)

                    Button("Method") {
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileMethod, anchor: .top) }
                    }
                    .buttonStyle(.bordered)

                    Button("Submit") {
                        withAnimation { proxy.scrollTo(SectionAnchor.mobileSubmit, anchor: .top) }
                    }
                    .buttonStyle(.bordered)
                }

                HStack(spacing: 8) {
                    NavigationLink {
                        PaymentMethodsView(user: user)
                    } label: {
                        Label("Payment Methods", systemImage: "creditcard.fill")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.bordered)

                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        Label("History", systemImage: "clock.arrow.circlepath")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                }

                if let selected = viewModel.selectedMobileMethod {
                    Text("Current: \((selected.network ?? selected.brand ?? "Mobile Money")) - \(selected.phoneNumber ?? "No phone")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var mobileMoneyActionCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Mobile Action")
                    .font(.headline)

                Text("Mobile Money")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Picker("Action", selection: $viewModel.mobileMoneyFlow) {
                    ForEach(MobileMoneyFlowType.allCases) { flow in
                        Text(flow.title).tag(flow)
                    }
                }
                .pickerStyle(.segmented)

                TextField("Amount", text: $viewModel.mobileAmountText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)
            }
        }
    }

    @ViewBuilder
    private var mobileMoneyMethodCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Mobile Method")
                    .font(.headline)

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
    private var mobileMoneySubmitCard: some View {
        WalletOpsCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Confirm")
                    .font(.headline)

                Text(viewModel.mobileMoneyFlow == .cashIn ? "Submit Cash In request." : "Submit Cash Out request.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                Button(viewModel.mobileMoneyFlow == .cashIn ? "Submit Cash In" : "Submit Cash Out") {
                    Task { await viewModel.submitMobileMoney() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canSubmitMobileMoney)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.green)
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

    @ViewBuilder
    private func laneTile(
        title: String,
        subtitle: String,
        icon: String,
        selected: Bool,
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 7) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(selected ? .blue : .secondary)
                    .frame(width: 30, height: 30)
                    .background(Circle().fill((selected ? Color.blue : Color.secondary).opacity(0.14)))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.leading)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(selected ? Color.blue.opacity(0.09) : Color(.secondarySystemGroupedBackground))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(selected ? Color.blue.opacity(0.35) : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
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
