import SwiftUI

struct JobsListView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = JobsListViewModel()

    var body: some View {
        NavigationStack {
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
                                Text(job.employerName ?? "Unknown Employer")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Text(job.locationString ?? "Location unavailable")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)

                                if viewModel.appliedJobIds.contains(jobId) {
                                    Text("Applied")
                                        .font(.caption)
                                        .foregroundStyle(.green)
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
            .navigationTitle("Jobs")
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
}
