import Foundation
import Combine

@MainActor
final class OwnerFeeSettingsViewModel: ObservableObject {
    @Published var blindDateFeeUsd = "10.0"
    @Published var agentAuthorizationFeeUsd = "1.0"
    @Published var adPostFeeUsd = "1.0"
    @Published var forexProfitMargin = "0.010"
    @Published var stripeForexDepositProfitMargin = "0.005"
    @Published var mobileMoneyHiddenFeeRate = "0.0"
    @Published var eventTicketOwnerFeeRate = "0.05"
    @Published var marketplacePlatinumFeeRate = "0.02"
    @Published var garageSaleFeeRate = "0.02"

    @Published var isLoading = false
    @Published var isSaving = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()
    private let defaultRecord = OwnerFeeSettingsRecord(
        blindDateFeeUsd: 10.0,
        agentAuthorizationFeeUsd: 1.0,
        adPostFeeUsd: 1.0,
        forexProfitMargin: 0.010,
        stripeForexDepositProfitMargin: 0.005,
        mobileMoneyHiddenFeeRate: 0.0,
        eventTicketOwnerFeeRate: 0.05,
        marketplacePlatinumFeeRate: 0.02,
        garageSaleFeeRate: 0.02
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

        guard record.blindDateFeeUsd >= 0,
              record.agentAuthorizationFeeUsd >= 0,
              record.adPostFeeUsd >= 0 else {
            return "USD fees cannot be negative."
        }
        guard (0...1).contains(record.forexProfitMargin),
              (0...1).contains(record.stripeForexDepositProfitMargin),
              (0...1).contains(record.mobileMoneyHiddenFeeRate),
              (0...1).contains(record.eventTicketOwnerFeeRate),
              (0...1).contains(record.marketplacePlatinumFeeRate),
              (0...1).contains(record.garageSaleFeeRate) else {
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
            let adPost = Double(adPostFeeUsd.trimmingCharacters(in: .whitespacesAndNewlines)),
            let forex = Double(forexProfitMargin.trimmingCharacters(in: .whitespacesAndNewlines)),
            let stripe = Double(stripeForexDepositProfitMargin.trimmingCharacters(in: .whitespacesAndNewlines)),
            let hidden = Double(mobileMoneyHiddenFeeRate.trimmingCharacters(in: .whitespacesAndNewlines)),
            let eventOwnerFee = Double(eventTicketOwnerFeeRate.trimmingCharacters(in: .whitespacesAndNewlines)),
            let marketplaceFee = Double(marketplacePlatinumFeeRate.trimmingCharacters(in: .whitespacesAndNewlines)),
            let garageSaleFee = Double(garageSaleFeeRate.trimmingCharacters(in: .whitespacesAndNewlines))
        else {
            return nil
        }

        return OwnerFeeSettingsRecord(
            blindDateFeeUsd: blind,
            agentAuthorizationFeeUsd: agent,
            adPostFeeUsd: adPost,
            forexProfitMargin: forex,
            stripeForexDepositProfitMargin: stripe,
            mobileMoneyHiddenFeeRate: hidden,
            eventTicketOwnerFeeRate: eventOwnerFee,
            marketplacePlatinumFeeRate: marketplaceFee,
            garageSaleFeeRate: garageSaleFee
        )
    }

    private func apply(_ record: OwnerFeeSettingsRecord) {
        blindDateFeeUsd = format(record.blindDateFeeUsd, maxFractionDigits: 2)
        agentAuthorizationFeeUsd = format(record.agentAuthorizationFeeUsd, maxFractionDigits: 2)
        adPostFeeUsd = format(record.adPostFeeUsd, maxFractionDigits: 2)
        forexProfitMargin = format(record.forexProfitMargin, maxFractionDigits: 4)
        stripeForexDepositProfitMargin = format(record.stripeForexDepositProfitMargin, maxFractionDigits: 4)
        mobileMoneyHiddenFeeRate = format(record.mobileMoneyHiddenFeeRate, maxFractionDigits: 4)
        eventTicketOwnerFeeRate = format(record.eventTicketOwnerFeeRate, maxFractionDigits: 4)
        marketplacePlatinumFeeRate = format(record.marketplacePlatinumFeeRate, maxFractionDigits: 4)
        garageSaleFeeRate = format(record.garageSaleFeeRate, maxFractionDigits: 4)
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
            && approx(lhs.adPostFeeUsd, rhs.adPostFeeUsd)
            && approx(lhs.forexProfitMargin, rhs.forexProfitMargin)
            && approx(lhs.stripeForexDepositProfitMargin, rhs.stripeForexDepositProfitMargin)
            && approx(lhs.mobileMoneyHiddenFeeRate, rhs.mobileMoneyHiddenFeeRate)
            && approx(lhs.eventTicketOwnerFeeRate, rhs.eventTicketOwnerFeeRate)
            && approx(lhs.marketplacePlatinumFeeRate, rhs.marketplacePlatinumFeeRate)
            && approx(lhs.garageSaleFeeRate, rhs.garageSaleFeeRate)
    }

    private func approx(_ lhs: Double, _ rhs: Double, epsilon: Double = 0.000001) -> Bool {
        abs(lhs - rhs) < epsilon
    }
}

