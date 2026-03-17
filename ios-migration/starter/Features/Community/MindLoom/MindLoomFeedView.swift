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
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.82))
                }
                .listRowBackground(Color.black)
            }

            if !creatorItems.isEmpty {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(creatorItems) { creator in
                                NavigationLink {
                                    MindLoomProfileView(authorId: creator.id, currentUserId: user.uid, currentUser: user)
                                } label: {
                                    VStack(spacing: 6) {
                                        AsyncImage(url: URL(string: creator.profileUrl ?? "")) { phase in
                                            switch phase {
                                            case .success(let image):
                                                image.resizable().scaledToFill()
                                            default:
                                                Circle()
                                                    .fill(Color.white.opacity(0.15))
                                                    .overlay(
                                                        Image(systemName: "person.fill")
                                                            .foregroundStyle(.white.opacity(0.75))
                                                    )
                                            }
                                        }
                                        .frame(width: 58, height: 58)
                                        .clipShape(Circle())

                                        Text(creator.name.isEmpty ? "User" : creator.name)
                                            .font(.caption2)
                                            .lineLimit(1)
                                            .foregroundStyle(.white)
                                    }
                                    .frame(width: 70)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 2)
                    }
                }
                .listRowBackground(Color.black)
            }

            Section {
                Picker("Feed", selection: $viewModel.selectedScope) {
                    ForEach(MindLoomFeedScope.allCases) { scope in
                        Text(scope.rawValue).tag(scope)
                    }
                }
                .pickerStyle(.segmented)
            }
            .listRowBackground(Color.black)

            Section {
                if viewModel.isLoading && viewModel.posts.isEmpty {
                    ProgressView("Loading feed...")
                        .foregroundStyle(.white)
                } else if viewModel.displayedPosts.isEmpty {
                    Text(
                        viewModel.selectedScope == .following
                            ? "No posts yet from people you follow."
                            : "No posts yet."
                    )
                    .foregroundStyle(.white.opacity(0.75))
                } else {
                    ForEach(viewModel.displayedPosts) { post in
                        postCard(post)
                            .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.black)
                    }
                }
            }
            .listRowBackground(Color.black)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Color.black.ignoresSafeArea())
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
                Label("Create", systemImage: "plus")
                    .fontWeight(.semibold)
            }
            .buttonStyle(.borderedProminent)
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
                    comments: viewModel.commentsByPostId[post.id ?? ""] ?? [],
                    isLoading: viewModel.commentingPostIds.contains(post.id ?? ""),
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
                    isSaving: viewModel.updatingPostIds.contains(post.id ?? ""),
                    onCancel: { activeEditPost = nil },
                    onSave: {
                        let postId = post.id ?? ""
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
                guard let postId = pendingDeletePost?.id, !postId.isEmpty else {
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

    @ViewBuilder
    private func postCard(_ post: MindLoomPostRecord) -> some View {
        let postId = post.id ?? ""
        let isOwner = (post.authorId ?? "") == user.uid
        let isLiked = (post.likes ?? []).contains(user.uid)
        let likeCount = (post.likes ?? []).count
        let commentCount = post.commentsCount ?? 0

        VStack(alignment: .leading, spacing: 10) {
            HStack {
                NavigationLink {
                    MindLoomProfileView(authorId: post.authorId ?? "", currentUserId: user.uid, currentUser: user)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.authorName ?? "User")
                            .font(.headline)
                            .foregroundStyle(.white)
                        Text(post.timestamp?.dateValue().formatted(date: .abbreviated, time: .shortened) ?? "--")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.72))
                    }
                }
                .buttonStyle(.plain)
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
                        Image(systemName: "ellipsis.circle")
                            .foregroundStyle(.white.opacity(0.85))
                    }
                }
            }

            if let text = post.text, !text.isEmpty {
                Text(text)
                    .font(.body)
                    .foregroundStyle(.white)
            }

            postMediaView(post)

            HStack {
                Text("\(likeCount) likes")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
                Spacer()
                Text("\(commentCount) comments")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.72))
            }

            HStack(spacing: 8) {
                Button(isLiked ? "Liked" : "Like") {
                    Task { await viewModel.toggleLike(post: post, uid: user.uid) }
                }
                .buttonStyle(.bordered)
                .tint(.white.opacity(0.9))
                .disabled(postId.isEmpty || viewModel.likingPostIds.contains(postId))

                Button("Comment") {
                    activeCommentsPost = post
                    Task { await viewModel.loadComments(for: post) }
                }
                .buttonStyle(.bordered)

                ShareLink(item: shareText(for: post)) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.bordered)

                if !isOwner {
                    Button("Message") {
                        Task { await viewModel.sendChatInvitation(user: user, targetUserId: post.authorId ?? "") }
                    }
                    .buttonStyle(.bordered)
                }
            }

            if viewModel.likingPostIds.contains(postId) || viewModel.deletingPostIds.contains(postId) {
                ProgressView()
                    .controlSize(.small)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white.opacity(0.08))
        )
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
                    default:
                        Image(systemName: "photo")
                            .foregroundStyle(.white.opacity(0.72))
                    }
                }
                .frame(height: 220)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 12))
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
            }
        }
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
