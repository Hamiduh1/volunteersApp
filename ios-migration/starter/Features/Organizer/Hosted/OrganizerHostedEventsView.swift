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
                        VStack(alignment: .leading, spacing: 8) {
                            Text(event.title ?? "Untitled Event")
                                .font(.headline)
                            Text(event.locationName ?? event.locationAddress ?? "Location unavailable")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)

                            if let date = event.eventDateTime?.dateValue() {
                                Text(date.formatted(date: .abbreviated, time: .shortened))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            HStack {
                                Text("Limit: \(event.volunteerLimit ?? 0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Text("Applied: \(event.participantsCount ?? 0)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            HStack(spacing: 8) {
                                NavigationLink {
                                    OrganizerApplicationsReviewView(user: user, preselectedEventId: event.id)
                                } label: {
                                    Text("Applicants")
                                }
                                .buttonStyle(.borderedProminent)

                                Button("Edit") {
                                    editingEvent = event
                                }
                                .buttonStyle(.bordered)
                            }
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
