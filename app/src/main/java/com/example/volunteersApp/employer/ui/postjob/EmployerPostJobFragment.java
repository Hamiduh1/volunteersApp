package com.example.volunteersApp.employer.ui.postjob; // Adjust package as needed

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

// Your app specific imports
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentEmployerPostJobBinding; // Use Fragment binding
import com.example.volunteersApp.employer.JobPosting; // Your JobPosting model

// Firebase
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

public class EmployerPostJobFragment extends Fragment {

    private static final String TAG = "EmployerPostJobFrag";
    private static final String JOB_POSTINGS_COLLECTION = "job_postings"; // Or "events"

    private FragmentEmployerPostJobBinding binding; // Changed from ActivityPostJobBinding
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private FirebaseFirestore db;
    private CollectionReference jobPostingsCollectionRef;

    private Calendar calendar;
    private NavController navController;

    private String editingPostingId = null;
    private boolean isEditMode = false;
    private String currentStatusForEdit = "open";

    // Argument key for navigation
    public static final String ARG_EDIT_POSTING_ID = "EDIT_POSTING_ID";


    public EmployerPostJobFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getInstance().getCurrentUser(); // Re-fetch, though activity should guard

        db = FirebaseFirestore.getInstance();
        jobPostingsCollectionRef = db.collection(JOB_POSTINGS_COLLECTION);
        calendar = Calendar.getInstance();

        if (getArguments() != null && getArguments().containsKey(ARG_EDIT_POSTING_ID)) {
            isEditMode = true;
            editingPostingId = getArguments().getString(ARG_EDIT_POSTING_ID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEmployerPostJobBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        // Check auth status again (important for fragments)
        if (currentUser == null) {
            Toast.makeText(getContext(), "Authentication required.", Toast.LENGTH_LONG).show();
            // Consider navigating to login via NavController if a global action exists
            // Or handle as per your app's auth flow for fragments
            if (isAdded() && getActivity() != null) {
                // For now, popping back or finishing activity if this fragment is top level
                // This depends on how it's hosted. If in EmployerMainActivity, that handles auth.
                navController.popBackStack(); // Example: go back
            }
            return;
        }


        if (isEditMode) {
            if (getActivity() instanceof AppCompatActivity) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle("Edit Opportunity");
            }
            binding.buttonSubmitPost.setText(R.string.update_opportunity);
            if (editingPostingId != null && !editingPostingId.isEmpty()) {
                fetchAndPrefillJobData(editingPostingId);
            } else {
                Toast.makeText(getContext(), "Error: Posting ID missing for edit.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "EDIT_POSTING_ID arg was present but its value was null or empty.");
                navController.popBackStack();
            }
        } else {
            if (getActivity() instanceof AppCompatActivity) {
                ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(getString(R.string.post_new_opportunity_title));
            }
            binding.buttonSubmitPost.setText(R.string.post_opportunity);
        }
        setupUI();
    }

    private void fetchAndPrefillJobData(String documentId) {
        binding.progressBarPostJob.setVisibility(View.VISIBLE);
        jobPostingsCollectionRef.document(documentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || binding == null) return; // Check if fragment is still valid
                    binding.progressBarPostJob.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        JobPosting existingJob = documentSnapshot.toObject(JobPosting.class);
                        if (existingJob != null) {
                            currentStatusForEdit = existingJob.getStatus() != null ? existingJob.getStatus() : "open";
                            prefillForm(existingJob);
                        } else {
                            Log.e(TAG, "Failed to deserialize JobPosting object for ID: " + documentId);
                            Toast.makeText(getContext(), "Error: Could not load job details.", Toast.LENGTH_SHORT).show();
                            navController.popBackStack();
                        }
                    } else {
                        Log.w(TAG, "Job posting not found in Firestore for ID: " + documentId);
                        Toast.makeText(getContext(), "Error: Job posting not found.", Toast.LENGTH_SHORT).show();
                        navController.popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressBarPostJob.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to fetch job for edit from Firestore (ID: " + documentId + "): ", e);
                    Toast.makeText(getContext(), "Failed to load details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    navController.popBackStack();
                });
    }

