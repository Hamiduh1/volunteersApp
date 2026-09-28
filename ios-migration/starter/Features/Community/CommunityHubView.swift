import SwiftUI

private enum CommunityFeatureRoute: String, CaseIterable, Identifiable {
    case marketplace
    case dating
    case mindLoom
    case sponsored
    case socialInbox
    case userDirectory

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
        case .marketplace: return Color(red: 0.98, green: 0.55, blue: 0.00)
        case .dating: return Color(red: 0.90, green: 0.22, blue: 0.21)
        case .mindLoom: return Color(red: 1.00, green: 0.70, blue: 0.00)
        case .sponsored: return Color(red: 0.43, green: 0.30, blue: 0.25)
        case .socialInbox: return Color(red: 0.56, green: 0.14, blue: 0.67)
        case .userDirectory: return Color(red: 0.12, green: 0.53, blue: 0.90)
        }
    }
}

struct CommunityHubView: View {
    let user: AppSessionUser

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                dashboardHomeCard
                quickNavigationCard

                Text("Community Features")
                    .font(.headline.weight(.bold))
                    .padding(.top, 4)

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
        .navigationTitle("Community Hub")
        .navigationBarTitleDisplayMode(.inline)
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
    private var dashboardHomeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Dashboard Home")
                .font(.title3.weight(.heavy))
                .foregroundStyle(.white)
            Text("Connect, share, and explore your loop.")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.84))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [Color(red: 0.08, green: 0.12, blue: 0.19), Color(red: 0.14, green: 0.23, blue: 0.33)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        )
    }

    @ViewBuilder
    private var quickNavigationCard: some View {
        HStack(spacing: 8) {
            quickNavButton(.marketplace)
            quickNavButton(.mindLoom)
            quickNavButton(.socialInbox)
        }
    }

    @ViewBuilder
    private func quickNavButton(_ route: CommunityFeatureRoute) -> some View {
        NavigationLink {
            destination(for: route)
        } label: {
            VStack(spacing: 5) {
                Image(systemName: route.iconName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(route.color)
                Text(route.title)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(.secondarySystemBackground))
            )
        }
        .buttonStyle(.plain)
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
