import Foundation
import Combine

@MainActor
final class PaymentMethodsViewModel: ObservableObject {
    @Published private(set) var methods: [PaymentMethodRecord] = []
    @Published private(set) var payoutStatus: PayoutSetupStatusRecord?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var onboardingUrl = ""

    private let repository = PaymentsRepository()

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let methodsTask = repository.fetchPaymentMethods(uid: user.uid)
            async let payoutTask = repository.fetchPayoutStatus()
            methods = try await methodsTask
            payoutStatus = try await payoutTask
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createConnectAccount() async {
        do {
            try await repository.createConnectAccount()
            payoutStatus = try await repository.fetchPayoutStatus()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createOnboardingLink() async {
        do {
            onboardingUrl = try await repository.createOnboardingLink()
            if onboardingUrl.isEmpty {
                errorMessage = "Onboarding URL was not returned."
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
