import SwiftUI

struct GlobalWalletHomeView: View {
    private enum BeneficiarySectionAnchor {
        static let dashboard = "beneficiary_dashboard"
        static let list = "beneficiary_list"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = GlobalWalletHomeViewModel()
    @State private var showBeneficiaryManager = false
    @State private var beneficiaryToDelete: BeneficiaryRecord?
    @State private var beneficiarySearchQuery = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                heroBalanceCard
                dashboardHomeCard
                globalCalculatorCard
                if user.role == .volunteer || user.role == .user {
                    becomeAgentCard
                }
                recentTransactionsCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("My Global Wallet")
        .task { await viewModel.refresh(uid: user.uid) }
        .refreshable { await viewModel.refresh(uid: user.uid) }
        .sheet(isPresented: $showBeneficiaryManager) {
            beneficiaryManagerSheet
        }
        .alert("Delete Beneficiary?", isPresented: Binding(
            get: { beneficiaryToDelete != nil },
            set: { if !$0 { beneficiaryToDelete = nil } }
        )) {
            Button("Cancel", role: .cancel) {
                beneficiaryToDelete = nil
            }
            Button("Delete", role: .destructive) {
                guard let beneficiary = beneficiaryToDelete else { return }
                Task {
                    await viewModel.deleteBeneficiary(beneficiary)
                    beneficiaryToDelete = nil
                }
            }
        } message: {
            Text("This beneficiary will be removed from your wallet recipients.")
        }
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
    private var heroBalanceCard: some View {
        CardContainer(background: .blue.opacity(0.92)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Total Balance (\(viewModel.summary.currency))")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.8))
                Text(balanceText)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                if viewModel.pendingDepositCount > 0 {
                    Label("Pending deposits: \(viewModel.pendingDepositCount)", systemImage: "clock.badge.exclamationmark")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.white)
                    Text("Card deposits are usually quick. ACH bank deposits can take 1-3 business days.")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.86))
                }

                HStack(spacing: 10) {
                    NavigationLink {
                        WalletFundingView(direction: .deposit, viewModel: viewModel)
                    } label: {
                        walletHeroButton(
                            title: "Deposit",
                            icon: "arrow.down.circle.fill",
                            isFilled: true
                        )
                    }

                    NavigationLink {
                        WalletFundingView(direction: .withdraw, viewModel: viewModel)
                    } label: {
                        walletHeroButton(
                            title: "Withdraw",
                            icon: "arrow.up.circle.fill",
                            isFilled: false
                        )
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var dashboardHomeCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text("Dashboard Home")
                    .font(.headline)

                Text("Primary Wallet Navigation")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    NavigationLink {
                        WalletTransactView(user: user)
                    } label: {
                        dashboardTile(
                            title: "Send Money",
                            subtitle: "App user or mobile money",
                            icon: "paperplane.fill"
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        PaymentMethodsView(user: user)
                    } label: {
                        dashboardTile(
                            title: "Payment Methods",
                            subtitle: "Cards, banks, mobile money",
                            icon: "creditcard.fill"
                        )
                    }
                    .buttonStyle(.plain)
                }

                Divider()

                Text("Operations")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                NavigationLink {
                    WalletFundingView(direction: .deposit, viewModel: viewModel)
                } label: {
                    walletActionRow(
                        title: "Deposit",
                        subtitle: "Add money to wallet",
                        icon: "arrow.down.circle.fill"
                    )
                }

                NavigationLink {
                    WalletFundingView(direction: .withdraw, viewModel: viewModel)
                } label: {
                    walletActionRow(
                        title: "Withdraw",
                        subtitle: "Transfer wallet funds out",
                        icon: "arrow.up.circle.fill"
                    )
                }

                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .mobileMoney)
                } label: {
                    walletActionRow(
                        title: "Mobile Money",
                        subtitle: "Cash in / cash out operations",
                        icon: "iphone.gen3.radiowaves.left.and.right"
                    )
                }

                NavigationLink {
                    WalletOperationsView(user: user, initialTab: .agent)
                } label: {
                    walletActionRow(
                        title: "Agent Portal",
                        subtitle: "Code generation and agent payouts",
                        icon: "person.badge.shield.checkmark.fill"
                    )
                }

                NavigationLink {
                    WalletTransactionHistoryView(user: user)
                } label: {
                    walletActionRow(
                        title: "Transaction History",
                        subtitle: "View full wallet receipts",
                        icon: "clock.arrow.circlepath"
                    )
                }

