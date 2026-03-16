import Foundation
import Combine

@MainActor
final class CallHistoryViewModel: ObservableObject {
    @Published private(set) var callLogs: [CallLogRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = CallsRepository()

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            callLogs = try await repository.fetchCallLogs(uid: user.uid)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
