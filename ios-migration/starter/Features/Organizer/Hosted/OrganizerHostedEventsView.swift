import SwiftUI

struct OrganizerHostedEventsView: View {
    let user: AppSessionUser
    @StateObject private var viewModel = OrganizerHostedEventsViewModel()
    @State private var showingComposer = false
    @State private var editingEvent: EventRecord?

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView("Loading hosted events...")
                } else if viewModel.events.isEmpty {
                    Text("No hosted events yet.")
                        .foregroundStyle(.secondary)
                } else {
                    List(viewModel.events) { event in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(event.title ?? "Untitled Event")
                                .font(.headline)
                            Text(event.locationName ?? event.locationAddress ?? "Location TBD")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if let eventId = event.id, !eventId.isEmpty {
                                Button(role: .destructive) {
                                    Task { await viewModel.deleteEvent(eventId: eventId, uid: user.uid) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                        .swipeActions(edge: .leading, allowsFullSwipe: false) {
                            if event.id != nil {
                                Button {
                                    editingEvent = event
                                } label: {
                                    Label("Edit", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Hosted Events")
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
                OrganizerEventComposerView(user: user) {
                    Task { await viewModel.refresh(uid: user.uid) }
                }
            }
            .sheet(item: $editingEvent) { event in
                OrganizerEventComposerView(user: user, existingEvent: event) {
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