                Button {
                    showBeneficiaryManager = true
                } label: {
                    walletActionRow(
                        title: "Manage Beneficiaries",
                        subtitle: "Review and delete recipients",
                        icon: "person.2.fill"
                    )
                }
                .buttonStyle(.plain)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.green)
                }
            }
        }
    }

    @ViewBuilder
    private var globalCalculatorCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Global Calculator")
                            .font(.headline)
                        Text("Live FX estimate")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "globe")
                        .foregroundStyle(.blue)
                }

                HStack(spacing: 10) {
                    TextField("From amount", text: $viewModel.calculatorAmount)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: viewModel.calculatorAmount) { _, value in
                            viewModel.onCalculatorInputsChanged(amount: value)
                        }

                    Picker("From", selection: $viewModel.calculatorFromCountry) {
                        ForEach(viewModel.supportedCountries, id: \.self) { country in
                            Text("\(country) (\(viewModel.countryCodeLabel(country)))")
                                .tag(country)
                        }
                    }
                    .onChange(of: viewModel.calculatorFromCountry) { _, value in
                        viewModel.onCalculatorInputsChanged(fromCountry: value)
                    }
                }

                HStack(spacing: 10) {
                    TextField(
                        "To amount",
                        text: .constant(
                            viewModel.isCalculating || viewModel.calculatorError != nil
                                ? ""
                                : String(format: "%.2f", viewModel.calculatorResult)
                        )
                    )
                    .textFieldStyle(.roundedBorder)
                    .disabled(true)

                    Picker("To", selection: $viewModel.calculatorToCountry) {
                        ForEach(viewModel.supportedCountries, id: \.self) { country in
                            Text("\(country) (\(viewModel.countryCodeLabel(country)))")
                                .tag(country)
                        }
                    }
                    .onChange(of: viewModel.calculatorToCountry) { _, value in
                        viewModel.onCalculatorInputsChanged(toCountry: value)
                    }
                }

                Group {
                    if viewModel.isCalculating {
                        ProgressView("Fetching exchange rate...")
                            .font(.footnote)
                    } else if let calculatorError = viewModel.calculatorError, !calculatorError.isEmpty {
                        Text(calculatorError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    } else {
                        let fromCode = viewModel.countryCodeLabel(viewModel.calculatorFromCountry)
                        let toCode = viewModel.countryCodeLabel(viewModel.calculatorToCountry)
                        Text("1.00 \(fromCode) = \(String(format: "%.3f", viewModel.calculatorRate)) \(toCode)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var becomeAgentCard: some View {
        CardContainer(background: Color.orange.opacity(0.12)) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Earn as an Agent")
                        .font(.subheadline.weight(.bold))
                    Text("Facilitate cash transactions and earn commissions.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("Authorize") {
                    Task { await viewModel.authorizeAgent() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSubmittingFunding)
            }
        }
    }

    @ViewBuilder
    private var recentTransactionsCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Recent Transactions")
                        .font(.headline)
                    Spacer()
                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        Text("View all")
                            .font(.caption.weight(.semibold))
                    }
                }

                if viewModel.isLoading && viewModel.transactions.isEmpty {
                    ProgressView("Loading wallet...")
                } else if viewModel.transactions.isEmpty {
                    Text("No transactions yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.transactions.prefix(10)) { tx in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(tx.title)
                                    .font(.subheadline.weight(.semibold))
                                Spacer()
                                Text(formattedAmount(tx.amount))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(tx.amount >= 0 ? .green : .red)
                            }
                            Text("\(tx.type.uppercased()) - \(tx.status)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let note = tx.note, !note.isEmpty {
                                Text(note)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            if let date = tx.createdAt {
                                Text(date.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 3)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var beneficiaryManagerSheet: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Dashboard Home")
                                .font(.headline)

                            Text("Android-style beneficiary management and quick navigation.")
                                .font(.caption)
                                .foregroundStyle(.secondary)

                            HStack(spacing: 8) {
                                beneficiaryMetricTile(value: "\(viewModel.beneficiaries.count)", label: "Total")
                                beneficiaryMetricTile(value: "\(filteredBeneficiaries.count)", label: "Filtered")
                                beneficiaryMetricTile(value: "\(uniqueBeneficiaryNetworks)", label: "Networks")
                            }

                            HStack(spacing: 8) {
                                Button("Saved List") {
                                    withAnimation {
                                        proxy.scrollTo(BeneficiarySectionAnchor.list, anchor: .top)
                                    }
                                }
                                .buttonStyle(.bordered)

                                Button("Clear Search") {
                                    beneficiarySearchQuery = ""
                                }
                                .buttonStyle(.bordered)
                                .disabled(beneficiarySearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }

                            NavigationLink {
                                WalletTransactView(user: user)
                            } label: {
                                Label("Open Send Money", systemImage: "paperplane.fill")
                                    .font(.subheadline.weight(.semibold))
                            }

                            if let status = viewModel.statusMessage, !status.isEmpty {
                                Text(status)
                                    .font(.footnote)
                                    .foregroundStyle(.green)
                            }
                        }
                    }
                    .id(BeneficiarySectionAnchor.dashboard)

                    Section("Saved Beneficiaries") {
                        if filteredBeneficiaries.isEmpty {
                            Text(viewModel.beneficiaries.isEmpty ? "No beneficiaries found." : "No beneficiaries match your search.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(filteredBeneficiaries) { beneficiary in
                                let name = beneficiary.name ?? "Beneficiary"
                                let details = [beneficiary.network, beneficiary.phone]
                                    .compactMap { $0 }
                                    .joined(separator: " - ")
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(name)
                                            .font(.headline)
                                        if !details.isEmpty {
                                            Text(details)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    Button(role: .destructive) {
                                        beneficiaryToDelete = beneficiary
                                    } label: {
                                        Image(systemName: "trash")
                                    }
                                }
                            }
                        }
                    }
                    .id(BeneficiarySectionAnchor.list)
                }
                .searchable(
                    text: $beneficiarySearchQuery,
                    placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Search by name, phone, or network"
                )
            }
            .navigationTitle("Manage Beneficiaries")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        beneficiarySearchQuery = ""
                        showBeneficiaryManager = false
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    @ViewBuilder
    private func beneficiaryMetricTile(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    private var filteredBeneficiaries: [BeneficiaryRecord] {
        let query = beneficiarySearchQuery
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()

        guard !query.isEmpty else { return viewModel.beneficiaries }

        return viewModel.beneficiaries.filter { beneficiary in
            let name = beneficiary.name?.lowercased() ?? ""
            let phone = beneficiary.phone?.lowercased() ?? ""
            let network = beneficiary.network?.lowercased() ?? ""
            return name.contains(query) || phone.contains(query) || network.contains(query)
        }
    }

    private var uniqueBeneficiaryNetworks: Int {
        Set(
            viewModel.beneficiaries.compactMap { beneficiary in
                beneficiary.network?
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .lowercased()
            }
        ).count
    }

    @ViewBuilder
    private func walletHeroButton(title: String, icon: String, isFilled: Bool) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
            Text(title)
                .fontWeight(.semibold)
        }
        .font(.subheadline)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .foregroundStyle(isFilled ? .black : .white)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(isFilled ? Color.white : Color.white.opacity(0.1))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color.white.opacity(isFilled ? 0 : 0.6), lineWidth: isFilled ? 0 : 1)
        )
    }

    @ViewBuilder
    private func walletActionRow(title: String, subtitle: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func dashboardTile(title: String, subtitle: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.headline)
                .foregroundStyle(.blue)
                .frame(width: 30, height: 30)
                .background(Circle().fill(Color.blue.opacity(0.14)))
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
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    private var balanceText: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = viewModel.summary.currency
        return formatter.string(from: NSNumber(value: viewModel.summary.balance))
            ?? "\(viewModel.summary.currency) \(String(format: "%.2f", viewModel.summary.balance))"
    }

    private func formattedAmount(_ amount: Double) -> String {
        let sign = amount >= 0 ? "+" : "-"
        return "\(sign)\(String(format: "%.2f", abs(amount)))"
    }
}

