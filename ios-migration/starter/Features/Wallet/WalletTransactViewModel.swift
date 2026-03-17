import Foundation
import Combine

enum WalletDestinationType: String, CaseIterable, Identifiable {
    case wallet
    case card
    case bank
    case beneficiary

    var id: String { rawValue }

    var title: String {
        switch self {
        case .wallet: return "App User Wallet"
        case .card: return "App User Card"
        case .bank: return "App User Bank"
        case .beneficiary: return "Beneficiary Mobile Money"
        }
    }

    var backendDestinationType: String? {
        switch self {
        case .wallet: return "WALLET"
        case .card: return "CARD"
        case .bank: return "BANK"
        case .beneficiary: return nil
        }
    }
}

@MainActor
final class WalletTransactViewModel: ObservableObject {
    private enum Constants {
        static let minCurrencyLength = 3
    }

    @Published var destinationType: WalletDestinationType = .wallet {
        didSet {
            statusMessage = nil
            quote = nil
            errorMessage = nil
            if destinationType == .beneficiary {
                clearRecipientRoutingState()
            } else {
                scheduleRecipientRoutingLookup()
            }
        }
    }
    @Published var recipientUserId = "" {
        didSet {
            quote = nil
            scheduleRecipientRoutingLookup()
        }
    }
    @Published var selectedBeneficiaryId = "" {
        didSet { quote = nil }
    }
    @Published var selectedRecipientMethodId = "" {
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

    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var beneficiaries: [BeneficiaryRecord] = []
    @Published private(set) var recipientMethods: [PaymentMethodRecord] = []
    @Published private(set) var recipientHasPayoutAccount = false
    @Published private(set) var isLoadingRecipientMethods = false
    @Published private(set) var quote: WalletQuoteRecord?
    @Published var isLoading = false
    @Published var isFetchingQuote = false
    @Published var isSubmitting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()
    private var currentUserId = ""
    private var recipientLookupTask: Task<Void, Never>?

    deinit {
        recipientLookupTask?.cancel()
    }

    var canFetchQuote: Bool {
        amountValue > 0 &&
            !normalizedFromCurrency.isEmpty &&
            !normalizedToCurrency.isEmpty &&
            !isFetchingQuote
    }

    var canSubmit: Bool {
        amountValue > 0 &&
            destinationValidationError == nil &&
            !isWalletInsufficient &&
            !isSubmitting
    }

    var isWalletInsufficient: Bool {
        amountValue > summary.balance
    }

    var recipientDestinationHelpText: String? {
        switch destinationType {
        case .wallet:
            return "Transfers directly into another app user's wallet."
        case .card, .bank:
            if recipientUserId.trimmedNonEmpty == nil {
                return "Enter recipient user ID to validate payout setup."
            }
            if !recipientHasPayoutAccount {
                return "Recipient must complete payout setup in Payment Methods first."
            }
            if filteredRecipientMethods.isEmpty {
                return destinationType == .card
                    ? "Recipient has no eligible card payout method."
                    : "Recipient has no eligible bank payout method."
            }
            return "Recipient payout routing ready."
        case .beneficiary:
            return "Use saved beneficiary details for mobile money transfer."
        }
    }

    var filteredRecipientMethods: [PaymentMethodRecord] {
        switch destinationType {
        case .card:
            return recipientMethods.filter { method in
                destinationForMethod(method) == .card && isMethodPayoutReady(method)
            }
        case .bank:
            return recipientMethods.filter { method in
                destinationForMethod(method) == .bank && isMethodPayoutReady(method)
            }
        default:
            return []
        }
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
        case .wallet:
            return recipientUserId.trimmedNonEmpty == nil
                ? "Recipient user ID is required."
                : nil
        case .beneficiary:
            guard !selectedBeneficiaryId.isEmpty else { return "Select a beneficiary." }
            guard selectedBeneficiary != nil else { return "Selected beneficiary is unavailable." }
            return nil
        case .card, .bank:
            guard recipientUserId.trimmedNonEmpty != nil else {
                return "Recipient user ID is required."
            }
            guard recipientHasPayoutAccount else {
                return "Recipient has not completed payout setup."
            }
            guard !filteredRecipientMethods.isEmpty else {
                return destinationType == .card
                    ? "Recipient has no eligible card payout method."
                    : "Recipient has no eligible bank payout method."
            }
            guard selectedRecipientMethod != nil else {
                return "Select a recipient payout method."
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
            async let summaryTask = repository.fetchSummary(uid: uid)
            async let beneficiariesTask = repository.fetchBeneficiaries(uid: uid)

            summary = try await summaryTask
            beneficiaries = try await beneficiariesTask

            if selectedBeneficiaryId.isEmpty || selectedBeneficiary == nil {
                selectedBeneficiaryId = beneficiaries.first?.id ?? ""
            }
            if fromCurrency.uppercased() == "USD" && toCurrency.uppercased() == "USD" {
                fromCurrency = summary.currency
                toCurrency = summary.currency
            }
            statusMessage = "Wallet ready. Balance \(summary.currency) \(String(format: "%.2f", summary.balance))."
            scheduleRecipientRoutingLookup()
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
        if isWalletInsufficient {
            errorMessage = "Insufficient wallet balance."
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
            case .wallet:
                message = try await repository.sendToAppUser(
                    recipientUserId: recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines),
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
            case .card, .bank:
                guard let selectedMethod = selectedRecipientMethod else {
                    errorMessage = "Select a recipient payout method."
                    return
                }
                message = try await repository.sendToRecipientPayout(
                    recipientUserId: recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines),
                    paymentMethod: selectedMethod,
                    destinationType: destinationType.backendDestinationType,
                    amount: amount,
                    currency: normalizedFromCurrency,
                    note: note
                )
            }

            statusMessage = message
            amountText = ""
            note = ""
            quote = nil
            if let refreshedSummary = try? await repository.fetchSummary(uid: currentUserId) {
                summary = refreshedSummary
            }
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private var selectedBeneficiary: BeneficiaryRecord? {
        beneficiaries.first { $0.id == selectedBeneficiaryId }
    }

    private var selectedRecipientMethod: PaymentMethodRecord? {
        filteredRecipientMethods.first { recipientMethodIdentifier($0) == selectedRecipientMethodId }
    }

    private func scheduleRecipientRoutingLookup() {
        recipientLookupTask?.cancel()

        guard destinationType != .beneficiary else {
            clearRecipientRoutingState()
            return
        }
        guard let recipientId = recipientUserId.trimmedNonEmpty else {
            clearRecipientRoutingState()
            return
        }

        recipientLookupTask = Task { [weak self] in
            await self?.loadRecipientRouting(recipientUserId: recipientId)
        }
    }

    private func loadRecipientRouting(recipientUserId: String) async {
        let requestedRecipient = recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !requestedRecipient.isEmpty else {
            clearRecipientRoutingState()
            return
        }

        isLoadingRecipientMethods = true
        defer { isLoadingRecipientMethods = false }

        do {
            async let payoutTask = repository.fetchRecipientHasPayoutAccount(recipientUserId: requestedRecipient)
            async let methodsTask = repository.fetchRecipientPaymentMethods(recipientUserId: requestedRecipient)
            let hasPayout = try await payoutTask
            let methods = try await methodsTask

            guard requestedRecipient == recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines) else {
                return
            }

            recipientHasPayoutAccount = hasPayout
            recipientMethods = methods

            if let selected = selectedRecipientMethod,
               filteredRecipientMethods.contains(where: { recipientMethodIdentifier($0) == recipientMethodIdentifier(selected) }) {
                // Keep current selection.
            } else {
                selectedRecipientMethodId = filteredRecipientMethods.first.map(recipientMethodIdentifier) ?? ""
            }
        } catch {
            guard requestedRecipient == recipientUserId.trimmingCharacters(in: .whitespacesAndNewlines) else {
                return
            }
            recipientHasPayoutAccount = false
            recipientMethods = []
            selectedRecipientMethodId = ""
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func clearRecipientRoutingState() {
        recipientHasPayoutAccount = false
        recipientMethods = []
        selectedRecipientMethodId = ""
        isLoadingRecipientMethods = false
    }

    func recipientMethodIdentifier(_ method: PaymentMethodRecord) -> String {
        if let id = method.id?.trimmedNonEmpty {
            return id
        }
        if let external = method.externalAccountId?.trimmedNonEmpty {
            return external
        }
        return ""
    }

    private func destinationForMethod(_ method: PaymentMethodRecord) -> WalletDestinationType? {
        let type = (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let brand = (method.brand ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()

        if type.contains("CARD") ||
            brand.contains("VISA") ||
            brand.contains("MASTERCARD") ||
            brand.contains("AMEX") {
            return .card
        }
        if type.contains("BANK") || type.contains("ACCOUNT") {
            return .bank
        }
        return nil
    }

    private func isMethodPayoutReady(_ method: PaymentMethodRecord) -> Bool {
        let status = (method.status ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if status.contains("DISABLED") || status.contains("REVOKED") {
            return false
        }

        let hasIdentifier =
            method.id?.trimmedNonEmpty != nil ||
            method.externalAccountId?.trimmedNonEmpty != nil
        if !hasIdentifier {
            return false
        }

        if destinationForMethod(method) == .bank {
            if let achCreditEnabled = method.achCreditEnabled, achCreditEnabled == false {
                return false
            }
        }
        return true
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let cleaned = trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }
}
