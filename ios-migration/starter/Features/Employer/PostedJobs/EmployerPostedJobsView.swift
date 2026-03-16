import SwiftUI

struct EmployerPostedJobsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = EmployerPostedJobsViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView("Loading posted jobs...")
                } else if viewModel.jobs.isEmpty {
                    Text("No jobs posted yet.")
                        .foregroundStyle(.secondary)
                } else {
                    List(viewModel.jobs) { job in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(job.title ?? "Untitled Job")
                                .font(.headline)
                            Text(job.locationString ?? "Location TBD")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Posted Jobs")
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
}
