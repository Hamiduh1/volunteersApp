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

enum CallDirectionFilter: String, CaseIterable, Identifiable {
    case all
    case incoming
    case outgoing
    case missed

    var id: String { rawValue }
    var title: String { rawValue.capitalized }
}

@MainActor
final class CallHistoryViewModel: ObservableObject {
    @Published private(set) var callLogs: [CallLogRecord] = []
    @Published var selectedFilter: CallHistoryFilter = .all
    @Published var directionFilter: CallDirectionFilter = .all
    @Published var query = ""
    @Published var isLoading = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = CallsRepository()

    var filteredCallLogs: [CallLogRecord] {
        let byType: [CallLogRecord]
        switch selectedFilter {
        case .all:
            byType = callLogs
        case .audio:
            byType = callLogs.filter { ($0.type ?? "").lowercased() != "video" }
        case .video:
            byType = callLogs.filter { ($0.type ?? "").lowercased() == "video" }
        case .missed:
            byType = callLogs.filter { ($0.status ?? "").lowercased() == "missed" }
        }

        let byDirection: [CallLogRecord]
        switch directionFilter {
        case .all:
            byDirection = byType
        case .incoming:
            byDirection = byType.filter { ($0.direction ?? "").lowercased() == "incoming" }
        case .outgoing:
            byDirection = byType.filter { ($0.direction ?? "").lowercased() == "outgoing" }
        case .missed:
            byDirection = byType.filter { ($0.status ?? "").lowercased() == "missed" || ($0.direction ?? "").lowercased() == "missed" }
        }

        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return byDirection }
        return byDirection.filter { call in
            (call.peerName ?? "").lowercased().contains(cleanQuery)
                || (call.peerUid ?? "").lowercased().contains(cleanQuery)
                || (call.status ?? "").lowercased().contains(cleanQuery)
                || (call.type ?? "").lowercased().contains(cleanQuery)
        }
    }

    var missedCount: Int {
        callLogs.filter { ($0.status ?? "").lowercased() == "missed" || ($0.direction ?? "").lowercased() == "missed" }.count
    }

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            callLogs = try await repository.fetchCallLogs(uid: user.uid)
            statusMessage = callLogs.isEmpty
                ? "No call records found."
                : "Loaded \(callLogs.count) call records (\(missedCount) missed)."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

