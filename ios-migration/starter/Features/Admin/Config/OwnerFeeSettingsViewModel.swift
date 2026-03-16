import Foundation
import Combine

@MainActor
final class OwnerFeeSettingsViewModel: ObservableObject {
    @Published var blindDateFeeUsd = "10.0"
    @Published var agentAuthorizationFeeUsd = "1.0"
    @Published var forexProfitMargin = "0.010"
    @Published var stripeForexDepositProfitMargin = "0.005"
    @Published var mobileMoneyHiddenFeeRate = "0.0"

    @Published var isLoading = false
    @Published var isSaving = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let record = try await repository.fetchFeeSettings()
            blindDateFeeUsd = String(record.blindDateFeeUsd)
            agentAuthorizationFeeUsd = String(record.agentAuthorizationFeeUsd)
            forexProfitMargin = String(record.forexProfitMargin)
            stripeForexDepositProfitMargin = String(record.stripeForexDepositProfitMargin)
            mobileMoneyHiddenFeeRate = String(record.mobileMoneyHiddenFeeRate)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func save() async {
        guard
            let blind = Double(blindDateFeeUsd),
            let agent = Double(agentAuthorizationFeeUsd),
            let forex = Double(forexProfitMargin),
            let stripe = Double(stripeForexDepositProfitMargin),
            let hidden = Double(mobileMoneyHiddenFeeRate)
        else {
            errorMessage = "Enter valid numeric values for all fee fields."
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            let record = OwnerFeeSettingsRecord(
                blindDateFeeUsd: blind,
                agentAuthorizationFeeUsd: agent,
                forexProfitMargin: forex,
                stripeForexDepositProfitMargin: stripe,
                mobileMoneyHiddenFeeRate: hidden
            )
            try await repository.saveFeeSettings(record)
            statusMessage = "Fee settings saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
