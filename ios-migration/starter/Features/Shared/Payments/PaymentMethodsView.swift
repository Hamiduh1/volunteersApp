import SwiftUI

struct PaymentMethodsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = PaymentMethodsViewModel()

    @State private var showAddOptions = false
    @State private var showAddCardSheet = false
    @State private var showAddBankSheet = false
    @State private var showAddMobileMoneySheet = false

    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                payoutSetupCard
                if hasCardRelinkRequirement {
                    relinkWarningCard
                }
                methodsSection(title: "Cards", methods: viewModel.cardMethods, icon: "creditcard.fill")
                methodsSection(title: "Bank Accounts", methods: viewModel.bankMethods, icon: "building.columns.fill")
                methodsSection(title: "Mobile Money", methods: viewModel.mobileMoneyMethods, icon: "iphone.gen3.radiowaves.left.and.right")
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Payment Methods")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddOptions = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                }
            }
        }
        .task { await viewModel.refresh(user: user) }
        .refreshable { await viewModel.refresh(user: user) }
        .confirmationDialog("Add Payment Method", isPresented: $showAddOptions, titleVisibility: .visible) {
            Button("Card") { showAddCardSheet = true }
            Button("Bank Account") { showAddBankSheet = true }
            Button("Mobile Money") { showAddMobileMoneySheet = true }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showAddCardSheet) {
            AddCardSheet(viewModel: viewModel, isPresented: $showAddCardSheet)
        }
        .sheet(isPresented: $showAddBankSheet) {
            AddBankSheet(viewModel: viewModel, isPresented: $showAddBankSheet)
        }
        .sheet(isPresented: $showAddMobileMoneySheet) {
            AddMobileMoneySheet(viewModel: viewModel, isPresented: $showAddMobileMoneySheet)
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
        .alert("Status", isPresented: Binding(
            get: { viewModel.statusMessage != nil },
            set: { if !$0 { viewModel.statusMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.statusMessage ?? "")
        }
    }

    @ViewBuilder
    private var payoutSetupCard: some View {
        PaymentCardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Text("Payout Setup")
                    .font(.headline)
                Text("Complete setup to receive wallet-to-card and wallet-to-bank payouts.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

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

                HStack(spacing: 10) {
                    Button("Complete Setup") {
                        Task {
                            await viewModel.startPayoutSetup()
                            if let url = URL(string: viewModel.onboardingUrl), !viewModel.onboardingUrl.isEmpty {
                                openURL(url)
                            }
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isWorking)

                    Button("Refresh Link") {
                        Task {
                            await viewModel.createOnboardingLink()
                            if let url = URL(string: viewModel.onboardingUrl), !viewModel.onboardingUrl.isEmpty {
                                openURL(url)
                            }
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(viewModel.isWorking)
                }

                if viewModel.isWorking {
                    ProgressView()
                        .controlSize(.small)
                }
            }
        }
    }

    @ViewBuilder
    private var relinkWarningCard: some View {
        PaymentCardContainer {
            VStack(alignment: .leading, spacing: 8) {
                Text("Action required")
                    .font(.headline)
                    .foregroundStyle(.red)
                Text("One or more cards require re-linking before instant charges can be used.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Re-link Card") {
                    showAddCardSheet = true
                }
                .buttonStyle(.borderedProminent)
            }
        }
    }

    @ViewBuilder
    private func methodsSection(title: String, methods: [PaymentMethodRecord], icon: String) -> some View {
        PaymentCardContainer {
            VStack(alignment: .leading, spacing: 10) {
                Label(title, systemImage: icon)
                    .font(.headline)

                if methods.isEmpty {
                    Text("No \(title.lowercased()) found.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(methods.enumerated()), id: \.offset) { index, method in
                        methodRow(method)
                        if index < methods.count - 1 {
                            Divider()
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func methodRow(_ method: PaymentMethodRecord) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(viewModel.displayName(method))
                            .font(.subheadline.weight(.semibold))
                        if viewModel.isDefault(method) {
                            Text("Default")
                                .font(.caption2.weight(.bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.green.opacity(0.15))
                                .foregroundStyle(.green)
                                .clipShape(Capsule())
                        }
                    }
                    Text(viewModel.subtitle(method))
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if let status = method.status, !status.isEmpty {
                        Text(status)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
            }

            HStack(spacing: 8) {
                if !viewModel.isDefault(method) {
                    Button("Set Default") {
                        Task {
                            let result = await viewModel.setDefault(methodId: viewModel.methodId(method))
                            if result.success {
                                viewModel.statusMessage = result.message
                            } else {
                                viewModel.errorMessage = result.message
                            }
                        }
                    }
                    .buttonStyle(.bordered)
                    .disabled(viewModel.isWorking || viewModel.methodId(method).isEmpty)
                }

                if normalizedType(method) == "MOBILE_MONEY" && viewModel.normalizedVerificationStatus(method) != "VERIFIED" {
                    Button("Verify Now") {
                        Task {
                            let result = await viewModel.requestMobileMoneyVerification(methodId: viewModel.methodId(method))
                            if result.success {
                                viewModel.statusMessage = result.message
                            } else {
                                viewModel.errorMessage = result.message
                            }
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isWorking || viewModel.methodId(method).isEmpty)
                }

                Button("Delete", role: .destructive) {
                    Task {
                        let result = await viewModel.deletePaymentMethod(methodId: viewModel.methodId(method))
                        if result.success {
                            viewModel.statusMessage = result.message
                        } else {
                            viewModel.errorMessage = result.message
                        }
                    }
                }
                .buttonStyle(.bordered)
                .disabled(viewModel.isWorking || viewModel.methodId(method).isEmpty)
            }

            if normalizedType(method) == "MOBILE_MONEY",
               let verificationError = method.lastVerificationError,
               !verificationError.isEmpty,
               viewModel.normalizedVerificationStatus(method) == "FAILED" {
                Text(verificationError)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
        }
    }

    @ViewBuilder
    private func statusRow(_ title: String, _ value: Bool) -> some View {
        HStack {
            Text(title)
                .font(.footnote)
            Spacer()
            Text(value ? "Yes" : "No")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(value ? .green : .secondary)
        }
    }

    private func normalizedType(_ method: PaymentMethodRecord) -> String {
        (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    private var hasCardRelinkRequirement: Bool {
        viewModel.cardMethods.contains { $0.requiresRelinkForCharges == true }
    }
}

private struct PaymentCardContainer<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}

private struct AddCardSheet: View {
    @ObservedObject var viewModel: PaymentMethodsViewModel
    @Binding var isPresented: Bool

    @State private var name = ""
    @State private var number = ""
    @State private var expiry = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Cardholder Name", text: $name)
                TextField("Card Number", text: $number)
                    .keyboardType(.numberPad)
                TextField("Expiry (MM/YY)", text: $expiry)
                    .keyboardType(.numbersAndPunctuation)
            }
            .navigationTitle("Add Card")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task {
                            let result = await viewModel.addCard(name: name, number: number, expiry: expiry)
                            if result.success {
                                viewModel.statusMessage = result.message
                                isPresented = false
                            } else {
                                viewModel.errorMessage = result.message
                            }
                        }
                    }
                    .disabled(viewModel.isWorking)
                }
            }
        }
    }
}

private struct AddBankSheet: View {
    @ObservedObject var viewModel: PaymentMethodsViewModel
    @Binding var isPresented: Bool

    @State private var bankName = ""
    @State private var accountHolder = ""
    @State private var accountNumber = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Bank Name", text: $bankName)
                TextField("Account Holder", text: $accountHolder)
                TextField("Account Number", text: $accountNumber)
                    .keyboardType(.numberPad)
            }
            .navigationTitle("Add Bank")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task {
                            let result = await viewModel.addBankAccount(
                                bankName: bankName,
                                accountHolderName: accountHolder,
                                accountNumber: accountNumber
                            )
                            if result.success {
                                viewModel.statusMessage = result.message
                                isPresented = false
                            } else {
                                viewModel.errorMessage = result.message
                            }
                        }
                    }
                    .disabled(viewModel.isWorking)
                }
            }
        }
    }
}

private struct AddMobileMoneySheet: View {
    @ObservedObject var viewModel: PaymentMethodsViewModel
    @Binding var isPresented: Bool

    @State private var phone = ""
    @State private var network = ""
    @State private var registeredName = ""
    @State private var country = "United States"
    @State private var dialCode = "+1"
    @State private var currency = "USD"

    var body: some View {
        NavigationStack {
            Form {
                TextField("Phone Number", text: $phone)
                    .keyboardType(.phonePad)
                TextField("Network", text: $network)
                TextField("Registered Name", text: $registeredName)
                TextField("Country", text: $country)
                TextField("Dial Code", text: $dialCode)
                TextField("Currency", text: $currency)
                    .textInputAutocapitalization(.characters)
            }
            .navigationTitle("Add Mobile Money")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        Task {
                            let result = await viewModel.addMobileMoneyAccount(
                                phone: phone,
                                network: network,
                                registeredName: registeredName,
                                country: country,
                                dialCode: dialCode,
                                currency: currency
                            )
                            if result.success {
                                viewModel.statusMessage = result.message
                                isPresented = false
                            } else {
                                viewModel.errorMessage = result.message
                            }
                        }
                    }
                    .disabled(viewModel.isWorking)
                }
            }
        }
    }
}

