import Foundation
import Combine

@MainActor
final class MindLoomProfileViewModel: ObservableObject {
    @Published private(set) var summary: MindLoomProfileSummary?
    @Published private(set) var posts: [MindLoomPostRecord] = []
    @Published var isFollowing = false
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    func refresh(authorId: String, currentUserId: String) async {
        guard !authorId.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let summaryTask = repository.fetchMindLoomProfile(authorId: authorId)
            async let postsTask = repository.fetchMindLoomPosts(authorId: authorId)
            async let followingTask = repository.isFollowing(currentUid: currentUserId, targetUid: authorId)

            summary = try await summaryTask
            posts = try await postsTask
            isFollowing = try await followingTask
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleFollow(authorId: String, currentUserId: String) async {
        do {
            try await repository.setFollow(currentUid: currentUserId, targetUid: authorId, follow: !isFollowing)
            await refresh(authorId: authorId, currentUserId: currentUserId)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
