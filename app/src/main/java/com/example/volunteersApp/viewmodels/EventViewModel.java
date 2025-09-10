package com.example.volunteersApp.viewmodels;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel; // Using ViewModel as per original

// ENSURE THIS IMPORT IS CORRECT and matches your EventModel.java
import com.example.volunteersApp.models.EventModel;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
// ListenerRegistration not needed if all fetches are one-time 'get()'
// import com.google.firebase.firestore.ListenerRegistration;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class EventViewModel extends ViewModel {

    private static final String TAG = "EventViewModel";
    private static final String EVENTS_COLLECTION = "events";
    private static final String FIELD_STATUS = "status"; // Field name in Firestore for event status
    private static final String STATUS_UPCOMING = "UPCOMING"; // Value for upcoming status in Firestore
    private static final String FIELD_EVENT_TIMESTAMP = "eventDateTime"; // Field for event date/time
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_EVENT_NAME_LOWERCASE = "titleLowercase"; // Assuming 'title' is primary, create a lowercase version for searching
    private static final String FIELD_ORGANIZER_ID = "organizerId";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final CollectionReference eventsRef = db.collection(EVENTS_COLLECTION);

    private final MutableLiveData<List<EventModel>> _events = new MutableLiveData<>();
    public LiveData<List<EventModel>> getEvents() {
        return _events;
    }

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() {
        return _isLoading;
    }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() {
        return _errorMessage;
    }

    // --- Filter States ---
    private final MutableLiveData<String> _currentCategoryFilter = new MutableLiveData<>(null);
    public LiveData<String> getCurrentCategoryFilter() { // Keep original name for consistency if used elsewhere
        return _currentCategoryFilter;
    }

    private final MutableLiveData<String> _currentSearchQuery = new MutableLiveData<>(null);
    public LiveData<String> getCurrentSearchQuery() { // Keep original name
        return _currentSearchQuery;
    }

    private final MutableLiveData<String> _currentOrganizerIdFilter = new MutableLiveData<>(null);
    public LiveData<String> getCurrentOrganizerIdFilter() { // Keep original name
        return _currentOrganizerIdFilter;
    }
    // --- End Filter States ---

    public EventViewModel() {
        Log.d(TAG, "EventViewModel initialized.");
        // Initial fetch can be triggered by the Fragment when it's ready
        // fetchEvents(null, null, false); // Example: initial load without forcing organizer
    }

    public void setCategoryFilter(String category) {
        String newCategory = (category != null && category.trim().isEmpty()) ? null : category;
        if (Objects.equals(_currentCategoryFilter.getValue(), newCategory)) return;

        // When a general category is set, clear the specific organizer filter
        // unless you want them to be combined, which complicates query logic.
        _currentOrganizerIdFilter.setValue(null);
        _currentCategoryFilter.setValue(newCategory);
        Log.d(TAG, "Category filter set: " + newCategory + ". Organizer filter cleared. Fetching events.");
        fetchEvents();
    }

    public void setSearchQuery(String query) {
        String newQuery = (query != null && query.trim().isEmpty()) ? null :
                (query != null ? query.trim().toLowerCase(Locale.ROOT) : null);
        if (Objects.equals(_currentSearchQuery.getValue(), newQuery)) return;

        _currentOrganizerIdFilter.setValue(null); // Clear specific organizer filter
        _currentSearchQuery.setValue(newQuery);
        Log.d(TAG, "Search query set: " + newQuery + ". Organizer filter cleared. Fetching events.");
        fetchEvents();
    }

    /**
     * Sets the ViewModel to filter events by a specific organizer.
     * This clears other general filters like category and search query.
     * @param organizerId The ID of the organizer to filter by.
     */
    public void fetchEventsByOrganizer(String organizerId) {
        String newOrganizerId = (organizerId != null && organizerId.trim().isEmpty()) ? null : organizerId;
        // Fetch even if it's the same organizerId, in case data has changed (e.g., swipe refresh)
        // if (Objects.equals(_currentOrganizerIdFilter.getValue(), newOrganizerId)) return;

        _currentOrganizerIdFilter.setValue(newOrganizerId);
        _currentCategoryFilter.setValue(null); // Clear general category filter
        _currentSearchQuery.setValue(null);   // Clear general search query
        Log.d(TAG, "Organizer ID filter set: " + newOrganizerId + ". Other filters cleared. Fetching events.");
        fetchEvents(); // This will prioritize the organizerId filter
    }

    /**
     * Fetches events based on the ViewModel's current internal filter states.
     * This is the primary method called by filter setters or refresh actions.
     */
    public void fetchEvents() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null); // Clear previous errors

        String category = _currentCategoryFilter.getValue();
        String searchQuery = _currentSearchQuery.getValue();
        String organizerId = _currentOrganizerIdFilter.getValue();

        Query firestoreQuery = eventsRef; // Start with the base collection reference

        if (organizerId != null && !organizerId.isEmpty()) {
            // Priority 1: Filter by Organizer ID
            Log.d(TAG, "Fetching events for ORGANIZER ID: " + organizerId);
            firestoreQuery = firestoreQuery.whereEqualTo(FIELD_ORGANIZER_ID, organizerId)
                    .orderBy(FIELD_EVENT_TIMESTAMP, Query.Direction.DESCENDING); // Show organizer's newest events first
        } else {
            // General browsing (not for a specific organizer)
            Log.d(TAG, "Fetching general events. Category: " + category + ", Search: " + searchQuery);
            // Always filter by status for general browsing (e.g., only "UPCOMING")
            firestoreQuery = firestoreQuery.whereEqualTo(FIELD_STATUS, STATUS_UPCOMING);

            if (category != null && !category.isEmpty()) {
                firestoreQuery = firestoreQuery.whereEqualTo(FIELD_CATEGORY, category);
                Log.d(TAG, "Applied CATEGORY filter: " + category);
            }

            if (searchQuery != null && !searchQuery.isEmpty()) {
                // For Firestore string range queries for "starts with" type search:
                // Ensure you have a 'titleLowercase' (or similar) field in your EventModel and Firestore documents.
                firestoreQuery = firestoreQuery.orderBy(FIELD_EVENT_NAME_LOWERCASE) // Must order by the field used in range query
                        .whereGreaterThanOrEqualTo(FIELD_EVENT_NAME_LOWERCASE, searchQuery)
                        .whereLessThanOrEqualTo(FIELD_EVENT_NAME_LOWERCASE, searchQuery + "\uf8ff");
                Log.d(TAG, "Applied SEARCH filter: " + searchQuery);
            } else {
                // Default sort for general browsing if no search query.
                // If category is applied, usually still sort by time.
                firestoreQuery = firestoreQuery.orderBy(FIELD_EVENT_TIMESTAMP, Query.Direction.ASCENDING); // Show soonest upcoming events first
            }
        }

        // Apply a limit for pagination or safety
        firestoreQuery = firestoreQuery.limit(30);

        Log.d(TAG, "Executing Firestore query...");
        firestoreQuery.get().addOnCompleteListener(task -> {
            _isLoading.setValue(false);
            if (task.isSuccessful() && task.getResult() != null) {
                List<EventModel> fetchedEvents = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        EventModel event = document.toObject(EventModel.class);
                        // Event ID should be set by @DocumentId in EventModel
                        // if (event.getEventId() == null || event.getEventId().isEmpty()) {
                        //     event.setEventId(document.getId());
                        //     Log.w(TAG, "Event ID was null after toObject(), set from document.getId(): " + document.getId());
                        // }
                        fetchedEvents.add(event);
                    } catch (Exception e) {
                        Log.e(TAG, "Error converting document " + document.getId() + " to EventModel", e);
                    }
                }
                _events.setValue(fetchedEvents);
                Log.d(TAG, "Events fetched successfully. Count: " + fetchedEvents.size());
                if (fetchedEvents.isEmpty() && (organizerId != null || category != null || searchQuery != null)) {
                    _errorMessage.setValue("No events found matching your criteria."); // More specific message if filters were active
                } else if (fetchedEvents.isEmpty()) {
                    // _errorMessage.setValue("No events available at the moment."); // Generic if no filters
                }
            } else {
                Log.e(TAG, "Error fetching events from Firestore: ", task.getException());
                _errorMessage.setValue("Failed to load events. Please check your connection or try again.");
                _events.setValue(new ArrayList<>()); // Clear events / provide empty list on error
            }
        });
    }

    /**
     * Call this if the user manually triggers a refresh (e.g., swipe-to-refresh).
     * Re-fetches with current filters stored in the ViewModel's LiveData.
     */
    public void refreshEvents() {
        Log.d(TAG, "Refresh events triggered.");
        // The main fetchEvents() method already uses the current filter LiveData values.
        fetchEvents();
    }


    @Override
    protected void onCleared() {
        super.onCleared();
        // If using SnapshotListeners, they should be cleared here.
        // Since we are using .get() for one-time fetches, no listeners to clear in this version.
        Log.d(TAG, "EventViewModel onCleared.");
    }
}



