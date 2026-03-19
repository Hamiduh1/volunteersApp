import SwiftUI

struct AppRouterView: View {
    @EnvironmentObject var session: SessionManager

    var body: some View {
        switch session.state {
        case .loading:
            BrandedLaunchLoadingView()
        case .signedOut:
            NavigationStack {
                AuthLaunchView()
            }
        case .signedIn(let user):
            switch user.role {
            case .volunteer, .user, .agent:
                VolunteerHomeTabView(user: user)
            case .organizer:
                OrganizerHomeTabView(user: user)
            case .employer:
                EmployerHomeTabView(user: user)
            case .owner, .admin, .associate, .support, .supportAssociate:
                AdminHomeTabView(user: user)
            case .unknown:
                Text("Role is unknown. Please contact support.")
                    .padding()
            }
        case .error(let message):
            VStack(spacing: 12) {
                Text("Session Error")
                    .font(.headline)
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Retry") {
                    session.start()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
    }
}

private struct BrandedLaunchLoadingView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.07, green: 0.13, blue: 0.20),
                    Color(red: 0.12, green: 0.20, blue: 0.30)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 14) {
                BrandSymbolView(
                    assetName: BrandAsset.appLogo,
                    fallbackSystemName: "person.3.sequence.fill",
                    size: 78,
                    useTemplate: false
                )
                BrandSymbolView(
                    assetName: BrandAsset.companyMark,
                    fallbackSystemName: "building.2.fill",
                    size: 38,
                    useTemplate: false
                )
                Text("Volunteers App")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                ProgressView("Loading dashboard...")
                    .tint(.white)
            }
            .padding(22)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.white.opacity(0.12))
            )
            .padding(20)
        }
    }
}
