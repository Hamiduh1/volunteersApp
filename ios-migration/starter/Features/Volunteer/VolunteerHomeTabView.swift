import SwiftUI

struct VolunteerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            EventsListView(user: user)
                .tabItem {
                    Label("Events", systemImage: "calendar")
                }

            JobsListView(user: user)
                .tabItem {
                    Label("Jobs", systemImage: "briefcase")
                }

            MyActivityView(user: user)
                .tabItem {
                    Label("Activity", systemImage: "clock")
                }

            GlobalWalletHomeView(user: user)
                .tabItem {
                    Label("Wallet", systemImage: "wallet.pass")
                }

            CommunityHubView(user: user)
                .tabItem {
                    Label("Community", systemImage: "person.3.sequence")
                }

            AdvancedToolsHomeView(user: user)
                .tabItem {
                    Label("Tools", systemImage: "sparkles")
                }

            ProfileHomeView(user: user)
            .tabItem {
                Label("Profile", systemImage: "person")
            }
        }
    }
}
