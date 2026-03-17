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
    @Published var hostSessionTitle = ""
    @Published var isLoading = false
    @Published var isRequestingToken = false
    @Published var isHostOperationInProgress = false
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

    func canHostLive(user: AppSessionUser) -> Bool {
        switch user.role {
        case .organizer, .owner, .admin:
            return true
        default:
            return false
        }
    }

    func activeHostedSession(for uid: String) -> LiveSessionRecord? {
        sessions.first { session in
            let hostId = (session.hostId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return hostId == uid && matchesStatus(session.status, filter: .live)
        }
    }

    func refresh(user: AppSessionUser? = nil) async {
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
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func startLiveSession(user: AppSessionUser) async {
        guard canHostLive(user: user) else {
            errorMessage = "Only organizers/admin can start live sessions."
            return
        }
        guard activeHostedSession(for: user.uid) == nil else {
            errorMessage = "You already have a live session running."
            return
        }

        isHostOperationInProgress = true
        errorMessage = nil
        defer { isHostOperationInProgress = false }

        do {
            let displayName = user.email ?? "Organizer"
            let created = try await repository.startLiveSession(
                hostUid: user.uid,
                hostName: displayName,
                title: hostSessionTitle
            )
            hostSessionTitle = ""
            statusMessage = "Live session started: \(created.title ?? "Untitled Session")."
            await refresh(user: user)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func endLiveSession(session: LiveSessionRecord, user: AppSessionUser) async {
        guard canHostLive(user: user) else {
            errorMessage = "Only organizers/admin can end live sessions."
            return
        }
        guard let sessionId = session.id, !sessionId.isEmpty else {
            errorMessage = "Session id is missing."
            return
        }

        isHostOperationInProgress = true
        errorMessage = nil
        defer { isHostOperationInProgress = false }

        do {
            try await repository.endLiveSession(sessionId: sessionId, hostUid: user.uid)
            statusMessage = "Live session ended."
            await refresh(user: user)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
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
            errorMessage = AppErrorMapper.message(from: error)
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

