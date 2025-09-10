package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
// import androidx.annotation.Nullable; // Not strictly needed with current code
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
// import androidx.navigation.NavDestination; // Not strictly needed with current code
// import androidx.navigation.Navigation; // Not strictly needed with current code
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions; // Keep if you use Cloud Functions elsewhere
import com.google.firebase.storage.FirebaseStorage;   // Keep if you use Storage elsewhere
import com.squareup.picasso.Picasso;

import java.util.HashSet;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // These can be initialized in setupNavHeader more safely
    private TextView pNameHeaderTextView;
    private TextView pEmailHeaderTextView;
    private CircleImageView proHeaderImageView;

    private boolean isUserDataLoaded = false;

    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // If you use emulators:
        // connectToEmulators();

        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        bottomNavigationView = binding.bottomNavView;

        setupToolbar();
        setupNavigation();
        // setupNavHeader() will be called from onStart or when user data is loaded to ensure views are ready
    }

    private void setupToolbar() {
        if (binding.toolbar != null) {
            setSupportActionBar(binding.toolbar);
        } else {
            Log.e(TAG, "Toolbar not found in binding. Check activity_main.xml");
        }
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        } else {
            Log.e(TAG, "NavHostFragment not found. Check ID R.id.nav_host_fragment_content_main in activity_main.xml");
            Toast.makeText(this, getString(R.string.error_setting_up_navigation), Toast.LENGTH_LONG).show();
            return;
        }

        // --- MODIFIED: Removed R.id.nav_host_event from topLevelDestinations ---
        Set<Integer> topLevelDestinations = new HashSet<>();
        topLevelDestinations.add(R.id.nav_home);
        topLevelDestinations.add(R.id.nav_volunteering_events);
        // topLevelDestinations.add(R.id.nav_host_event); // REMOVED
        topLevelDestinations.add(R.id.nav_my_events_activity); // This is likely "My Applications" for a volunteer
        topLevelDestinations.add(R.id.nav_profile);
        // Ensure these IDs match your volunteer_nav_graph.xml and volunteer_bottom_nav_menu.xml

        appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations)
                .setOpenableLayout(drawerLayout)
                .build();

        if (getSupportActionBar() != null) {
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        }

        NavigationUI.setupWithNavController(navigationView, navController);
        NavigationUI.setupWithNavController(bottomNavigationView, navController);

        navigationView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();
            boolean handledByNavigationUI = NavigationUI.onNavDestinationSelected(menuItem, navController);

            if (handledByNavigationUI) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            // Handle custom drawer items not in the NavGraph
            if (id == R.id.nav_wallet) {
                startActivity(new Intent(MainActivity.this, Wallet.class)); // Ensure Wallet.class exists
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_share) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                String shareBody = getString(R.string.share_app_body_template, "YOUR_APP_DOWNLOAD_LINK_HERE"); // Replace placeholder
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app_subject));
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_using)));
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_feedback) {
                startActivity(new Intent(MainActivity.this, feedBackTab.class)); // Ensure feedBackTab.class exists
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_report) {
                startActivity(new Intent(MainActivity.this, Report.class)); // Ensure Report.class exists
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.nav_sign_out) {
                signOutUser();
                // No need to close drawer here as activity will finish
                return true;
            }
            return false; // Item not handled
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            Log.d(TAG, "Navigated to destination: " + destination.getLabel() + " (ID: " + destination.getId() + ")");
            // Example: Hide BottomNavigationView on a specific detail fragment
            // Set<Integer> noBottomNavDestinations = new HashSet<>(Arrays.asList(R.id.eventDetailFragment, R.id.anotherDetailFragment));
            // if (noBottomNavDestinations.contains(destination.getId())) {
            //    bottomNavigationView.setVisibility(View.GONE);
            // } else {
            //    bottomNavigationView.setVisibility(View.VISIBLE);
            // }
        });
    }

    private void setupNavHeaderViews() {
        // Ensure this is called only when headerView is confirmed to be non-null
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            Log.e(TAG, "Navigation header view is null in setupNavHeaderViews. Cannot find subviews.");
            return;
        }
        pNameHeaderTextView = headerView.findViewById(R.id.profilename2);
        pEmailHeaderTextView = headerView.findViewById(R.id.profileemail2);
        proHeaderImageView = headerView.findViewById(R.id.pro1);

        if (pNameHeaderTextView == null || pEmailHeaderTextView == null || proHeaderImageView == null) {
            Log.e(TAG, "One or more NavHeader views (profilename2, profileemail2, pro1) not found.");
        }

        if (proHeaderImageView != null) {
            proHeaderImageView.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() != null) {
                    if (navController.getGraph().findNode(R.id.nav_profile) != null) {
                        // Navigate to profile and ensure bottom nav is synced
                        if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() != R.id.nav_profile) {
                            navController.navigate(R.id.nav_profile);
                        }
                        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
                    } else {
                        Log.w(TAG, "R.id.nav_profile not found in NavGraph for header image click.");
                    }
                } else {
                    Toast.makeText(MainActivity.this, R.string.log_in_to_view_profile, Toast.LENGTH_SHORT).show();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
    }


    private void signOutUser() {
        mAuth.signOut();
        isUserDataLoaded = false; // Reset flag
        // Clear header immediately for better UX
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            setupNavHeaderViews(); // To get view references if null
            updateUIForNoUser();
        }
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(MainActivity.this, getString(R.string.signed_out_message), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        // Initialize header views here, as header might not be inflated in onCreate
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            setupNavHeaderViews(); // Initialize pNameHeaderTextView etc.
        } else {
            Log.e(TAG, "NavigationView or its header not available in onStart.");
        }

        if (currentUser != null) {
            if (!isUserDataLoaded) {
                loadUserDataFromFirestore(currentUser.getUid());
            } else {
                // Data was loaded, UI should be up-to-date or Firestore listener handles it.
                Log.d(TAG, "User data already marked as loaded.");
            }
        } else {
            updateUIForNoUser();
            isUserDataLoaded = false;
        }
    }

    // Inside MainActivity.java

    private void connectToEmulators() { // Keep if needed for development
        try {
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099);
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080);

            // For Firebase Storage:
            // You can get the default app instance and check if it's null,
            // though typically FirebaseApp.getInstance() won't be null if Firebase is initialized.
            // A more direct approach for storage if you only have one instance:
            FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199);

            // For Firebase Functions:
            // There's no getApp() method on FirebaseFunctions.
            // Just call useEmulator directly. It will throw an IllegalStateException
            // if called after a network request or if arguments are invalid.
            FirebaseFunctions.getInstance().useEmulator("10.0.2.2", 5001); // CORRECTED LINE

            Log.d(TAG, "Attempted to connect to Firebase Emulators");
        } catch (IllegalStateException e) {
            // This exception can be thrown if useEmulator is called after the service
            // has already been used, or if FirebaseApp.initializeApp() hasn't been called.
            // It's also possible if you try to connect to an emulator that's not running
            // or if the host/port is invalid, but that might lead to other errors later.
            Log.w(TAG, "Firebase Emulators setup issue (e.g., already used, or not initialized): " + e.getMessage());
        }
    }


    private void loadUserDataFromFirestore(String userId) {
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "loadUserDataFromFirestore: null or empty userId.");
            updateUIForNoUser();
            isUserDataLoaded = false;
            return;
        }
        Log.d(TAG, "Loading user data for UID: " + userId);
        DocumentReference userDocRef = db.collection("users").document(userId);
        userDocRef.addSnapshotListener(this, (snapshot, e) -> { // Added 'this' for activity lifecycle
            if (e != null) {
                Log.w(TAG, "Firestore listen failed for user data.", e);
                Toast.makeText(MainActivity.this, getString(R.string.failed_to_load_profile_data), Toast.LENGTH_SHORT).show();
                updateUIForError(); // Show error state in header
                isUserDataLoaded = false; // Allow retry on next onStart if needed
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Log.d(TAG, "User data received from Firestore.");
                updateUIWithFirestoreData(snapshot);
                isUserDataLoaded = true;
            } else {
                Log.d(TAG, "User document does not exist for UID: " + userId + ". Showing partial UI.");
                updateUIForPartialUser(mAuth.getCurrentUser()); // Use current FirebaseUser if doc is missing
                isUserDataLoaded = true; // Still, consider "loaded" to prevent loops if doc never appears
            }
        });
    }

    private void updateUIWithFirestoreData(DocumentSnapshot documentSnapshot) {
        if (pNameHeaderTextView == null || pEmailHeaderTextView == null || proHeaderImageView == null) {
            Log.w(TAG, "NavHeader views not initialized in updateUIWithFirestoreData. Attempting setup.");
            if (navigationView != null && navigationView.getHeaderCount() > 0) {
                setupNavHeaderViews(); // Try to get them again
                if(pNameHeaderTextView == null) { // Still null after attempt
                    Log.e(TAG, "NavHeader views still null after re-attempt. Cannot update header UI from Firestore data.");
                    return;
                }
            } else {
                Log.e(TAG, "NavigationView or header not available. Cannot update header UI from Firestore data.");
                return;
            }
        }

        String profileName = documentSnapshot.getString("username"); // Assuming 'username' from Firestore
        String email = documentSnapshot.getString("email");
        String imageUrl = documentSnapshot.getString("profilePictureUrl");

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if ((email == null || email.isEmpty()) && firebaseUser != null) {
            email = firebaseUser.getEmail(); // Fallback to auth email if Firestore email is missing
        }

        pNameHeaderTextView.setText(profileName != null && !profileName.isEmpty() ? profileName : getString(R.string.name_not_set));
        pEmailHeaderTextView.setText(email != null && !email.isEmpty() ? email : getString(R.string.email_not_set));

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Picasso.get().load(imageUrl).fit().centerCrop()
                    .placeholder(R.drawable.default_profile_image) // Ensure you have this drawable
                    .error(R.drawable.default_profile_image)       // Ensure you have this drawable
                    .into(proHeaderImageView);
        } else {
            proHeaderImageView.setImageResource(R.drawable.default_profile_image);
        }
    }

    private void updateUIForNoUser() {
        if (pNameHeaderTextView == null && navigationView != null && navigationView.getHeaderCount() > 0) setupNavHeaderViews();

        if (pNameHeaderTextView != null) pNameHeaderTextView.setText(getString(R.string.please_log_in));
        if (pEmailHeaderTextView != null) pEmailHeaderTextView.setText("");
        if (proHeaderImageView != null) proHeaderImageView.setImageResource(R.drawable.default_profile_image);
    }

    private void updateUIForPartialUser(FirebaseUser firebaseUser) {
        if (pNameHeaderTextView == null && navigationView != null && navigationView.getHeaderCount() > 0) setupNavHeaderViews();

        if (pNameHeaderTextView != null) pNameHeaderTextView.setText(getString(R.string.complete_your_profile));
        if (pEmailHeaderTextView != null) {
            pEmailHeaderTextView.setText(firebaseUser != null && firebaseUser.getEmail() != null ? firebaseUser.getEmail() : getString(R.string.email_not_set));
        }
        if (proHeaderImageView != null) proHeaderImageView.setImageResource(R.drawable.default_profile_image);
    }

    private void updateUIForError() {
        if (pNameHeaderTextView == null && navigationView != null && navigationView.getHeaderCount() > 0) setupNavHeaderViews();

        if (pNameHeaderTextView != null) pNameHeaderTextView.setText(getString(R.string.error_loading_name));
        if (pEmailHeaderTextView != null) pEmailHeaderTextView.setText(getString(R.string.error_loading_email));
        if (proHeaderImageView != null) proHeaderImageView.setImageResource(R.drawable.default_profile_image);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // getMenuInflater().inflate(R.menu.main_options_menu, menu); // If you have a toolbar options menu
        return true; // Return true to display the menu, false otherwise
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Ensure NavController and AppBarConfiguration are available
        if (navController == null || appBarConfiguration == null) {
            return super.onSupportNavigateUp();
        }
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (navController != null && !navController.popBackStack()) {
            // If NavController didn't handle back press (e.g., at the start destination of the graph)
            // and we are on a top-level destination of bottom navigation, allow default behavior (exit app).
            // This prevents getting stuck on the first tab.
            Set<Integer> topLevelDests = appBarConfiguration.getTopLevelDestinations();
            if (navController.getCurrentDestination() != null &&
                    topLevelDests.contains(navController.getCurrentDestination().getId())) {
                super.onBackPressed(); // Allow exit if on a top-level bottom nav item
            } else {
                super.onBackPressed(); // Or just always call super if popBackStack is false
            }
        } else if (navController == null) {
            super.onBackPressed();
        }
        // If navController.popBackStack() was true, it handled the back press.
    }
}

