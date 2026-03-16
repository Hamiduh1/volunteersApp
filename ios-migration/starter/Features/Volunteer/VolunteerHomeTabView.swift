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

            VStack(spacing: 16) {
                Text("Signed in as \(user.email ?? user.uid)")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Sign Out") {
                    do {
                        try AuthService.shared.signOut()
                    } catch {
                        // Keep this minimal in starter templates.
                    }
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .tabItem {
                Label("Profile", systemImage: "person")
            }
        }
    }
}
