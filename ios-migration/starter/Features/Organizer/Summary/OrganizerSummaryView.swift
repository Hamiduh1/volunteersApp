import SwiftUI

struct OrganizerSummaryView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerSummaryViewModel()

    var body: some View {
        NavigationStack {
            List {
                if let status = viewModel.statusMessage, !status.isEmpty {
                    Section {
                        Text(status)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Event Summary") {
                    if viewModel.isLoading && viewModel.items.isEmpty {
                        ProgressView("Loading summary...")
                    } else if viewModel.items.isEmpty {
                        Text("No summary data available.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.items) { item in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(item.title)
                                    .font(.headline)
                                if let date = item.date {
                                    Text(date.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }

                                HStack {
                                    summaryChip("Applied", "\(item.appliedCount)")
                                    summaryChip("Pending", "\(item.pendingCount)")
                                    summaryChip("Approved", "\(item.approvedCount)")
                                    summaryChip("Rejected", "\(item.rejectedCount)")
                                }

                                HStack {
                                    Text("Volunteer Limit: \(item.volunteerLimit)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    Text("Earnings: $\(String(format: "%.2f", item.totalEarnings))")
                                        .font(.caption.weight(.semibold))
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            .navigationTitle("Organizer Summary")
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

    @ViewBuilder
    private func summaryChip(_ title: String, _ value: String) -> some View {
        Text("\(title): \(value)")
            .font(.caption2)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(.secondarySystemBackground))
            .clipShape(Capsule())
    }
}

