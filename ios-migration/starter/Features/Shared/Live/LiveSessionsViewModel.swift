import Foundation
import Combine

enum LiveSessionStatusFilter: String, CaseIterable, Identifiable {
    case all
    case live
    case scheduled
    case ended

    var id: String { rawValue }

    var title: String {
        rawValue.capitalized
    }
}

@MainActor
final class LiveSessionsViewModel: ObservableObject {
    @Published private(set) var sessions: [LiveSessionRecord] = []
    @Published var query = ""
    @Published var statusFilter: LiveSessionStatusFilter = .all
    @Published var isLoading = false
    @Published var isRequestingToken = false
    @Published private(set) var requestingSessionKey: String?
    @Published var statusMessage: String?
    @Published var tokenStatusMessage: String?
    @Published var errorMessage: String?
    @Published var tokenPreview = ""

    private let repository = LiveRepository()

    var filteredSessions: [LiveSessionRecord] {
        let statusFiltered: [LiveSessionRecord]
        switch statusFilter {
        case .all:
            statusFiltered = sessions
        default:
            statusFiltered = sessions.filter { matchesStatus($0.status, filter: statusFilter) }
        }

        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanQuery.isEmpty else { return statusFiltered }
        return statusFiltered.filter { session in
            (session.title ?? "").lowercased().contains(cleanQuery)
                || (session.hostName ?? "").lowercased().contains(cleanQuery)
                || (session.hostId ?? "").lowercased().contains(cleanQuery)
                || (session.agoraChannelName ?? "").lowercased().contains(cleanQuery)
                || (session.status ?? "").lowercased().contains(cleanQuery)
        }
    }

    var liveCount: Int {
        sessions.filter { matchesStatus($0.status, filter: .live) }.count
    }

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            sessions = try await repository.fetchLiveSessions()
            statusMessage = sessions.isEmpty
                ? "No live sessions found."
                : "Loaded \(sessions.count) sessions (\(liveCount) live)."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func requestToken(session: LiveSessionRecord, uid: String) async {
        let key = sessionKey(session)
        requestingSessionKey = key
        isRequestingToken = true
        tokenStatusMessage = nil
        errorMessage = nil
        defer {
            requestingSessionKey = nil
            isRequestingToken = false
        }

        do {
            let token = try await repository.getRtcToken(
                channelName: session.agoraChannelName ?? "",
                uid: uid
            )
            tokenPreview = token
            let channel = session.agoraChannelName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let channelLabel = channel.isEmpty ? "selected session" : channel
            tokenStatusMessage = "Token received for \(channelLabel)."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func isRequestingToken(for session: LiveSessionRecord) -> Bool {
        requestingSessionKey == sessionKey(session)
    }

    func clearTokenPreview() {
        tokenPreview = ""
        tokenStatusMessage = nil
    }

    private func sessionKey(_ session: LiveSessionRecord) -> String {
        let id = session.id?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !id.isEmpty { return id }
        let channel = session.agoraChannelName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !channel.isEmpty { return channel }
        let title = session.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "untitled"
        let host = session.hostId?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? session.hostName?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? "unknown-host"
        let created = session.createdAt?.dateValue().timeIntervalSince1970 ?? 0
        return "\(title.lowercased())::\(host.lowercased())::\(Int(created))"
    }

    private func matchesStatus(_ rawStatus: String?, filter: LiveSessionStatusFilter) -> Bool {
        let status = (rawStatus ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch filter {
        case .all:
            return true
        case .live:
            return status.contains("live") || status.contains("active") || status.contains("started")
        case .scheduled:
            return status.contains("scheduled") || status.contains("upcoming") || status.contains("pending")
        case .ended:
            return status.contains("ended") || status.contains("completed") || status.contains("closed")
        }
    }
}
