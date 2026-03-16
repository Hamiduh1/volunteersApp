import Foundation
import Combine

@MainActor
final class LiveSessionsViewModel: ObservableObject {
    @Published private(set) var sessions: [LiveSessionRecord] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var tokenPreview = ""

    private let repository = LiveRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            sessions = try await repository.fetchLiveSessions()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func requestToken(session: LiveSessionRecord, uid: String) async {
        let channel = session.agoraChannelName ?? ""
        guard !channel.isEmpty else {
            errorMessage = "Session channel is missing."
            return
        }
        do {
            let token = try await repository.getRtcToken(channelName: channel, uid: uid)
            tokenPreview = token.isEmpty ? "Token response was empty." : token
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
