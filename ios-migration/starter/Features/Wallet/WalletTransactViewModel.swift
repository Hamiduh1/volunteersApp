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
    @Published var destinationType: WalletDestinationType = .appUser {
        didSet {
            statusMessage = nil
            quote = nil
            errorMessage = nil
        }
    }
    @Published var recipientUserId = "" {
        didSet { quote = nil }
    }
    @Published var selectedBeneficiaryId = "" {
        didSet { quote = nil }
    }
    @Published var selectedPaymentMethodId = "" {
        didSet { quote = nil }
    }
    @Published var amountText = "" {
        didSet { quote = nil }
    }
    @Published var fromCurrency = "USD" {
        didSet { quote = nil }
    }
    @Published var toCurrency = "USD" {
        didSet { quote = nil }
    }
    @Published var note = ""

    @Published private(set) var beneficiaries: [BeneficiaryRecord] = []
    @Published private(set) var paymentMethods: [PaymentMethodRecord] = []
    @Published private(set) var quote: WalletQuoteRecord?
    @Published var isLoading = false
    @Published var isFetchingQuote = false
    @Published var isSubmitting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()

    var canFetchQuote: Bool {
        amountValue > 0 && !normalizedFromCurrency.isEmpty && !normalizedToCurrency.isEmpty && !isFetchingQuote
    }

    var canSubmit: Bool {
        amountValue > 0 && destinationValidationError == nil && !isSubmitting
    }

    private var amountValue: Double {
        Double(amountText.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
    }

    private var normalizedFromCurrency: String {
        fromCurrency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    private var normalizedToCurrency: String {
        toCurrency.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    private var destinationValidationError: String? {
        switch destinationType {
        case .appUser:
            return recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "Recipient user ID is required."
                : nil
        case .beneficiary:
            return selectedBeneficiaryId.isEmpty ? "Select a beneficiary." : nil
        case .paymentMethod:
            return selectedPaymentMethodId.isEmpty ? "Select a payment method." : nil
        }
    }

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
        let amount = amountValue
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }
        guard !normalizedFromCurrency.isEmpty, !normalizedToCurrency.isEmpty else {
            errorMessage = "Enter both currencies."
            return
        }

        isFetchingQuote = true
        errorMessage = nil
        defer { isFetchingQuote = false }

        do {
            quote = try await repository.getQuote(
                amount: amount,
                fromCurrency: normalizedFromCurrency,
                toCurrency: normalizedToCurrency
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func submit() async {
        let amount = amountValue
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }
        if let destinationValidationError {
            errorMessage = destinationValidationError
            return
        }

        if normalizedFromCurrency != normalizedToCurrency && quote == nil {
            errorMessage = "Get quote before sending across currencies."
            return
        }

        isSubmitting = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmitting = false }

        do {
            let message: String
            switch destinationType {
            case .appUser:
                let recipient = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
                message = try await repository.sendToAppUser(
                    recipientUserId: recipient,
                    amount: amount,
                    currency: normalizedFromCurrency,
                    note: note
                )
            case .beneficiary:
                message = try await repository.sendToBeneficiary(
                    beneficiaryId: selectedBeneficiaryId,
                    amount: amount,
                    currency: normalizedFromCurrency,
                    note: note
                )
            case .paymentMethod:
                message = try await repository.sendToPaymentMethod(
                    paymentMethodId: selectedPaymentMethodId,
                    amount: amount,
                    currency: normalizedFromCurrency,
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
