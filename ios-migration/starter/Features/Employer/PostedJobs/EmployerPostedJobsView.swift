import SwiftUI
import UIKit

struct EmployerPostedJobsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = EmployerPostedJobsViewModel()
    @State private var showingComposer = false
    @State private var editingJob: JobRecord?
    @State private var reviewingApplicantsForJob: JobRecord?
    @State private var selectedDetailsJob: JobRecord?

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView("Loading posted jobs...")
                } else if viewModel.jobs.isEmpty {
                    Text("No jobs posted yet.")
                        .foregroundStyle(.secondary)
                } else {
                    List {
                        Section {
                            dashboardSummary
                        }

                        Section {
                            Picker("Status", selection: $viewModel.selectedFilter) {
                                ForEach(EmployerJobsFilter.allCases) { filter in
                                    Text(filter.title).tag(filter)
                                }
                            }
                            .pickerStyle(.segmented)
                        }

                        Section("Manage Jobs") {
                            if viewModel.filteredJobs.isEmpty {
                                Text("No jobs in this filter.")
                                    .foregroundStyle(.secondary)
                            } else {
                                ForEach(viewModel.filteredJobs) { job in
                                    EmployerPostedJobRow(
                                        job: job,
                                        applicantsCount: viewModel.applicationsCount(for: job.id ?? ""),
                                        pendingCount: viewModel.pendingApplicationsCount(for: job.id ?? ""),
                                        onManageApplicants: {
                                            guard let jobId = job.id, !jobId.isEmpty else { return }
                                            reviewingApplicantsForJob = job
                                        },
                                        onDetails: {
                                            selectedDetailsJob = job
                                        },
                                        onEdit: {
                                            editingJob = job
                                        },
                                        onToggleStatus: {
                                            guard let jobId = job.id, !jobId.isEmpty else { return }
                                            Task { await viewModel.toggleJobStatus(jobId: jobId, currentStatus: job.status, uid: user.uid) }
                                        },
                                        onDelete: {
                                            guard let jobId = job.id, !jobId.isEmpty else { return }
                                            Task { await viewModel.deleteJob(jobId: jobId, uid: user.uid) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("My Posted Jobs")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingComposer = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .task { await viewModel.refresh(uid: user.uid) }
            .refreshable { await viewModel.refresh(uid: user.uid) }
            .sheet(isPresented: $showingComposer) {
                EmployerJobComposerView(user: user) {
                    Task { await viewModel.refresh(uid: user.uid) }
                }
            }
            .sheet(item: $editingJob) { job in
                EmployerJobComposerView(user: user, existingJob: job) {
                    Task { await viewModel.refresh(uid: user.uid) }
                }
            }
            .sheet(item: $reviewingApplicantsForJob) { job in
                EmployerApplicationsReviewView(
                    user: user,
                    jobId: job.id,
                    jobTitle: job.title
                )
            }
            .sheet(item: $selectedDetailsJob) { job in
                EmployerJobDetailView(job: job)
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

    private var dashboardSummary: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                summaryCard(title: "Posted Jobs", value: "\(viewModel.totalJobs)")
                summaryCard(title: "Open Jobs", value: "\(viewModel.openJobs)")
                summaryCard(title: "Closed Jobs", value: "\(viewModel.closedJobs)")
                summaryCard(title: "Applications", value: "\(viewModel.totalApplications)")
                summaryCard(title: "Pending", value: "\(viewModel.pendingApplications)")
            }
            .padding(.vertical, 4)
        }
    }

    private func summaryCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3.weight(.semibold))
        }
        .frame(width: 120, alignment: .leading)
        .padding(10)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct EmployerPostedJobRow: View {
    let job: JobRecord
    let applicantsCount: Int
    let pendingCount: Int
    let onManageApplicants: () -> Void
    let onDetails: () -> Void
    let onEdit: () -> Void
    let onToggleStatus: () -> Void
    let onDelete: () -> Void

    private var isClosed: Bool {
        (job.status ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == "closed"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(job.title ?? "Untitled Job")
                        .font(.headline)
                    if let role = job.jobTitle, !role.isEmpty {
                        Text(role)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(job.locationName ?? job.locationString ?? "Location unavailable")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let date = job.date, !date.isEmpty {
                        Text("\(date) \(job.time ?? "")")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(isClosed ? "CLOSED" : "OPEN")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background((isClosed ? Color.red : Color.green).opacity(0.12))
                    .foregroundStyle(isClosed ? Color.red : Color.green)
                    .clipShape(Capsule())
            }

            HStack(spacing: 12) {
                Text("Applicants: \(applicantsCount)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Pending: \(pendingCount)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            HStack {
                Button("Manage Applicants", action: onManageApplicants)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    .disabled((job.id ?? "").isEmpty)
                Button("Details", action: onDetails)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                Button("Edit", action: onEdit)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
            }

            HStack {
                Button(isClosed ? "Reopen" : "Close", action: onToggleStatus)
                    .buttonStyle(.bordered)
                    .tint(isClosed ? .green : .orange)
                    .controlSize(.small)
                Button("Delete", role: .destructive, action: onDelete)
                    .buttonStyle(.bordered)
                    .controlSize(.small)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct EmployerJobDetailView: View {
    let job: JobRecord

    var body: some View {
        NavigationStack {
            List {
                Section("Overview") {
                    detailRow("Title", job.title ?? "Untitled")
                    detailRow("Role", job.jobTitle ?? "N/A")
                    detailRow("Organization", job.organizationName ?? job.employerName ?? "N/A")
                    detailRow("Status", ((job.status ?? "open").uppercased()))
                }

                Section("Schedule & Location") {
                    detailRow("Date", job.date ?? "TBD")
                    detailRow("Time", job.time ?? "TBD")
                    detailRow("Location", job.locationName ?? job.locationString ?? "N/A")
                    detailRow("Category", job.category ?? "General")
                }

                Section("Capacity") {
                    detailRow("Volunteers Needed", "\(job.volunteersNeeded ?? job.totalSlots ?? 0)")
                    detailRow("Slots Filled", "\(job.slotsFilled ?? 0)")
                    detailRow("Applicants", "\(job.applicantsCount ?? 0)")
                }

                Section("Description") {
                    Text(job.description?.isEmpty == false ? job.description! : "No description provided.")
                }
            }
            .navigationTitle("Job Details")
        }
    }

    @ViewBuilder
    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .multilineTextAlignment(.trailing)
        }
    }
}
