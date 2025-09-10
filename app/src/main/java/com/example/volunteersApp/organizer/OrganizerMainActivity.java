package com.example.volunteersApp.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.LoginActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.ActivityOrganizerMainBinding;
// No need to import individual fragment classes here if NavController handles instantiation.

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashSet;
import java.util.Set;

public class OrganizerMainActivity extends AppCompatActivity {
    // Removed 'implements NavigationView.OnNavigationItemSelectedListener'
    // as NavigationUI will handle it for destinations in the graph.
    // We'll add custom handling for items not in the graph if needed.

    private static final String TAG = "OrganizerMainActivity";
    private ActivityOrganizerMainBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private DrawerLayout drawerLayout;
    // private ActionBarDrawerToggle toggle; // toggle will be managed by NavigationUI with AppBarConfiguration

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOrganizerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        setSupportActionBar(binding.toolbar);

        if (currentUser == null) {
            Log.w(TAG, "No user found. Redirecting to LoginActivity.");
            redirectToLogin();
            return;
        }

        drawerLayout = binding.organizerDrawerLayout;

        // --- Setup Navigation Component ---
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.organizer_fragment_container);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Define top-level destinations. These are destinations where the
            // drawer icon will show instead of an Up arrow.
            Set<Integer> topLevelDestinations = new HashSet<>();
            topLevelDestinations.add(R.id.nav_organizer_home);
            topLevelDestinations.add(R.id.nav_hosted_events);
            topLevelDestinations.add(R.id.nav_organizer_applications);
            topLevelDestinations.add(R.id.nav_organizer_activity_summary);
            topLevelDestinations.add(R.id.nav_organizer_profile);

            appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations)
                    .setOpenableLayout(drawerLayout) // Use setOpenableLayout for DrawerLayout
                    .build();

            // Setup ActionBar with NavController and AppBarConfiguration
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            // Setup BottomNavigationView with NavController
            if (binding.organizerBottomNavigation != null) {
                NavigationUI.setupWithNavController(binding.organizerBottomNavigation, navController);
            }

            // Setup NavigationView (Drawer) with NavController
            if (binding.organizerNavView != null) {
                NavigationUI.setupWithNavController(binding.organizerNavView, navController);
                // Add custom listener for items NOT handled by NavController (e.g., logout, create event if it's special)
                binding.organizerNavView.setNavigationItemSelectedListener(item -> {
                    // Let NavigationUI try to handle it first
                    boolean handledByNavigationUI = NavigationUI.onNavDestinationSelected(item, navController);
                    if (handledByNavigationUI) {
                        if (drawerLayout != null) {
                            drawerLayout.closeDrawer(GravityCompat.START);
                        }
                        return true;
                    }

                    // Handle custom actions for drawer items not in the nav graph
                    int id = item.getItemId();
                    if (id == R.id.nav_drawer_create_event) {
                        // TODO: Navigate to your Create Event screen.
                        // If Create Event is a destination in your organizer_nav_graph:
                        // navController.navigate(R.id.your_create_event_destination_id_or_action_id);
                        // Example: If nav_host_event_organizer_side is your create event screen from anywhere
                        navController.navigate(R.id.nav_host_event_organizer_side); // Or pass an action ID
                        Toast.makeText(this, "Create Event (Drawer) Clicked", Toast.LENGTH_SHORT).show();
                    } else if (id == R.id.nav_drawer_messages) {
                        Toast.makeText(this, "Messages (Drawer) Clicked", Toast.LENGTH_SHORT).show();
                        // TODO: Implement Messages navigation/action
                    } else if (id == R.id.nav_drawer_notifications) {
                        Toast.makeText(this, "Notifications (Drawer) Clicked", Toast.LENGTH_SHORT).show();
                        // TODO: Implement Notifications navigation/action
                    } else if (id == R.id.nav_drawer_settings) {
                        Toast.makeText(this, "Settings (Drawer) Clicked", Toast.LENGTH_SHORT).show();
                        // TODO: Implement Settings navigation/action
                    } else if (id == R.id.nav_drawer_logout) {
                        promptLogout();
                    }

                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                    return true; // Return true if you've handled the item
                });
            }
        } else {
            Log.e(TAG, "NavHostFragment not found! Navigation will not work.");
            Toast.makeText(this, "Critical error: Navigation setup failed.", Toast.LENGTH_LONG).show();
            // Consider finishing the activity or showing a more permanent error state
        }
        // --- End Navigation Component Setup ---

        loadOrganizerDataForNavHeader();

        // No need for manual fragment loading if startDestination is set in navGraph
        // if (savedInstanceState == null) {
        //     loadFragment(OrganizerDashboardFragment.newInstance(), OrganizerDashboardFragment.class.getSimpleName());
        //     if (binding.organizerBottomNavigation != null) {
        //         binding.organizerBottomNavigation.setSelectedItemId(R.id.nav_organizer_home);
        //     }
        // }
    }

    public void loadOrganizerDataForNavHeader() {
        if (currentUser != null && binding.organizerNavView != null && binding.organizerNavView.getHeaderCount() > 0) {
            View headerView = binding.organizerNavView.getHeaderView(0);
            TextView navHeaderName = headerView.findViewById(R.id.organizer_nav_header_name);
            TextView navHeaderEmail = headerView.findViewById(R.id.organizer_nav_header_email);

            if (navHeaderEmail != null) {
                navHeaderEmail.setText(currentUser.getEmail());
            }

            if (currentUser.getUid() != null) {
                DocumentReference userDocRef = db.collection("users").document(currentUser.getUid());
                userDocRef.get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.isEmpty() && navHeaderName != null) {
                            navHeaderName.setText(name);
                        } else if (navHeaderName != null) {
                            navHeaderName.setText(R.string.organizer_name_placeholder);
                        }
                    } else {
                        Log.w(TAG, "User document for nav header not found.");
                        if (navHeaderName != null) {
                            navHeaderName.setText(R.string.organizer_name_placeholder);
                        }
                    }
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching organizer data for nav header", e);
                    if (navHeaderName != null) {
                        navHeaderName.setText(R.string.organizer_name_placeholder);
                    }
                });
            } else {
                Log.w(TAG, "Current user UID is null, cannot fetch Firestore data for nav header.");
                if (navHeaderName != null) navHeaderName.setText(R.string.organizer_name_placeholder);
            }
        } else {
            Log.w(TAG, "Cannot load nav header data: conditions not met.");
        }
    }

    // This method might be removed or simplified as NavigationUI handles bottom nav clicks to graph destinations
    // private void setupBottomNavigation() { ... }

    // This method is no longer needed for main navigation flow
    // private void loadFragment(Fragment fragment, String tag) { ... }


    // This is handled by NavigationUI if drawer item IDs match nav graph destination IDs.
    // Kept a custom listener above for items that DON'T match or have special actions.
    // @Override
    // public boolean onNavigationItemSelected(@NonNull MenuItem item) { ... }


    public void promptLogout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_confirmation_title))
                .setMessage(getString(R.string.logout_confirmation_message))
                .setPositiveButton(getString(R.string.logout_confirm), (dialog, which) -> performLogout())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void performLogout() {
        Log.i(TAG, "Logging out user: " + (currentUser != null ? currentUser.getUid() : "null user"));
        mAuth.signOut();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(OrganizerMainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity(); // Use finishAffinity to clear all activities in the task
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.organizer_main_menu, menu);
        return true;
    }

    // This method handles the Up button in the toolbar and other options menu items
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Let NavigationUI try to handle Up button and drawer toggle
        if (navController != null && NavigationUI.onNavDestinationSelected(item, navController)) {
            return true;
        }

        // Handle your custom toolbar menu items
        int id = item.getItemId();
        if (id == R.id.action_profile) { // This might be redundant if profile is in drawer/bottom nav
            Toast.makeText(this, "Profile (Toolbar) clicked - Consider removing if in main nav", Toast.LENGTH_SHORT).show();
            // If you still want it to navigate:
            // if (navController != null) navController.navigate(R.id.nav_organizer_profile);
            return true;
        } else if (id == R.id.action_settings) {
            Toast.makeText(this, "Settings (Toolbar) clicked", Toast.LENGTH_SHORT).show();
            // TODO: Navigate to a Settings Activity/Fragment
            // if (navController != null) navController.navigate(R.id.your_settings_destination_id);
            return true;
        } else if (id == R.id.action_logout) {
            promptLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // This is crucial for the Up button in the ActionBar to work with NavController
    @Override
    public boolean onSupportNavigateUp() {
        return (navController != null && NavigationUI.navigateUp(navController, appBarConfiguration))
                || super.onSupportNavigateUp();
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser freshUser = mAuth.getCurrentUser();
        if (freshUser == null) {
            Log.w(TAG, "User logged out or session expired. Redirecting to LoginActivity from onStart.");
            if (!isFinishing()) {
                redirectToLogin();
            }
        } else {
            currentUser = freshUser;
            // Nav header data is loaded in onCreate, consider if refresh here is always needed
            // or only on specific events (e.g., after profile update).
            // loadOrganizerDataForNavHeader(); // Potentially redundant if loaded in onCreate and not changed
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (navController != null && navController.getCurrentDestination() != null &&
                navController.getCurrentDestination().getId() == R.id.nav_organizer_home) {
            // If on the start destination, allow default back press behavior (exit app or go to previous task)
            super.onBackPressed();
        } else if (navController != null && !navController.popBackStack()) {
            // If NavController couldn't pop (e.g., at start of graph), then default
            super.onBackPressed();
        }
        // If navController.popBackStack() returned true, it handled the back press.
    }
}