private struct WalletFundingView: View {
    let direction: WalletFundingDirection
    @ObservedObject var viewModel: GlobalWalletHomeViewModel

    var body: some View {
        Form {
            Section("Flow") {
                Text(direction == .deposit ? "Deposit to Wallet" : "Withdraw from Wallet")
                    .font(.headline)
                Text(directionDescription)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("Amount") {
                TextField("Amount", text: $viewModel.fundingAmountText)
                    .keyboardType(.decimalPad)
            }

            Section("Funding Method") {
                if eligibleMethods.isEmpty {
                    Text("No eligible methods available.")
                        .foregroundStyle(.secondary)
                } else {
                    Picker("Method", selection: $viewModel.selectedFundingMethodId) {
                        ForEach(Array(eligibleMethods.enumerated()), id: \.offset) { _, method in
                            Text(viewModel.methodLabel(method))
                                .tag(viewModel.methodIdentifier(method))
                        }
                    }
                }
            }

            if let selectedMethod = viewModel.selectedFundingMethod,
               let type = selectedMethod.type?.uppercased(),
               type.contains("MOBILE") {
                Section("Mobile Money") {
                    Text("Mobile money flows use the linked network and phone number.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Button {
                    Task { await viewModel.submitFunding(direction) }
                } label: {
                    if viewModel.isSubmittingFunding {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(direction == .deposit ? "Submit Deposit" : "Submit Withdrawal")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isSubmittingFunding || eligibleMethods.isEmpty || viewModel.parsedFundingAmount <= 0)
            }

            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .foregroundStyle(.green)
                        .font(.footnote)
                }
            }
        }
        .navigationTitle(direction == .deposit ? "Deposit" : "Withdraw")
        .onAppear {
            viewModel.prepareFunding(for: direction)
        }
    }

    private var eligibleMethods: [PaymentMethodRecord] {
        viewModel.fundingEligibleMethods(for: direction)
    }

    private var directionDescription: String {
        switch direction {
        case .deposit:
            return "Add money from card, ACH-enabled bank, or mobile money."
        case .withdraw:
            return "Move wallet funds to linked card, bank, or mobile money."
        }
    }
}

private struct CardContainer<Content: View>: View {
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
