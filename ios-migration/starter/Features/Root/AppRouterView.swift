import SwiftUI

struct AppRouterView: View {
    @EnvironmentObject var session: SessionManager

    var body: some View {
        switch session.state {
        case .loading:
            VStack(spacing: 12) {
                BrandSymbolView(
                    assetName: BrandAsset.companyMark,
                    fallbackSystemName: "person.3.sequence",
                    size: 38,
                    useTemplate: false
                )
                ProgressView("Loading...")
            }
        case .signedOut:
            LoginView()
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
