import SwiftUI

struct OrganizerDashboardView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerDashboardViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    welcomeCard
                    metricsGrid
                    actionsCard
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Organizer Dashboard")
            .task { await viewModel.refresh(uid: user.uid) }
            .refreshable { await viewModel.refresh(uid: user.uid) }
            .alert("Error", isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { if !$0 { viewModel.errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.errorMessage ?? "Unknown error")
            }
        }
    }

    @ViewBuilder
    private var welcomeCard: some View {
        OrganizerDashCard(background: .blue.opacity(0.92)) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Welcome Back")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
                Text(viewModel.snapshot.organizerName)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                if viewModel.snapshot.canGoLive {
                    Label("Go Live enabled", systemImage: "video.fill")
                        .font(.footnote)
                        .foregroundStyle(.white)
                }
            }
        }
    }

    @ViewBuilder
    private var metricsGrid: some View {
        HStack(spacing: 10) {
            metricCard(title: "Events", value: "\(viewModel.snapshot.eventCount)", icon: "calendar")
            metricCard(title: "Volunteers", value: "\(viewModel.snapshot.totalVolunteers)", icon: "person.2")
            metricCard(title: "Earnings", value: "$\(String(format: "%.2f", viewModel.snapshot.totalEarnings))", icon: "dollarsign.circle")
        }
    }

    @ViewBuilder
    private var actionsCard: some View {
        OrganizerDashCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Quick Actions")
                    .font(.headline)

                NavigationLink {
                    OrganizerHostedEventsView(user: user)
                } label: {
                    actionRow(title: "Manage Events", subtitle: "Create, edit, and review applicants", icon: "calendar.badge.clock")
                }

                NavigationLink {
                    OrganizerApplicationsReviewView(user: user)
                } label: {
                    actionRow(title: "Review Applications", subtitle: "All, pending, approved, and rejected", icon: "person.crop.rectangle.stack")
                }

                NavigationLink {
                    OrganizerSummaryView(user: user)
                } label: {
                    actionRow(title: "Event Summary", subtitle: "Hosted and active volunteer metrics", icon: "chart.bar.xaxis")
                }

                NavigationLink {
                    OrganizerWalletView(user: user)
                } label: {
                    actionRow(title: "Organizer Wallet", subtitle: "Balance, tracked income, and history", icon: "wallet.pass")
                }

                NavigationLink {
                    OrganizerProfileSetupView(user: user)
                } label: {
                    actionRow(title: "Organizer Profile", subtitle: "Organization name, bio, and location", icon: "person.crop.circle")
                }

                if viewModel.snapshot.canGoLive {
                    NavigationLink {
                        LiveSessionsView(user: user)
                    } label: {
                        actionRow(title: "Go Live", subtitle: "Start or monitor live sessions", icon: "video")
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func metricCard(title: String, value: String, icon: String) -> some View {
        OrganizerDashCard {
            VStack(alignment: .leading, spacing: 6) {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.headline.weight(.bold))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder
    private func actionRow(title: String, subtitle: String, icon: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct OrganizerDashCard<Content: View>: View {
    private let background: Color
    @ViewBuilder private var content: Content

    init(background: Color = Color(.secondarySystemBackground), @ViewBuilder content: () -> Content) {
        self.background = background
        self.content = content()
    }

    var body: some View {
        VStack { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(background)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
