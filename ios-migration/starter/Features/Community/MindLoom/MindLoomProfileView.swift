import SwiftUI

struct MindLoomProfileView: View {
    let authorId: String
    let currentUserId: String
    @StateObject private var viewModel = MindLoomProfileViewModel()

    private var isSelf: Bool {
        authorId == currentUserId
    }

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if let summary = viewModel.summary {
                Section("Profile") {
                    Text(summary.authorName).font(.title3.bold())
                    Text("@\(summary.authorId)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if !summary.authorEmail.isEmpty, isSelf {
                        Text(summary.authorEmail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        stat("Followers", summary.followersCount)
                        stat("Following", summary.followingCount)
                        stat("Likes", summary.likesCount)
                    }
                    if !isSelf {
                        Button(viewModel.isFollowing ? "Unfollow" : "Follow") {
                            Task { await viewModel.toggleFollow(authorId: authorId, currentUserId: currentUserId) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isFollowUpdating)
                        if viewModel.isFollowUpdating {
                            ProgressView()
                                .controlSize(.small)
                        }
                    }
                }
            }

            Section("Posts") {
                if viewModel.isLoading && viewModel.posts.isEmpty {
                    ProgressView("Loading posts...")
                } else if viewModel.posts.isEmpty {
                    Text("No posts yet.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.posts) { post in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(post.text ?? "")
                                .font(.body)
                            Text("\((post.likes ?? []).count) likes")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Profile")
        .task { await viewModel.refresh(authorId: authorId, currentUserId: currentUserId) }
        .refreshable { await viewModel.refresh(authorId: authorId, currentUserId: currentUserId) }
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
    private func stat(_ label: String, _ value: Int) -> some View {
        VStack {
            Text("\(value)").font(.headline)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
