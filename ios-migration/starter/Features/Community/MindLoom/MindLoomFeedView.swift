import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import UIKit

struct MindLoomFeedView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = MindLoomFeedViewModel()

    @State private var selectedImageItem: PhotosPickerItem?
    @State private var selectedVideoItem: PhotosPickerItem?
    @State private var showDocumentPicker = false
    @State private var selectedAttachment: CommunityAttachmentDraft?

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Create Post") {
                TextField("Share something...", text: $viewModel.postText, axis: .vertical)
                    .lineLimit(1...4)

                attachmentToolbar

                if let attachment = selectedAttachment {
                    attachmentPreview(attachment)
                }

                Button {
                    Task {
                        let created = await viewModel.createPost(user: user, attachment: selectedAttachment)
                        if created {
                            selectedAttachment = nil
                            selectedImageItem = nil
                            selectedVideoItem = nil
                        }
                    }
                } label: {
                    if viewModel.isPosting {
                        ProgressView()
                    } else {
                        Text("Post to MindLoom")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(
                    (viewModel.postText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && selectedAttachment == nil)
                    || viewModel.isPosting
                )
            }

            Section("Feed") {
                if viewModel.isLoading && viewModel.posts.isEmpty {
                    ProgressView("Loading feed...")
                } else if viewModel.posts.isEmpty {
                    Text("No posts yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.posts) { post in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                NavigationLink {
                                    MindLoomProfileView(authorId: post.authorId ?? "", currentUserId: user.uid)
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(post.authorName ?? "User")
                                            .font(.headline)
                                        Text(post.timestamp?.dateValue().formatted(date: .abbreviated, time: .shortened) ?? "--")
                                            .font(.caption2)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                .buttonStyle(.plain)
                                Spacer()
                            }

                            if let text = post.text, !text.isEmpty {
                                Text(text)
                                    .font(.body)
                            }

                            postMediaView(post)

                            HStack {
                                let likesCount = (post.likes ?? []).count
                                Text("\(likesCount) likes")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Spacer()
                                let postId = post.id ?? ""
                                let isLiked = (post.likes ?? []).contains(user.uid)
                                Button(isLiked ? "Liked" : "Like") {
                                    Task { await viewModel.toggleLike(post: post, uid: user.uid) }
                                }
                                .buttonStyle(.bordered)
                                .disabled(postId.isEmpty || viewModel.likingPostIds.contains(postId))
                                if viewModel.likingPostIds.contains(postId) {
                                    ProgressView()
                                        .controlSize(.small)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("MindLoom")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
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
    private var attachmentToolbar: some View {
        HStack(spacing: 8) {
            PhotosPicker(
                selection: $selectedImageItem,
                matching: .images
            ) {
                Label("Add Image", systemImage: "photo")
            }
            .buttonStyle(.bordered)

            PhotosPicker(
                selection: $selectedVideoItem,
                matching: .videos
            ) {
                Label("Add Video", systemImage: "video")
            }
            .buttonStyle(.bordered)

            Button {
                showDocumentPicker = true
            } label: {
                Label("Add Doc", systemImage: "doc")
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
                    .frame(height: 180)
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
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(height: 220)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else if type == "VIDEO" {
                Link(destination: url) {
                    Label("Open Video", systemImage: "video")
                }
                .font(.subheadline)
            } else if type == "DOCUMENT" {
                Link(destination: url) {
                    Label("Open Document", systemImage: "doc")
                }
                .font(.subheadline)
            }
        }
    }
}
