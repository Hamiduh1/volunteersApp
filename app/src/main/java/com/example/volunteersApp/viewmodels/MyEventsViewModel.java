/** The ViewModel needs to be enhanced. It should fetch all
 * of the user's events at once and then provide a LiveData<List<EventModel>>
 *     that is filtered based on the currently selected tab.
 *  Efficient Data Loading: The ViewModel now fetches all relevant data in one go
 *  and filters it on the device, which is much faster for the user when
 *  switching tabs.2.Simplified UI: The layout no longer needs a ViewPager2
 *  or AppBarLayout. A simple TabLayout and RecyclerView are cleaner and more
 *  performant.3.Centralized Logic: All business logic
 *  (fetching, filtering, withdrawing) now resides in the MyEventsViewModel,
 *  making the MyEventsFragment a simpler UI controller. This is excellent
 *  for testing and maintenance.
 *     **/

// PATH: C:/Users/16147/volunteersApp2/VolunteersApp/app/src/main/java/com/example/volunteersApp/viewmodels/MyEventsViewModel.java

package com.example.volunteersApp.viewmodels;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.volunteersApp.models.EventModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyEventsViewModel extends ViewModel {

    private static final String TAG = "MyEventsViewModel";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // The single source of truth for all events the user has interacted with.
    private final MutableLiveData<List<EventModel>> allMyEvents = new MutableLiveData<>();

    // The current filter being applied (e.g., APPLIED, UPCOMING, PAST).
    public enum EventFilter { APPLIED, UPCOMING, PAST }
    private final MutableLiveData<EventFilter> currentFilter = new MutableLiveData<>(EventFilter.APPLIED);

    // The publicly exposed LiveData that combines `allMyEvents` and `currentFilter`.
    // This is the list the Fragment will observe.
    private final MediatorLiveData<List<EventModel>> filteredEvents = new MediatorLiveData<>();

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public MyEventsViewModel() {
        // This is where the magic happens. Whenever the master list or the filter changes,
        // this observer will re-filter the list and update `filteredEvents`.
        filteredEvents.addSource(allMyEvents, events -> applyFilter());
        filteredEvents.addSource(currentFilter, filter -> applyFilter());
        fetchMyEventsData(); // Initial fetch
    }

    // Public getters for the Fragment to observe
    public LiveData<List<EventModel>> getFilteredEvents() {
        return filteredEvents;
    }
    public LiveData<Boolean> isLoading() {
        return isLoading;
    }
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    // Called by the Fragment when a tab is clicked
    public void setFilter(EventFilter filter) {
        currentFilter.setValue(filter);
    }

    private void applyFilter() {
        List<EventModel> allEvents = allMyEvents.getValue();
        EventFilter filter = currentFilter.getValue();
        if (allEvents == null || filter == null) {
            return;
        }

        Log.d(TAG, "Applying filter: " + filter);
        ArrayList<EventModel> result = new ArrayList<>();
        Date now = new Date();

        for (EventModel event : allEvents) {
            boolean isUpcoming = event.getEventDateTime() != null && event.getEventDateTime().toDate().after(now);
            // Status is stored in a map on the event now, which we need to check
            String userStatus = event.getUserStatus(); // Assumes a helper field on the model

            switch (filter) {
                case APPLIED:
                    if ("pending".equalsIgnoreCase(userStatus) && isUpcoming) {
                        result.add(event);
                    }
                    break;
                case UPCOMING:
                    if ("approved".equalsIgnoreCase(userStatus) && isUpcoming) {
                        result.add(event);
                    }
                    break;
                case PAST:
                    if ("approved".equalsIgnoreCase(userStatus) && !isUpcoming) {
                        result.add(event);
                    }
                    break;
            }
        }
        filteredEvents.setValue(result);
    }

    public void fetchMyEventsData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("User not logged in.");
            return;
        }
        isLoading.setValue(true);
        String volunteerId = currentUser.getUid();

        // 1. Get all application documents for the user
        db.collectionGroup("applications").whereEqualTo("volunteerUid", volunteerId)
                .get()
                .addOnSuccessListener(applicationSnapshots -> {
                    if (applicationSnapshots.isEmpty()) {
                        Log.d(TAG, "User has no applications.");
                        allMyEvents.setValue(new ArrayList<>());
                        isLoading.setValue(false);
                        return;
                    }

                    List<Task<DocumentSnapshot>> eventTasks = new ArrayList<>();
                    for (QueryDocumentSnapshot appDoc : applicationSnapshots) {
                        String eventId = appDoc.getString("jobPostId"); // Or "eventId"
                        if (eventId != null && !eventId.isEmpty()) {
                            // 2. For each application, create a task to fetch the corresponding event document
                            eventTasks.add(db.collection("events").document(eventId).get());
                        }
                    }

                    // 3. When all event fetching tasks are complete...
                    Tasks.whenAllSuccess(eventTasks).addOnSuccessListener(eventDocuments -> {
                        List<EventModel> events = new ArrayList<>();
                        for (Object docObj : eventDocuments) {
                            DocumentSnapshot eventDoc = (DocumentSnapshot) docObj;
                            if (eventDoc.exists()) {
                                EventModel event = eventDoc.toObject(EventModel.class);
                                if (event != null) {
                                    // 4. Find the matching application to get the user's status for this event
                                    for(QueryDocumentSnapshot appDoc : applicationSnapshots){
                                        String eventId = appDoc.getString("jobPostId"); // Or "eventId"
                                        if(eventDoc.getId().equals(eventId)){
                                            event.setUserStatus(appDoc.getString("status")); // Set status on the model
                                            break;
                                        }
                                    }
                                    events.add(event);
                                }
                            }
                        }
                        Log.d(TAG, "Successfully fetched " + events.size() + " events.");
                        allMyEvents.setValue(events); // Update the master list
                        isLoading.setValue(false);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to fetch one or more event details", e);
                        errorMessage.setValue("Error loading event details.");
                        isLoading.setValue(false);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user applications", e);
                    errorMessage.setValue("Error loading your applications.");
                    isLoading.setValue(false);
                });
    }

    public void withdrawFromEvent(String eventId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            errorMessage.setValue("You must be logged in.");
            return;
        }
        String volunteerId = currentUser.getUid();
        isLoading.setValue(true);

        // Find the specific application document to delete
        db.collection("events").document(eventId)
                .collection("applications")
                .whereEqualTo("volunteerUid", volunteerId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        errorMessage.setValue("Could not find your application to withdraw.");
                        isLoading.setValue(false);
                        return;
                    }
                    DocumentSnapshot appDoc = queryDocumentSnapshots.getDocuments().get(0);
                    String applicationId = appDoc.getId();

                    // Use a batch write to perform multiple operations atomically
                    WriteBatch batch = db.batch();

                    // 1. Delete the application from the event's subcollection
                    batch.delete(db.collection("events").document(eventId).collection("applications").document(applicationId));

                    // 2. Delete the application reference from the user's own record
                    batch.delete(db.collection("users").document(volunteerId).collection("appliedToEvents").document(eventId));

                    // 3. Decrement the volunteer count on the main event document
                    batch.update(db.collection("events").document(eventId), "slotsFilled", FieldValue.increment(-1));

                    batch.commit().addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully withdrew from event: " + eventId);
                        // Refetch all data to ensure UI is perfectly in sync
                        fetchMyEventsData();
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to commit withdrawal batch", e);
                        errorMessage.setValue("Failed to withdraw application.");
                        isLoading.setValue(false);
                    });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to find application to withdraw", e);
                    errorMessage.setValue("Error finding your application.");
                    isLoading.setValue(false);
                });
    }
}

