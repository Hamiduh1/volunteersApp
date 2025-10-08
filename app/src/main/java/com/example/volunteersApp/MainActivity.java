package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.volunteersApp.databinding.ActivityMainBinding;
import com.example.volunteersApp.databinding.NavHeaderMainBinding; // FIXED: Correct import
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.squareup.picasso.Picasso;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private NavHeaderMainBinding headerBinding; // FIXED: Use the correct binding class name

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration userProfileListenerRegistration;

    private boolean isUserDataLoaded = false;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    // Direct view references from the main binding
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

        // Initialize views from binding
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        bottomNavigationView = binding.bottomNavView;

        setupToolbar();
        setupNavigation();
        setupNavHeader(); // Setup header binding
        handleOnBackPressed(); // Handle the new back press callback
    }

    public DrawerLayout getDrawerLayout() {
        return this.drawerLayout;
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        } else {
            Log.e(TAG, "NavHostFragment not found. Check your activity_main.xml layout.");
            Toast.makeText(this, getString(R.string.error_setting_up_navigation), Toast.LENGTH_LONG).show();
            return;
        }

        // --- Top-level destinations for Volunteer ---
        Set<Integer> topLevelDestinations = new HashSet<>(Arrays.asList(
                R.id.nav_home,
                R.id.nav_browse_jobs,
                R.id.nav_browse_events,
                R.id.nav_my_activity,
                R.id.nav_profile
        ));

        appBarConfiguration = new AppBarConfiguration.Builder(topLevelDestinations)
                .setOpenableLayout(drawerLayout)
                .build();

        // Setup ActionBar, NavigationView, and BottomNavigationView with NavController
        if (getSupportActionBar() != null) {
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        }
        NavigationUI.setupWithNavController(navigationView, navController);
        NavigationUI.setupWithNavController(bottomNavigationView, navController);

        // Custom handling for Drawer items
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            int id = menuItem.getItemId();
            boolean handledByNavigationUI = NavigationUI.onNavDestinationSelected(menuItem, navController);
            if (handledByNavigationUI) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            // Handle custom items here that start other activities
            if (id == R.id.nav_wallet) {
                startActivity(new Intent(MainActivity.this, Wallet.class));
            } else if (id == R.id.nav_share) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                String shareBody = getString(R.string.share_app_body_template, "YOUR_APP_DOWNLOAD_LINK_HERE");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app_subject));
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_using)));
            } else if (id == R.id.nav_feedback) {
                startActivity(new Intent(MainActivity.this, feedBackTab.class));
            } else if (id == R.id.nav_report) {
                startActivity(new Intent(MainActivity.this, Report.class));
            } else if (id == R.id.nav_sign_out) {
                signOutUser();
                return true; // Return true as we've handled it
            } else {
                return false; // Item not handled
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupNavHeader() {
        if (navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            headerBinding = NavHeaderMainBinding.bind(headerView); // FIXED: Bind to the existing header view

            headerBinding.pro1.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() != null) {
                    if (navController.getCurrentDestination() != null && navController.getCurrentDestination().getId() != R.id.nav_profile) {
                        navController.navigate(R.id.nav_profile);
                    }
                } else {
                    Toast.makeText(MainActivity.this, R.string.log_in_to_view_profile, Toast.LENGTH_SHORT).show();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        } else {
            Log.e(TAG, "NavigationView has no header view. Cannot setup NavHeader views.");
        }
    }

    private void signOutUser() {
        Log.d(TAG, "Signing out user.");
        mAuth.signOut();
        isUserDataLoaded = false;
        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
            userProfileListenerRegistration = null;
        }
        updateUIForNoUser();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(MainActivity.this, getString(R.string.signed_out_message), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (headerBinding == null && navigationView.getHeaderCount() > 0) {
            setupNavHeader();
        }
        if (currentUser != null) {
            if (!isUserDataLoaded && userProfileListenerRegistration == null) {
                loadUserDataFromFirestore(currentUser.getUid());
            }
        } else {
            updateUIForNoUser();
            isUserDataLoaded = false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
            userProfileListenerRegistration = null;
            Log.d(TAG, "Firestore user profile listener detached.");
        }
    }

    private void loadUserDataFromFirestore(String userId) {
        if (userId == null || userId.isEmpty()) {
            updateUIForNoUser();
            isUserDataLoaded = false;
            return;
        }
        if (userProfileListenerRegistration != null) {
            userProfileListenerRegistration.remove();
        }
        DocumentReference userDocRef = db.collection("users").document(userId);
        userProfileListenerRegistration = userDocRef.addSnapshotListener(this, (snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Firestore listen failed for user data.", e);
                updateUIForError();
                isUserDataLoaded = false;
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                updateUIWithFirestoreData(snapshot);
                isUserDataLoaded = true;
            } else {
                updateUIForPartialUser(mAuth.getCurrentUser());
                isUserDataLoaded = true;
            }
        });
    }

    private void updateUIWithFirestoreData(@NonNull DocumentSnapshot documentSnapshot) {
        if (headerBinding == null) {
            Log.e(TAG, "Header binding is null. Cannot update header UI.");
            return;
        }
        String profileName = documentSnapshot.getString("username");
        String emailFromFirestore = documentSnapshot.getString("email");
        String imageUrl = documentSnapshot.getString("profilePictureUrl");
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        String finalEmail = emailFromFirestore;
        if ((finalEmail == null || finalEmail.isEmpty()) && firebaseUser != null) {
            finalEmail = firebaseUser.getEmail();
        }
        headerBinding.profilename2.setText(profileName != null && !profileName.isEmpty() ? profileName : getString(R.string.name_not_set));
        headerBinding.profileemail2.setText(finalEmail != null && !finalEmail.isEmpty() ? finalEmail : getString(R.string.email_not_set));
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Picasso.get().load(imageUrl).fit().centerCrop()
                    .placeholder(R.drawable.default_profile_image).error(R.drawable.default_profile_image)
                    .into(headerBinding.pro1);
        } else {
            headerBinding.pro1.setImageResource(R.drawable.default_profile_image);
        }
    }

    private void updateUIForNoUser() {
        if (headerBinding == null) return;
        headerBinding.profilename2.setText(getString(R.string.please_log_in));
        headerBinding.profileemail2.setText("");
        headerBinding.pro1.setImageResource(R.drawable.default_profile_image);
    }

    private void updateUIForPartialUser(FirebaseUser firebaseUser) {
        if (headerBinding == null) return;
        headerBinding.profilename2.setText(getString(R.string.complete_your_profile));
        if (firebaseUser != null) {
            headerBinding.profileemail2.setText(firebaseUser.getEmail());
        }
        headerBinding.pro1.setImageResource(R.drawable.default_profile_image);
    }

    private void updateUIForError() {
        if (headerBinding == null) return;
        headerBinding.profilename2.setText(getString(R.string.error_loading_name));
        headerBinding.profileemail2.setText(getString(R.string.error_loading_email));
        headerBinding.pro1.setImageResource(R.drawable.default_profile_image);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return (navController != null && appBarConfiguration != null && NavigationUI.navigateUp(navController, appBarConfiguration))
                || super.onSupportNavigateUp();
    }

    private void handleOnBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    if (!navController.navigateUp()) {
                        finish();
                    }
                }
            }
        });
    }
}


