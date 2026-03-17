import Foundation
import Combine

@MainActor
final class MindLoomFeedViewModel: ObservableObject {
    @Published private(set) var posts: [MindLoomPostRecord] = []
    @Published private(set) var likingPostIds: Set<String> = []
    @Published var postText = ""
    @Published var isLoading = false
    @Published var isPosting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    func refresh() async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            posts = try await repository.fetchMindLoomPosts()
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    @discardableResult
    func createPost(user: AppSessionUser, attachment: CommunityAttachmentDraft?) async -> Bool {
        let text = postText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty || attachment != nil else { return false }

        isPosting = true
        errorMessage = nil
        statusMessage = nil
        defer { isPosting = false }

        do {
            try await repository.createMindLoomPost(user: user, text: text, attachment: attachment)
            postText = ""
            await refresh()
            statusMessage = "Post shared to MindLoom."
            return true
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
            return false
        }
    }

    func toggleLike(post: MindLoomPostRecord, uid: String) async {
        guard let postId = post.id, !postId.isEmpty else { return }
        guard !likingPostIds.contains(postId) else { return }
        likingPostIds.insert(postId)
        defer { likingPostIds.remove(postId) }

        let currentlyLiked = (post.likes ?? []).contains(uid)
        updateLocalLikes(postId: postId, uid: uid, like: !currentlyLiked)

        do {
            try await repository.toggleMindLoomLike(post: post, uid: uid)
            statusMessage = currentlyLiked ? "Like removed." : "Post liked."
        } catch {
            updateLocalLikes(postId: postId, uid: uid, like: currentlyLiked)
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    private func updateLocalLikes(postId: String, uid: String, like: Bool) {
        guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
        var updated = posts[index]
        var likes = updated.likes ?? []
        if like {
            if !likes.contains(uid) { likes.append(uid) }
        } else {
            likes.removeAll { $0 == uid }
        }
        updated.likes = likes
        posts[index] = updated
    }
}

