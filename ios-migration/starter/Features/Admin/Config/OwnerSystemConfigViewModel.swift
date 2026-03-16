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

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let config = try await repository.fetchSystemConfig()
            maintenanceMode = config.maintenanceMode
            allowNewSignups = config.allowNewSignups
            enableBlindDate = config.enableBlindDate
            enableLiveStreams = config.enableLiveStreams
            maxUploadMb = String(config.maxUploadMb)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func save() async {
        guard let maxUpload = Int(maxUploadMb), maxUpload > 0 else {
            errorMessage = "Max upload must be a positive number."
            return
        }

        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            let config = OwnerSystemConfigRecord(
                maintenanceMode: maintenanceMode,
                allowNewSignups: allowNewSignups,
                enableBlindDate: enableBlindDate,
                enableLiveStreams: enableLiveStreams,
                maxUploadMb: maxUpload
            )
            try await repository.saveSystemConfig(config)
            statusMessage = "System config saved."
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
