import SwiftUI

struct EventsListView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = EventsListViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView("Loading events...")
                } else {
                    List(viewModel.events) { event in
                        let eventId = event.id ?? ""
                        NavigationLink {
                            EventDetailView(eventId: eventId, user: user)
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(event.title ?? "Untitled Event")
                                    .font(.headline)
                                Text(event.locationName ?? event.locationAddress ?? "Location TBD")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                HStack {
                                    if viewModel.appliedEventIds.contains(eventId) {
                                        Text("Applied")
                                            .font(.caption)
                                            .foregroundStyle(.green)
                                    } else {
                                        Button("Apply") {
                                            Task { await viewModel.apply(eventId: eventId, user: user) }
                                        }
                                        .buttonStyle(.borderedProminent)
                                        .disabled(eventId.isEmpty || viewModel.applyInFlightEventIds.contains(eventId))
                                        if viewModel.applyInFlightEventIds.contains(eventId) {
                                            ProgressView()
                                                .controlSize(.small)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle("Events")
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
