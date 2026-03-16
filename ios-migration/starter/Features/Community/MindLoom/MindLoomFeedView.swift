import SwiftUI

struct MindLoomFeedView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = MindLoomFeedViewModel()

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
                Button {
                    Task { await viewModel.createPost(user: user) }
                } label: {
                    if viewModel.isPosting {
                        ProgressView()
                    } else {
                        Text("Post to MindLoom")
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.postText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isPosting)
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

                            Text(post.text ?? "")
                                .font(.body)

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
