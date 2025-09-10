package com.example.volunteersApp.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
// import androidx.core.content.ContextCompat; // Not explicitly used in this version
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentHomeBinding;
import com.example.volunteersApp.models.EventDetails; // Assuming this is your event model for the RecyclerView
import com.example.volunteersApp.adapters.UpcomingEventsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
// HomeFragmentDirections will be used if specific actions are defined for organizers.
// Ensure it's generated after nav graph changes.
// import com.example.volunteersApp.ui.home.HomeFragmentDirections;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private FragmentHomeBinding binding; // Use _binding pattern if preferred
    private NavController navController;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private UpcomingEventsAdapter upcomingEventsAdapter;
    private List<EventDetails> upcomingEventList;

    private static final String USERS_COLLECTION = "users";
    private static final String FIELD_ROLE = "role";
    private static final String ROLE_ORGANIZER = "organizer";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        upcomingEventList = new ArrayList<>();

        setupUpcomingEventsRecyclerView();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        setupWelcomeMessage();
        // setupButtonListeners(); // Listeners will be set after checking user role
        checkUserRoleAndSetupUI();
        loadUpcomingEvents();
    }

    private void setupWelcomeMessage() {
        if (binding == null) return;
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String welcomeMessage;
            if (displayName != null && !displayName.isEmpty()) {
                welcomeMessage = getString(R.string.welcome_message_user, displayName);
            } else {
                welcomeMessage = getString(R.string.welcome_to_volunteers_app) + "!";
            }
            binding.textViewWelcome.setText(welcomeMessage);
        } else {
            binding.textViewWelcome.setText(getString(R.string.welcome_to_volunteers_app));
        }
    }

    private void checkUserRoleAndSetupUI() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            DocumentReference userDocRef = db.collection(USERS_COLLECTION).document(currentUser.getUid());
            userDocRef.get().addOnCompleteListener(task -> {
                if (!isAdded() || binding == null) {
                    return; // Fragment not active or view destroyed
                }
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        String userRole = document.getString(FIELD_ROLE);
                        if (ROLE_ORGANIZER.equalsIgnoreCase(userRole)) {
                            // User is an organizer
                            binding.buttonHostEvent.setVisibility(View.VISIBLE);
                            Log.d(TAG, "User is an ORGANIZER. Host Event button visible.");
                        } else {
                            // User is a volunteer or role not defined as organizer
                            binding.buttonHostEvent.setVisibility(View.GONE);
                            Log.d(TAG, "User is a VOLUNTEER or role undefined. Host Event button hidden.");
                        }
                    } else {
                        // User document doesn't exist, assume volunteer for safety
                        binding.buttonHostEvent.setVisibility(View.GONE);
                        Log.d(TAG, "User document does not exist. Host Event button hidden.");
                    }
                } else {
                    // Error fetching role, assume volunteer for safety
                    binding.buttonHostEvent.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to get user role: ", task.getException());
                }
                // Setup general button listeners after role check (if some are always visible)
                setupCommonButtonListeners();
                setupHostEventListener(); // Setup host event listener which might depend on visibility
            });
        } else {
            // No user logged in, hide organizer-specific features
            if (binding != null) {
                binding.buttonHostEvent.setVisibility(View.GONE);
            }
            setupCommonButtonListeners(); // Setup common buttons
        }
    }


    private void setupCommonButtonListeners() {
        if (binding == null || navController == null) return;

        binding.buttonBrowseAllEvents.setOnClickListener(v -> {
            try {
                // Assuming R.id.action_nav_home_to_nav_volunteering_events is the correct action ID
                // from nav_home to your general event browsing screen.
                // This corresponds to <action android:id="@+id/action_nav_home_to_nav_volunteering_events" ... />
                navController.navigate(R.id.action_nav_home_to_nav_volunteering_events);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Navigation action 'action_nav_home_to_nav_volunteering_events' not found.", e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.error_navigating_all_events, Toast.LENGTH_SHORT).show();
                }
            }
        });
        // Add other common button listeners here
    }

    private void setupHostEventListener() {
        if (binding == null || navController == null) return;

        // This listener is only relevant if the button is visible (i.e., for organizers)
        binding.buttonHostEvent.setOnClickListener(v -> {
            // Ensure you have an action defined in your mobile_navigation.xml
            // specifically for ORGANIZERS from home to host_event.
            // Example: <action android:id="@+id/action_nav_home_organizer_to_nav_host_event" app:destination="@id/nav_host_event"/>
            // If you are using Safe Args, it would be HomeFragmentDirections.actionNavHomeOrganizerToNavHostEvent()
            try {
                // Replace with your actual action ID for organizers
                NavDirections action = HomeFragmentDirections.actionNavHomeOrganizerToNavHostEvent();
                navController.navigate(action);
            } catch (Exception e) { // Catch general exceptions for navigation for Safe Args
                Log.e(TAG, "Navigation action for 'Host Event' (organizer path) not found or error. Check mobile_navigation.xml and HomeFragmentDirections.", e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.error_navigating_host_event, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


    private void setupUpcomingEventsRecyclerView() {
        if (binding == null || getContext() == null) return;

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerViewUpcomingEvents.setLayoutManager(layoutManager);

        upcomingEventsAdapter = new UpcomingEventsAdapter(upcomingEventList, event -> {
            if (binding == null || navController == null) return; // Re-check as click is async
            if (event.getEventId() == null || event.getEventId().isEmpty()) {
                Log.e(TAG, "Clicked event has no ID. Cannot navigate.");
                if(getContext() != null) {
                    Toast.makeText(getContext(), R.string.error_event_id_missing, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            try {
                // Ensure HomeFragmentDirections and the action are correctly generated
                HomeFragmentDirections.ActionNavHomeToEventDetailFragment action =
                        HomeFragmentDirections.actionNavHomeToEventDetailFragment(event.getEventId());
                navController.navigate(action);
            } catch (Exception e) {
                Log.e(TAG, "Failed to navigate to event details for event ID: " + event.getEventId(), e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.error_navigating_event_details, Toast.LENGTH_SHORT).show();
                }
            }
        });
        binding.recyclerViewUpcomingEvents.setAdapter(upcomingEventsAdapter);
    }

    private void loadUpcomingEvents() {
        if (binding == null) return;
        setLoadingState(true);
        binding.textViewNoUpcomingEvents.setVisibility(View.GONE);

        Timestamp now = Timestamp.now();

        db.collection("events")
                .whereGreaterThanOrEqualTo("eventDateTime", now) // Ensure field name matches EventModel
                .orderBy("eventDateTime", Query.Direction.ASCENDING)
                .limit(5)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    setLoadingState(false);

                    if (task.isSuccessful() && task.getResult() != null) {
                        upcomingEventList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            EventDetails event = document.toObject(EventDetails.class);
                            event.setEventId(document.getId());
                            upcomingEventList.add(event);
                        }
                        if (upcomingEventsAdapter != null) { // Check adapter before notifying
                            upcomingEventsAdapter.notifyDataSetChanged();
                        }

                        if (upcomingEventList.isEmpty()) {
                            binding.textViewNoUpcomingEvents.setText(R.string.no_upcoming_events);
                            binding.textViewNoUpcomingEvents.setVisibility(View.VISIBLE);
                            binding.recyclerViewUpcomingEvents.setVisibility(View.GONE);
                        } else {
                            binding.textViewNoUpcomingEvents.setVisibility(View.GONE);
                            binding.recyclerViewUpcomingEvents.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Log.e(TAG, "Error loading upcoming events: ", task.getException());
                        binding.textViewNoUpcomingEvents.setText(R.string.error_loading_events);
                        binding.textViewNoUpcomingEvents.setVisibility(View.VISIBLE);
                        binding.recyclerViewUpcomingEvents.setVisibility(View.GONE);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.error_loading_events, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setLoadingState(boolean isLoading) {
        if (binding != null) {
            binding.progressBarHome.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.buttonBrowseAllEvents.setEnabled(!isLoading);
            // Host event button's enabled state is also tied to its visibility (role)
            if (binding.buttonHostEvent.getVisibility() == View.VISIBLE) {
                binding.buttonHostEvent.setEnabled(!isLoading);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Crucial for ViewBinding to avoid memory leaks
    }
}
