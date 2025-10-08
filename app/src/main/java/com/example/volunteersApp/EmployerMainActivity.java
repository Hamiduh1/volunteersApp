package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
// Removed ActionBarDrawerToggle as it's handled by NavigationUI with AppBarConfiguration
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
// Corrected Log import if you intended android.util.Log
import android.util.Log; // Changed from androidx.media3.common.util.Log
// androidx.media3.common.util.UnstableApi is fine if you are using Media3 features,
// but not strictly needed for basic navigation setup. Remove if not using Media3 directly here.
// import androidx.media3.common.util.UnstableApi;

import androidx.navigation.NavController;
// Removed Navigation.findNavController as NavHostFragment.getNavController() is preferred
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.databinding.ActivityEmployerMainBinding;
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmployerMainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "EmployerMainActivity"; // Added for logging

    private ActivityEmployerMainBinding binding;
    private FirebaseAuth mAuth;
    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavView;

    // @UnstableApi // Only if specific Media3 APIs are used directly in this method
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmployerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        drawerLayout = binding.employerDrawerLayout;
        navigationView = binding.employerNavView;
        bottomNavView = binding.employerBottomNavView;
        Toolbar toolbar = binding.employerToolbar;
        setSupportActionBar(toolbar);

        // Define ALL top-level destinations for AppBarConfiguration
        // (those in bottom nav and potentially unique top-level drawer items)
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_employer_home,
                R.id.nav_employer_post_job,         // Added
                R.id.nav_employer_posted_jobs,
                R.id.nav_employer_applications,
                R.id.nav_employer_profile_management
        )
                .setOpenableLayout(drawerLayout) // Use build() on the Builder
                .build();


        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_employer_main);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController); // For Drawer
            NavigationUI.setupWithNavController(bottomNavView, navController);  // For BottomNav
        } else {
            Log.e(TAG, "NavHostFragment (R.id.nav_host_fragment_content_employer_main) not found.");
            Toast.makeText(this, "Critical navigation error.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Set the listener for drawer items AFTER setupWithNavController if you need to override
        // or add custom behavior like sign out.
        navigationView.setNavigationItemSelectedListener(this);

        updateNavHeader(currentUser);
    }

    private void updateNavHeader(FirebaseUser user) {
        View headerView = navigationView.getHeaderView(0);
        TextView textViewName = headerView.findViewById(R.id.employer_profile_name);
        TextView textViewEmail = headerView.findViewById(R.id.employer_profile_email);
        CircleImageView imageViewProfile = headerView.findViewById(R.id.employer_profile_image);

        if (user == null) { // Should not happen due to onCreate check, but good practice
            Log.w(TAG, "updateNavHeader called with null user");
            return;
        }

        if (textViewName != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                textViewName.setText(user.getDisplayName());
            } else {
                textViewName.setText(R.string.employer_name_placeholder);
            }
        }

        if (textViewEmail != null) {
            textViewEmail.setText(user.getEmail()); // FirebaseUser.getEmail() can be null
        }

        if (imageViewProfile != null) {
            // Placeholder, implement actual image loading if needed
            imageViewProfile.setImageResource(R.drawable.default_org_logo);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            // Let NavController handle back press first for fragment transactions
            if (navController != null && navController.navigateUp()) {
                // Navigation handled by NavController
            } else {
                // If NavController doesn't handle it (e.g., at start destination), then default
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_employer_sign_out) { // Ensure this ID exists in your employer_drawer_menu.xml
            signOutUser();
            drawerLayout.closeDrawer(GravityCompat.START); // Close drawer after handling
            return true; // Item handled
        }
        // IMPORTANT: Let NavigationUI try to handle navigation for drawer items that
        // match destinations in your navigation graph FIRST.
        // onNavDestinationSelected returns true if it handles the navigation.
        boolean handledByNavigationUI = NavigationUI.onNavDestinationSelected(item, navController);

        if (handledByNavigationUI) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        } else {
            // If NavigationUI didn't handle it, it's not a direct graph destination.
            // Handle other custom cases here if any.
            // For example, if you had a "Settings" item that opens a new Activity:
            // if (id == R.id.nav_settings_activity) {
            //     startActivity(new Intent(this, SettingsFragment.class));
            //     drawerLayout.closeDrawer(GravityCompat.START);
            //     return true;
            // }
            Log.w(TAG, "Drawer item " + item.getTitle() + " not handled by NavigationUI or custom cases.");
            drawerLayout.closeDrawer(GravityCompat.START); // Still close the drawer
            return false; // Item not fully handled (or let default behavior if that's desired)
        }
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(EmployerMainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
