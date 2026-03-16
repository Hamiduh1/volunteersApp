import SwiftUI

struct CommunityHubView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                Section("Social") {
                    NavigationLink("MindLoom") {
                        MindLoomFeedView(user: user)
                    }
                }

                Section("Commerce") {
                    NavigationLink("Marketplace") {
                        MarketplaceView(user: user)
                    }
                    NavigationLink("Sponsored & Garage") {
                        SponsoredContentView()
                    }
                }
            }
            .navigationTitle("Community")
        }
    }
}
