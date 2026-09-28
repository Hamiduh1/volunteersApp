import SwiftUI

struct OrganizerApplicationsReviewView: View {
    let user: AppSessionUser
    let preselectedEventId: String?
    @StateObject private var viewModel = OrganizerApplicationsReviewViewModel()

    init(user: AppSessionUser, preselectedEventId: String? = nil) {
        self.user = user
        self.preselectedEventId = preselectedEventId
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
                    ForEach(OrganizerApplicationFilter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .padding([.horizontal, .top])

                if !viewModel.eventFilterOptions.isEmpty {
                    Picker("Event", selection: $viewModel.selectedEventIdFilter) {
                        Text("All Events").tag("")
                        ForEach(viewModel.eventFilterOptions, id: \.id) { option in
                            Text(option.title).tag(option.id)
                        }
                    }
                    .pickerStyle(.menu)
                    .padding(.horizontal)
                }

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
                            Text(item.eventTitle).font(.headline)
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
                    }
                }
            }
            .navigationTitle("Review Applications")
            .task {
                await viewModel.refresh(uid: user.uid)
                if let preselectedEventId, !preselectedEventId.isEmpty {
                    viewModel.selectedEventIdFilter = preselectedEventId
                }
            }
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
