import Foundation
import Combine

enum WalletDestinationType: String, CaseIterable, Identifiable {
    case appUser = "app_user"
    case beneficiary
    case paymentMethod = "payment_method"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .appUser: return "App User"
        case .beneficiary: return "Beneficiary"
        case .paymentMethod: return "Bank/Card"
        }
    }
}

@MainActor
final class WalletTransactViewModel: ObservableObject {
    @Published var destinationType: WalletDestinationType = .appUser
    @Published var recipientUserId = ""
    @Published var selectedBeneficiaryId = ""
    @Published var selectedPaymentMethodId = ""
    @Published var amountText = ""
    @Published var fromCurrency = "USD"
    @Published var toCurrency = "USD"
    @Published var note = ""

    @Published private(set) var beneficiaries: [BeneficiaryRecord] = []
    @Published private(set) var paymentMethods: [PaymentMethodRecord] = []
    @Published private(set) var quote: WalletQuoteRecord?
    @Published var isLoading = false
    @Published var isSubmitting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    func refresh(uid: String) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let beneficiariesTask = repository.fetchBeneficiaries(uid: uid)
            async let methodsTask = repository.fetchPaymentMethods(uid: uid)
            beneficiaries = try await beneficiariesTask
            paymentMethods = try await methodsTask

            if selectedBeneficiaryId.isEmpty {
                selectedBeneficiaryId = beneficiaries.first?.id ?? ""
            }
            if selectedPaymentMethodId.isEmpty {
                selectedPaymentMethodId = paymentMethods.first?.id ?? ""
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func fetchQuote() async {
        guard let amount = Double(amountText), amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }

        do {
            quote = try await repository.getQuote(
                amount: amount,
                fromCurrency: fromCurrency,
                toCurrency: toCurrency
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func submit(user: AppSessionUser) async {
        guard let amount = Double(amountText), amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }

        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }

        do {
            let message: String
            switch destinationType {
            case .appUser:
                let recipient = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !recipient.isEmpty else {
                    errorMessage = "Recipient user ID is required."
                    return
                }
                message = try await repository.sendToAppUser(
                    recipientUserId: recipient,
                    amount: amount,
                    currency: fromCurrency,
                    note: note
                )
            case .beneficiary:
                guard !selectedBeneficiaryId.isEmpty else {
                    errorMessage = "Select a beneficiary."
                    return
                }
                message = try await repository.sendToBeneficiary(
                    beneficiaryId: selectedBeneficiaryId,
                    amount: amount,
                    currency: fromCurrency,
                    note: note
                )
            case .paymentMethod:
                guard !selectedPaymentMethodId.isEmpty else {
                    errorMessage = "Select a payment method."
                    return
                }
                message = try await repository.sendToPaymentMethod(
                    paymentMethodId: selectedPaymentMethodId,
                    amount: amount,
                    currency: fromCurrency,
                    note: note
                )
            }

            statusMessage = message
            amountText = ""
            note = ""
            quote = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
