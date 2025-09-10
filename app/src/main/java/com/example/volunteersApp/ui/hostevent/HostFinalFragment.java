package com.example.volunteersApp.ui.hostevent;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentHostFinalBinding;

// Import FirebaseFirestore and DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException; // Good to be aware of this specific exception

public class HostFinalFragment extends Fragment {

    private static final String TAG = "HostFinalFragment";

    private FragmentHostFinalBinding binding;
    private NavController navController;

    private String eventIdArg;
    private String eventNameArg;

    public HostFinalFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventIdArg = HostFinalFragmentArgs.fromBundle(getArguments()).getEventId();
            eventNameArg = HostFinalFragmentArgs.fromBundle(getArguments()).getEventTitle();
            Log.d(TAG, "Received EventId: " + eventIdArg + ", EventName: " + eventNameArg);
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToMyEventsOrHome();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHostFinalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        setupToolbar();
        setupConfirmationMessages();
        setupButtonListeners();

        // If eventNameArg is not passed or is empty, try fetching it
        if ((eventNameArg == null || eventNameArg.isEmpty()) && eventIdArg != null && !eventIdArg.isEmpty()) {
            fetchEventNameAndDisplay(eventIdArg);
        }
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(binding.toolbarHostFinalFragment);

            // Configure AppBar with NavController
            // Ensure your graph is correctly set up for this
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
            NavigationUI.setupWithNavController(binding.toolbarHostFinalFragment, navController, appBarConfiguration);

            ActionBar actionBar = activity.getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(getString(R.string.host_final_activity_title));
                // Optional: Decide if "Up" button should be shown
                // By default, NavigationUI might show it if not a top-level destination.
                // To always hide:
                // actionBar.setDisplayHomeAsUpEnabled(false);
                // actionBar.setDisplayShowHomeEnabled(false);
            }
        }
    }

    private void setupConfirmationMessages() {
        binding.textViewConfirmationMessageMain.setText(getString(R.string.successfully_hosted_main_title));

        if (eventNameArg != null && !eventNameArg.isEmpty()) {
            binding.textViewConfirmationMessageDetail.setText(getString(R.string.host_final_congrats_with_event_name, eventNameArg));
        } else if (eventIdArg != null && !eventIdArg.isEmpty()) {
            // Initially set with ID, will be updated by fetchEventNameAndDisplay if name is found
            binding.textViewConfirmationMessageDetail.setText(getString(R.string.host_final_congrats_with_event_id, eventIdArg));
        } else {
            binding.textViewConfirmationMessageDetail.setText(getString(R.string.host_final_congrats_generic));
        }
    }

    private void setupButtonListeners() {
        binding.buttonGoToMyEvents.setOnClickListener(v -> navigateToMyEventsOrHome());

        binding.buttonHostAnotherEventFinal.setOnClickListener(v -> {
            try {
                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.nav_host_event, true) // Pop to and include nav_host_event to remove it when navigating to it again
                        .build();
                // Ensure R.id.nav_host_event is the correct ID for your initial hosting fragment.
                navController.navigate(R.id.nav_host_event, null, navOptions);
            } catch (Exception e) {
                Log.e(TAG, "Failed to navigate to Host Another Event (nav_host_event)", e);
                Toast.makeText(getContext(), "Could not navigate to Host Event form", Toast.LENGTH_SHORT).show();
                navigateToHome();
            }
        });
    }

    private void navigateToMyEventsOrHome() {
        try {
            // Make sure R.id.nav_my_events is the correct ID for your "My Events" destination
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), false) // Pop up to the start destination of the graph (e.g. home)
                    .setLaunchSingleTop(true) // Avoid multiple copies of MyEvents if already there
                    .build();
            navController.navigate(R.id.nav_my_events, null, navOptions);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "'My Events' (R.id.nav_my_events) destination not found or issue navigating, falling back to Home.", e);
            navigateToHome();
        }
    }

    private void navigateToHome() {
        try {
            // Ensure R.id.nav_home is the correct ID for your home/start destination
            NavOptions navOptions = new NavOptions.Builder()
                    .setPopUpTo(R.id.nav_home, true) // Pop to and include nav_home, clearing stack above it
                    .build();
            navController.navigate(R.id.nav_home, null, navOptions);
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate to Home (R.id.nav_home)", e);
            Toast.makeText(getContext(), "Error navigating to home screen.", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchEventNameAndDisplay(String eventId) {
        if (eventId == null || eventId.isEmpty() || getContext() == null) {
            Log.w(TAG, "fetchEventNameAndDisplay: Invalid eventId or context is null.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("events").document(eventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    // Check binding and context again, as fragment might be destroyed during async call
                    if (binding == null || getContext() == null || !isAdded()) {
                        return;
                    }
                    if (documentSnapshot.exists()) {
                        // Ensure "eventName" is the correct field name in your Firestore document
                        String eventNameFromDb = documentSnapshot.getString("eventName");
                        if (eventNameFromDb != null && !eventNameFromDb.isEmpty()) {
                            binding.textViewConfirmationMessageDetail.setText(
                                    getString(R.string.host_final_congrats_with_event_name, eventNameFromDb));
                        } else {
                            Log.w(TAG, "Event name field is missing or empty in Firestore for ID: " + eventId);
                            // Fallback if name is not in DB but ID is known
                            binding.textViewConfirmationMessageDetail.setText(
                                    getString(R.string.host_final_congrats_with_event_id, eventId));
                        }
                    } else {
                        Log.w(TAG, "Event document not found in Firestore for ID: " + eventId);
                        binding.textViewConfirmationMessageDetail.setText(
                                getString(R.string.host_final_congrats_with_event_id, eventId));
                    }
                })
                .addOnFailureListener(e -> { // 'e' is correctly typed as Exception here
                    // Check binding and context again
                    if (binding == null || getContext() == null || !isAdded()) {
                        return;
                    }
                    // The 'e' parameter in the lambda is an Exception, which is a Throwable.
                    // So, it can be directly passed to Log.e.
                    Log.e(TAG, "Error fetching event details for ID: " + eventId, e);
                    binding.textViewConfirmationMessageDetail.setText(
                            getString(R.string.host_final_congrats_with_event_id, eventId));
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}



