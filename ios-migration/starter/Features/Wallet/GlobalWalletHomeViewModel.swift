import Foundation
import Combine

enum WalletFundingDirection: String {
    case deposit
    case withdraw
}

@MainActor
final class GlobalWalletHomeViewModel: ObservableObject {
    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var transactions: [WalletTransactionRecord] = []
    @Published private(set) var pendingDepositCount: Int = 0
    @Published private(set) var paymentMethods: [PaymentMethodRecord] = []
    @Published private(set) var beneficiaries: [BeneficiaryRecord] = []
    @Published private(set) var supportedCountries: [String] = []

    @Published var selectedFundingMethodId = ""
    @Published var fundingAmountText = ""
    @Published var isSubmittingFunding = false

    @Published var calculatorAmount = "10"
    @Published var calculatorFromCountry = "United States"
    @Published var calculatorToCountry = "Ghana"
    @Published var calculatorResult: Double = 10
    @Published var calculatorRate: Double = 1
    @Published var calculatorError: String?
    @Published var isCalculating = false

    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = GlobalWalletRepository()
    private var currentUid = ""
    private var calculatorTask: Task<Void, Never>?
    private let countryCurrencyMap: [String: String] = [
        "United States": "USD", "Euro Area": "EUR", "Japan": "JPY", "United Kingdom": "GBP",
        "Australia": "AUD", "Canada": "CAD", "Switzerland": "CHF", "China": "CNY",
        "Hong Kong": "HKD", "New Zealand": "NZD", "Sweden": "SEK", "South Korea": "KRW",
        "Singapore": "SGD", "Norway": "NOK", "Mexico": "MXN", "India": "INR",
        "Russia": "RUB", "Brazil": "BRL", "South Africa": "ZAR", "Turkey": "TRY",
        "Indonesia": "IDR", "Poland": "PLN", "Philippines": "PHP", "Thailand": "THB",
        "United Arab Emirates": "AED", "Saudi Arabia": "SAR", "Israel": "ILS",
        "Nigeria": "NGN", "Egypt": "EGP", "Ghana": "GHS", "Kenya": "KES", "Uganda": "UGX",
        "Tanzania": "TZS", "Algeria": "DZD", "Morocco": "MAD", "Ethiopia": "ETB",
        "Zambia": "ZMW", "Botswana": "BWP", "Namibia": "NAD", "Rwanda": "RWF"
    ]

    init() {
        supportedCountries = countryCurrencyMap.keys.sorted()
    }

    deinit {
        calculatorTask?.cancel()
    }

