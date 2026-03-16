import SwiftUI

struct EmployerHomeTabView: View {
    let user: AppSessionUser

    var body: some View {
        TabView {
            EmployerPostedJobsView(user: user)
                .tabItem { Label("Posted Jobs", systemImage: "briefcase") }

            EmployerApplicationsReviewView(user: user)
                .tabItem { Label("Applications", systemImage: "person.3") }

            VStack(spacing: 16) {
                Text("Employer: \(user.email ?? user.uid)")
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
