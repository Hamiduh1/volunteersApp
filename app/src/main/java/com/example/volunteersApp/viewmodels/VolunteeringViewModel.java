package com.example.volunteersApp.viewmodels;

import android.util.Log;
import androidx.annotation.NonNull; // For consistency, though not strictly used in this version
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

// CRITICAL: Ensure this is the correct and only import for EventModel
import com.example.volunteersApp.models.EventModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects; // For cleaner null checks

public class VolunteeringViewModel extends ViewModel {

    private static final String TAG = "VolunteeringViewModel";
    private static final String EVENTS_COLLECTION = "events";
    private static final String FIELD_STATUS = "status";
    private static final String STATUS_UPCOMING = "UPCOMING"; // Match value in Firestore
    // Assuming 'eventDateTime' is your Timestamp field for sorting and is in EventModel
    private static final String FIELD_EVENT_DATETIME = "eventDateTime";
    private static final String FIELD_CATEGORY = "category";
    // For client-side search, we don't need a specific lowercase field constant for query building

    private final FirebaseFirestore db;

    // LiveData for the list of opportunities (EventModel)
    private final MutableLiveData<List<EventModel>> _opportunities = new MutableLiveData<>();
    public LiveData<List<EventModel>> getOpportunities() {
        return _opportunities;
    }

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() {
        return _isLoading;
    }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() {
        return _errorMessage;
    }

    // For storing and retrieving the current search query
    private final MutableLiveData<String> _searchQueryLiveData = new MutableLiveData<>(null);
    public LiveData<String> getSearchQueryLiveData() { // Expose LiveData if fragment needs to observe
        return _searchQueryLiveData;
    }
    public String getSearchQueryValue() { // Keep this for convenience if needed
        return _searchQueryLiveData.getValue();
    }


    // For storing and retrieving the current category filter
    private final MutableLiveData<String> _categoryFilterLiveData = new MutableLiveData<>(null);
    public LiveData<String> getCategoryFilterLiveData() { // Expose LiveData
        return _categoryFilterLiveData;
    }
    // public String getCurrentCategoryFilterValue() { // Redundant if getCategoryFilterLiveData().getValue() is used
    //     return _categoryFilterLiveData.getValue();
    // }


    // To keep track of the last successful fetch for client-side filtering if only search changed
    private List<EventModel> lastFetchedFullList = new ArrayList<>();


    public VolunteeringViewModel() {
        db = FirebaseFirestore.getInstance();
        Log.d(TAG, "VolunteeringViewModel initialized.");
        // Initial fetch is typically triggered by the Fragment observing LiveData
        // and calling fetchOpportunities(false) if data is null/empty.
    }

    /**
     * Sets the category filter and triggers a new fetch.
     * @param category The category to filter by (null or "All" to clear).
     */
    public void setCategoryFilter(@Nullable String category) {
        String newCategory = (category != null && (category.trim().isEmpty() || category.equalsIgnoreCase("All"))) ? null : category;

        if (!Objects.equals(_categoryFilterLiveData.getValue(), newCategory)) {
            _categoryFilterLiveData.setValue(newCategory);
            Log.d(TAG, "Category filter changed to: " + newCategory + ". Refetching opportunities.");
            // When category changes, we must refetch from Firestore as client-side filtering won't suffice.
            fetchOpportunities(false); // Pass false for forceRefresh, as it's a filter change
        }
    }

    /**
     * Sets the search query. If client-side search is enabled and only the search query changes,
     * it might re-filter the last fetched list. Otherwise, it triggers a new fetch.
     * @param query The search query (null to clear).
     */
    public void setSearchQuery(@Nullable String query) {
        String oldQuery = _searchQueryLiveData.getValue();
        String newQueryNormalized = (query != null && !query.trim().isEmpty()) ? query.trim() : null;

        if (!Objects.equals(oldQuery, newQueryNormalized)) {
            _searchQueryLiveData.setValue(newQueryNormalized);
            Log.d(TAG, "Search query changed to: " + newQueryNormalized);

            // Option 1: Always refetch from Firestore when search query changes (simpler, less efficient for minor query tweaks)
            // fetchOpportunities(false);

            // Option 2: Apply client-side filtering if a valid list was already fetched for the current category
            if (!lastFetchedFullList.isEmpty()) {
                Log.d(TAG, "Applying client-side search to last fetched list.");
                applyClientSideSearchAndPost();
            } else {
                // If no prior list (e.g., first search, or after category change cleared it), fetch from Firestore
                Log.d(TAG, "No valid last fetched list, refetching from Firestore for new search query.");
                fetchOpportunities(false);
            }
        }
    }


