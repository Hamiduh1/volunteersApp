import Foundation
import Combine

@MainActor
final class OwnerSystemConfigViewModel: ObservableObject {
    @Published var maintenanceMode = false
    @Published var allowNewSignups = true
    @Published var enableBlindDate = true
    @Published var enableLiveStreams = true
    @Published var maxUploadMb = "10"

    @Published var isLoading = false
    @Published var isSaving = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = OwnerAdminRepository()
    private let defaultRecord = OwnerSystemConfigRecord(
        maintenanceMode: false,
        allowNewSignups: true,
        enableBlindDate: true,
        enableLiveStreams: true,
        maxUploadMb: 10
    )
    private var loadedConfig: OwnerSystemConfigRecord?

    var canSave: Bool {
        !isLoading && !isSaving && validationMessage == nil && hasUnsavedChanges
    }

    var hasUnsavedChanges: Bool {
        guard let current = parseCurrentConfig() else { return false }
        guard let loadedConfig else { return true }
        return current.maintenanceMode != loadedConfig.maintenanceMode
            || current.allowNewSignups != loadedConfig.allowNewSignups
            || current.enableBlindDate != loadedConfig.enableBlindDate
            || current.enableLiveStreams != loadedConfig.enableLiveStreams
            || current.maxUploadMb != loadedConfig.maxUploadMb
    }

    var validationMessage: String? {
        guard let maxUpload = Int(maxUploadMb.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return "Max upload must be a whole number."
        }
        guard (1...2048).contains(maxUpload) else {
            return "Max upload must be between 1 MB and 2048 MB."
        }
        return nil
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            let config = try await repository.fetchSystemConfig()
            loadedConfig = config
            apply(config)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func save() async {
        guard validationMessage == nil, let config = parseCurrentConfig() else {
            errorMessage = validationMessage ?? "Max upload must be a valid number."
            return
        }

        isSaving = true
        errorMessage = nil
        statusMessage = nil
        defer { isSaving = false }

        do {
            try await repository.saveSystemConfig(config)
            loadedConfig = config
            apply(config)
            statusMessage = "System config saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func restoreLastLoaded() {
        guard let loadedConfig else { return }
        errorMessage = nil
        statusMessage = nil
        apply(loadedConfig)
    }

    func applyDefaults() {
        errorMessage = nil
        statusMessage = nil
        apply(defaultRecord)
    }

    func setUploadPreset(_ sizeMb: Int) {
        maxUploadMb = String(sizeMb)
    }

    private func parseCurrentConfig() -> OwnerSystemConfigRecord? {
        guard let maxUpload = Int(maxUploadMb.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            return nil
        }
        return OwnerSystemConfigRecord(
            maintenanceMode: maintenanceMode,
            allowNewSignups: allowNewSignups,
            enableBlindDate: enableBlindDate,
            enableLiveStreams: enableLiveStreams,
            maxUploadMb: maxUpload
        )
    }

    private func apply(_ config: OwnerSystemConfigRecord) {
        maintenanceMode = config.maintenanceMode
        allowNewSignups = config.allowNewSignups
        enableBlindDate = config.enableBlindDate
        enableLiveStreams = config.enableLiveStreams
        maxUploadMb = String(config.maxUploadMb)
    }
}
