package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem; // For menu item clicks
import android.view.View;
import android.widget.TextView; // For nav header
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi; // Import the annotation

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.databinding.ActivityEmployerMainBinding; // Your binding class
// Import for CircleImageView if you use it in employer nav header
import de.hdodenhof.circleimageview.CircleImageView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Glide for image loading in nav header
// import com.bumptech.glide.Glide;

public class EmployerMainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private ActivityEmployerMainBinding binding; // Replaces individual view binding
    private FirebaseAuth mAuth;
    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    private DrawerLayout drawerLayout; // Renamed from binding.employerDrawerLayout for clarity
    private NavigationView navigationView; // Renamed
    private BottomNavigationView bottomNavView; // Renamed

    @UnstableApi // If you want to opt-in for the whole onCreate method
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

        // Initialize components from binding
        drawerLayout = binding.employerDrawerLayout;
        navigationView = binding.employerNavView;
        bottomNavView = binding.employerBottomNavView;
        Toolbar toolbar = binding.employerToolbar; // Get toolbar from binding
        setSupportActionBar(toolbar);


        // Setup AppBarConfiguration: Define top-level destinations for the drawer
        // These are the destinations where the drawer icon will be shown instead of the up arrow.
        // You'll define these IDs in your employer_navigation.xml and employer_drawer_menu.xml
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_employer_home, // Example: Create this destination
                R.id.nav_employer_posted_jobs,
                R.id.nav_employer_applications,
                R.id.nav_employer_profile_management
                // Add other top-level destinations for employer
        )
                .setOpenableLayout(drawerLayout)
                .build();

        // Setup NavController

        // Recommended way - get NavController directly from the NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_employer_main);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            // Now that you have the navController, proceed with your setup
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
            NavigationUI.setupWithNavController(bottomNavView, navController);
        } else {
            // This is a critical error - the NavHostFragment itself wasn't found.
            // This would happen if R.id.nav_host_fragment_content_employer_main isn't in your layout
            // OR if the FragmentContainerView with that ID isn't correctly named as NavHostFragment.
            Log.e("EmployerMain", "NavHostFragment not found in layout. " +
                    "Ensure R.id.nav_host_fragment_content_employer_main is a FragmentContainerView " +
                    "with android:name=\"androidx.navigation.fragment.NavHostFragment\"");
            // You might want to finish the activity or show an error message,
            // as navigation will not work.
            Toast.makeText(this, "Critical error: Navigation host not found.", Toast.LENGTH_LONG).show();
            finish(); // Example: close the activity if navigation is essential
            return; // Stop further execution in onCreate if navHostFragment is null
        }

        //  navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_employer_main);
        //navController = Navigation.findNavController(this, R.id.nav_host_test);
        // Setup Toolbar with NavController and AppBarConfiguration
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);

        // Setup NavigationView (Drawer) with NavController
        NavigationUI.setupWithNavController(navigationView, navController);
        navigationView.setNavigationItemSelectedListener(this); // Handle drawer item clicks

        // Setup BottomNavigationView with NavController
        NavigationUI.setupWithNavController(bottomNavView, navController);

        // Optional: Update Nav Header dynamically
        updateNavHeader(currentUser);
    }

    // Corrected code in EmployerMainActivity.java
    private void updateNavHeader(FirebaseUser user) {
        View headerView = navigationView.getHeaderView(0);
        // Use the IDs from nav_header_employer_main.xml
        TextView textViewName = headerView.findViewById(R.id.employer_profile_name);
        TextView textViewEmail = headerView.findViewById(R.id.employer_profile_email);
        CircleImageView imageViewProfile = headerView.findViewById(R.id.employer_profile_image);

        // It's a good practice to check if the views were actually found before using them,
        // though if the layout and IDs are correct, they should be found.
        if (textViewName != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                textViewName.setText(user.getDisplayName());
            } else {
                // Ensure R.string.employer_name_placeholder is defined in your strings.xml
                textViewName.setText(R.string.employer_name_placeholder);
            }
        }

        if (textViewEmail != null && user.getEmail() != null) { // Also check if user.getEmail() is null
            textViewEmail.setText(user.getEmail());
        }

        if (imageViewProfile != null) {
            // Load profile image using Glide or Picasso (example)
            // if (user.getPhotoUrl() != null) {
            // Glide.with(this).load(user.getPhotoUrl()).placeholder(R.drawable.default_org_logo).into(imageViewProfile);
            // } else {
            // imageViewProfile.setImageResource(R.drawable.default_org_logo); // From your XML
            // }
            imageViewProfile.setImageResource(R.drawable.default_org_logo); // From your XML
        }
    }


    @Override
    public boolean onSupportNavigateUp() {
        // Handles the Up button in the toolbar (back navigation and drawer toggle)
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        // Close drawer if open, otherwise default back behavior
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        // The id corresponds to the menu item id in employer_drawer_menu.xml
        int id = item.getItemId();

        // Basic navigation is handled by NavigationUI.setupWithNavController.
        // This is for custom actions like sign out or navigating to non-graph activities.

        if (id == R.id.nav_employer_sign_out) { // Example: Create this ID in employer_drawer_menu
            signOutUser();
        } else {
            // Allow NavigationUI to handle the navigation for other items
            // Need to close the drawer manually if NavigationUI doesn't handle the item.
            // However, if the item ID matches a destination in the nav graph,
            // NavigationUI.onNavDestinationSelected will handle it.
            // For simplicity, we can let NavigationUI try first.
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (!handled) {
                // Handle other custom cases if needed
                Toast.makeText(this, "Selected: " + item.getTitle(), Toast.LENGTH_SHORT).show();
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true; // Return true to display the item as the selected item
    }

    private void signOutUser() {
        mAuth.signOut();
        Intent intent = new Intent(EmployerMainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

