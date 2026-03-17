import SwiftUI

struct JobsListView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = JobsListViewModel()

    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView("Loading jobs...")
            } else {
                List(viewModel.jobs) { job in
                    let jobId = job.id ?? ""
                    NavigationLink {
                        JobDetailView(jobId: jobId, user: user)
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(job.title ?? "Untitled Job")
                                .font(.headline)
                            if let role = job.jobTitle, !role.isEmpty {
                                Text(role)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Text(job.organizationName ?? job.employerName ?? "Unknown Employer")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text(job.locationName ?? job.locationString ?? "Location unavailable")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if let date = job.date, !date.isEmpty {
                                Text("\(date) \(job.time ?? "")")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            if let needed = job.volunteersNeeded ?? job.totalSlots {
                                Text("Volunteers Needed: \(needed)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }

                            if viewModel.appliedJobIds.contains(jobId) {
                                Button("Already Applied") {}
                                    .buttonStyle(.bordered)
                                    .tint(.green)
                                    .disabled(true)
                            } else {
                                Button("Apply") {
                                    Task { await viewModel.apply(jobId: jobId, user: user) }
                                }
                                .buttonStyle(.borderedProminent)
                                .disabled(jobId.isEmpty || viewModel.applyInFlightJobIds.contains(jobId))
                                if viewModel.applyInFlightJobIds.contains(jobId) {
                                    ProgressView()
                                        .controlSize(.small)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .task { await viewModel.refresh(for: user) }
        .refreshable { await viewModel.refresh(for: user) }
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
