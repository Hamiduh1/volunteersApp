import SwiftUI

struct AdminHomeTabView: View {
    let user: AppSessionUser

    private var isOwnerOrAdmin: Bool {
        user.role == .owner || user.role == .admin
    }

    var body: some View {
        TabView {
            if isOwnerOrAdmin {
                OwnerDashboardView(user: user)
                    .tabItem { Label("Dashboard", systemImage: "chart.line.uptrend.xyaxis") }

                AdminControlsHubView(user: user)
                    .tabItem { Label("Controls", systemImage: "switch.2") }
            }

            SupportConsoleView(user: user)
                .tabItem { Label("Support", systemImage: "person.2.badge.gearshape") }

            GlobalWalletHomeView(user: user)
                .tabItem { Label("Wallet", systemImage: "wallet.pass") }

            CommunityHubView(user: user)
                .tabItem { Label("Community", systemImage: "person.3.sequence") }

            AdvancedToolsHomeView(user: user)
                .tabItem { Label("Tools", systemImage: "sparkles") }

            VStack(spacing: 16) {
                Text("Role: \((user.role.rawValue).uppercased())")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text(user.email ?? user.uid)
                    .font(.caption)
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

struct AdminControlsHubView: View {
    let user: AppSessionUser

    var body: some View {
        NavigationStack {
            List {
                Section("Moderation") {
                    NavigationLink("Payout Queue") {
                        AdminPayoutQueueView(user: user)
                    }
                    NavigationLink("User Reports") {
                        OwnerUserReportsView()
                    }
                    NavigationLink("KYC Review") {
                        OwnerKYCReviewView()
                    }
                }

                Section("Configuration") {
                    NavigationLink("Fee Settings") {
                        OwnerFeeSettingsView()
                    }
                    NavigationLink("System Config") {
                        OwnerSystemConfigView()
                    }
                }
            }
            .navigationTitle("Admin Controls")
        }
    }
}
