import Foundation

struct PaymentMethodActionResult {
    let success: Bool
    let message: String
}

@MainActor
final class PaymentMethodsViewModel: ObservableObject {
    @Published private(set) var methods: [PaymentMethodRecord] = []
    @Published private(set) var payoutStatus: PayoutSetupStatusRecord?
    @Published var isLoading = false
    @Published var isWorking = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?
    @Published var onboardingUrl = ""

    private let repository = PaymentsRepository()
    private var currentUserId = ""

    var cardMethods: [PaymentMethodRecord] {
        methods.filter { normalizedType($0) == "CARD" }
    }

    var bankMethods: [PaymentMethodRecord] {
        methods.filter { normalizedType($0) == "BANK" }
    }

    var mobileMoneyMethods: [PaymentMethodRecord] {
        methods.filter { normalizedType($0) == "MOBILE_MONEY" }
    }

    func refresh(user: AppSessionUser) async {
        currentUserId = user.uid
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let methodsTask = repository.fetchPaymentMethods(uid: user.uid)
            async let payoutTask = repository.fetchPayoutStatus()
            methods = try await methodsTask
            payoutStatus = try await payoutTask
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func startPayoutSetup() async {
        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }
        do {
            try await repository.createConnectAccount()
            onboardingUrl = try await repository.createOnboardingLink()
            payoutStatus = try await repository.fetchPayoutStatus()
            statusMessage = onboardingUrl.isEmpty ? "Payout setup started." : "Payout setup link generated."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func createOnboardingLink() async {
        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }
        do {
            onboardingUrl = try await repository.createOnboardingLink()
            if onboardingUrl.isEmpty {
                errorMessage = "Onboarding URL was not returned."
            } else {
                statusMessage = "Onboarding link generated."
            }
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func addCard(name: String, number: String, expiry: String) async -> PaymentMethodActionResult {
        let sanitizedNumber = number.filter(\.isNumber)
        if name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PaymentMethodActionResult(success: false, message: "Cardholder name is required.")
        }
        if let error = validateCardNumber(sanitizedNumber) {
            return PaymentMethodActionResult(success: false, message: error)
        }
        if let error = validateExpiry(expiry) {
            return PaymentMethodActionResult(success: false, message: error)
        }

        isWorking = true
        defer { isWorking = false }
        do {
            let response = try await repository.addCard(
                cardHolderName: name,
                cardNumber: sanitizedNumber,
                expiryDate: expiry,
                isDefault: methods.isEmpty
            )
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: response.message)
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func addBankAccount(bankName: String, accountHolderName: String, accountNumber: String) async -> PaymentMethodActionResult {
        let sanitized = accountNumber.filter(\.isNumber)
        if bankName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PaymentMethodActionResult(success: false, message: "Bank name is required.")
        }
        if accountHolderName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PaymentMethodActionResult(success: false, message: "Account holder name is required.")
        }
        if let error = validateAccountNumber(sanitized) {
            return PaymentMethodActionResult(success: false, message: error)
        }

        isWorking = true
        defer { isWorking = false }
        do {
            let response = try await repository.addBankAccount(
                bankName: bankName,
                accountHolderName: accountHolderName,
                accountNumber: sanitized,
                isDefault: methods.isEmpty
            )
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: response.message)
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func addMobileMoneyAccount(
        phone: String,
        network: String,
        registeredName: String,
        country: String,
        dialCode: String,
        currency: String
    ) async -> PaymentMethodActionResult {
        if phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PaymentMethodActionResult(success: false, message: "Phone number is required.")
        }
        if network.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return PaymentMethodActionResult(success: false, message: "Network is required.")
        }

        isWorking = true
        defer { isWorking = false }
        do {
            let response = try await repository.addMobileMoneyAccount(
                phone: phone,
                network: network,
                registeredName: registeredName,
                country: country,
                dialCode: dialCode,
                currency: currency,
                isDefault: methods.isEmpty
            )
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: response.message)
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func requestMobileMoneyVerification(methodId: String) async -> PaymentMethodActionResult {
        isWorking = true
        defer { isWorking = false }
        do {
            let message = try await repository.requestMobileMoneyVerification(paymentMethodId: methodId)
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: message)
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func deletePaymentMethod(methodId: String) async -> PaymentMethodActionResult {
        guard !currentUserId.isEmpty else {
            return PaymentMethodActionResult(success: false, message: "You must be logged in.")
        }

        isWorking = true
        defer { isWorking = false }
        do {
            try await repository.deletePaymentMethod(uid: currentUserId, methodId: methodId)
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: "Payment method deleted successfully.")
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func setDefault(methodId: String) async -> PaymentMethodActionResult {
        guard !currentUserId.isEmpty else {
            return PaymentMethodActionResult(success: false, message: "You must be logged in.")
        }

        isWorking = true
        defer { isWorking = false }
        do {
            try await repository.setDefaultPaymentMethod(uid: currentUserId, methodId: methodId)
            methods = try await repository.fetchPaymentMethods(uid: currentUserId)
            return PaymentMethodActionResult(success: true, message: "Default payment method updated.")
        } catch {
            return PaymentMethodActionResult(success: false, message: AppErrorMapper.message(from: error))
        }
    }

    func isDefault(_ method: PaymentMethodRecord) -> Bool {
        method.isDefault == true
    }

    func methodId(_ method: PaymentMethodRecord) -> String {
        if let id = method.id?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty {
            return id
        }
        if let external = method.externalAccountId?.trimmingCharacters(in: .whitespacesAndNewlines), !external.isEmpty {
            return external
        }
        return ""
    }

    func displayName(_ method: PaymentMethodRecord) -> String {
        switch normalizedType(method) {
        case "CARD":
            return method.label ?? method.brand ?? "Card"
        case "BANK":
            return method.label ?? method.bankName ?? "Bank Account"
        case "MOBILE_MONEY":
            let network = method.network ?? method.brand ?? "Mobile Money"
            let phone = method.phoneNumber ?? ""
            return phone.isEmpty ? network : "\(network) (\(phone))"
        default:
            return method.label ?? method.brand ?? method.type ?? "Method"
        }
    }

    func subtitle(_ method: PaymentMethodRecord) -> String {
        let last4 = method.last4 ?? "0000"
        switch normalizedType(method) {
        case "CARD":
            return "**** **** **** \(last4)"
        case "BANK":
            return "Account ****\(last4)"
        case "MOBILE_MONEY":
            let status = normalizedVerificationStatus(method)
                .replacingOccurrences(of: "_", with: " ")
                .capitalized
            return "\(method.country ?? "") - \(status)"
        default:
            return "**** \(last4)"
        }
    }

    func normalizedVerificationStatus(_ method: PaymentMethodRecord) -> String {
        let status = (method.verificationStatus ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if !status.isEmpty {
            return status
        }
        return method.phoneOwnershipVerified == true ? "VERIFIED" : "UNVERIFIED"
    }

    private func normalizedType(_ method: PaymentMethodRecord) -> String {
        (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    }

    private func validateAccountNumber(_ number: String) -> String? {
        if number.isEmpty { return "Account number is required." }
        if number.count < 6 { return "Account number is too short." }
        if number.count > 18 { return "Account number is too long." }
        return nil
    }

    private func validateCardNumber(_ number: String) -> String? {
        if number.isEmpty { return "Card number is required." }
        if number.count < 12 || number.count > 19 { return "Card number length is invalid." }
        if !passesLuhn(number) { return "Card number is invalid." }
        return nil
    }

    private func validateExpiry(_ expiry: String) -> String? {
        let pattern = try? NSRegularExpression(pattern: "^(0[1-9]|1[0-2])/[0-9]{2}$")
        let range = NSRange(location: 0, length: expiry.count)
        guard pattern?.firstMatch(in: expiry, options: [], range: range) != nil else {
            return "Expiry must be in MM/YY format."
        }

        let parts = expiry.split(separator: "/")
        guard parts.count == 2,
              let month = Int(parts[0]),
              let year = Int(parts[1]) else {
            return "Expiry must be in MM/YY format."
        }

        let calendar = Calendar.current
        let currentYear = calendar.component(.year, from: Date()) % 100
        let currentMonth = calendar.component(.month, from: Date())

        if year < currentYear || (year == currentYear && month < currentMonth) {
            return "Card is expired."
        }

        return nil
    }

    private func passesLuhn(_ number: String) -> Bool {
        var sum = 0
        var shouldDouble = false

        for char in number.reversed() {
            guard var digit = Int(String(char)) else { return false }
            if shouldDouble {
                digit *= 2
                if digit > 9 { digit -= 9 }
            }
            sum += digit
            shouldDouble.toggle()
        }
        return sum % 10 == 0
    }
}

