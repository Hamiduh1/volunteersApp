import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

private struct MindLoomCreatorItem: Identifiable {
    let id: String
    let name: String
    let profileUrl: String?
}

struct MindLoomFeedView: View {
    private enum SectionAnchor {
        static let dashboard = "mindloom_dashboard"
        static let creators = "mindloom_creators"
        static let scope = "mindloom_scope"
        static let feed = "mindloom_feed"
    }

    let user: AppSessionUser
    @StateObject private var viewModel = MindLoomFeedViewModel()
    @State private var showComposer = false
    @State private var activeCommentsPost: MindLoomPostRecord?
    @State private var activeEditPost: MindLoomPostRecord?
    @State private var editPostText = ""
    @State private var pendingDeletePost: MindLoomPostRecord?

    private var creatorItems: [MindLoomCreatorItem] {
        var seen = Set<String>()
        var output: [MindLoomCreatorItem] = []
        for post in viewModel.posts {
            let authorId = (post.authorId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !authorId.isEmpty, !seen.contains(authorId) else { continue }
            seen.insert(authorId)
            output.append(
                MindLoomCreatorItem(
                    id: authorId,
                    name: (post.authorName ?? "User").trimmingCharacters(in: .whitespacesAndNewlines),
                    profileUrl: post.authorProfileUrl
                )
            )
        }
        return output
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 10) {
                    dashboardHomeSection(proxy: proxy)
                        .id(SectionAnchor.dashboard)

                    if let status = viewModel.statusMessage, !status.isEmpty {
                        statusBanner(status)
                    }

                    if !creatorItems.isEmpty {
                        creatorStripCard()
                            .id(SectionAnchor.creators)
                    }

                    scopePickerCard()
                        .id(SectionAnchor.scope)

                    feedSection()
                        .id(SectionAnchor.feed)
                }
                .padding(.horizontal, 14)
                .padding(.top, 12)
                .padding(.bottom, 92)
            }
            .scrollIndicators(.hidden)
            .background(backgroundGradient.ignoresSafeArea())
            .navigationTitle("MindLoom")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        LiveSessionsView(user: user)
                    } label: {
                        Image(systemName: "dot.radiowaves.left.and.right")
                    }
                    .tint(.white)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                Button {
                    showComposer = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                        Text("Create")
                            .fontWeight(.semibold)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(
                        Capsule()
                            .fill(
                                LinearGradient(
                                    colors: [Color.pink.opacity(0.92), Color.orange.opacity(0.88)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                    )
                    .foregroundStyle(.white)
                    .shadow(color: Color.black.opacity(0.28), radius: 16, y: 10)
                }
                .padding(.trailing, 16)
                .padding(.bottom, 22)
            }
            .sheet(isPresented: $showComposer) {
                NavigationStack {
                    MindLoomComposerSheet(
                        user: user,
                        viewModel: viewModel
                    ) {
                        showComposer = false
                    }
                }
            }
            .sheet(item: $activeCommentsPost) { post in
                NavigationStack {
                    MindLoomCommentsSheet(
                        post: post,
                        comments: viewModel.commentsByPostId[post.mindLoomNormalizedPostId] ?? [],
                        isLoading: viewModel.commentingPostIds.contains(post.mindLoomNormalizedPostId)
                            || viewModel.postingCommentKeys.contains(post.mindLoomCommentInflightKey),
                        onReload: {
                            Task { await viewModel.loadComments(for: post) }
                        },
                        onPostComment: { text in
                            Task { await viewModel.postComment(user: user, post: post, text: text) }
                        }
                    )
                }
            }
            .sheet(item: $activeEditPost) { post in
                NavigationStack {
                    MindLoomEditPostSheet(
                        text: $editPostText,
                        isSaving: viewModel.updatingPostIds.contains(post.mindLoomNormalizedPostId),
                        onCancel: { activeEditPost = nil },
                        onSave: {
                            let postId = post.mindLoomNormalizedPostId
                            guard !postId.isEmpty else { return }
                            Task {
                                await viewModel.updatePost(user: user, postId: postId, text: editPostText)
                                activeEditPost = nil
                            }
                        }
                    )
                }
            }
            .confirmationDialog("Delete Post", isPresented: Binding(
                get: { pendingDeletePost != nil },
                set: { if !$0 { pendingDeletePost = nil } }
            ), titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    let postId = pendingDeletePost?.mindLoomNormalizedPostId ?? ""
                    guard !postId.isEmpty else {
                        pendingDeletePost = nil
                        return
                    }
                    Task {
                        await viewModel.deletePost(user: user, postId: postId)
                        pendingDeletePost = nil
                    }
                }
                Button("Cancel", role: .cancel) { pendingDeletePost = nil }
            }
            .task { await viewModel.refresh(for: user) }
            .refreshable { await viewModel.refresh(for: user) }
            .alert("Error", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "Unknown error")
            }
        }
    }

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 0.04, green: 0.05, blue: 0.09),
                Color(red: 0.08, green: 0.06, blue: 0.12),
                Color.black
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    @ViewBuilder
    private func dashboardHomeSection(proxy: ScrollViewProxy) -> some View {
        MindLoomPanel {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("MindLoom")
                        .font(.system(.title3, design: .rounded, weight: .bold))
                        .foregroundStyle(.white)
                    Text("Creator-first social space with faster navigation and a denser Android-style layout.")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.72))
                }

                HStack(spacing: 8) {
                    dashboardMetric(value: "\(viewModel.posts.count)", label: "Posts", tint: .pink)
                    dashboardMetric(value: "\(viewModel.displayedPosts.count)", label: "Visible", tint: .orange)
                    dashboardMetric(value: "\(creatorItems.count)", label: "Creators", tint: .blue)
                }

                HStack(spacing: 8) {
                    dashboardTile(title: "Create", icon: "plus.circle.fill", tint: .pink) {
                        showComposer = true
                    }
                    dashboardTile(title: "Refresh", icon: "arrow.clockwise.circle.fill", tint: .orange) {
                        Task { await viewModel.refresh(for: user) }
                    }
                    NavigationLink {
                        LiveSessionsView(user: user)
                    } label: {
                        dashboardTileLabel(title: "Go Live", icon: "dot.radiowaves.left.and.right", tint: .blue)
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 8) {
                    dashboardQuickButton("Creators") {
                        withAnimation { proxy.scrollTo(SectionAnchor.creators, anchor: .top) }
                    }
                    dashboardQuickButton("Feed Type") {
                        withAnimation { proxy.scrollTo(SectionAnchor.scope, anchor: .top) }
                    }
                    dashboardQuickButton("Posts") {
                        withAnimation { proxy.scrollTo(SectionAnchor.feed, anchor: .top) }
                    }
                }

                NavigationLink {
                    CommunityHubView(user: user)
                } label: {
                    Label("Community Dashboard", systemImage: "square.grid.2x2.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            Capsule()
                                .fill(Color.white.opacity(0.10))
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func statusBanner(_ status: String) -> some View {
        MindLoomPanel(fill: Color.green.opacity(0.14), stroke: Color.green.opacity(0.25)) {
            Label(status, systemImage: "checkmark.circle.fill")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.white.opacity(0.92))
        }
    }

    @ViewBuilder
    private func creatorStripCard() -> some View {
        MindLoomPanel {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Featured Creators")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Spacer()
                    Text("\(creatorItems.count)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.7))
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(creatorItems) { creator in
                            NavigationLink {
                                MindLoomProfileView(authorId: creator.id, currentUserId: user.uid, currentUser: user)
                            } label: {
                                VStack(spacing: 7) {
                                    profileAvatar(url: creator.profileUrl, size: 56)

                                    Text(creator.name.isEmpty ? "User" : creator.name)
                                        .font(.caption2.weight(.semibold))
                                        .lineLimit(1)
                                        .foregroundStyle(.white)
                                }
                                .frame(width: 72)
                                .padding(.vertical, 4)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func scopePickerCard() -> some View {
        MindLoomPanel {
            VStack(alignment: .leading, spacing: 8) {
                Text("Feed Focus")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Picker("Feed", selection: $viewModel.selectedScope) {
                    ForEach(MindLoomFeedScope.allCases) { scope in
                        Text(scope.rawValue).tag(scope)
                    }
                }
                .pickerStyle(.segmented)
            }
        }
    }

    @ViewBuilder
    private func feedSection() -> some View {
        if viewModel.isLoading && viewModel.posts.isEmpty {
            MindLoomPanel {
                ProgressView("Loading feed...")
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else if viewModel.displayedPosts.isEmpty {
            MindLoomPanel {
                Text(
                    viewModel.selectedScope == .following
                        ? "No posts yet from people you follow."
                        : "No posts yet."
                )
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.72))
            }
        } else {
            ForEach(viewModel.displayedPosts) { post in
                postCard(post)
            }
        }
    }

    @ViewBuilder
    private func dashboardMetric(value: String, label: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value)
                .font(.system(.headline, design: .rounded, weight: .bold))
                .foregroundStyle(.white)
            Text(label.uppercased())
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .foregroundStyle(.white.opacity(0.66))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 9)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(tint.opacity(0.18))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(tint.opacity(0.22), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func dashboardTile(title: String, icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            dashboardTileLabel(title: title, icon: icon, tint: tint)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func dashboardTileLabel(title: String, icon: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(tint)
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.white.opacity(0.07))
        )
    }

    @ViewBuilder
    private func dashboardQuickButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    Capsule()
                        .fill(Color.white.opacity(0.08))
                )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func postCard(_ post: MindLoomPostRecord) -> some View {
        let postId = post.mindLoomNormalizedPostId
        let isOwner = (post.authorId ?? "") == user.uid
        let isLiked = (post.likes ?? []).contains(user.uid)
        let likeCount = (post.likes ?? []).count
        let commentCount = post.commentsCount ?? 0

        MindLoomPanel(fill: Color.white.opacity(0.08), stroke: Color.white.opacity(0.08)) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 10) {
                    NavigationLink {
                        MindLoomProfileView(authorId: post.authorId ?? "", currentUserId: user.uid, currentUser: user)
                    } label: {
                        profileAvatar(url: post.authorProfileUrl, size: 42)
                    }
                    .buttonStyle(.plain)

                    VStack(alignment: .leading, spacing: 2) {
                        NavigationLink {
                            MindLoomProfileView(authorId: post.authorId ?? "", currentUserId: user.uid, currentUser: user)
                        } label: {
                            Text(post.authorName ?? "User")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.white)
                        }
                        .buttonStyle(.plain)

                        Text(post.timestamp?.dateValue().formatted(date: .abbreviated, time: .shortened) ?? "--")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.62))
                    }

                    Spacer()

                    if isOwner {
                        Menu {
                            Button("Edit") {
                                editPostText = post.text ?? ""
                                activeEditPost = post
                            }
                            Button("Delete", role: .destructive) {
                                pendingDeletePost = post
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.headline)
                                .foregroundStyle(.white.opacity(0.76))
                                .frame(width: 28, height: 28)
                                .background(Circle().fill(Color.white.opacity(0.06)))
                        }
                    }
                }

                if let text = post.text, !text.isEmpty {
                    Text(text)
                        .font(.callout)
                        .foregroundStyle(.white.opacity(0.94))
                        .fixedSize(horizontal: false, vertical: true)
                }

                postMediaView(post)

                HStack(spacing: 8) {
                    metricPill(title: "\(likeCount) likes", icon: "heart.fill")
                    metricPill(title: "\(commentCount) comments", icon: "bubble.left.fill")
                }

                HStack(spacing: 8) {
                    actionButton(
                        title: isLiked ? "Liked" : "Like",
                        systemImage: isLiked ? "heart.fill" : "heart",
                        tint: isLiked ? .pink : .white.opacity(0.86)
                    ) {
                        Task { await viewModel.toggleLike(post: post, uid: user.uid) }
                    }
                    .disabled(postId.isEmpty || viewModel.likingPostIds.contains(postId))

                    actionButton(
                        title: "Comment",
                        systemImage: "bubble.left",
                        tint: .white.opacity(0.86)
                    ) {
                        activeCommentsPost = post
                        Task { await viewModel.loadComments(for: post) }
                    }

                    ShareLink(item: shareText(for: post)) {
                        actionButtonLabel(title: "Share", systemImage: "square.and.arrow.up", tint: .white.opacity(0.86))
                    }
                    .buttonStyle(.plain)

                    if !isOwner {
                        actionButton(
                            title: "Message",
                            systemImage: "paperplane",
                            tint: .white.opacity(0.86)
                        ) {
                            Task { await viewModel.sendChatInvitation(user: user, targetUserId: post.authorId ?? "") }
                        }
                    }
                }

                if viewModel.likingPostIds.contains(postId) || viewModel.deletingPostIds.contains(postId) {
                    ProgressView()
                        .controlSize(.small)
                        .tint(.white)
                }
            }
        }
    }

    @ViewBuilder
    private func postMediaView(_ post: MindLoomPostRecord) -> some View {
        let type = (post.mediaType ?? "").uppercased()
        let urlText = (post.mediaUrl ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !urlText.isEmpty, let url = URL(string: urlText) {
            if type == "IMAGE" {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .empty:
                        ProgressView()
                            .tint(.white)
                    default:
                        Image(systemName: "photo")
                            .foregroundStyle(.white.opacity(0.72))
                    }
                }
                .frame(height: 214)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else if type == "VIDEO" {
                Link(destination: url) {
                    Label("Open Video", systemImage: "video")
                        .foregroundStyle(.white)
                }
                .font(.subheadline)
            } else if type == "DOCUMENT" {
                Link(destination: url) {
                    Label("Open Document", systemImage: "doc")
                        .foregroundStyle(.white)
                }
                .font(.subheadline)
            } else if type == "LIVE_SESSION" {
                Link(destination: url) {
                    Label("Open Live Stream", systemImage: "dot.radiowaves.left.and.right")
                        .foregroundStyle(.white)
                }
                .font(.subheadline)
            } else if type == "LIVE_REPLAY" {
                MindLoomReplayAccessButton(
                    sessionId: post.resolvedLiveSessionId,
                    shareAccessToken: post.replayShareAccessToken,
                    fallbackURL: url
                )
            }
        }
    }

    @ViewBuilder
    private func profileAvatar(url: String?, size: CGFloat) -> some View {
        AsyncImage(url: URL(string: url ?? "")) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                Circle()
                    .fill(Color.white.opacity(0.12))
                    .overlay(
                        Image(systemName: "person.fill")
                            .foregroundStyle(.white.opacity(0.7))
                    )
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
    }

    @ViewBuilder
    private func metricPill(title: String, icon: String) -> some View {
        Label(title, systemImage: icon)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.white.opacity(0.72))
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(Color.white.opacity(0.06))
            )
    }

    @ViewBuilder
    private func actionButton(title: String, systemImage: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            actionButtonLabel(title: title, systemImage: systemImage, tint: tint)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func actionButtonLabel(title: String, systemImage: String, tint: Color) -> some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(
                Capsule()
                    .fill(Color.white.opacity(0.06))
            )
    }

    private func shareText(for post: MindLoomPostRecord) -> String {
        let author = (post.authorName ?? "User").trimmingCharacters(in: .whitespacesAndNewlines)
        let text = (post.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty {
            return "Check out \(author) on MindLoom."
        }
        return "\(author): \(text)"
    }
}

