import SwiftUI

struct MindLoomProfileView: View {
    let authorId: String
    let currentUserId: String
    let currentUser: AppSessionUser? = nil
    @StateObject private var viewModel = MindLoomProfileViewModel()

    private var isSelf: Bool {
        authorId == currentUserId
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    profileBanner(status)
                }

                if let summary = viewModel.summary {
                    profileHeader(summary)
                    postSection(summary: summary)
                } else if viewModel.isLoading {
                    profileCard {
                        ProgressView("Loading profile...")
                            .tint(.white)
                    }
                } else {
                    profileCard {
                        Text("Profile unavailable.")
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.72))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .scrollIndicators(.hidden)
        .background(backgroundGradient.ignoresSafeArea())
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
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

    private var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color(red: 0.03, green: 0.05, blue: 0.08),
                Color(red: 0.07, green: 0.06, blue: 0.12),
                Color.black
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    @ViewBuilder
    private func profileBanner(_ status: String) -> some View {
        profileCard(fill: Color.green.opacity(0.14), stroke: Color.green.opacity(0.24)) {
            Label(status, systemImage: "checkmark.circle.fill")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.white.opacity(0.92))
        }
    }

    @ViewBuilder
    private func profileHeader(_ summary: MindLoomProfileSummary) -> some View {
        profileCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 12) {
                    profileAvatar(url: summary.authorProfileUrl, size: 74)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(summary.authorName)
                            .font(.system(.title3, design: .rounded, weight: .bold))
                            .foregroundStyle(.white)
                        Text("@\(summary.authorId)")
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.62))
                        if !summary.authorEmail.isEmpty, isSelf {
                            Text(summary.authorEmail)
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.68))
                        }
                    }

                    Spacer(minLength: 0)
                }

                HStack(spacing: 8) {
                    statCard("Followers", summary.followersCount, tint: .pink)
                    statCard("Following", summary.followingCount, tint: .orange)
                    statCard("Likes", summary.likesCount, tint: .blue)
                }

                if !isSelf {
                    HStack(spacing: 8) {
                        Button(viewModel.isFollowing ? "Unfollow" : "Follow") {
                            Task { await viewModel.toggleFollow(authorId: authorId, currentUserId: currentUserId) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isFollowUpdating)

                        if let currentUser {
                            Button("Message") {
                                Task { await viewModel.sendChatInvitation(currentUser: currentUser, authorId: authorId) }
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                HStack(spacing: 8) {
                    ShareLink(item: shareProfileText(summary: summary)) {
                        profileActionChip(title: "Share Profile", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(.plain)

                    if viewModel.isFollowUpdating {
                        ProgressView()
                            .controlSize(.small)
                            .tint(.white)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func postSection(summary: MindLoomProfileSummary) -> some View {
        profileCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Posts")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Spacer()
                    Text("\(viewModel.posts.count)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white.opacity(0.68))
                }

                if viewModel.isLoading && viewModel.posts.isEmpty {
                    ProgressView("Loading posts...")
                        .tint(.white)
                } else if viewModel.posts.isEmpty {
                    Text("No posts yet.")
                        .font(.footnote)
                        .foregroundStyle(.white.opacity(0.72))
                } else {
                    ForEach(Array(viewModel.posts.enumerated()), id: \.element.id) { index, post in
                        profilePostCard(post, summary: summary)
                        if index < viewModel.posts.count - 1 {
                            Divider()
                                .overlay(Color.white.opacity(0.08))
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func profilePostCard(_ post: MindLoomPostRecord, summary: MindLoomProfileSummary) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                profileAvatar(url: summary.authorProfileUrl, size: 38)

                VStack(alignment: .leading, spacing: 2) {
                    Text(summary.authorName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                    if let ts = post.timestamp?.dateValue() {
                        Text(ts.formatted(date: .abbreviated, time: .shortened))
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.62))
                    }
                }

                Spacer()

                Text("\((post.likes ?? []).count) likes")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.66))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(Capsule().fill(Color.white.opacity(0.06)))
            }

            if let text = post.text, !text.isEmpty {
                Text(text)
                    .font(.callout)
                    .foregroundStyle(.white.opacity(0.92))
                    .fixedSize(horizontal: false, vertical: true)
            }

            postMediaView(post)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func profileCard<Content: View>(
        fill: Color = Color.white.opacity(0.06),
        stroke: Color = Color.white.opacity(0.08),
        @ViewBuilder content: () -> Content
    ) -> some View {
        MindLoomProfilePanel(fill: fill, stroke: stroke, content: content)
    }

    private func shareProfileText(summary: MindLoomProfileSummary) -> String {
        "Check out \(summary.authorName) on MindLoom."
    }

    @ViewBuilder
    private func statCard(_ label: String, _ value: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text("\(value)")
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
                .stroke(tint.opacity(0.24), lineWidth: 1)
        )
    }

    @ViewBuilder
    private func profileActionChip(title: String, systemImage: String) -> some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                Capsule()
                    .fill(Color.white.opacity(0.08))
            )
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
                            .foregroundStyle(.white.opacity(0.72))
                    )
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.white.opacity(0.12), lineWidth: 1))
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
                .frame(height: 190)
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
}

private struct MindLoomProfilePanel<Content: View>: View {
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
