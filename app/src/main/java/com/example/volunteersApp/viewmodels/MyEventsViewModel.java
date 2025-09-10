// Conceptual MyEventsViewModel.java (you'll need to create and implement this)
package com.example.volunteersApp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.volunteersApp.models.EventModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MyEventsViewModel extends ViewModel {
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private MutableLiveData<List<EventModel>> _appliedEvents = new MutableLiveData<>();
    public LiveData<List<EventModel>> appliedEvents = _appliedEvents;

    private MutableLiveData<List<EventModel>> _approvedUpcomingEvents = new MutableLiveData<>();
    public LiveData<List<EventModel>> approvedUpcomingEvents = _approvedUpcomingEvents;

    private MutableLiveData<List<EventModel>> _pastAttendedEvents = new MutableLiveData<>();
    public LiveData<List<EventModel>> pastAttendedEvents = _pastAttendedEvents;

    private MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> isLoading = _isLoading;

    private MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> errorMessage = _errorMessage;


    public void fetchMyEventsData() {
        String volunteerId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (volunteerId == null) {
            _errorMessage.setValue("User not logged in.");
            return;
        }
        _isLoading.setValue(true);

        // This is a simplified example. You'll need a more robust way to query.
        // Option 1: Query an 'applications' collection and then fetch related event details.
        // Option 2: If events store an array of applicant IDs/statuses (less scalable for querying).

        // Example for "Applied/Pending" and "Approved" (you'll need to adapt Firestore structure)
        db.collection("applications")
                .whereEqualTo("volunteerId", volunteerId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> appliedEventIds = new ArrayList<>();
                        List<String> approvedEventIds = new ArrayList<>();

                        for (QueryDocumentSnapshot appDoc : task.getResult()) {
                            String status = appDoc.getString("status");
                            String eventId = appDoc.getString("eventId");
                            if (eventId == null) continue;

                            if ("pending".equals(status) || "applied".equals(status)) {
                                appliedEventIds.add(eventId);
                            } else if ("approved".equals(status)) {
                                approvedEventIds.add(eventId);
                            }
                        }
                        fetchEventsByIds(appliedEventIds, _appliedEvents, false);
                        fetchEventsByIds(approvedEventIds, _approvedUpcomingEvents, true); // True to filter for upcoming/past

                    } else {
                        _errorMessage.setValue("Failed to load applications: " + task.getException());
                        _isLoading.setValue(false);
                    }
                });
    }

    private void fetchEventsByIds(List<String> eventIds, MutableLiveData<List<EventModel>> liveData, boolean filterDate) {
        if (eventIds.isEmpty()) {
            liveData.setValue(new ArrayList<>());
            if (!filterDate || liveData == _approvedUpcomingEvents) { // only stop loading if it's the last one for approved
                // This logic needs to be better for multiple async calls before setting loading to false
            }
            if (liveData == _approvedUpcomingEvents) _pastAttendedEvents.setValue(new ArrayList<>()); // Clear past if no approved
            return;
        }

        db.collection("events").whereIn("eventId", eventIds) // Assuming EventModel has eventId field matching doc ID
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<EventModel> events = task.getResult().toObjects(EventModel.class);
                        if (filterDate && liveData == _approvedUpcomingEvents) { // Specific for approved events
                            List<EventModel> upcoming = new ArrayList<>();
                            List<EventModel> past = new ArrayList<>();
                            Date now = new Date();
                            for (EventModel event : events) {
                                if (event.getEventDateTime() != null && event.getEventDateTime().toDate().after(now)) {
                                    upcoming.add(event);
                                } else {
                                    past.add(event);
                                }
                            }
                            _approvedUpcomingEvents.setValue(upcoming);
                            _pastAttendedEvents.setValue(past);
                        } else {
                            liveData.setValue(events);
                        }
                    } else {
                        _errorMessage.setValue("Failed to load event details: " + task.getException());
                    }
                    // This needs more robust handling for multiple async calls to set _isLoading correctly
                    _isLoading.setValue(false); // Simplified
                });
    }
}
