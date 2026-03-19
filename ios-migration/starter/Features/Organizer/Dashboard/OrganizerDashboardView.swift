import SwiftUI

struct OrganizerDashboardView: View {
    let user: AppSessionUser
    let onOpenTab: (OrganizerHomeTab) -> Void
    @StateObject private var viewModel = OrganizerDashboardViewModel()
    @State private var showingComposer = false
    @State private var showingWallet = false
    @State private var showingProfile = false
    @State private var showingLive = false

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
            .navigationTitle("Dashboard")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("My Wallet", systemImage: "wallet.pass.fill") {
                            showingWallet = true
                        }
                        Button("Organizer Profile", systemImage: "person.crop.circle") {
                            showingProfile = true
                        }
                        if viewModel.snapshot.canGoLive {
                            Button("Go Live", systemImage: "video.fill") {
                                showingLive = true
                            }
                        }
                    } label: {
                        Image(systemName: "line.3.horizontal")
                    }
                }
            }
            .task { await viewModel.refresh(uid: user.uid) }
            .refreshable { await viewModel.refresh(uid: user.uid) }
            .sheet(isPresented: $showingComposer) {
                OrganizerEventComposerView(user: user) {
                    Task { await viewModel.refresh(uid: user.uid) }
                }
            }
            .sheet(isPresented: $showingWallet) {
                NavigationStack {
                    OrganizerWalletView(user: user)
                }
            }
            .sheet(isPresented: $showingProfile) {
                NavigationStack {
                    OrganizerProfileSetupView(user: user)
                }
            }
            .sheet(isPresented: $showingLive) {
                NavigationStack {
                    LiveSessionsView(user: user)
                }
            }
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

                Button {
                    showingComposer = true
                } label: {
                    actionRow(title: "Create New Event", subtitle: "Host a new volunteer event", icon: "plus.circle.fill")
                }
                .buttonStyle(.plain)

                Button {
                    onOpenTab(.events)
                } label: {
                    actionRow(title: "Manage Events", subtitle: "View and edit hosted events", icon: "calendar.badge.clock")
                }
                .buttonStyle(.plain)

                Button {
                    onOpenTab(.requests)
                } label: {
                    actionRow(title: "Review Applications", subtitle: "All, pending, approved, and rejected", icon: "person.crop.rectangle.stack")
                }
                .buttonStyle(.plain)

                Button {
                    onOpenTab(.summary)
                } label: {
                    actionRow(title: "Event Summary", subtitle: "Hosted and active volunteer metrics", icon: "chart.bar.xaxis")
                }
                .buttonStyle(.plain)

                Button {
                    showingWallet = true
                } label: {
                    actionRow(title: "Organizer Wallet", subtitle: "Balance, tracked income, and history", icon: "wallet.pass")
                }
                .buttonStyle(.plain)

                Button {
                    showingProfile = true
                } label: {
                    actionRow(title: "Organizer Profile", subtitle: "Organization name, bio, and location", icon: "person.crop.circle")
                }
                .buttonStyle(.plain)

                if viewModel.snapshot.canGoLive {
                    Button {
                        showingLive = true
                    } label: {
                        actionRow(title: "Go Live", subtitle: "Start or monitor live sessions", icon: "video")
                    }
                    .buttonStyle(.plain)
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
