import SwiftUI

@MainActor
final class EmployerDashboardViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published private(set) var welcomeMessage = "Welcome, Employer!"
    @Published private(set) var jobCount = 0
    @Published private(set) var applicationCount = 0

    private let repository = EmployerRepository()

    func refresh(user: AppSessionUser) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let fallbackName = user.email?.split(separator: "@").first.map(String.init) ?? "Employer"
        welcomeMessage = "Welcome, \(fallbackName)!"

        do {
            async let jobsTask = repository.fetchPostedJobs(uid: user.uid)
            async let appsTask = repository.fetchManagedApplications(uid: user.uid)
            let jobs = try await jobsTask
            let applications = try await appsTask
            jobCount = jobs.count
            applicationCount = applications.count
        } catch {
            errorMessage = AppErrorMapper.message(from: error)
        }
    }
}

struct EmployerDashboardView: View {
    let user: AppSessionUser
    let onOpenTab: (EmployerHomeTab) -> Void

    @StateObject private var viewModel = EmployerDashboardViewModel()
    @State private var showComposer = false
    @State private var showLive = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    welcomeCard
                    metricsRow
                    quickActions
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Employer Hub")
            .task { await viewModel.refresh(user: user) }
            .refreshable { await viewModel.refresh(user: user) }
            .sheet(isPresented: $showComposer) {
                EmployerJobComposerView(user: user) {
                    Task { await viewModel.refresh(user: user) }
                }
            }
            .sheet(isPresented: $showLive) {
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

    private var welcomeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(viewModel.welcomeMessage)
                .font(.title3.weight(.bold))
            Text("Manage your organization and opportunities efficiently.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.blue.opacity(0.14))
        )
    }

    private var metricsRow: some View {
        HStack(spacing: 12) {
            metricCard(title: "Jobs Posted", value: "\(viewModel.jobCount)")
            metricCard(title: "Applications", value: "\(viewModel.applicationCount)")
        }
    }

    private func metricCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title2.weight(.bold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var quickActions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Quick Management")
                .font(.headline)

            actionRow(
                title: "Post New Opportunity",
                subtitle: "Create a job for volunteers",
                icon: "plus.circle.fill",
                tint: .blue
            ) {
                showComposer = true
            }

            actionRow(
                title: "Manage Posted Jobs",
                subtitle: "Edit or close active listings",
                icon: "briefcase.fill",
                tint: .green
            ) {
                onOpenTab(.jobs)
            }

            actionRow(
                title: "Review Applications",
                subtitle: "Approve or reject volunteer requests",
                icon: "person.3.fill",
                tint: .orange
            ) {
                onOpenTab(.applications)
            }

            actionRow(
                title: "Go Live Now",
                subtitle: "Engage your community in real time",
                icon: "video.fill",
                tint: .red
            ) {
                showLive = true
            }

            actionRow(
                title: "Organization Profile",
                subtitle: "Update employer details",
                icon: "building.2.fill",
                tint: .purple
            ) {
                onOpenTab(.profile)
            }
        }
    }

    private func actionRow(
        title: String,
        subtitle: String,
        icon: String,
        tint: Color,
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.headline)
                    .foregroundStyle(tint)
                    .frame(width: 34, height: 34)
                    .background(Circle().fill(tint.opacity(0.14)))

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
