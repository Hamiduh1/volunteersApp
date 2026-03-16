import SwiftUI

struct WalletTransactView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = WalletTransactViewModel()

    var body: some View {
        Form {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            Section("Destination") {
                Picker("Type", selection: $viewModel.destinationType) {
                    ForEach(WalletDestinationType.allCases) { option in
                        Text(option.title).tag(option)
                    }
                }

                switch viewModel.destinationType {
                case .appUser:
                    TextField("Recipient User ID", text: $viewModel.recipientUserId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                case .beneficiary:
                    if viewModel.beneficiaries.isEmpty {
                        Text("No beneficiaries found.")
                            .foregroundStyle(.secondary)
                    } else {
                        Picker("Beneficiary", selection: $viewModel.selectedBeneficiaryId) {
                            ForEach(viewModel.beneficiaries) { item in
                                let id = item.id ?? ""
                                Text(itemLabel(item)).tag(id)
                            }
                        }
                    }
                case .paymentMethod:
                    if viewModel.paymentMethods.isEmpty {
                        Text("No payment methods found.")
                            .foregroundStyle(.secondary)
                    } else {
                        Picker("Method", selection: $viewModel.selectedPaymentMethodId) {
                            ForEach(viewModel.paymentMethods) { method in
                                let id = method.id ?? ""
                                Text(methodLabel(method)).tag(id)
                            }
                        }
                    }
                }
            }

            Section("Amount") {
                TextField("Amount", text: $viewModel.amountText)
                    .keyboardType(.decimalPad)
                TextField("From Currency", text: $viewModel.fromCurrency)
                    .textInputAutocapitalization(.characters)
                TextField("To Currency", text: $viewModel.toCurrency)
                    .textInputAutocapitalization(.characters)
                TextField("Note (optional)", text: $viewModel.note)

                Button("Get Quote") {
                    Task { await viewModel.fetchQuote() }
                }
                .buttonStyle(.bordered)
                .disabled(!viewModel.canFetchQuote)

                if viewModel.isFetchingQuote {
                    ProgressView()
                        .controlSize(.small)
                }

                if let quote = viewModel.quote {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Rate: \(String(format: "%.4f", quote.rate))")
                        Text("You send: \(quote.sourceCurrency) \(String(format: "%.2f", quote.sourceAmount))")
                        Text("Recipient gets: \(quote.targetCurrency) \(String(format: "%.2f", quote.recipientAmount))")
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }

            Section {
                Button {
                    Task { await viewModel.submit() }
                } label: {
                    if viewModel.isSubmitting {
                        ProgressView()
                    } else {
                        Text("Send Money")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!viewModel.canSubmit)
            }
        }
        .navigationTitle("Send Money")
        .task { await viewModel.refresh(uid: user.uid) }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "Unknown error")
        }
    }

    private func itemLabel(_ item: BeneficiaryRecord) -> String {
        let name = item.name ?? "Beneficiary"
        let suffix = [item.network, item.phone].compactMap { $0 }.joined(separator: " - ")
        return suffix.isEmpty ? name : "\(name) - \(suffix)"
    }

    private func methodLabel(_ method: PaymentMethodRecord) -> String {
        let brand = (method.brand ?? method.type ?? "Method").capitalized
        let last4 = method.last4 ?? "0000"
        return "\(brand) **** \(last4)"
    }
}
