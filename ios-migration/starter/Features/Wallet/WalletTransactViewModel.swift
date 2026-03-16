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
    private enum Constants {
        static let minCurrencyLength = 3
    }

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
    private var currentUserId = ""

    var canFetchQuote: Bool {
        amountValue > 0 && !normalizedFromCurrency.isEmpty && !normalizedToCurrency.isEmpty && !isFetchingQuote
    }

    var canSubmit: Bool {
        amountValue > 0 && destinationValidationError == nil && !isSubmitting
    }

    private var amountValue: Double {
        let cleaned = amountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "")
        return Double(cleaned) ?? 0
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
            guard !selectedBeneficiaryId.isEmpty else { return "Select a beneficiary." }
            guard selectedBeneficiary != nil else { return "Selected beneficiary is no longer available." }
            return nil
        case .paymentMethod:
            guard !selectedPaymentMethodId.isEmpty else { return "Select a payment method." }
            guard let method = selectedPaymentMethod else { return "Selected method is no longer available." }
            let normalizedType = (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if normalizedType.contains("MOBILE_MONEY") {
                return "Select a bank account or card for this destination."
            }
            return nil
        }
    }

    func refresh(uid: String) async {
        currentUserId = uid
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let beneficiariesTask = repository.fetchBeneficiaries(uid: uid)
            async let methodsTask = repository.fetchPaymentMethods(uid: uid)
            beneficiaries = try await beneficiariesTask
            paymentMethods = try await methodsTask

            if selectedBeneficiaryId.isEmpty || selectedBeneficiary == nil {
                selectedBeneficiaryId = beneficiaries.first?.id ?? ""
            }
            if selectedPaymentMethodId.isEmpty || selectedPaymentMethod == nil {
                selectedPaymentMethodId = paymentMethods.first?.id ?? ""
            }
            statusMessage = "Ready to send. \(beneficiaries.count) beneficiaries and \(paymentMethods.count) payment methods loaded."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func fetchQuote() async {
        let amount = amountValue
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }
        guard normalizedFromCurrency.count >= Constants.minCurrencyLength,
              normalizedToCurrency.count >= Constants.minCurrencyLength else {
            errorMessage = "Enter valid 3-letter currency codes."
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
            errorMessage = AppErrorMapper.message(from: error)
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
        guard !currentUserId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            errorMessage = "User session is unavailable. Refresh and try again."
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
                guard let beneficiary = selectedBeneficiary else {
                    errorMessage = "Selected beneficiary is unavailable."
                    return
                }
                message = try await repository.sendToBeneficiary(
                    beneficiary: beneficiary,
                    amount: amount,
                    currency: normalizedFromCurrency,
                    note: note
                )
            case .paymentMethod:
                guard let method = selectedPaymentMethod else {
                    errorMessage = "Selected payment method is unavailable."
                    return
                }
                message = try await repository.sendToPaymentMethod(
                    senderUserId: currentUserId,
                    paymentMethod: method,
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
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private var selectedBeneficiary: BeneficiaryRecord? {
        beneficiaries.first { $0.id == selectedBeneficiaryId }
    }

    private var selectedPaymentMethod: PaymentMethodRecord? {
        paymentMethods.first { $0.id == selectedPaymentMethodId }
    }
}

