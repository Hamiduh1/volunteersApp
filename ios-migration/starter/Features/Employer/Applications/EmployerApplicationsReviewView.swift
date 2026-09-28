import SwiftUI

struct EmployerApplicationsReviewView: View {
    let user: AppSessionUser
    let jobId: String?
    let jobTitle: String?
    @StateObject private var viewModel = EmployerApplicationsReviewViewModel()
    @State private var selectedItem: EmployerManagedApplicationItem?

    init(user: AppSessionUser, jobId: String? = nil, jobTitle: String? = nil) {
        self.user = user
        self.jobId = jobId
        self.jobTitle = jobTitle
    }

    var body: some View {
        NavigationStack {
            VStack {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }

                Picker("Filter", selection: $viewModel.selectedFilter) {
                    ForEach(EmployerApplicationFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .padding([.horizontal, .top])

                if viewModel.isLoading {
                    Spacer()
                    ProgressView("Loading applications...")
                    Spacer()
                } else if viewModel.filteredItems.isEmpty {
                    Spacer()
                    Text("No applications found.")
                        .foregroundStyle(.secondary)
                    Spacer()
                } else {
                    List(viewModel.filteredItems) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(item.jobTitle).font(.headline)
                            Text(item.volunteerName).font(.subheadline)
                            if !item.volunteerEmail.isEmpty {
                                Text(item.volunteerEmail).font(.caption).foregroundStyle(.secondary)
                            }
                            Text("Status: \(item.status.displayTitle)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            if item.status.isPendingLike {
                                HStack {
                                    Button("Approve") {
                                        Task { await viewModel.approve(item, uid: user.uid) }
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(viewModel.updatingIds.contains(item.id))

                                    Button("Reject") {
                                        Task { await viewModel.reject(item, uid: user.uid) }
                                    }
                                    .buttonStyle(.bordered)
                                    .disabled(viewModel.updatingIds.contains(item.id))

                                    if viewModel.updatingIds.contains(item.id) {
                                        ProgressView()
                                            .controlSize(.small)
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            selectedItem = item
                        }
                    }
                }
            }
            .navigationTitle(jobTitle?.isEmpty == false ? (jobTitle ?? "Applicants") : "All Requests")
            .task { await viewModel.refresh(uid: user.uid, jobId: jobId) }
            .refreshable { await viewModel.refresh(uid: user.uid, jobId: jobId) }
            .sheet(item: $selectedItem) { item in
                EmployerApplicationDetailSheet(
                    item: item,
                    isUpdating: viewModel.updatingIds.contains(item.id),
                    onApprove: {
                        Task {
                            await viewModel.approve(item, uid: user.uid)
                            if !viewModel.updatingIds.contains(item.id) {
                                selectedItem = nil
                            }
                        }
                    },
                    onReject: {
                        Task {
                            await viewModel.reject(item, uid: user.uid)
                            if !viewModel.updatingIds.contains(item.id) {
                                selectedItem = nil
                            }
                        }
                    }
                )
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

private struct EmployerApplicationDetailSheet: View {
    let item: EmployerManagedApplicationItem
    let isUpdating: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Volunteer") {
                    detailRow("Name", item.volunteerName)
                    detailRow("Email", item.volunteerEmail.isEmpty ? "N/A" : item.volunteerEmail)
                }

                Section("Application") {
                    detailRow("Job", item.jobTitle)
                    detailRow("Status", item.status.displayTitle)
                    detailRow("Applied", formattedDate(item.appliedAt))
                }

                if item.status.isPendingLike {
                    Section("Decision") {
                        Button {
                            onApprove()
                        } label: {
                            if isUpdating {
                                ProgressView()
                            } else {
                                Text("Approve")
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isUpdating)

                        Button(role: .destructive) {
                            onReject()
                        } label: {
                            if isUpdating {
                                ProgressView()
                            } else {
                                Text("Reject")
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(.bordered)
                        .disabled(isUpdating)
                    }
                }
            }
            .navigationTitle("Application Detail")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
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

    private func formattedDate(_ value: Date?) -> String {
        guard let value else { return "N/A" }
        return value.formatted(date: .abbreviated, time: .shortened)
    }
}
