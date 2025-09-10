package com.example.volunteersApp;

import android.content.Intent;
import android.net.Uri; // For email intent
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
// DocumentId import is not directly used in Activity, but good to know it's in Application.java
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
// QuerySnapshot, DocumentSnapshot, FirebaseFirestoreException, EventListener are used implicitly by lambda

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
// OptionalInt and IntStream not used in this revised version for findApplication...

import com.example.volunteersApp.adapters.ApplicantAdapter;
import com.example.volunteersApp.models.Application;
import com.example.volunteersApp.models.ApplicationWithUserDetails;
import com.example.volunteersApp.models.User; // Ensure this User model has getUid, getTitle, getEmail, getProfileImageUrl
import com.google.firebase.firestore.QueryDocumentSnapshot;

// Implement the correct listener from ApplicantAdapter
public class JobApplicantsActivity extends AppCompatActivity implements ApplicantAdapter.OnApplicantActionListener {

    private static final String TAG = "JobApplicantsActivity";
    public static final String JOB_POST_ID_EXTRA = "JOB_POST_ID";
    public static final String JOB_TITLE_EXTRA = "JOB_TITLE";

    private RecyclerView recyclerViewApplicants;
    private ApplicantAdapter applicantAdapter;
    // applicantList is managed by ListAdapter via submitList, but you might keep a local copy for other logic if needed.
    // For simplicity with ListAdapter, direct manipulation of a separate list here is less common.
    // private List<ApplicationWithUserDetails> applicantListLocalCache = new ArrayList<>(); // Example if you need a readily accessible list

    private FirebaseFirestore db;
    private CollectionReference applicationsRef;
    private CollectionReference usersRef;
    private ListenerRegistration applicationsListener;

