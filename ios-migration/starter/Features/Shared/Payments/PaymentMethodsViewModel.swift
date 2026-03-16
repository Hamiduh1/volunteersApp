import Foundation
import Combine

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

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let methodsTask = repository.fetchPaymentMethods(uid: user.uid)
            async let payoutTask = repository.fetchPayoutStatus()
            methods = try await methodsTask
            payoutStatus = try await payoutTask
            statusMessage = "Loaded \(methods.count) payment methods."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func createConnectAccount() async {
        isWorking = true
        errorMessage = nil
        statusMessage = nil
        defer { isWorking = false }
        do {
            try await repository.createConnectAccount()
            payoutStatus = try await repository.fetchPayoutStatus()
            statusMessage = "Connect account created or already exists."
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
}

