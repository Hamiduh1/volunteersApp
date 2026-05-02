import SwiftUI

struct OwnerUserReportsView: View {
    @StateObject private var viewModel = OwnerUserReportsViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            AdminRoleResponsibilitiesSection(
                screenKey: AdminRoleResponsibilitiesScreenKey.USER_REPORTS,
                fallbackGuide: AdminRoleResponsibilitiesGuide(
                    roleTitle: "Reports Review Associate",
                    mission: "Review user complaints and convert reports into safe moderation actions.",
                    responsibilities: [
                        "Validate report reason and context before action.",
                        "Identify repeat offenders and escalate severe abuse quickly.",
                        "Keep reviewer notes objective and policy-aligned.",
                        "Coordinate with support for user-facing follow-up."
                    ],
                    escalationRule: "Escalate violence, exploitation, or legal-risk reports immediately."
                )
            )

            Section("Filters") {
                TextField("Search reports", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Picker("Source", selection: $viewModel.activeFilter) {
                    ForEach(OwnerUserReportsFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                Text("Showing \(viewModel.filteredReports.count) report(s)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            if viewModel.isLoading && viewModel.filteredReports.isEmpty {
                ProgressView("Loading user reports...")
            } else if viewModel.filteredReports.isEmpty {
                Text("No user reports found.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(viewModel.filteredReports) { report in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(report.reportedUserName)
                            .font(.headline)
                        Text(report.reason)
                            .font(.subheadline)
                        Text("Source: \(report.sourceCollection)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if let reporter = report.reportingUserDisplayName {
                            Text("Reporter: \(reporter)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let eventName = report.eventName {
                            Text("Context: \(eventName)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if let at = report.timestamp {
                            Text(at.formatted(date: .abbreviated, time: .shortened))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .navigationTitle("User Reports")
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
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
