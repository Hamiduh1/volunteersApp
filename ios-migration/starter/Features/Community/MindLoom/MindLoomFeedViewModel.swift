import Foundation
import Combine

enum MindLoomFeedScope: String, CaseIterable, Identifiable {
    case forYou = "For You"
    case following = "Following"

    var id: String { rawValue }
}

@MainActor
final class MindLoomFeedViewModel: ObservableObject {
    @Published private(set) var posts: [MindLoomPostRecord] = []
    @Published private(set) var followingIds: Set<String> = []
    @Published private(set) var likingPostIds: Set<String> = []
    @Published private(set) var commentsByPostId: [String: [MindLoomCommentRecord]] = [:]
    @Published private(set) var commentingPostIds: Set<String> = []
    @Published private(set) var deletingPostIds: Set<String> = []
    @Published private(set) var updatingPostIds: Set<String> = []
    @Published var selectedScope: MindLoomFeedScope = .forYou
    @Published var postText = ""
    @Published var isLoading = false
    @Published var isPosting = false
    @Published var statusMessage: String?
    @Published var errorMessage: String?

    private let repository = CommunityRepository()

    var displayedPosts: [MindLoomPostRecord] {
        guard selectedScope == .following else { return posts }
        return posts.filter { post in
            guard let authorId = post.authorId, !authorId.isEmpty else { return false }
            return followingIds.contains(authorId)
        }
    }

    func refresh(for user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        statusMessage = nil
        defer { isLoading = false }

        do {
            async let postsTask = repository.fetchMindLoomPosts()
            async let followingTask = repository.fetchFollowingIds(currentUid: user.uid)
            posts = try await postsTask
            var following = try await followingTask
            following.insert(user.uid)
            followingIds = following
            commentsByPostId = [:]
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
            await refresh(for: user)
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

    func loadComments(for post: MindLoomPostRecord) async {
        guard let postId = post.id, !postId.isEmpty else { return }
        guard !commentingPostIds.contains(postId) else { return }
        commentingPostIds.insert(postId)
        defer { commentingPostIds.remove(postId) }

        do {
            let comments = try await repository.fetchMindLoomComments(post: post)
            commentsByPostId[postId] = comments
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func postComment(user: AppSessionUser, post: MindLoomPostRecord, text: String) async {
        guard let postId = post.id, !postId.isEmpty else { return }
        do {
            try await repository.postMindLoomComment(user: user, post: post, text: text)
            if let index = posts.firstIndex(where: { $0.id == postId }) {
                var updated = posts[index]
                let current = updated.commentsCount ?? 0
                updated.commentsCount = current + 1
                posts[index] = updated
            }
            await loadComments(for: post)
            statusMessage = "Comment posted."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func updatePost(user: AppSessionUser, postId: String, text: String) async {
        let cleanPostId = postId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPostId.isEmpty else { return }
        guard !updatingPostIds.contains(cleanPostId) else { return }
        updatingPostIds.insert(cleanPostId)
        defer { updatingPostIds.remove(cleanPostId) }

        do {
            try await repository.updateMindLoomPost(authorUid: user.uid, postId: cleanPostId, newText: text)
            if let index = posts.firstIndex(where: { $0.id == cleanPostId }) {
                var updated = posts[index]
                updated.text = text.trimmingCharacters(in: .whitespacesAndNewlines)
                posts[index] = updated
            }
            statusMessage = "Post updated successfully."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func deletePost(user: AppSessionUser, postId: String) async {
        let cleanPostId = postId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanPostId.isEmpty else { return }
        guard !deletingPostIds.contains(cleanPostId) else { return }
        deletingPostIds.insert(cleanPostId)
        defer { deletingPostIds.remove(cleanPostId) }

        do {
            try await repository.deleteMindLoomPost(authorUid: user.uid, postId: cleanPostId)
            posts.removeAll { $0.id == cleanPostId }
            commentsByPostId.removeValue(forKey: cleanPostId)
            statusMessage = "Post deleted."
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }

    func sendChatInvitation(user: AppSessionUser, targetUserId: String) async {
        errorMessage = nil
        statusMessage = nil
        let cleanTarget = targetUserId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTarget.isEmpty else {
            errorMessage = "User info missing."
            return
        }
        do {
            let message = try await repository.sendSponsoredChatInvitation(
                sender: user,
                recipientId: cleanTarget,
                contextLabel: "MindLoom",
                duplicateMessage: "Invitation already sent."
            )
            statusMessage = message
        } catch {
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

