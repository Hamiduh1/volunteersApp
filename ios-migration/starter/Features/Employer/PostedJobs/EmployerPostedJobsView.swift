import SwiftUI

struct EmployerPostedJobsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = EmployerPostedJobsViewModel()
    @State private var showingComposer = false
    @State private var editingJob: JobRecord?

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
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if let jobId = job.id, !jobId.isEmpty {
                                Button(role: .destructive) {
                                    Task { await viewModel.deleteJob(jobId: jobId, uid: user.uid) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: false) {
                            if job.id != nil {
                                Button {
                                    editingJob = job
                                } label: {
                                    Label("Edit", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Posted Jobs")
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
