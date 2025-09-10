package com.example.volunteersApp.employer.ui.home;

import android.os.Bundle;
import android.util.Log; // For logging navigation errors, if any
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
// Removed unused TextView import if binding is always used
// import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
// Removed unused ContextCompat import
// import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider; // Keep for potential ViewModel use
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
// Consider using NavOptions if you need to customize navigation behavior (e.g., animations, popUpTo)
// import androidx.navigation.NavOptions;

import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentEmployerHomeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmployerHomeFragment extends Fragment {

    private static final String TAG = "EmployerHomeFragment"; // For logging

    private FragmentEmployerHomeBinding binding;
    private NavController navController;
    private FirebaseAuth mAuth;
    // private EmployerHomeViewModel employerHomeViewModel; // Uncomment if you create this ViewModel

    public EmployerHomeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        // Initialize ViewModel if using one
        // employerHomeViewModel = new ViewModelProvider(this).get(EmployerHomeViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEmployerHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // It's good practice to get the NavController associated with this fragment's view
        navController = Navigation.findNavController(view);

        setupToolbarTitle();
        displayWelcomeMessage();
        setupActionButtons();
        // observeViewModel(); // If using a ViewModel
    }

    private void setupToolbarTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(R.string.title_employer_home);
            }
        }
    }

    private void displayWelcomeMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();

            if (displayName != null && !displayName.isEmpty()) {
                // Ensure R.string.welcome_user is defined as "Welcome, %1$s!"
                binding.textViewEmployerWelcome.setText(getString(R.string.welcome_user, displayName));
            } else if (email != null && !email.isEmpty()) {
                // You might want a different format or the same R.string.welcome_user
                binding.textViewEmployerWelcome.setText(getString(R.string.welcome_user, email));
            } else {
                // Ensure R.string.welcome_employer_placeholder is defined
                binding.textViewEmployerWelcome.setText(R.string.welcome_employer_placeholder);
            }
        } else {
            // This case should ideally not happen if EmployerMainActivity checks for logged-in user,
            // but good to have a fallback or log an error.
            binding.textViewEmployerWelcome.setText(R.string.welcome_employer_placeholder);
            Log.w(TAG, "Current user is null in EmployerHomeFragment.");
        }
    }

    private void setupActionButtons() {
        binding.buttonEmployerPostNewJob.setOnClickListener(v -> navigateTo(R.id.nav_employer_post_job));
        binding.buttonEmployerViewPostedJobs.setOnClickListener(v -> navigateTo(R.id.nav_employer_posted_jobs));
        binding.buttonEmployerViewApplications.setOnClickListener(v -> navigateTo(R.id.nav_employer_applications));
        binding.buttonEmployerManageProfile.setOnClickListener(v -> navigateTo(R.id.nav_employer_profile_management));
    }

    // Helper method for navigation to reduce boilerplate and add potential error handling
    private void navigateTo(int destinationId) {
        try {
            // Example of using NavOptions if you want to add animations or specific behaviors:
            // NavOptions navOptions = new NavOptions.Builder()
            // .setEnterAnim(R.anim.slide_in_right)
            // .setExitAnim(R.anim.slide_out_left)
            // .setPopEnterAnim(R.anim.slide_in_left)
            // .setPopExitAnim(R.anim.slide_out_right)
            // .build();
            // navController.navigate(destinationId, null, navOptions);
            navController.navigate(destinationId);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Navigation failed: Destination ID " + destinationId + " not found.", e);
            // Optionally show a Toast to the user or handle the error appropriately
            // Toast.makeText(getContext(), "Error navigating. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    /*
    private void observeViewModel() {
        if (employerHomeViewModel != null) {
            employerHomeViewModel.getWelcomeTextLiveData().observe(getViewLifecycleOwner(), text -> {
                binding.textViewEmployerWelcome.setText(text);
            });
            // Observe other LiveData from ViewModel
        }
    }
    */

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Crucial for preventing memory leaks
    }
}

