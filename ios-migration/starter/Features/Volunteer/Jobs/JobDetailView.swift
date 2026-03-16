import SwiftUI

struct JobDetailView: View {
    let jobId: String
    let user: AppSessionUser
    @StateObject private var viewModel = JobDetailViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if viewModel.isLoading {
                    ProgressView("Loading job...")
                } else if let job = viewModel.job {
                    Text(job.title ?? "Job")
                        .font(.title2).bold()

                    Text("Employer: \(job.employerName ?? "Unknown")")
                        .foregroundStyle(.secondary)
                    Text("Location: \(job.locationString ?? "TBD")")
                        .foregroundStyle(.secondary)

                    if let description = job.description, !description.isEmpty {
                        Text(description)
                    }

                    if viewModel.isApplied {
                        Text("Application status: \((viewModel.application?.status ?? .unknown).displayTitle)")
                            .font(.footnote)
                            .foregroundStyle(.green)
                    } else {
                        Button("Apply to Job") {
                            Task { await viewModel.apply(jobId: jobId, user: user) }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(viewModel.isApplying)
                        if viewModel.isApplying {
                            ProgressView()
                                .controlSize(.small)
                        }
                    }
                } else {
                    Text("Job not found.")
                        .foregroundStyle(.secondary)
                }
            }
            .padding()
        }
        .navigationTitle("Job Details")
        .task { await viewModel.load(jobId: jobId, user: user) }
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