private struct MindLoomPanel<Content: View>: View {
    let fill: Color
    let stroke: Color
    @ViewBuilder var content: Content

    init(
        fill: Color = Color.white.opacity(0.06),
        stroke: Color = Color.white.opacity(0.08),
        @ViewBuilder content: () -> Content
    ) {
        self.fill = fill
        self.stroke = stroke
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(fill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(stroke, lineWidth: 1)
        )
    }
}

private struct MindLoomCommentsSheet: View {
    let post: MindLoomPostRecord
    let comments: [MindLoomCommentRecord]
    let isLoading: Bool
    let onReload: () -> Void
    let onPostComment: (String) -> Void
    @State private var commentText = ""

    var body: some View {
        List {
            Section("Post") {
                if let text = post.text, !text.isEmpty {
                    Text(text)
                } else {
                    Text("No post text.")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Add Comment") {
                TextField("Add a comment...", text: $commentText, axis: .vertical)
                    .lineLimit(2...4)
                Button("Post Comment") {
                    let text = commentText.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !text.isEmpty else { return }
                    onPostComment(text)
                    commentText = ""
                }
                .disabled(commentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            Section("Comments") {
                if isLoading && comments.isEmpty {
                    ProgressView("Loading comments...")
                } else if comments.isEmpty {
                    Text("No comments yet. Be the first!")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(comments) { comment in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(comment.authorName ?? "User")
                                .font(.subheadline.weight(.semibold))
                            Text(comment.text ?? "")
                                .font(.body)
                            if let ts = comment.timestamp?.dateValue() {
                                Text(ts.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
        }
        .navigationTitle("Comments")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Reload") { onReload() }
            }
        }
    }
}

private struct MindLoomEditPostSheet: View {
    @Binding var text: String
    let isSaving: Bool
    let onCancel: () -> Void
    let onSave: () -> Void

    var body: some View {
        Form {
            Section("Edit Post") {
                TextField("Update text content", text: $text, axis: .vertical)
                    .lineLimit(6...10)
            }
        }
        .navigationTitle("Edit Post")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Cancel") { onCancel() }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Save") { onSave() }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSaving)
            }
        }
    }
}

private struct MindLoomComposerSheet: View {
    let user: AppSessionUser
    @ObservedObject var viewModel: MindLoomFeedViewModel
    let onClose: () -> Void

    @State private var selectedImageItem: PhotosPickerItem?
    @State private var selectedVideoItem: PhotosPickerItem?
    @State private var showDocumentPicker = false
    @State private var selectedAttachment: CommunityAttachmentDraft?

    private var canPost: Bool {
        (!viewModel.postText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || selectedAttachment != nil)
        && !viewModel.isPosting
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                OutlinedFieldLike(
                    text: $viewModel.postText,
                    title: "What's on your mind?"
                )

                if let selectedAttachment {
                    attachmentPreview(selectedAttachment)
                }

                mediaButtons

                Button {
                    Task {
                        let created = await viewModel.createPost(user: user, attachment: selectedAttachment)
                        if created {
                            selectedAttachment = nil
                            selectedImageItem = nil
                            selectedVideoItem = nil
                            onClose()
                        }
                    }
                } label: {
                    if viewModel.isPosting {
                        ProgressView()
                    } else {
                        Text("Post")
                            .fontWeight(.bold)
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity, alignment: .center)
                .disabled(!canPost)
            }
            .padding(20)
        }
        .navigationTitle("Create Post")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onClose() }
            }
        }
        .onChange(of: selectedImageItem) { newValue in
            guard let newValue else { return }
            Task {
                if let data = try? await newValue.loadTransferable(type: Data.self) {
                    selectedAttachment = CommunityAttachmentDraft(
                        type: .image,
                        data: data,
                        fileName: "mindloom_image_\(Int(Date().timeIntervalSince1970)).jpg",
                        contentType: "image/jpeg"
                    )
                    selectedVideoItem = nil
                }
            }
        }
        .onChange(of: selectedVideoItem) { newValue in
            guard let newValue else { return }
            Task {
                if let data = try? await newValue.loadTransferable(type: Data.self) {
                    selectedAttachment = CommunityAttachmentDraft(
                        type: .video,
                        data: data,
                        fileName: "mindloom_video_\(Int(Date().timeIntervalSince1970)).mp4",
                        contentType: "video/mp4"
                    )
                    selectedImageItem = nil
                }
            }
        }
        .fileImporter(
            isPresented: $showDocumentPicker,
            allowedContentTypes: [.pdf, .item],
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first else { return }
            let granted = url.startAccessingSecurityScopedResource()
            defer {
                if granted { url.stopAccessingSecurityScopedResource() }
            }

            do {
                let data = try Data(contentsOf: url)
                selectedAttachment = CommunityAttachmentDraft(
                    type: .document,
                    data: data,
                    fileName: url.lastPathComponent,
                    contentType: "application/pdf"
                )
                selectedImageItem = nil
                selectedVideoItem = nil
            } catch {
                viewModel.errorMessage = AppErrorMapper.message(from: error)
            }
        }
    }

    @ViewBuilder
    private var mediaButtons: some View {
        HStack(spacing: 10) {
            PhotosPicker(selection: $selectedImageItem, matching: .images) {
                Label("Image", systemImage: "photo")
            }
            .buttonStyle(.bordered)

            PhotosPicker(selection: $selectedVideoItem, matching: .videos) {
                Label("Video", systemImage: "video")
            }
            .buttonStyle(.bordered)

            Button {
                showDocumentPicker = true
            } label: {
                Label("Document", systemImage: "doc")
            }
            .buttonStyle(.bordered)

            if selectedAttachment != nil {
                Button("Clear", role: .destructive) {
                    selectedAttachment = nil
                    selectedImageItem = nil
                    selectedVideoItem = nil
                }
                .buttonStyle(.bordered)
            }
        }
    }

    @ViewBuilder
    private func attachmentPreview(_ attachment: CommunityAttachmentDraft) -> some View {
        switch attachment.type {
        case .image:
            if let image = UIImage(data: attachment.data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        case .video:
            Label(attachment.fileName, systemImage: "video.fill")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        case .document:
            Label(attachment.fileName, systemImage: "doc.fill")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

private struct OutlinedFieldLike: View {
    @Binding var text: String
    let title: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.semibold))
            TextField("", text: $text, axis: .vertical)
                .lineLimit(6...10)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                )
        }
    }
}

struct MindLoomReplayAccessButton: View {
    let sessionId: String?
    let shareAccessToken: String?
    let fallbackURL: URL

    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                Task { await openReplay() }
            } label: {
                Label(
                    isLoading ? "Loading Replay..." : "Watch Replay",
                    systemImage: "play.rectangle.fill"
                )
                .foregroundStyle(.white)
            }
            .buttonStyle(.bordered)
            .disabled(isLoading)

            if let errorMessage, !errorMessage.isEmpty {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red.opacity(0.9))
            }
        }
    }

    @MainActor
    private func openReplay() async {
        let cleanSessionId = sessionId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !cleanSessionId.isEmpty else {
            UIApplication.shared.open(fallbackURL)
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let playbackURL = try await LiveRepository().createLiveReplayAccessLink(
                sessionId: cleanSessionId,
                shareAccessToken: shareAccessToken
            )
            UIApplication.shared.open(playbackURL)
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}
