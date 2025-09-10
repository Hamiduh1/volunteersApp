package com.example.volunteersApp.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.volunteersApp.R;
import com.example.volunteersApp.adapters.ApplicantAdapter;
import com.example.volunteersApp.models.Application;
import com.example.volunteersApp.models.ApplicationWithUserDetails;
import com.example.volunteersApp.models.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
// Removed Firebase Timestamp as it's handled within Application model presumably

import java.util.ArrayList;
import java.util.List;
// import java.util.Objects; // Not directly used

public class ViewApplicantsFragment extends Fragment implements ApplicantAdapter.OnApplicantActionListener {

    private static final String TAG = "ViewApplicantsFragment";

    private String eventId;

    private RecyclerView recyclerViewApplicants;
    private ApplicantAdapter applicantAdapter;
    // This list is temporarily used to build the data before submitting to ListAdapter
    private List<ApplicationWithUserDetails> currentApplicantDetailsList;

    private ProgressBar progressBarViewApplicants;
    private TextView textViewStatusViewApplicants;
    private SwipeRefreshLayout swipeRefreshLayoutViewApplicants;

    private FirebaseFirestore db;

    public ViewApplicantsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();

        if (getArguments() != null) {
            eventId = ViewApplicantsFragmentArgs.fromBundle(getArguments()).getEventId();
        }

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Event ID is missing from arguments!");
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.error_event_id_missing_toast), Toast.LENGTH_LONG).show();
            }
            if (isAdded() && NavHostFragment.findNavController(this).getCurrentDestination() != null &&
                    NavHostFragment.findNavController(this).getCurrentDestination().getId() == R.id.viewApplicantsFragment) {
                NavHostFragment.findNavController(this).popBackStack();
            }
            return;
        }
        currentApplicantDetailsList = new ArrayList<>(); // Initialize the temporary list
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_applicants, container, false);
        recyclerViewApplicants = view.findViewById(R.id.recyclerViewApplicants);
        progressBarViewApplicants = view.findViewById(R.id.progressBarViewApplicants);
        textViewStatusViewApplicants = view.findViewById(R.id.textViewStatusViewApplicants);
        swipeRefreshLayoutViewApplicants = view.findViewById(R.id.swipeRefreshLayoutViewApplicants);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupSwipeRefreshLayout();

        if (eventId != null && !eventId.isEmpty()) {
            loadApplicationsAndUserDetails();
        } else {
            updateUIVisibility(true, R.string.event_id_missing_error_display, false);
        }
    }

    private void setupRecyclerView() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null in setupRecyclerView, cannot initialize ApplicantAdapter.");
            return;
        }

        // Pass the Context from the fragment and the fragment itself as the listener.
        // getContext() provides the Context.
        // 'this' (ViewApplicantsFragment) is the OnApplicantActionListener.
        applicantAdapter = new ApplicantAdapter(getContext(), this);

        recyclerViewApplicants.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewApplicants.setAdapter(applicantAdapter);
    }


    private void setupSwipeRefreshLayout() {
        swipeRefreshLayoutViewApplicants.setOnRefreshListener(() -> {
            if (eventId != null && !eventId.isEmpty()) {
                loadApplicationsAndUserDetails();
            } else {
                swipeRefreshLayoutViewApplicants.setRefreshing(false);
                updateUIVisibility(true, R.string.event_id_missing_error_display, false);
            }
        });
    }

    private void loadApplicationsAndUserDetails() {
        if (!isAdded() || eventId == null || eventId.isEmpty()) {
            updateUIVisibility(true, R.string.failed_to_load_applicants_generic, false);
            if (swipeRefreshLayoutViewApplicants.isRefreshing()) {
                swipeRefreshLayoutViewApplicants.setRefreshing(false);
            }
            return;
        }
        updateUIVisibility(false, 0, true);

        db.collection("events").document(eventId).collection("applicants")
                .get()
                .addOnCompleteListener(applicationTask -> {
                    if (!isAdded()) return; // Fragment not added anymore

                    if (applicationTask.isSuccessful() && applicationTask.getResult() != null) {
                        if (applicationTask.getResult().isEmpty()) {
                            Log.d(TAG, "No applications found for event: " + eventId);
                            currentApplicantDetailsList.clear();
                            updateAdapterData(); // Submit empty list
                            updateUIVisibility(true, R.string.no_applicants_for_this_event_message, false);
                            return;
                        }

                        List<Application> applicationsFromDb = new ArrayList<>();
                        List<Task<DocumentSnapshot>> userDetailTasks = new ArrayList<>();

                        for (QueryDocumentSnapshot appDoc : applicationTask.getResult()) {
                            Application application = appDoc.toObject(Application.class);
                            // Ensure applicationId is set, useful if not automatically mapped by Firestore
                            if (application.getApplicationId() == null || application.getApplicationId().isEmpty()) {
                                application.setApplicationId(appDoc.getId());
                            }
                            applicationsFromDb.add(application);

                            if (application.getVolunteerUid() != null && !application.getVolunteerUid().isEmpty()) {
                                userDetailTasks.add(db.collection("users").document(application.getVolunteerUid()).get());
                            } else {
                                Log.w(TAG, "Application " + appDoc.getId() + " is missing volunteerUid.");
                                userDetailTasks.add(Tasks.forResult(null)); // Add a null-resolving task to keep list sizes in sync
                            }
                        }

                        if (applicationsFromDb.isEmpty()) { // Should be caught by getResult().isEmpty() but good for safety
                            Log.d(TAG, "No valid applications processed for event: " + eventId);
                            currentApplicantDetailsList.clear();
                            updateAdapterData();
                            updateUIVisibility(true, R.string.no_applicants_for_this_event_message, false);
                            return;
                        }

                        Tasks.whenAllSuccess(userDetailTasks.toArray(new Task[0]))
                                .addOnSuccessListener(userSnapshotsList -> {
                                    if (!isAdded()) return;
                                    currentApplicantDetailsList.clear(); // Clear before repopulating

                                    for (int i = 0; i < applicationsFromDb.size(); i++) {
                                        Application currentApplication = applicationsFromDb.get(i);
                                        DocumentSnapshot userSnapshot = null;
                                        if (i < userSnapshotsList.size() && userSnapshotsList.get(i) instanceof DocumentSnapshot) {
                                            userSnapshot = (DocumentSnapshot) userSnapshotsList.get(i);
                                        }

                                        User user = null;
                                        if (userSnapshot != null && userSnapshot.exists()) {
                                            user = userSnapshot.toObject(User.class);
                                            if (user != null && (user.getUid() == null || user.getUid().isEmpty())) {
                                                user.setUid(userSnapshot.getId()); // Ensure User Uid is set
                                            }
                                        }

                                        ApplicationWithUserDetails combinedDetails = new ApplicationWithUserDetails(currentApplication, user);
                                        // If ApplicationWithUserDetails needs eventId and it's not in Application model:
                                        // combinedDetails.setEventId(this.eventId); // Uncomment if needed

                                        currentApplicantDetailsList.add(combinedDetails);
                                    }

                                    updateAdapterData(); // This will call submitList
                                    updateUIVisibility(currentApplicantDetailsList.isEmpty(),
                                            currentApplicantDetailsList.isEmpty() ? R.string.no_applicants_for_this_event_message : 0, false);

                                }).addOnFailureListener(e -> {
                                    if (!isAdded()) return;
                                    Log.e(TAG, "Error fetching some user details: ", e);
                                    // Populate with applications and placeholders for users
                                    currentApplicantDetailsList.clear();
                                    for (Application app : applicationsFromDb) {
                                        ApplicationWithUserDetails details = new ApplicationWithUserDetails(app, (User) null);
                                        // if (details.getApplication() != null) details.getApplication().setEventId(this.eventId); // if needed
                                        currentApplicantDetailsList.add(details);
                                    }
                                    updateAdapterData();
                                    updateUIVisibility(currentApplicantDetailsList.isEmpty(),
                                            R.string.failed_to_load_some_applicant_details_message, false);
                                });
                    } else {
                        Log.e(TAG, "Error loading applications: ", applicationTask.getException());
                        currentApplicantDetailsList.clear();
                        updateAdapterData();
                        updateUIVisibility(true, R.string.failed_to_load_applicants_generic, false);
                    }
                });
    }

    private void updateAdapterData() {
        if (applicantAdapter == null) return;
        // ApplicantAdapter is a ListAdapter, use submitList.
        // Create a new list for DiffUtil to work correctly.
        applicantAdapter.submitList(new ArrayList<>(currentApplicantDetailsList));
    }


    private void updateUIVisibility(boolean listIsEmpty, int emptyListMessageResId, boolean showProgressBar) {
        if (!isAdded() || getView() == null) return;

        progressBarViewApplicants.setVisibility(showProgressBar ? View.VISIBLE : View.GONE);

        if (!showProgressBar && swipeRefreshLayoutViewApplicants.isRefreshing()) {
            swipeRefreshLayoutViewApplicants.setRefreshing(false);
        }

        if (listIsEmpty && !showProgressBar) {
            recyclerViewApplicants.setVisibility(View.GONE);
            textViewStatusViewApplicants.setVisibility(View.VISIBLE);
            if (emptyListMessageResId != 0 && getContext() != null) {
                textViewStatusViewApplicants.setText(getString(emptyListMessageResId));
            } else {
                textViewStatusViewApplicants.setText(getString(R.string.no_data_available));
            }
        } else if (!showProgressBar) {
            recyclerViewApplicants.setVisibility(View.VISIBLE);
            textViewStatusViewApplicants.setVisibility(View.GONE);
        } else { // Still loading
            recyclerViewApplicants.setVisibility(View.GONE);
            textViewStatusViewApplicants.setVisibility(View.GONE);
        }
    }


    // --- ApplicantAdapter.OnApplicantActionListener Implementation ---

    @Override
    public void onAcceptApplicant(ApplicationWithUserDetails applicantDetails) {
        if (!isAdded() || getContext() == null) return;
        if (eventId == null || applicantDetails == null || applicantDetails.getApplication() == null || applicantDetails.getApplication().getApplicationId() == null) {
            Toast.makeText(getContext(), getString(R.string.error_processing_action), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Accept applicant: " + applicantDetails.getTitle() + " (App ID: " + applicantDetails.getApplication().getApplicationId() + ")");

        db.collection("events").document(eventId)
                .collection("applicants").document(applicantDetails.getApplication().getApplicationId())
                .update("status", "accepted")
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), getString(R.string.applicant_accepted_toast, applicantDetails.getTitle()), Toast.LENGTH_SHORT).show();
                    loadApplicationsAndUserDetails(); // Reload data to reflect changes
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to accept applicant: " + applicantDetails.getApplication().getApplicationId(), e);
                    Toast.makeText(getContext(), getString(R.string.failed_to_accept_applicant_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onRejectApplicant(ApplicationWithUserDetails applicantDetails) {
        if (!isAdded() || getContext() == null) return;
        if (eventId == null || applicantDetails == null || applicantDetails.getApplication() == null || applicantDetails.getApplication().getApplicationId() == null) {
            Toast.makeText(getContext(), getString(R.string.error_processing_action), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Reject applicant: " + applicantDetails.getTitle() + " (App ID: " + applicantDetails.getApplication().getApplicationId() + ")");

        db.collection("events").document(eventId)
                .collection("applicants").document(applicantDetails.getApplication().getApplicationId())
                .update("status", "rejected")
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), getString(R.string.applicant_rejected_toast, applicantDetails.getTitle()), Toast.LENGTH_SHORT).show();
                    loadApplicationsAndUserDetails(); // Reload data to reflect changes
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to reject applicant: " + applicantDetails.getApplication().getApplicationId(), e);
                    Toast.makeText(getContext(), getString(R.string.failed_to_reject_applicant_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onContactApplicant(ApplicationWithUserDetails applicantDetails) {
        if (!isAdded() || getContext() == null) return;
        // Use getEmail() from ApplicationWithUserDetails which should handle if user is null
        String email = applicantDetails.getEmail();
        if (email == null || email.isEmpty() || email.equals(getString(R.string.email_not_available)) || email.equals(getString(R.string.placeholder_na))) {
            Toast.makeText(getContext(), getString(R.string.cannot_contact_no_email, applicantDetails.getTitle()), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getContext(), getString(R.string.contacting_applicant_email, applicantDetails.getTitle(), email), Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});

        String eventNameForSubject = eventId; // Ideally, fetch event name for a friendlier subject
        // For simplicity, using eventId. You might want to pass event name or fetch it.
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_application_for_event, eventNameForSubject));

        if (getActivity() != null && intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(getContext(), getString(R.string.no_email_app_found_contact, applicantDetails.getTitle()), Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onApplicantClick(ApplicationWithUserDetails applicantDetails, int position) {
        if (!isAdded() || getContext() == null) return;
        // Use getVolunteerUid() from ApplicationWithUserDetails
        String volunteerUid = applicantDetails.getVolunteerUid();
        if (volunteerUid == null || volunteerUid.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.error_opening_profile_no_uid), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Clicked on applicant: " + applicantDetails.getTitle() + " UID: " + volunteerUid);
        Toast.makeText(getContext(), getString(R.string.viewing_profile_of, applicantDetails.getTitle()), Toast.LENGTH_SHORT).show();

        try {
            ViewApplicantsFragmentDirections.ActionViewApplicantsFragmentToUserProfileFragment action =
                    ViewApplicantsFragmentDirections.actionViewApplicantsFragmentToUserProfileFragment(volunteerUid);
            NavHostFragment.findNavController(this).navigate(action);
        } catch (Exception e) {
            Log.e(TAG, "Navigation to user profile failed for UID: " + volunteerUid, e);
            Toast.makeText(getContext(), getString(R.string.error_navigating_to_profile_toast), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nullify views and adapter to help GC and prevent potential leaks
        if (recyclerViewApplicants != null) {
            recyclerViewApplicants.setAdapter(null);
        }
        recyclerViewApplicants = null;
        applicantAdapter = null; // Important for ListAdapter too
        progressBarViewApplicants = null;
        textViewStatusViewApplicants = null;
        swipeRefreshLayoutViewApplicants = null;
        currentApplicantDetailsList = null; // Clear the temporary list
    }
}
