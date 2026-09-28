import SwiftUI

struct AuthLaunchView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                heroCard
                dashboardHomeCard
                authActionsCard
            }
            .padding(16)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Welcome")
    }

    @ViewBuilder
    private var heroCard: some View {
        ZStack(alignment: .leading) {
            LinearGradient(
                colors: [
                    Color(red: 0.07, green: 0.13, blue: 0.20),
                    Color(red: 0.13, green: 0.22, blue: 0.33)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    BrandSymbolView(
                        assetName: BrandAsset.appLogo,
                        fallbackSystemName: "person.3.sequence.fill",
                        size: 48,
                        useTemplate: false
                    )
                    BrandSymbolView(
                        assetName: BrandAsset.companyMark,
                        fallbackSystemName: "building.2.fill",
                        size: 28,
                        useTemplate: false
                    )
                }

                Text("Volunteers App")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(.white)
                Text("SoftSolutions Technologies LLC")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.84))
                Text("Connect volunteers, organizers, and employers in one loop.")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.86))
            }
            .padding(20)
        }
        .frame(maxWidth: .infinity, minHeight: 180)
    }

    @ViewBuilder
    private var dashboardHomeCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Dashboard Home")
                .font(.headline)
            Text("Authenticate to open the right dashboard for your account type.")
                .font(.caption)
                .foregroundStyle(.secondary)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    roleChip("Volunteer")
                    roleChip("Organizer")
                    roleChip("Employer")
                    roleChip("Admin")
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(.secondarySystemBackground))
        )
    }

    @ViewBuilder
    private var authActionsCard: some View {
        VStack(spacing: 10) {
            NavigationLink {
                LoginView()
            } label: {
                Text("Sign In")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            }
            .buttonStyle(.borderedProminent)

            NavigationLink {
                SignUpView()
            } label: {
                Text("Create Account")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.bordered)

            HStack(spacing: 8) {
                NavigationLink("Verify Email") {
                    EmailVerificationView()
                }
                .buttonStyle(.bordered)

                NavigationLink("Forgot Password") {
                    ForgotPasswordView()
                }
                .buttonStyle(.bordered)
            }
            .font(.caption.weight(.semibold))
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(.secondarySystemBackground))
        )
    }

    @ViewBuilder
    private func roleChip(_ title: String) -> some View {
        Text(title)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(Color(.tertiarySystemFill))
            )
    }
}