    private void prefillForm(JobPosting job) {
        // Assuming binding is not null due to checks in calling methods
        if (binding.editTextOrganizationName != null) { // Check if this field exists in your fragment_employer_post_job.xml
            binding.editTextOrganizationName.setText(job.getOrganizationName());
        }
        binding.editTextEventName.setText(job.getTitle());
        if (binding.editTextJobTitle != null) { // Check if this field exists
            binding.editTextJobTitle.setText(job.getJobTitle());
        }
        binding.editTextDescription.setText(job.getDescription());
        binding.editTextDate.setText(job.getDate());
        binding.editTextTime.setText(job.getTime());
        binding.editTextLocation.setText(job.getLocationName());
        binding.editTextCategory.setText(job.getCategory());
        binding.editTextVolunteersNeeded.setText(String.valueOf(job.getVolunteersNeeded()));

        if (job.getDate() != null && !job.getDate().isEmpty()) {
            try {
                SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                calendar.setTime(Objects.requireNonNull(sdfDate.parse(job.getDate())));
            } catch (ParseException | NullPointerException e) {
                Log.e(TAG, "Error parsing date for prefill: " + job.getDate(), e);
            }
        }
        if (job.getTime() != null && !job.getTime().isEmpty()) {
            try {
                SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.US);
                Calendar tempCal = Calendar.getInstance();
                tempCal.setTime(Objects.requireNonNull(sdfTime.parse(job.getTime())));
                calendar.set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE));
            } catch (ParseException | NullPointerException e) {
                Log.e(TAG, "Error parsing time for prefill: " + job.getTime(), e);
            }
        }
    }

    private void setupUI() {
        binding.editTextDate.setOnClickListener(v -> showDatePickerDialog());
        binding.editTextTime.setOnClickListener(v -> showTimePickerDialog());
        binding.buttonSubmitPost.setOnClickListener(v -> validateAndPostJob());
    }

    private void showDatePickerDialog() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
        };
        new DatePickerDialog(requireContext(), dateSetListener, // Use requireContext() in Fragments
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void updateDateInView() {
        String myFormat = "yyyy-MM-dd";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.US);
        binding.editTextDate.setText(sdf.format(calendar.getTime()));
        binding.editTextDate.setError(null);
    }

    private void showTimePickerDialog() {
        TimePickerDialog.OnTimeSetListener timeSetListener = (view, hourOfDay, minute) -> {
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            updateTimeInView();
        };
        new TimePickerDialog(requireContext(), timeSetListener, // Use requireContext()
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false)
                .show();
    }

    private void updateTimeInView() {
        String myFormat = "hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.US);
        binding.editTextTime.setText(sdf.format(calendar.getTime()));
        binding.editTextTime.setError(null);
    }

    private void validateAndPostJob() {
        // Ensure binding and views are accessible
        if (binding == null || !isAdded()) {
            Log.e(TAG, "Validation failed: Binding is null or fragment not added.");
            return;
        }

        String organizationName = (binding.editTextOrganizationName != null) ? binding.editTextOrganizationName.getText().toString().trim() : (currentUser != null ? currentUser.getDisplayName() : ""); // Default to current user's display name if field not present or empty
        String eventName = binding.editTextEventName.getText().toString().trim();
        String jobTitle = (binding.editTextJobTitle != null) ? binding.editTextJobTitle.getText().toString().trim() : "";
        String description = binding.editTextDescription.getText().toString().trim();
        String date = binding.editTextDate.getText().toString().trim();
        String time = binding.editTextTime.getText().toString().trim();
        String location = binding.editTextLocation.getText().toString().trim();
        String category = binding.editTextCategory.getText().toString().trim();
        String volunteersNeededStr = binding.editTextVolunteersNeeded.getText().toString().trim();

        // VALIDATION
        if (binding.editTextOrganizationName != null && TextUtils.isEmpty(organizationName) && currentUser.getDisplayName() == null) { // Only require if not pre-filled and user has no display name
            binding.editTextOrganizationName.setError("Organization name is required.");
            binding.editTextOrganizationName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(eventName)) {
            binding.editTextEventName.setError("Event/Opportunity title is required.");
            binding.editTextEventName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(description)) {
            binding.editTextDescription.setError("Description is required.");
            binding.editTextDescription.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(date)) {
            binding.editTextDate.setError("Date is required.");
            Toast.makeText(getContext(), "Please select a date.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(time)) {
            binding.editTextTime.setError("Time is required.");
            Toast.makeText(getContext(), "Please select a time.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(location)) {
            binding.editTextLocation.setError("Location is required.");
            binding.editTextLocation.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(category)) {
            binding.editTextCategory.setError("Category is required.");
            binding.editTextCategory.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(volunteersNeededStr)) {
            binding.editTextVolunteersNeeded.setError("Number of volunteers is required.");
            binding.editTextVolunteersNeeded.requestFocus();
            return;
        }

        int volunteersNeeded;
        try {
            volunteersNeeded = Integer.parseInt(volunteersNeededStr);
            if (volunteersNeeded <= 0) {
                binding.editTextVolunteersNeeded.setError("Must be greater than 0.");
                binding.editTextVolunteersNeeded.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            binding.editTextVolunteersNeeded.setError("Invalid number for volunteers.");
            binding.editTextVolunteersNeeded.requestFocus();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(getContext(), "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            // Navigate to login
            return;
        }
        String employerUid = currentUser.getUid();
        // If organizationName was not provided and user has a display name, use it.
        // Otherwise, it might remain empty if the field itself isn't present in the layout.
        if (organizationName.isEmpty() && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()){
            organizationName = currentUser.getDisplayName();
        }


        binding.progressBarPostJob.setVisibility(View.VISIBLE);
        binding.buttonSubmitPost.setEnabled(false);

        String status = isEditMode ? currentStatusForEdit : "open";

        JobPosting jobPosting = new JobPosting(
                employerUid,
                organizationName, // This will be user's display name if not filled and field is not present
                eventName,
                jobTitle, // Can be empty if field not present
                description,
                date,
                time,
                location,
                category,
                volunteersNeeded,
                status
                // Firestore will handle timestamp for new posts
        );

        if (isEditMode && editingPostingId != null) {
            DocumentReference docRef = jobPostingsCollectionRef.document(editingPostingId);
            docRef.set(jobPosting, SetOptions.merge()) // SetOptions.merge to update
                    .addOnSuccessListener(aVoid -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Toast.makeText(getContext(), "Opportunity updated successfully!", Toast.LENGTH_SHORT).show();
                        navController.popBackStack(); // Go back after successful update
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.e(TAG, "Failed to update opportunity in Firestore.", e);
                        Toast.makeText(getContext(), "Failed to update. " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } else {
            jobPostingsCollectionRef.add(jobPosting)
                    .addOnSuccessListener(documentReference -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.d(TAG, "New opportunity posted with ID: " + documentReference.getId());
                        Toast.makeText(getContext(), "Opportunity posted successfully!", Toast.LENGTH_SHORT).show();
                        navController.popBackStack(); // Go back after successful post
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded() || binding == null) return;
                        binding.progressBarPostJob.setVisibility(View.GONE);
                        binding.buttonSubmitPost.setEnabled(true);
                        Log.e(TAG, "Failed to post opportunity to Firestore.", e);
                        Toast.makeText(getContext(), "Failed to post. " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Important for Fragments to prevent memory leaks
    }
}
