import Foundation
import Combine

enum WalletOperationsTab: String, CaseIterable, Identifiable {
    case mobileMoney = "mobile_money"
    case agent

    var id: String { rawValue }

    var title: String {
        switch self {
        case .mobileMoney: return "Mobile Money"
        case .agent: return "Agent"
        }
    }
}

enum MobileMoneyFlowType: String, CaseIterable, Identifiable {
    case cashIn = "cash_in"
    case cashOut = "cash_out"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .cashIn: return "Cash In"
        case .cashOut: return "Cash Out"
        }
    }
}

@MainActor
final class WalletOperationsViewModel: ObservableObject {
    @Published var selectedTab: WalletOperationsTab
    @Published var mobileMoneyFlow: MobileMoneyFlowType = .cashIn
    @Published var mobileAmountText = ""
    @Published var selectedMobileMethodId = ""
    @Published var agentAmountText = ""
    @Published var payoutSecretCode = ""

    @Published private(set) var summary = WalletSummary(balance: 0, currency: "USD")
    @Published private(set) var allMethods: [PaymentMethodRecord] = []
    @Published private(set) var statusMessage: String?
    @Published private(set) var errorMessage: String?
    @Published private(set) var isLoading = false
    @Published private(set) var isWorking = false
    @Published private(set) var generatedCodeRecord: AgentWithdrawalCodeRecord?

    private let repository = GlobalWalletRepository()
    private var currentUserId = ""

    init(initialTab: WalletOperationsTab = .mobileMoney) {
        selectedTab = initialTab
    }

    var mobileMoneyMethods: [PaymentMethodRecord] {
        allMethods.filter { method in
            let type = (method.type ?? "").trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return type.contains("MOBILE_MONEY")
        }
    }

    var selectedMobileMethod: PaymentMethodRecord? {
        mobileMoneyMethods.first { methodIdentifier($0) == selectedMobileMethodId }
    }

    var parsedMobileAmount: Double {
        let cleaned = mobileAmountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "")
        return Double(cleaned) ?? 0
    }

    var parsedAgentAmount: Double {
        let cleaned = agentAmountText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "")
        return Double(cleaned) ?? 0
    }

    var currentAgentFeePreview: AgentCashOutFeeRecord? {
        let amount = parsedAgentAmount
        guard amount > 0 else { return nil }
        return repository.calculateAgentCashOutFee(amount: amount)
    }

    var canSubmitMobileMoney: Bool {
        parsedMobileAmount > 0 && selectedMobileMethod != nil && !isWorking
    }

    var canGenerateCode: Bool {
        parsedAgentAmount > 0 && !isWorking
    }

    var canCompletePayout: Bool {
        payoutSecretCode.trimmingCharacters(in: .whitespacesAndNewlines).count >= 6 && !isWorking
    }

    func refresh(user: AppSessionUser) async {
        currentUserId = user.uid
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchSummary(uid: user.uid)
            async let methodsTask = repository.fetchPaymentMethods(uid: user.uid)
            summary = try await summaryTask
            allMethods = try await methodsTask

            if selectedMobileMethod == nil {
                selectedMobileMethodId = mobileMoneyMethods.first.map(methodIdentifier) ?? ""
            }
            statusMessage = "Wallet operations loaded."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func submitMobileMoney() async {
        guard let method = selectedMobileMethod else {
            errorMessage = "Select a mobile money method."
            return
        }

        let amount = parsedMobileAmount
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }

        if mobileMoneyFlow == .cashOut && summary.balance < amount {
            errorMessage = "Insufficient balance."
            return
        }

        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }

        let phone = method.phoneNumber?.trimmedNonEmpty ?? ""
        let network = method.network?.trimmedNonEmpty ?? (method.brand?.trimmedNonEmpty ?? "MobileMoney")
        let country = method.country?.trimmedNonEmpty ?? "United States"
        let dialCode = inferredDialCode(from: phone)
        let localCurrency = summary.currency
        let methodId = method.id?.trimmedNonEmpty

        do {
            let message: String
            switch mobileMoneyFlow {
            case .cashIn:
                message = try await repository.depositWithMobileMoney(
                    uid: currentUserId,
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
            case .cashOut:
                message = try await repository.withdrawToMobileMoney(
                    uid: currentUserId,
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

            statusMessage = message
            mobileAmountText = ""
            summary = try await repository.fetchSummary(uid: currentUserId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func authorizeAgent() async {
        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }

        do {
            statusMessage = try await repository.authorizeAgent()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func generateWithdrawalCode() async {
        let amount = parsedAgentAmount
        guard amount > 0 else {
            errorMessage = "Enter a valid amount."
            return
        }

        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }

        do {
            generatedCodeRecord = try await repository.generateAgentWithdrawalCode(
                uid: currentUserId,
                amount: amount,
                currency: summary.currency,
                currentBalance: summary.balance
            )
            statusMessage = "Code generated. It expires in 15 minutes."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func completeAgentPayout() async {
        let code = payoutSecretCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty else {
            errorMessage = "Enter the payout code."
            return
        }

        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }

        do {
            let message = try await repository.completeAgentCashOut(secretCode: code)
            statusMessage = message
            payoutSecretCode = ""
            summary = try await repository.fetchSummary(uid: currentUserId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func cashOutAgentEarnings() async {
        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }

        do {
            statusMessage = try await repository.cashOutAgentEarnings()
            summary = try await repository.fetchSummary(uid: currentUserId)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
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

    private func inferredDialCode(from phone: String) -> String {
        guard phone.hasPrefix("+") else { return "+1" }
        let digits = phone.dropFirst().prefix(3).filter(\.isNumber)
        if digits.isEmpty {
            return "+1"
        }
        return "+\(digits)"
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let cleaned = trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }
}
