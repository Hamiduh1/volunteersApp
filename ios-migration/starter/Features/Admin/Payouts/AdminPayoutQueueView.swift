import SwiftUI

struct AdminPayoutQueueView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = AdminPayoutQueueViewModel()

    var body: some View {
        List {
            if let status = viewModel.statusMessage, !status.isEmpty {
                Section {
                    Text(status)
                        .font(.subheadline)
                        .foregroundStyle(.green)
                }
            }

            Section {
                Picker("Filter", selection: $viewModel.activeFilter) {
                    ForEach(AdminPayoutFilter.allCases) { filter in
                        Text(filter.label).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .onChange(of: viewModel.activeFilter) { _, _ in
                    Task { await viewModel.refresh() }
                }
            }

            Section("Queue") {
                if viewModel.isLoading && viewModel.items.isEmpty {
                    ProgressView("Loading payout queue...")
                } else if viewModel.items.isEmpty {
                    Text("No payout requests for this filter.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.items) { item in
                        HStack(alignment: .top, spacing: 10) {
                            if user.role == .owner || user.role == .admin {
                                Button {
                                    viewModel.toggleSelection(item.id)
                                } label: {
                                    Image(systemName: viewModel.selectedIds.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(viewModel.selectedIds.contains(item.id) ? .blue : .secondary)
                                }
                                .buttonStyle(.plain)
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.requesterName)
                                    .font(.headline)
                                Text(item.requesterId)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Text("\(item.currency) \(String(format: "%.2f", item.amount)) - \(item.status)")
                                    .font(.subheadline)
                                if let destination = item.destinationLabel {
                                    Text(destination)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                if let createdAt = item.createdAt {
                                    Text(createdAt.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
        }
        .navigationTitle("Payout Queue")
        .toolbar {
            if user.role == .owner || user.role == .admin {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await viewModel.reverseSelected() }
                    } label: {
                        if viewModel.isReversing {
                            ProgressView()
                        } else {
                            Text("Reverse")
                        }
                    }
                    .disabled(viewModel.selectedIds.isEmpty || viewModel.isReversing)
                }
            }
        }
        .task { await viewModel.refresh() }
        .refreshable { await viewModel.refresh() }
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

