package com.example.volunteersApp.viewmodels;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.EventModel; // Use the standardized EventModel
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    private final MutableLiveData<String> welcomeMessage = new MutableLiveData<>();
    private final MutableLiveData<List<EventModel>> upcomingEvents = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        loadData();
    }

    public LiveData<String> getWelcomeMessage() {
        return welcomeMessage;
    }

    public LiveData<List<EventModel>> getUpcomingEvents() {
        return upcomingEvents;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    private void loadData() {
        loadWelcomeMessage();
        loadUpcomingEvents();
    }

    private void loadWelcomeMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String message;
            if (displayName != null && !displayName.isEmpty()) {
                message = getApplication().getString(R.string.welcome_message_user, displayName);
            } else {
                message = getApplication().getString(R.string.welcome_to_volunteers_app) + "!";
            }
            welcomeMessage.setValue(message);
        } else {
            welcomeMessage.setValue(getApplication().getString(R.string.welcome_to_volunteers_app));
        }
    }

    public void loadUpcomingEvents() {
        isLoading.setValue(true);
        db.collection("events")
                .whereGreaterThanOrEqualTo("eventDateTime", Timestamp.now())
                .orderBy("eventDateTime", Query.Direction.ASCENDING)
                .limit(5)
                .get()
                .addOnCompleteListener(task -> {
                    isLoading.setValue(false);
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<EventModel> events = task.getResult().toObjects(EventModel.class);
                        upcomingEvents.setValue(events);
                        if (events.isEmpty()) {
                            Log.d(TAG, "No upcoming events found.");
                        }
                    } else {
                        Log.e(TAG, "Error loading upcoming events: ", task.getException());
                        errorMessage.setValue(getApplication().getString(R.string.error_loading_events));
                    }
                });
    }
}
