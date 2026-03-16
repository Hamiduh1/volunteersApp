import Foundation
import Combine

enum CallHistoryFilter: String, CaseIterable, Identifiable {
    case all
    case audio
    case video
    case missed

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class CallHistoryViewModel: ObservableObject {
    @Published private(set) var callLogs: [CallLogRecord] = []
    @Published var selectedFilter: CallHistoryFilter = .all
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = CallsRepository()

    var filteredCallLogs: [CallLogRecord] {
        switch selectedFilter {
        case .all:
            return callLogs
        case .audio:
            return callLogs.filter { ($0.type ?? "").lowercased() != "video" }
        case .video:
            return callLogs.filter { ($0.type ?? "").lowercased() == "video" }
        case .missed:
            return callLogs.filter { ($0.status ?? "").lowercased() == "missed" }
        }
    }

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