    var parsedFundingAmount: Double {
        let cleaned = fundingAmountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "")
        return Double(cleaned) ?? 0
    }

    var selectedFundingMethod: PaymentMethodRecord? {
        paymentMethods.first { methodIdentifier($0) == selectedFundingMethodId }
    }

    var mobileMoneyMethods: [PaymentMethodRecord] {
        paymentMethods.filter(isMobileMoneyMethod)
    }

    func fundingEligibleMethods(for direction: WalletFundingDirection) -> [PaymentMethodRecord] {
        paymentMethods.filter { method in
            if isMobileMoneyMethod(method) { return true }
            if isCardMethod(method) { return true }
            if isBankMethod(method) {
                switch direction {
                case .deposit:
                    return isAchDepositEnabled(method)
                case .withdraw:
                    return true
                }
            }
            return false
        }
    }

    func refresh(uid: String) async {
        currentUid = uid
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchSummary(uid: uid)
            async let txTask = repository.fetchTransactions(uid: uid, limit: 20)
            async let pendingTask = repository.fetchPendingDepositCount(uid: uid)
            async let methodsTask = repository.fetchPaymentMethods(uid: uid, limit: 60)
            async let beneficiariesTask = repository.fetchBeneficiaries(uid: uid, limit: 80)
            summary = try await summaryTask
            transactions = try await txTask
            pendingDepositCount = try await pendingTask
            paymentMethods = try await methodsTask
            beneficiaries = try await beneficiariesTask

            let eligible = fundingEligibleMethods(for: .deposit)
            if selectedFundingMethod == nil || !eligible.contains(where: { methodIdentifier($0) == selectedFundingMethodId }) {
                selectedFundingMethodId = eligible.first.map(methodIdentifier) ?? ""
            }

            await recalculateCalculator(forceInstant: true)
            statusMessage = "Wallet loaded. \(transactions.count) recent transactions."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func submitFunding(_ direction: WalletFundingDirection) async {
        guard !currentUid.isEmpty else {
            errorMessage = "User session unavailable."
            return
        }
        let amount = parsedFundingAmount
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }
        guard let method = selectedFundingMethod else {
            errorMessage = "Select a payment method."
            return
        }
        if direction == .withdraw && amount > summary.balance {
            errorMessage = "Insufficient wallet balance."
            return
        }
        if direction == .deposit && isBankMethod(method) && !isAchDepositEnabled(method) {
            errorMessage = "This bank account is not enabled for ACH deposits."
            return
        }
        if direction == .withdraw && isMobileMoneyMethod(method) && method.phoneOwnershipVerified == false {
            errorMessage = "This mobile money number must be verified first."
            return
        }

        isSubmittingFunding = true
        errorMessage = nil
        statusMessage = nil
        defer { isSubmittingFunding = false }

        do {
            let message: String
            if isMobileMoneyMethod(method) {
                let phone = method.phoneNumber?.trimmedNonEmpty ?? ""
                let network = method.network?.trimmedNonEmpty ?? method.brand?.trimmedNonEmpty ?? "MobileMoney"
                let country = method.country?.trimmedNonEmpty ?? "United States"
                let dialCode = inferredDialCode(from: phone)
                let localCurrency = summary.currency
                let methodId = method.id?.trimmedNonEmpty

                switch direction {
                case .deposit:
                    message = try await repository.depositWithMobileMoney(
                        uid: currentUid,
                        amount: amount,
                        currency: summary.currency,
                        phone: phone,
                        network: network,
                        country: country,
                        dialCode: dialCode,
                        localCurrency: localCurrency,
                        paymentMethodId: methodId,
                        localAmount: amount
                    )
                case .withdraw:
                    message = try await repository.withdrawToMobileMoney(
                        uid: currentUid,
                        amount: amount,
                        currency: summary.currency,
                        phone: phone,
                        network: network,
                        country: country,
                        dialCode: dialCode,
                        localCurrency: localCurrency,
                        paymentMethodId: methodId,
                        localAmount: amount
                    )
                }
            } else {
                switch direction {
                case .deposit:
                    let methodId = methodIdentifier(method)
                    message = try await repository.depositFromExternalSource(
                        uid: currentUid,
                        amount: amount,
                        paymentMethodId: methodId,
                        walletCurrency: summary.currency
                    )
                case .withdraw:
                    message = try await repository.sendToPaymentMethod(
                        senderUserId: currentUid,
                        paymentMethod: method,
                        amount: amount,
                        currency: summary.currency,
                        note: "Wallet withdrawal"
                    )
                }
            }

            fundingAmountText = ""
            statusMessage = message
            try await refreshWalletSnapshot()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func deleteBeneficiary(_ beneficiary: BeneficiaryRecord) async {
        guard !currentUid.isEmpty else { return }
        guard let beneficiaryId = beneficiary.id?.trimmedNonEmpty else {
            errorMessage = "Invalid beneficiary identifier."
            return
        }

        do {
            try await repository.deleteBeneficiary(uid: currentUid, beneficiaryId: beneficiaryId)
            beneficiaries.removeAll { $0.id == beneficiaryId }
            statusMessage = "\(beneficiary.name ?? "Beneficiary") removed."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func authorizeAgent() async {
        errorMessage = nil
        statusMessage = nil
        do {
            statusMessage = try await repository.authorizeAgent()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func onCalculatorInputsChanged(
        amount: String? = nil,
        fromCountry: String? = nil,
        toCountry: String? = nil
    ) {
        if let amount {
            calculatorAmount = amount
        }
        if let fromCountry, countryCurrencyMap[fromCountry] != nil {
            calculatorFromCountry = fromCountry
        }
        if let toCountry, countryCurrencyMap[toCountry] != nil {
            calculatorToCountry = toCountry
        }
        Task { await recalculateCalculator(forceInstant: false) }
    }

    func methodIdentifier(_ method: PaymentMethodRecord) -> String {
        if let id = method.id?.trimmedNonEmpty {
            return id
        }
        if let external = method.externalAccountId?.trimmedNonEmpty {
            return external
        }
        return ""
    }

    func methodLabel(_ method: PaymentMethodRecord) -> String {
        let type = normalizedMethodType(method)
        if isMobileMoneyMethod(method) {
            let network = method.network?.trimmedNonEmpty ?? method.brand?.trimmedNonEmpty ?? "Mobile Money"
            let phone = method.phoneNumber?.trimmedNonEmpty ?? "No phone"
            return "\(network) - \(phone)"
        }
        let primary = (method.bankName?.trimmedNonEmpty ?? method.brand?.trimmedNonEmpty ?? type).capitalized
        let last4 = method.last4?.trimmedNonEmpty ?? "0000"
        return "\(primary) ...\(last4)"
    }

    func countryCodeLabel(_ country: String) -> String {
        let code = countryCurrencyMap[country] ?? ""
        return code.isEmpty ? country : code
    }

    private func refreshWalletSnapshot() async throws {
        async let summaryTask = repository.fetchSummary(uid: currentUid)
        async let txTask = repository.fetchTransactions(uid: currentUid, limit: 20)
        async let pendingTask = repository.fetchPendingDepositCount(uid: currentUid)
        summary = try await summaryTask
        transactions = try await txTask
        pendingDepositCount = try await pendingTask
    }

    private func recalculateCalculator(forceInstant: Bool) async {
        calculatorTask?.cancel()

        let delayNanos: UInt64 = forceInstant ? 0 : 300_000_000
        calculatorTask = Task { [weak self] in
            guard let self else { return }
            if delayNanos > 0 {
                try? await Task.sleep(nanoseconds: delayNanos)
            }
            if Task.isCancelled { return }
            await self.computeCalculatorQuote()
        }
    }

    private func computeCalculatorQuote() async {
        let amount = Double(
            calculatorAmount
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: ",", with: "")
        ) ?? 0

        guard amount > 0 else {
            calculatorResult = 0
            calculatorRate = 0
            calculatorError = "Enter a valid amount."
            isCalculating = false
            return
        }

        guard let fromCurrency = countryCurrencyMap[calculatorFromCountry],
              let toCurrency = countryCurrencyMap[calculatorToCountry] else {
            calculatorError = "Select valid countries."
            isCalculating = false
            return
        }

        calculatorError = nil
        isCalculating = true
        defer { isCalculating = false }

        if fromCurrency == toCurrency {
            calculatorRate = 1
            calculatorResult = amount
            return
        }

        do {
            let quote = try await repository.getQuote(
                amount: amount,
                fromCurrency: fromCurrency,
                toCurrency: toCurrency
            )
            calculatorRate = quote.rate
            calculatorResult = quote.recipientAmount
        } catch {
            calculatorError = AppErrorMapper.message(from: error)
        }
    }

    private func normalizedMethodType(_ method: PaymentMethodRecord) -> String {
        (method.type ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
    }

    private func isCardMethod(_ method: PaymentMethodRecord) -> Bool {
        let normalizedType = normalizedMethodType(method)
        let brand = (method.brand ?? "").uppercased()
        return normalizedType.contains("CARD")
            || brand.contains("VISA")
            || brand.contains("MASTERCARD")
            || brand.contains("AMEX")
    }

    private func isBankMethod(_ method: PaymentMethodRecord) -> Bool {
        let normalizedType = normalizedMethodType(method)
        return normalizedType.contains("BANK") || normalizedType.contains("ACCOUNT")
    }

    private func isMobileMoneyMethod(_ method: PaymentMethodRecord) -> Bool {
        let normalizedType = normalizedMethodType(method)
        return normalizedType.contains("MOBILE")
    }

    private func isAchDepositEnabled(_ method: PaymentMethodRecord) -> Bool {
        guard isBankMethod(method) else { return false }
        return method.achDebitEnabled == true || method.chargeSourceId?.trimmedNonEmpty != nil
    }

    private func inferredDialCode(from phone: String) -> String {
        let trimmed = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("+") else { return "+1" }
        let digits = trimmed.dropFirst().prefix(3).filter(\.isNumber)
        guard !digits.isEmpty else { return "+1" }
        return "+\(digits)"
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let cleaned = trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }
}