    /**
     * Fetches opportunities from Firestore based on the current category filter,
     * then applies client-side search filtering if a search query is set.
     *
     * @param forceRefresh Not actively used in this version but kept for potential future enhancements (e.g., cache busting).
     */
    public void fetchOpportunities(boolean forceRefresh) {
        String currentCategory = _categoryFilterLiveData.getValue();
        // Search query will be applied client-side from _searchQueryLiveData.getValue()

        Log.d(TAG, "Fetching opportunities. Force refresh: " + forceRefresh +
                ", Category: " + currentCategory);

        _isLoading.setValue(true);
        _errorMessage.setValue(null); // Clear previous errors

        Query query = db.collection(EVENTS_COLLECTION)
                .whereEqualTo(FIELD_STATUS, STATUS_UPCOMING) // Always show upcoming events
                // Use eventDateTime for sorting, ensure this field exists and is a Timestamp
                .orderBy(FIELD_EVENT_DATETIME, Direction.ASCENDING); // Show soonest first

        if (currentCategory != null && !currentCategory.isEmpty()) {
            query = query.whereEqualTo(FIELD_CATEGORY, currentCategory.trim());
            Log.d(TAG, "Applying Firestore category filter: " + currentCategory);
        }

        // Add a limit for safety/pagination
        query = query.limit(50); // Adjust as needed

        query.get().addOnCompleteListener(task -> {
            // _isLoading.setValue(false); // Set to false after processing OR if error occurs immediately

            if (task.isSuccessful() && task.getResult() != null) {
                List<EventModel> fetchedEventsThisTime = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        EventModel event = document.toObject(EventModel.class);
                        // @DocumentId in EventModel should handle setting eventId
                        fetchedEventsThisTime.add(event);
                    } catch (Exception e) {
                        Log.e(TAG, "Error converting document " + document.getId() + " to EventModel", e);
                    }
                }
                // Store the full list fetched for the current category before client-side search
                lastFetchedFullList = new ArrayList<>(fetchedEventsThisTime);
                Log.d(TAG, "Successfully fetched " + lastFetchedFullList.size() + " base opportunities for category: " + currentCategory);

                // Now, apply client-side search to this newly fetched list
                applyClientSideSearchAndPost();

            } else {
                Log.e(TAG, "Error fetching opportunities from Firestore: ", task.getException());
                String errorMsg = "Failed to load opportunities.";
                if (task.getException() != null && task.getException().getMessage() != null) {
                    errorMsg += " " + task.getException().getMessage();
                }
                _errorMessage.setValue(errorMsg);
                _opportunities.setValue(new ArrayList<>()); // Set to empty list on error
                lastFetchedFullList.clear(); // Clear cached list on error
            }
            _isLoading.setValue(false); // Ensure loading is set to false in all paths
        });
    }

    /**
     * Applies the current search query (from _searchQueryLiveData) to the lastFetchedFullList
     * and posts the filtered results to _opportunities.
     */
    private void applyClientSideSearchAndPost() {
        String currentSearchNormalized = null;
        if (_searchQueryLiveData.getValue() != null && !_searchQueryLiveData.getValue().trim().isEmpty()) {
            currentSearchNormalized = _searchQueryLiveData.getValue().trim().toLowerCase(Locale.ROOT);
        }

        if (currentSearchNormalized == null) {
            // No search query, post the full list fetched for the current category
            _opportunities.setValue(new ArrayList<>(lastFetchedFullList)); // Post a copy
            Log.d(TAG, "Client-side search cleared or not active. Posting " + lastFetchedFullList.size() + " events.");
        } else {
            List<EventModel> filteredEvents = new ArrayList<>();
            for (EventModel event : lastFetchedFullList) {
                boolean matches = false;
                // Search in title (ensure getTitle() exists and is correct)
                if (event.getTitle() != null && event.getTitle().toLowerCase(Locale.ROOT).contains(currentSearchNormalized)) {
                    matches = true;
                }
                // Optionally search in other fields (add null checks):
                // else if (event.getDescription() != null && event.getDescription().toLowerCase(Locale.ROOT).contains(currentSearchNormalized)) {
                //     matches = true;
                // }
                // else if (event.getLocationNameName() != null && event.getLocationNameName().toLowerCase(Locale.ROOT).contains(currentSearchNormalized)) {
                //     matches = true;
                // }
                if (matches) {
                    filteredEvents.add(event);
                }
            }
            _opportunities.setValue(filteredEvents);
            Log.d(TAG, "Client-side search for '" + currentSearchNormalized + "' on " + lastFetchedFullList.size() + " items resulted in " + filteredEvents.size() + " events.");
        }
    }


    // This method might not be needed if the Fragment doesn't require direct indexed access.
    // Kept for compatibility if it was used elsewhere.
    public int findEventPosition(String eventId) {
        List<EventModel> currentList = _opportunities.getValue();
        if (currentList == null || eventId == null) {
            return -1;
        }
        for (int i = 0; i < currentList.size(); i++) {
            EventModel event = currentList.get(i);
            if (event != null && Objects.equals(event.getEventId(), eventId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "VolunteeringViewModel onCleared");
        // No persistent listeners to clear in this version as it uses .get()
    }
}
