import Foundation

@MainActor
final class NotificationSettingsViewModel: ObservableObject {
    @Published private(set) var settings: NotificationSettingsRecord?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var statusMessage: String?

    private let uid: String
    private let repository = AlertsRepository()

    init(uid: String) {
        self.uid = uid
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            settings = try await repository.fetchNotificationSettings(uid: uid)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            if settings == nil {
                settings = NotificationSettingsRecord()
            }
        }
    }

    func toggle(_ field: NotificationSettingField, enabled: Bool) async {
        guard var current = settings else { return }
        current.set(field, enabled: enabled)
        settings = current

        do {
            try await repository.updateNotificationSetting(uid: uid, field: field, enabled: enabled)
            statusMessage = "\(field.title) updated."
        } catch {
            current.set(field, enabled: !enabled)
            settings = current
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
