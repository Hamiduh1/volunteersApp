/**  This is the most important change. Instead of fetching data
 * just once, the ViewModel will now open a real-time listener to the event
 * document in Firestore. Any change an organizer
 * makes will be instantly pushed to the ViewModel, which then updates the UI.
 * Added ListenerRegistration: A new variable eventListenerRegistration is added
 * to hold a reference to our real-time listener so we can detach it later.**/

// PATH: app/src/main/java/com/example/volunteersApp/viewmodels/EventDetailViewModel.java

package com.example.volunteersApp.viewmodels;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.EventDetails;
import com.google.firebase.auth.FirebaseAuth; // ADDED
import com.google.firebase.auth.FirebaseUser; // ADDED
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration; // ADDED
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.List;

public class EventDetailViewModel extends AndroidViewModel {
    private static final String TAG = "EventDetailViewModel";
    private static final String EVENTS_COLLECTION = "events";

    private final FirebaseFirestore db;
    private final FirebaseStorage storage;
    private final FirebaseAuth mAuth; // ADDED

    // ADDED: To manage and remove the real-time listener
    private ListenerRegistration eventListenerRegistration;

    private final MutableLiveData<EventDetails> _eventDetails = new MutableLiveData<>();
    public LiveData<EventDetails> getEventDetails() { return _eventDetails; }

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsLoading() { return _isLoading; }

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> getErrorMessage() { return _errorMessage; }

    private final MutableLiveData<Boolean> _isOrganizer = new MutableLiveData<>(false);
    public LiveData<Boolean> getIsOrganizer() { return _isOrganizer; }

    public enum ApplicationStatus { UNKNOWN, CAN_APPLY, APPLIED, ACCEPTED, NOT_APPLIED_CLOSED }
    private final MutableLiveData<ApplicationStatus> _volunteerApplicationStatus = new MutableLiveData<>(ApplicationStatus.UNKNOWN);
    public LiveData<ApplicationStatus> getVolunteerApplicationStatus() { return _volunteerApplicationStatus; }

    private final MutableLiveData<Boolean> _eventDeleteSuccess = new MutableLiveData<>();
    public LiveData<Boolean> getEventDeleteSuccess() { return _eventDeleteSuccess; }


    public EventDetailViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        mAuth = FirebaseAuth.getInstance(); // ADDED
    }

    /**
     * MODIFIED: This function now attaches a real-time listener to the event document.
     * It will automatically receive updates whenever the data changes in Firestore.
     */
    public void listenForEventDetails(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            _errorMessage.setValue(getApplication().getString(R.string.event_id_missing));
            return;
        }

        // Remove any existing listener before attaching a new one.
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove();
        }

        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        DocumentReference eventRef = db.collection(EVENTS_COLLECTION).document(eventId);

        // MODIFIED: Use .addSnapshotListener for real-time updates instead of .get()
        eventListenerRegistration = eventRef.addSnapshotListener((snapshot, error) -> {
            _isLoading.setValue(false); // Stop loading indicator after first update

            if (error != null) {
                Log.e(TAG, "Listen failed for event: " + eventId, error);
                _errorMessage.setValue("Error: " + error.getMessage());
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                EventDetails event = snapshot.toObject(EventDetails.class);
                if (event != null) {
                    event.setEventId(snapshot.getId());
                    _eventDetails.setValue(event);
                    // Every time the event data changes, re-evaluate the user's roles and status
                    checkUserRolesAndStatus(event);
                } else {
                    _errorMessage.setValue(getApplication().getString(R.string.error_parsing_event_data));
                }
            } else {
                _errorMessage.setValue(getApplication().getString(R.string.event_not_found_error));
                _eventDetails.setValue(null); // Clear data if event is deleted
            }
        });
    }

    /**
     * MODIFIED: This helper is now called automatically whenever the listener receives an update.
     */
    private void checkUserRolesAndStatus(EventDetails event) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || event == null) {
            _isOrganizer.setValue(false);
            _volunteerApplicationStatus.setValue(ApplicationStatus.UNKNOWN);
            return;
        }

        String currentUserId = currentUser.getUid();
        String organizerId = event.getOrganizerId();
        List<String> appliedIds = event.getAppliedVolunteerUids();
        List<String> acceptedIds = event.getAcceptedVolunteerUids();

        if (currentUserId.equals(organizerId)) {
            _isOrganizer.setValue(true);
            _volunteerApplicationStatus.setValue(ApplicationStatus.UNKNOWN); // Not relevant for organizer
        } else {
            _isOrganizer.setValue(false);
            if (acceptedIds != null && acceptedIds.contains(currentUserId)) {
                _volunteerApplicationStatus.setValue(ApplicationStatus.ACCEPTED);
            } else if (appliedIds != null && appliedIds.contains(currentUserId)) {
                _volunteerApplicationStatus.setValue(ApplicationStatus.APPLIED);
            } else if (event.isOpenForApplication()) {
                _volunteerApplicationStatus.setValue(ApplicationStatus.CAN_APPLY);
            } else {
                _volunteerApplicationStatus.setValue(ApplicationStatus.NOT_APPLIED_CLOSED);
            }
        }
    }

    /**
     * ADDED: This is crucial for preventing memory leaks.
     * When the ViewModel is destroyed, we must remove the Firestore listener.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (eventListenerRegistration != null) {
            eventListenerRegistration.remove();
            Log.d(TAG, "Event listener removed in onCleared.");
        }
    }

    // --- Other methods (apply, withdraw, delete) remain the same ---
    // They will trigger the listener automatically after they update the document.
    // The fetchEventDetails() calls inside them are now redundant but harmless.

    public void applyForEvent(String eventId, String userId) {
        if (eventId == null || userId == null) return;
        db.collection(EVENTS_COLLECTION).document(eventId)
                .update("appliedVolunteerUids", FieldValue.arrayUnion(userId))
                .addOnFailureListener(e -> {
                    _errorMessage.setValue(getApplication().getString(R.string.apply_failed) + ": " + e.getMessage());
                    Log.e(TAG, "Error applying for event", e);
                });
    }

    public void withdrawFromEvent(String eventId, String userId) {
        if (eventId == null || userId == null) return;
        db.collection(EVENTS_COLLECTION).document(eventId)
                .update("appliedVolunteerUids", FieldValue.arrayRemove(userId),
                        "acceptedVolunteerUids", FieldValue.arrayRemove(userId))
                .addOnFailureListener(e -> {
                    _errorMessage.setValue(getApplication().getString(R.string.withdraw_failed) + ": " + e.getMessage());
                    Log.e(TAG, "Error withdrawing from event", e);
                });
    }

    // deleteEvent and deleteImageFromStorage methods are unchanged.
    public void deleteEvent(EventDetails eventToDelete) { /* ... same as before ... */ }
    private void deleteImageFromStorage(String imageUrl, String eventIdForContext, boolean isEventDocDeleted) { /* ... same as before ... */ }
}
