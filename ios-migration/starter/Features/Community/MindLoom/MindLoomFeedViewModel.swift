import Foundation
import Combine

@MainActor
final class MindLoomFeedViewModel: ObservableObject {
    @Published private(set) var posts: [MindLoomPostRecord] = []
    @Published var postText = ""
    @Published var isLoading = false
    @Published var isPosting = false
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            posts = try await repository.fetchMindLoomPosts()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func createPost(user: AppSessionUser) async {
        let text = postText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        isPosting = true
        errorMessage = nil
        defer { isPosting = false }

        do {
            try await repository.createMindLoomTextPost(user: user, text: text)
            postText = ""
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func toggleLike(post: MindLoomPostRecord, uid: String) async {
        do {
            try await repository.toggleMindLoomLike(post: post, uid: uid)
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