    private String jobPostId;
    private String jobTitle;
    private TextView textViewNoApplicants;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job_applicants);

        jobPostId = getIntent().getStringExtra(JOB_POST_ID_EXTRA);
        jobTitle = getIntent().getStringExtra(JOB_TITLE_EXTRA);

        setupToolbar();
        initializeFirestore(); // Initialize Firestore before setting up RecyclerView if adapter needs it implicitly
        setupRecyclerView();   // Now setup RecyclerView and Adapter

        if (jobPostId == null || jobPostId.isEmpty()) {
            Toast.makeText(this, "Error: Job ID missing.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "JobPostId is null or empty. Cannot load applicants.");
            finish();
            return;
        }

        loadApplicants();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_job_applicants);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        if (jobTitle != null && !jobTitle.isEmpty()) {
            getSupportActionBar().setTitle("Applicants for: " + jobTitle);
        } else {
            getSupportActionBar().setTitle("Job Applicants");
        }
    }

    private void setupRecyclerView() {
        recyclerViewApplicants = findViewById(R.id.recyclerViewApplicants);
        textViewNoApplicants = findViewById(R.id.textViewNoApplicants);
        recyclerViewApplicants.setLayoutManager(new LinearLayoutManager(this));

        // Initialize ApplicantAdapter correctly for ListAdapter
        applicantAdapter = new ApplicantAdapter(this, this); // Pass 'this' as the OnApplicantActionListener
        recyclerViewApplicants.setAdapter(applicantAdapter);
    }

    private void initializeFirestore() {
        db = FirebaseFirestore.getInstance();
        applicationsRef = db.collection("applications");
        // Ensure "Users" is the correct case-sensitive name of your users collection in Firestore
        usersRef = db.collection("Users");
    }

    private void loadApplicants() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to view applicants.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "User not logged in. Cannot load applicants.");
            // Assuming you have these strings:
            // updateEmptyState(true, getString(R.string.not_logged_in_message));
            updateEmptyState(true, "You need to be logged in to see this."); // Placeholder if string is missing
            finish();
            return;
        }
        String currentEmployerUid = currentUser.getUid();

        Query query = applicationsRef
                .whereEqualTo("jobPostId", jobPostId)
                .whereEqualTo("employerUid", currentEmployerUid);

        // Temp list to accumulate changes before submitting to ListAdapter
        List<ApplicationWithUserDetails> currentApplicantDetailsList = new ArrayList<>();

        applicationsListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed for applications:", e);
                Toast.makeText(JobApplicantsActivity.this, "Failed to load applicants.", Toast.LENGTH_SHORT).show();
                // updateEmptyState(true, getString(R.string.failed_to_load_applicants));
                updateEmptyState(true, "Failed to load applicants. Please try again."); // Placeholder
                return;
            }

            if (snapshots == null) {
                Log.d(TAG, "Snapshots object was null.");
                updateEmptyState(applicantAdapter.getCurrentList().isEmpty(), null);
                return;
            }

            List<Application> applicationsFromSnapshot = new ArrayList<>();
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                Application application = dc.getDocument().toObject(Application.class);
                if (application.getApplicationId() == null || application.getApplicationId().isEmpty()) {
                    Log.e(TAG, "Application object from Firestore is missing ID. Skipping: " + dc.getDocument().getId());
                    continue;
                }
                // Instead of processing one by one immediately, collect all applications first
                // then fetch details. This is a simplification. For more complex scenarios,
                // you'd handle ADDED, MODIFIED, REMOVED more granularly with ListAdapter.

                // For simplicity with ListAdapter, we'll rebuild the list of applications
                // and then fetch all user details. This can be optimized.
                // A more advanced approach updates ListAdapter's list based on DocumentChange types.
            }

            // --- Simplified approach for ListAdapter: Re-fetch all and submit ---
            // Get all current applications from the snapshot
            List<Application> allApplications = new ArrayList<>();
            if (!snapshots.isEmpty()) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Application app = doc.toObject(Application.class);
                    if (app.getApplicationId() != null && !app.getApplicationId().isEmpty()) {
                        allApplications.add(app);
                    }
                }
            }

            if (allApplications.isEmpty()) {
                applicantAdapter.submitList(new ArrayList<>()); // Submit empty list
                updateEmptyState(true, null);
                return;
            }

            // Fetch user details for all applications
            fetchAllUserDetailsAndSubmit(allApplications);

        });
    }

    private void fetchAllUserDetailsAndSubmit(List<Application> applications) {
        List<ApplicationWithUserDetails> fullDetailsList = new ArrayList<>();
        if (applications.isEmpty()) {
            applicantAdapter.submitList(fullDetailsList); // Submit empty list
            updateEmptyState(true, null);
            return;
        }

        int[] completionCounter = {0}; // Counter to track completion of async calls

        for (Application app : applications) {
            fetchUserDetails(app, awud -> {
                if (awud != null) { // Ensure awud is not null before adding
                    fullDetailsList.add(awud);
                }
                completionCounter[0]++;
                if (completionCounter[0] == applications.size()) {
                    // All user details fetched, submit to adapter
                    applicantAdapter.submitList(new ArrayList<>(fullDetailsList)); // Submit a new copy
                    updateEmptyState(fullDetailsList.isEmpty(), null);
                }
            });
        }
    }


    // This method now fetches details for a single application and uses a callback.
    private void fetchUserDetails(Application application, UserDetailsCallback callback) {
        if (application == null || application.getVolunteerUid() == null || application.getVolunteerUid().isEmpty()) {
            Log.w(TAG, "Cannot fetch user details: App or Volunteer UID is null/empty. AppID: " + (application != null ? application.getApplicationId() : "Unknown"));
            // Create a placeholder using the appropriate constructor
            // Assuming ApplicationWithUserDetails has a constructor like:
            // ApplicationWithUserDetails(Application app, String name, String email, String profileImgUrl)
            Application placeholderApp = application != null ? application : new Application(); // Ensure placeholderApp is not null
            placeholderApp.setVolunteerName("Volunteer UID Missing"); // Set some default in the app if user is missing
            // Use the constructor that takes Application and individual user fields
            callback.onCallback(new ApplicationWithUserDetails(placeholderApp,
                    placeholderApp.getVolunteerUid(), // or "UID Missing"
                    "N/A",
                    "N/A",
                    null));
            return;
        }

        String volunteerUid = application.getVolunteerUid();
        usersRef.document(volunteerUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    ApplicationWithUserDetails awud;
                    if (documentSnapshot.exists()) {
                        User userDetails = documentSnapshot.toObject(User.class);
                        if (userDetails != null) {
                            // **ASSUMPTION**: User model has getUid(), getTitle(), getEmail(), getProfileImageUrl()
                            // If your User model uses different getter names, adjust them here.
                            // e.g., userDetails.getMail() -> userDetails.getEmail()
                            // e.g., userDetails.getUrl() -> userDetails.getProfileImageUrl()
                            awud = new ApplicationWithUserDetails(application, userDetails); // Use constructor User object
                        } else {
                            Log.w(TAG, "User details were null despite document existing for UID: " + volunteerUid);
                            awud = createPlaceholderAwud(application, "Details Missing");
                        }
                    } else {
                        Log.w(TAG, "User details not found for volunteer UID: " + volunteerUid);
                        awud = createPlaceholderAwud(application, "Details Not Found");
                    }
                    callback.onCallback(awud);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user details for volunteer: " + volunteerUid, e);
                    ApplicationWithUserDetails awud = createPlaceholderAwud(application, "Error Loading Details");
                    callback.onCallback(awud);
                });
    }

    private ApplicationWithUserDetails createPlaceholderAwud(Application application, String detailStatus) {
        String namePlaceholder = (application != null && application.getVolunteerName() != null && !application.getVolunteerName().isEmpty()) ?
                application.getVolunteerName() : "Name Not Found";
        String emailPlaceholder = detailStatus; // Or more specific like "Email N/A"

        // Use the constructor: ApplicationWithUserDetails(Application app, String name, String email, String profileImgUrl)
        return new ApplicationWithUserDetails(application,
                application != null ? application.getVolunteerUid() : "UID Missing", // Provide UID if available
                namePlaceholder,
                emailPlaceholder,
                null); // No profile image URL for placeholder
    }


    // findApplicationWithUserDetailsIndex and removeApplicationFromListById
    // are less critical if ListAdapter handles updates based on object equality and submitted lists.
    // However, if you need to find an item for other reasons:
    private int findApplicationInAdapterList(String applicationId) {
        if (applicationId == null) return -1;
        List<ApplicationWithUserDetails> currentList = applicantAdapter.getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            Application app = currentList.get(i).getApplication();
            if (app != null && applicationId.equals(app.getApplicationId())) {
                return i;
            }
        }
        return -1;
    }


    private void updateEmptyState(boolean isEmpty, @Nullable String customMessage) {
        if (isEmpty) {
            recyclerViewApplicants.setVisibility(View.GONE);
            textViewNoApplicants.setVisibility(View.VISIBLE);
            if (customMessage != null) {
                textViewNoApplicants.setText(customMessage);
            } else {
                // textViewNoApplicants.setText(R.string.no_applicants_found); // Use string resource
                textViewNoApplicants.setText("No applicants found for this job yet."); // Placeholder
            }
        } else {
            recyclerViewApplicants.setVisibility(View.VISIBLE);
            textViewNoApplicants.setVisibility(View.GONE);
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- Implementation of ApplicantAdapter.OnApplicantActionListener ---

    @Override
    public void onApplicantClick(ApplicationWithUserDetails applicantInfo, int position) {
        Toast.makeText(this, "Clicked on: " + applicantInfo.getTitle() + " (Status: " + applicantInfo.getStatus() + ")", Toast.LENGTH_SHORT).show();
        // Example: Navigate to a detailed profile view or another relevant activity
        // Intent intent = new Intent(this, VolunteerProfileActivity.class);
        // if (applicantInfo.getApplication() != null) {
        //     intent.putExtra("VOLUNTEER_UID", applicantInfo.getApplication().getVolunteerUid());
        //     intent.putExtra("APPLICATION_ID", applicantInfo.getApplication().getApplicationId());
        //     startActivity(intent);
        // }
    }

    @Override
    public void onAcceptApplicant(ApplicationWithUserDetails applicant) {
        if (applicant.getApplication() == null || applicant.getApplicationId() == null) {
            Toast.makeText(this, "Error: Application details missing.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot accept: Application or Application ID is null.");
            return;
        }
        String applicationId = applicant.getApplicationId();
        Toast.makeText(this, "Accepting: " + applicant.getTitle(), Toast.LENGTH_SHORT).show();

        applicationsRef.document(applicationId)
                .update("status", "accepted")
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Application " + applicationId + " successfully marked as accepted.");
                    Toast.makeText(JobApplicantsActivity.this, applicant.getTitle() + " accepted.", Toast.LENGTH_SHORT).show();
                    // Firestore listener should pick up the change and ListAdapter will update UI.
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating application " + applicationId + " to accepted", e);
                    Toast.makeText(JobApplicantsActivity.this, "Failed to accept " + applicant.getTitle(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onRejectApplicant(ApplicationWithUserDetails applicant) {
        if (applicant.getApplication() == null || applicant.getApplicationId() == null) {
            Toast.makeText(this, "Error: Application details missing.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot reject: Application or Application ID is null.");
            return;
        }
        String applicationId = applicant.getApplicationId();
        Toast.makeText(this, "Rejecting: " + applicant.getTitle(), Toast.LENGTH_SHORT).show();

        applicationsRef.document(applicationId)
                .update("status", "rejected")
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Application " + applicationId + " successfully marked as rejected.");
                    Toast.makeText(JobApplicantsActivity.this, applicant.getTitle() + " rejected.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error updating application " + applicationId + " to rejected", e);
                    Toast.makeText(JobApplicantsActivity.this, "Failed to reject " + applicant.getTitle(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onContactApplicant(ApplicationWithUserDetails applicant) {
        if (applicant.getEmail() == null || applicant.getEmail().isEmpty() || applicant.getEmail().equals("N/A")) {
            Toast.makeText(this, "Cannot contact: Email not available for " + applicant.getTitle(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Contacting: " + applicant.getTitle() + " (Email: " + applicant.getEmail() + ")", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{applicant.getEmail()});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Regarding your application for " + (jobTitle != null ? jobTitle : "the job"));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "No email app found to contact " + applicant.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (applicationsListener != null) {
            applicationsListener.remove();
            Log.d(TAG, "Applications listener removed.");
        }
    }

    interface UserDetailsCallback {
        void onCallback(@Nullable ApplicationWithUserDetails awud); // Allow null if user fetch fails critically
    }
    // Add missing string resources to res/values/strings.xml:
    // <string name="no_applicants_found">No applicants found for this job yet.</string>
    // <string name="failed_to_load_applicants">Failed to load applicants. Please try again.</string>
    // <string name="not_logged_in_message">You need to be logged in to see this.</string>
}
