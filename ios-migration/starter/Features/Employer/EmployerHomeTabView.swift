import SwiftUI

struct EmployerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            EmployerPostedJobsView(user: user)
                .tabItem { Label("Posted Jobs", systemImage: "briefcase") }

            EmployerApplicationsReviewView(user: user)
                .tabItem { Label("Applications", systemImage: "person.3") }

            GlobalWalletHomeView(user: user)
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
