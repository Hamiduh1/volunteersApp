import SwiftUI

struct OrganizerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            OrganizerHostedEventsView(user: user)
                .tabItem { Label("Hosted", systemImage: "calendar.badge.clock") }

            OrganizerApplicationsReviewView(user: user)
                .tabItem { Label("Applications", systemImage: "person.2") }

            OrganizerWalletView(user: user)
                .tabItem { Label("Wallet", systemImage: "wallet.pass") }

            CommunityHubView(user: user)
                .tabItem { Label("Community", systemImage: "person.3.sequence") }

            AdvancedToolsHomeView(user: user)
                .tabItem { Label("Tools", systemImage: "sparkles") }

            ProfileHomeView(user: user)
            .tabItem { Label("Profile", systemImage: "person") }
        }
    }
}
