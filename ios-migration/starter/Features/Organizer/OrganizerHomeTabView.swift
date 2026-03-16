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

            VStack(spacing: 16) {
                Text("Organizer: \(user.email ?? user.uid)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Sign Out") {
                    try? AuthService.shared.signOut()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .tabItem { Label("Profile", systemImage: "person") }
        }
    }
}
