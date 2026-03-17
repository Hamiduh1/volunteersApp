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
                    if let role = job.jobTitle, !role.isEmpty {
                        Text(role)
                            .font(.headline)
                            .foregroundStyle(.secondary)
                    }

                    Text("Employer: \(job.organizationName ?? job.employerName ?? "Unknown")")
                        .foregroundStyle(.secondary)
                    Text("Location: \(job.locationName ?? job.locationString ?? "Unavailable")")
                        .foregroundStyle(.secondary)
                    if let date = job.date, !date.isEmpty {
                        Text("Schedule: \(date) \(job.time ?? "")")
                            .foregroundStyle(.secondary)
                    }
                    if let needed = job.volunteersNeeded ?? job.totalSlots {
                        Text("Volunteers Needed: \(needed)")
                            .foregroundStyle(.secondary)
                    }
                    if let category = job.category, !category.isEmpty {
                        Text("Category: \(category)")
                            .foregroundStyle(.secondary)
                    }

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
