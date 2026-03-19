import SwiftUI

private enum WalletRecipientLane: String, CaseIterable, Identifiable {
    case appUser = "app_user"
    case mobileMoney = "mobile_money"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .appUser: return "App User"
        case .mobileMoney: return "Mobile Money"
        }
    }
}

struct WalletTransactView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = WalletTransactViewModel()
    @State private var recipientLane: WalletRecipientLane = .appUser

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                balanceCard
                dashboardHomeCard
                destinationCard
                amountCard
                quoteCard
                actionCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Send Money")
        .task { await viewModel.refresh(uid: user.uid) }
        .onAppear {
            recipientLane = viewModel.destinationType == .beneficiary ? .mobileMoney : .appUser
        }
        .onChange(of: viewModel.destinationType) { _, destination in
            recipientLane = destination == .beneficiary ? .mobileMoney : .appUser
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
    private var dashboardHomeCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text("Dashboard Home")
                    .font(.headline)

                HStack(alignment: .top, spacing: 10) {
                    Text("To receive card or bank payouts, recipient setup must be completed in Payment Methods.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Spacer()
                    NavigationLink {
                        PaymentMethodsView(user: user)
                    } label: {
                        Text("Open")
                            .font(.subheadline.weight(.semibold))
                    }
                }

                Text("Transfer Lane")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    laneTile(
                        title: "App User",
                        subtitle: "Wallet, card, or bank",
                        icon: "person.crop.circle.badge.checkmark",
                        selected: recipientLane == .appUser
                    ) {
                        recipientLane = .appUser
                        if viewModel.destinationType == .beneficiary {
                            viewModel.destinationType = .wallet
                        }
                    }

                    laneTile(
                        title: "Mobile Money",
                        subtitle: "Saved beneficiaries",
                        icon: "iphone.gen3.radiowaves.left.and.right",
                        selected: recipientLane == .mobileMoney
                    ) {
                        recipientLane = .mobileMoney
                        viewModel.destinationType = .beneficiary
                    }
                }

                Divider()

                HStack(spacing: 10) {
                    NavigationLink {
                        UserDirectoryView(user: user)
                    } label: {
                        quickActionTile(
                            title: "Find User",
                            icon: "person.2.fill"
                        )
                    }
                    .buttonStyle(.plain)

                    NavigationLink {
                        WalletTransactionHistoryView(user: user)
                    } label: {
                        quickActionTile(
                            title: "History",
                            icon: "clock.arrow.circlepath"
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var balanceCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 8) {
                Text("Wallet Balance")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(balanceText)
                    .font(.title.weight(.bold))
                if viewModel.isWalletInsufficient {
                    Text("Insufficient wallet balance for this transfer.")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }
        }
    }

    @ViewBuilder
    private var destinationCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text("Step 1: Recipient")
                    .font(.headline)

                Text("Lane: \(recipientLane.title)")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)

                if recipientLane == .appUser {
                    Text("App User Destination")
                        .font(.subheadline.weight(.semibold))

                    Picker("Destination", selection: $viewModel.destinationType) {
                        Text(WalletDestinationType.wallet.title).tag(WalletDestinationType.wallet)
                        Text(WalletDestinationType.card.title).tag(WalletDestinationType.card)
                        Text(WalletDestinationType.bank.title).tag(WalletDestinationType.bank)
                    }
                    .pickerStyle(.segmented)

                    TextField("Recipient App User ID", text: $viewModel.recipientUserId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)

                    NavigationLink {
                        UserDirectoryView(user: user)
                    } label: {
                        Label("Find App User", systemImage: "person.2")
                            .font(.subheadline)
                    }

                    if viewModel.isLoadingRecipientMethods &&
                        (viewModel.destinationType == .card || viewModel.destinationType == .bank) {
                        ProgressView("Loading recipient payout methods...")
                            .font(.footnote)
                    }

                    if viewModel.destinationType == .card || viewModel.destinationType == .bank {
                        payoutSetupStatusRow
                        recipientMethodPicker
                    }
                } else {
                    Text("Saved Beneficiaries")
                        .font(.subheadline.weight(.semibold))

                    if viewModel.beneficiaries.isEmpty {
                        Text("No beneficiaries found. Add a beneficiary first, then return to send money.")
                            .foregroundStyle(.secondary)
                    } else {
                        Picker("Beneficiary", selection: $viewModel.selectedBeneficiaryId) {
                            ForEach(viewModel.beneficiaries) { item in
                                Text(beneficiaryLabel(item)).tag(item.id ?? "")
                            }
                        }
                    }
                    Text("Mobile money uses beneficiary routing with verification.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                if let hint = viewModel.recipientDestinationHelpText, !hint.isEmpty {
                    Text(hint)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
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

    @ViewBuilder
    private func quickActionTile(title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(.blue)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(.secondarySystemGroupedBackground))
        )
    }

    @ViewBuilder
    private var amountCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text("Step 2: Amount & Currencies")
                    .font(.headline)

                TextField("Amount", text: $viewModel.amountText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)

                HStack(spacing: 10) {
                    TextField("From", text: $viewModel.fromCurrency)
                        .textInputAutocapitalization(.characters)
                        .textFieldStyle(.roundedBorder)

                    TextField("To", text: $viewModel.toCurrency)
                        .textInputAutocapitalization(.characters)
                        .textFieldStyle(.roundedBorder)
                }

                TextField("Note (optional)", text: $viewModel.note)
                    .textFieldStyle(.roundedBorder)

                Button("Get Quote") {
                    Task { await viewModel.fetchQuote() }
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.canFetchQuote)
            }
        }
    }

    @ViewBuilder
    private var quoteCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 8) {
                Text("Step 3: Conversion Preview")
                    .font(.headline)

                if viewModel.isFetchingQuote {
                    ProgressView("Fetching exchange rate...")
                } else if let quote = viewModel.quote {
                    Text("Rate: \(String(format: "%.4f", quote.rate))")
                    Text("You send: \(quote.sourceCurrency) \(String(format: "%.2f", quote.sourceAmount))")
                    Text("Recipient gets: \(quote.targetCurrency) \(String(format: "%.2f", quote.recipientAmount))")
                        .fontWeight(.semibold)
                } else {
                    Text("Quote appears here for cross-currency transfers.")
                        .foregroundStyle(.secondary)
                }
            }
            .font(.footnote)
        }
    }

    @ViewBuilder
    private var actionCard: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text("Step 4: Confirm")
                    .font(.headline)

                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }

                Button {
                    Task { await viewModel.submit() }
                } label: {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text(actionTitle)
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canSubmit)
            }
        }
    }

    @ViewBuilder
    private var payoutSetupStatusRow: some View {
        HStack(spacing: 10) {
            Label(
                viewModel.recipientHasPayoutAccount ? "Payout setup complete" : "Payout setup incomplete",
                systemImage: viewModel.recipientHasPayoutAccount ? "checkmark.seal.fill" : "exclamationmark.triangle.fill"
            )
            .font(.footnote)
            .foregroundStyle(viewModel.recipientHasPayoutAccount ? .green : .orange)
        }
    }

    @ViewBuilder
    private var recipientMethodPicker: some View {
        if viewModel.filteredRecipientMethods.isEmpty {
            Text("No eligible \(viewModel.destinationType == .card ? "card" : "bank") payout methods found.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else {
            Picker("Recipient payout method", selection: $viewModel.selectedRecipientMethodId) {
                ForEach(viewModel.filteredRecipientMethods) { method in
                    Text(methodLabel(method)).tag(viewModel.recipientMethodIdentifier(method))
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

    private var actionTitle: String {
        switch viewModel.destinationType {
        case .wallet:
            return "Send to App User Wallet"
        case .card:
            return "Send to App User Card"
        case .bank:
            return "Send to App User Bank"
        case .beneficiary:
            return "Send to Beneficiary Mobile Money"
        }
    }

    private func beneficiaryLabel(_ item: BeneficiaryRecord) -> String {
        let name = item.name ?? "Beneficiary"
        let suffix = [item.network, item.phone].compactMap { $0 }.joined(separator: " - ")
        return suffix.isEmpty ? name : "\(name) - \(suffix)"
    }

    private func methodLabel(_ method: PaymentMethodRecord) -> String {
        let primary = (method.bankName ?? method.brand ?? method.type ?? "Method").capitalized
        let last4 = (method.last4 ?? "0000")
        return "\(primary) ...\(last4)"
    }
}

private struct CardContainer<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
