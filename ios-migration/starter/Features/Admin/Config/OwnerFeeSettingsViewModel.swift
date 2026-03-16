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
    private let defaultRecord = OwnerFeeSettingsRecord(
        blindDateFeeUsd: 10.0,
        agentAuthorizationFeeUsd: 1.0,
        forexProfitMargin: 0.010,
        stripeForexDepositProfitMargin: 0.005,
        mobileMoneyHiddenFeeRate: 0.0
    )
    private var loadedRecord: OwnerFeeSettingsRecord?

    var hasUnsavedChanges: Bool {
        guard let current = parseRecord() else { return false }
        guard let loadedRecord else { return true }
        return !recordsEqual(current, loadedRecord)
    }

    var canSave: Bool {
        !isLoading && !isSaving && validationMessage == nil && hasUnsavedChanges
    }

    var validationMessage: String? {
        guard let record = parseRecord() else {
            return "Enter valid numeric values for all fee fields."
        }

        guard record.blindDateFeeUsd >= 0, record.agentAuthorizationFeeUsd >= 0 else {
            return "USD fees cannot be negative."
        }
        guard (0...1).contains(record.forexProfitMargin),
              (0...1).contains(record.stripeForexDepositProfitMargin),
              (0...1).contains(record.mobileMoneyHiddenFeeRate) else {
            return "Margin and fee-rate values must be between 0 and 1."
        }
        return nil
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let record = try await repository.fetchFeeSettings()
            loadedRecord = record
            apply(record)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func save() async {
        guard validationMessage == nil, let record = parseRecord() else {
            errorMessage = validationMessage ?? "Enter valid numeric values for all fee fields."
            return
        }

        isSaving = true
        errorMessage = nil
        statusMessage = nil
        defer { isSaving = false }

        do {
            try await repository.saveFeeSettings(record)
            loadedRecord = record
            apply(record)
            statusMessage = "Fee settings saved."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func restoreLastLoaded() {
        guard let loadedRecord else { return }
        errorMessage = nil
        statusMessage = nil
        apply(loadedRecord)
    }

    func applyDefaults() {
        errorMessage = nil
        statusMessage = nil
        apply(defaultRecord)
    }

    private func parseRecord() -> OwnerFeeSettingsRecord? {
        guard
            let blind = Double(blindDateFeeUsd.trimmingCharacters(in: .whitespacesAndNewlines)),
            let agent = Double(agentAuthorizationFeeUsd.trimmingCharacters(in: .whitespacesAndNewlines)),
            let forex = Double(forexProfitMargin.trimmingCharacters(in: .whitespacesAndNewlines)),
            let stripe = Double(stripeForexDepositProfitMargin.trimmingCharacters(in: .whitespacesAndNewlines)),
            let hidden = Double(mobileMoneyHiddenFeeRate.trimmingCharacters(in: .whitespacesAndNewlines))
        else {
            return nil
        }

        return OwnerFeeSettingsRecord(
            blindDateFeeUsd: blind,
            agentAuthorizationFeeUsd: agent,
            forexProfitMargin: forex,
            stripeForexDepositProfitMargin: stripe,
            mobileMoneyHiddenFeeRate: hidden
        )
    }

    private func apply(_ record: OwnerFeeSettingsRecord) {
        blindDateFeeUsd = format(record.blindDateFeeUsd, maxFractionDigits: 2)
        agentAuthorizationFeeUsd = format(record.agentAuthorizationFeeUsd, maxFractionDigits: 2)
        forexProfitMargin = format(record.forexProfitMargin, maxFractionDigits: 4)
        stripeForexDepositProfitMargin = format(record.stripeForexDepositProfitMargin, maxFractionDigits: 4)
        mobileMoneyHiddenFeeRate = format(record.mobileMoneyHiddenFeeRate, maxFractionDigits: 4)
    }

    private func format(_ value: Double, maxFractionDigits: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = maxFractionDigits
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }

    private func recordsEqual(_ lhs: OwnerFeeSettingsRecord, _ rhs: OwnerFeeSettingsRecord) -> Bool {
        approx(lhs.blindDateFeeUsd, rhs.blindDateFeeUsd)
            && approx(lhs.agentAuthorizationFeeUsd, rhs.agentAuthorizationFeeUsd)
            && approx(lhs.forexProfitMargin, rhs.forexProfitMargin)
            && approx(lhs.stripeForexDepositProfitMargin, rhs.stripeForexDepositProfitMargin)
            && approx(lhs.mobileMoneyHiddenFeeRate, rhs.mobileMoneyHiddenFeeRate)
    }

    private func approx(_ lhs: Double, _ rhs: Double, epsilon: Double = 0.000001) -> Bool {
        abs(lhs - rhs) < epsilon
    }
}

