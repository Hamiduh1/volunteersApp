import SwiftUI

struct AdminPayoutQueueView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = AdminPayoutQueueViewModel()
    @State private var showReverseConfirmation = false

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
                TextField("Search by user, id, status, destination", text: $viewModel.query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Text("Showing \(viewModel.filteredItems.count) item(s) • Selected \(viewModel.selectedIds.count)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Section("Queue") {
                if viewModel.isLoading && viewModel.filteredItems.isEmpty {
                    ProgressView("Loading payout queue...")
                } else if viewModel.filteredItems.isEmpty {
                    Text("No payout requests for this filter.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.filteredItems) { item in
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

            if user.role == .owner || user.role == .admin {
                Section("Reversal Controls") {
                    TextField("Reversal reason (minimum 12 characters)", text: $viewModel.reversalReason, axis: .vertical)
                        .lineLimit(2...4)
                    HStack {
                        Button("Select Visible") {
                            viewModel.selectVisible()
                        }
                        .disabled(viewModel.filteredItems.isEmpty || viewModel.isReversing)
                        Spacer()
                        Button("Clear Selection") {
                            viewModel.clearSelection()
                        }
                        .disabled(viewModel.selectedIds.isEmpty || viewModel.isReversing)
                    }
                    Button(role: .destructive) {
                        showReverseConfirmation = true
                    } label: {
                        if viewModel.isReversing {
                            ProgressView()
                        } else {
                            Text("Reverse Selected")
                        }
                    }
                    .disabled(!viewModel.canReverse)
                }
            }
        }
        .navigationTitle("Payout Queue")
        .confirmationDialog(
            "Confirm reversal for selected payout requests?",
            isPresented: $showReverseConfirmation,
            titleVisibility: .visible
        ) {
            Button("Reverse Now", role: .destructive) {
                Task { await viewModel.reverseSelected() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This action reverses the selected payout requests and should only be used when settlement failed.")
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

