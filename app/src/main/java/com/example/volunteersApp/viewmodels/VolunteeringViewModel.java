package com.example.volunteersApp.viewmodels;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.EventModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VolunteeringViewModel extends AndroidViewModel {

    private static final String TAG = "VolunteeringViewModel";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // LiveData for the main list of opportunities
    private final MutableLiveData<List<EventModel>> _opportunities = new MutableLiveData<>();
    public LiveData<List<EventModel>> getOpportunities() { return _opportunities; }

    // LiveData for UI state
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() { return _isLoading; }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() { return _errorMessage; }

    // LiveData for one-time success/failure events for applying
    private final MutableLiveData<String> _applySuccessMessage = new MutableLiveData<>();
    public LiveData<String> getApplySuccessMessage() { return _applySuccessMessage; }

    private final MutableLiveData<String> _applyErrorMessage = new MutableLiveData<>();
    public LiveData<String> getApplyErrorMessage() { return _applyErrorMessage; }

    // For storing and filtering
    private final MutableLiveData<String> _searchQueryLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<String> _categoryFilterLiveData = new MutableLiveData<>(null);
    private List<EventModel> lastFetchedFullList = new ArrayList<>();

    public VolunteeringViewModel(@NonNull Application application) {
        super(application);
        Log.d(TAG, "VolunteeringViewModel initialized.");
    }

    public void setCategoryFilter(@Nullable String category) {
        String newCategory = (category != null && (category.trim().isEmpty() || category.equalsIgnoreCase("All"))) ? null : category;
        if (!Objects.equals(_categoryFilterLiveData.getValue(), newCategory)) {
            _categoryFilterLiveData.setValue(newCategory);
            Log.d(TAG, "Category filter changed to: " + newCategory);
            fetchOpportunities(false); // Must refetch from Firestore
        }
    }

    public void setSearchQuery(@Nullable String query) {
        String oldQuery = _searchQueryLiveData.getValue();
        String newQueryNormalized = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        if (!Objects.equals(oldQuery, newQueryNormalized)) {
            _searchQueryLiveData.setValue(newQueryNormalized);
            Log.d(TAG, "Search query changed to: " + newQueryNormalized);
            applyClientSideSearchAndPost(); // Filter existing list
        }
    }

    public void fetchOpportunities(boolean forceRefresh) {
        String currentCategory = _categoryFilterLiveData.getValue();
        Log.d(TAG, "Fetching opportunities. Category: " + currentCategory);
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        Query query = db.collection("events")
                .whereEqualTo("status", "approved")
                .whereGreaterThan("eventDateTime", Timestamp.now())
                .orderBy("eventDateTime", Query.Direction.ASCENDING);

        if (currentCategory != null && !currentCategory.isEmpty()) {
            query = query.whereEqualTo("category", currentCategory.trim());
        }

        query.limit(50).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                lastFetchedFullList = task.getResult().toObjects(EventModel.class);
                Log.d(TAG, "Successfully fetched " + lastFetchedFullList.size() + " base opportunities.");
                applyClientSideSearchAndPost();
            } else {
                Log.e(TAG, "Error fetching opportunities: ", task.getException());
                _errorMessage.setValue(getApplication().getString(R.string.error_loading_events));
                _opportunities.setValue(new ArrayList<>());
                lastFetchedFullList.clear();
            }
            _isLoading.setValue(false);
        });
    }

    private void applyClientSideSearchAndPost() {
        String searchNormalized = _searchQueryLiveData.getValue() != null ? _searchQueryLiveData.getValue().trim().toLowerCase(Locale.ROOT) : null;

        if (searchNormalized == null || searchNormalized.isEmpty()) {
            _opportunities.setValue(new ArrayList<>(lastFetchedFullList));
        } else {
            List<EventModel> filteredList = new ArrayList<>();
            for (EventModel event : lastFetchedFullList) {
                if (event.getTitle() != null && event.getTitle().toLowerCase(Locale.ROOT).contains(searchNormalized)) {
                    filteredList.add(event);
                }
            }
            _opportunities.setValue(filteredList);
        }
    }

    // *** METHOD THAT FIXES THE FIRST ERROR ***
    public LiveData<Set<String>> fetchUserAppliedEventIds(String userId) {
        MutableLiveData<Set<String>> appliedEventIdsLiveData = new MutableLiveData<>();
        if (userId == null || userId.isEmpty()) {
            appliedEventIdsLiveData.setValue(new HashSet<>());
            return appliedEventIdsLiveData;
        }

        db.collection("users").document(userId).collection("appliedToEvents")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Set<String> ids = new HashSet<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            ids.add(document.getId());
                        }
                        appliedEventIdsLiveData.setValue(ids);
                    } else {
                        Log.w(TAG, "Failed to fetch user applied event IDs.", task.getException());
                        appliedEventIdsLiveData.setValue(new HashSet<>());
                    }
                });
        return appliedEventIdsLiveData;
    }

    // *** METHOD THAT FIXES THE SECOND ERROR ***
    public void applyForEvent(EventModel event, String volunteerUid) {
        _isLoading.setValue(true);
        String eventId = event.getEventId();

        Map<String, Object> userApplication = new HashMap<>();
        userApplication.put("eventId", eventId);
        userApplication.put("applicationDate", FieldValue.serverTimestamp());

        Map<String, Object> eventApplication = new HashMap<>();
        eventApplication.put("volunteerUid", volunteerUid);
        eventApplication.put("status", "pending");
        eventApplication.put("applicationDate", FieldValue.serverTimestamp());

        db.runBatch(batch -> {
            batch.set(db.collection("users").document(volunteerUid).collection("appliedToEvents").document(eventId), userApplication);
            batch.set(db.collection("events").document(eventId).collection("applicants").document(volunteerUid), eventApplication);
            batch.update(db.collection("events").document(eventId), "applicantsCount", FieldValue.increment(1)); // Also increment count
        }).addOnCompleteListener(task -> {
            _isLoading.setValue(false);
            if (task.isSuccessful()) {
                Log.d(TAG, "Successfully applied for event: " + event.getTitle());
                _applySuccessMessage.setValue(getApplication().getString(R.string.application_submitted));
            } else {
                Log.e(TAG, "Failed to apply for event", task.getException());
                String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                _applyErrorMessage.setValue(getApplication().getString(R.string.error_submitting_application, errorMsg));
            }
        });
    }

    // Helper methods to clear one-time event messages
    public void clearApplySuccessMessage() {
        _applySuccessMessage.setValue(null);
    }
    public void clearApplyErrorMessage() {
        _applyErrorMessage.setValue(null);
    }
}
