import SwiftUI

private enum CommunityFeatureRoute: String, CaseIterable, Identifiable {
    case socialInbox
    case userDirectory
    case mindLoom
    case dating
    case marketplace
    case sponsored

    var id: String { rawValue }

    var title: String {
        switch self {
        case .socialInbox: return "Social Inbox"
        case .userDirectory: return "User Directory"
        case .mindLoom: return "MindLoom"
        case .dating: return "Dating Loop"
        case .marketplace: return "Marketplace"
        case .sponsored: return "Sponsored"
        }
    }

    var subtitle: String {
        switch self {
        case .socialInbox: return "Chats, invitations, and calls"
        case .userDirectory: return "Browse app users and connect"
        case .mindLoom: return "Short videos, docs, and live content"
        case .dating: return "Dating and Blind Date hub"
        case .marketplace: return "Discover and list products"
        case .sponsored: return "Advertisements and garage sales"
        }
    }

    var iconName: String {
        switch self {
        case .socialInbox: return "bubble.left.and.bubble.right.fill"
        case .userDirectory: return "person.2.fill"
        case .mindLoom: return "sparkles.tv"
        case .dating: return "heart.fill"
        case .marketplace: return "storefront.fill"
        case .sponsored: return "megaphone.fill"
        }
    }

    var color: Color {
        switch self {
        case .socialInbox: return Color(red: 0.13, green: 0.59, blue: 0.95)
        case .userDirectory: return Color(red: 0.30, green: 0.69, blue: 0.31)
        case .mindLoom: return Color(red: 1.00, green: 0.76, blue: 0.03)
        case .dating: return Color(red: 0.91, green: 0.12, blue: 0.39)
        case .marketplace: return Color(red: 0.61, green: 0.15, blue: 0.69)
        case .sponsored: return Color(red: 1.00, green: 0.34, blue: 0.13)
        }
    }
}

struct CommunityHubView: View {
    let user: AppSessionUser

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Community Hub")
                    .font(.title2.weight(.bold))
                Text("Connect, share, and discover across the full community loop.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(CommunityFeatureRoute.allCases) { route in
                        NavigationLink {
                            destination(for: route)
                        } label: {
                            featureCard(route)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
    }

    @ViewBuilder
    private func destination(for route: CommunityFeatureRoute) -> some View {
        switch route {
        case .socialInbox:
            ConversationsListView(user: user)
        case .userDirectory:
            UserDirectoryView(user: user)
        case .mindLoom:
            MindLoomFeedView(user: user)
        case .dating:
            DateHubView(user: user)
        case .marketplace:
            MarketplaceView(user: user)
        case .sponsored:
            SponsoredContentView(user: user)
        }
    }

    @ViewBuilder
    private func featureCard(_ route: CommunityFeatureRoute) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: route.iconName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(route.color)
                .frame(width: 38, height: 38)
                .background(Circle().fill(route.color.opacity(0.14)))

            Text(route.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(1)

            Text(route.subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 138, alignment: .topLeading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(route.color.opacity(0.18), lineWidth: 1)
        )
    }
}
