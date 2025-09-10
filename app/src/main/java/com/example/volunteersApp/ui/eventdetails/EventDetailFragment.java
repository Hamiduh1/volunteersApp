package com.example.volunteersApp.ui.eventdetails;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.EditEventActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentEventDetailBinding;
import com.example.volunteersApp.models.EventDetails; // Using the updated EventDetails POJO
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class EventDetailFragment extends Fragment {

    private static final String TAG = "EventDetailFragment";
    public static final String ARG_EVENT_ID = "eventId";

    private FragmentEventDetailBinding binding;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;

    private String eventId;
    private EventDetails currentEvent;
    private boolean isOrganizer = false;
    private NavController navController;

    private final ActivityResultLauncher<Intent> editEventLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    Toast.makeText(getContext(), R.string.event_updated_successfully, Toast.LENGTH_SHORT).show();
                    loadEventDetails(); // Reload to show changes
                } else if (result.getResultCode() == EditEventActivity.RESULT_EVENT_DELETED) {
                    Toast.makeText(getContext(), R.string.event_deleted_successfully, Toast.LENGTH_SHORT).show();
                    if (navController != null) {
                        navController.popBackStack();
                    }
                }
            });

    public EventDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
            // If using Safe Args, it would look like:
            // eventId = EventDetailFragmentArgs.fromBundle(getArguments()).getEventId();
        }

        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Event ID is null or empty in onCreate.");
            // Actual error handling and navigation back should happen in onViewCreated
            // to ensure UI is available for Toasts or navigation.
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEventDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        setupToolbar();

        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(getContext(), R.string.event_not_found_error, Toast.LENGTH_LONG).show();
            if (navController != null) {
                navController.popBackStack();
            }
            return;
        }

        loadEventDetails();
        binding.buttonEventDetailAction.setOnClickListener(v -> handleActionButtonClick());
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity && binding.toolbarEventDetail != null) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbarEventDetail);
        }

        if (navController != null && binding.toolbarEventDetail != null) {
            AppBarConfiguration appBarConfiguration =
                    new AppBarConfiguration.Builder(navController.getGraph()).build();
            // Use collapsingToolbarLayout with NavController for title collapsing behavior
            NavigationUI.setupWithNavController(binding.collapsingToolbarEventDetail, binding.toolbarEventDetail, navController, appBarConfiguration);
        }
        // Set initial title for collapsing toolbar if needed, often left blank to be filled by content
        if (binding.collapsingToolbarEventDetail != null) {
            binding.collapsingToolbarEventDetail.setTitle(""); // Or some placeholder until content loads
        }
    }

    private void loadEventDetails() {
        if (eventId == null || eventId.isEmpty()) {
            // This case should ideally be caught before calling loadEventDetails
            Log.e(TAG, "Attempted to load event details with null or empty eventId.");
            return;
        }
        setLoadingState(true);

        DocumentReference eventRef = db.collection("events").document(eventId);
        eventRef.get().addOnCompleteListener(task -> {
            if (!isAdded() || getContext() == null) return; // Fragment not attached or context lost

            setLoadingState(false);
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    currentEvent = document.toObject(EventDetails.class);
                    if (currentEvent != null) {
                        currentEvent.setEventId(document.getId()); // Ensure POJO has the ID
                        populateEventDetails();
                        checkIfUserIsOrganizer();
                        requireActivity().invalidateOptionsMenu(); // To update menu items based on organizer status
                    } else {
                        Log.e(TAG, "Failed to parse event document to EventDetails for ID: " + eventId);
                        Toast.makeText(getContext(), R.string.error_loading_event_details_generic, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "No such event document with ID: " + eventId);
                    Toast.makeText(getContext(), R.string.event_not_found_error, Toast.LENGTH_SHORT).show();
                    if (navController != null) navController.popBackStack();
                }
            } else {
                Log.e(TAG, "Error getting event details for ID: " + eventId, task.getException());
                String errorMessage = (task.getException() != null && task.getException().getMessage() != null)
                        ? task.getException().getMessage()
                        : getString(R.string.unknown_error);
                Toast.makeText(getContext(), getString(R.string.error_loading_event_details, errorMessage), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoadingState(boolean isLoading) {
        if (binding == null) return; // View already destroyed
        binding.progressBarEventDetail.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.scrollViewEventDetail.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        binding.buttonEventDetailAction.setEnabled(!isLoading); // Disable button during load
    }

    private void populateEventDetails() {
        if (currentEvent == null || binding == null || !isAdded() || getContext() == null) {
            Log.w(TAG, "populateEventDetails called with null currentEvent, binding, or context.");
            return;
        }

        // Collapsing Toolbar Title
        if (binding.collapsingToolbarEventDetail != null) {
            binding.collapsingToolbarEventDetail.setTitle(currentEvent.getTitle());
        } else if (binding.toolbarEventDetail != null) { // Fallback if no collapsing toolbar
            binding.toolbarEventDetail.setTitle(currentEvent.getTitle());
        }

        binding.textViewEventDetailName.setText(currentEvent.getTitle());
        binding.textViewEventDetailDescription.setText(currentEvent.getDescription());
        binding.textViewEventDetailLocation.setText(getString(R.string.label_location_colon_xml, currentEvent.getLocationName()));

        // Date & Time using formatted strings from EventDetails
        binding.textViewEventDetailDateTime.setText(
                getString(R.string.label_date_time_colon_xml,
                        currentEvent.getFormattedDate() != null ? currentEvent.getFormattedDate() : getString(R.string.not_available_short),
                        currentEvent.getFormattedTime() != null ? currentEvent.getFormattedTime() : ""
                )
        );

        // Organizer Name
        binding.textViewEventDetailOrganizer.setText(getString(R.string.label_hosted_by_colon_xml, currentEvent.getOrganizerName()));

        // Event Type
        binding.textViewEventDetailCategory.setText(getString(R.string.label_type_colon_xml, currentEvent.getType()));

        // Skills Required
        if (currentEvent.getSkillsRequired() != null && !currentEvent.getSkillsRequired().isEmpty() && !currentEvent.getSkillsRequired().equalsIgnoreCase("None")) {
            binding.layoutSkillsRequired.setVisibility(View.VISIBLE);
            binding.textViewEventDetailSkills.setText(getString(R.string.label_skills_colon_xml, currentEvent.getSkillsRequired()));
        } else {
            binding.layoutSkillsRequired.setVisibility(View.GONE);
        }

        // Volunteer Limit/Slots
        String limitText;
        if (currentEvent.getVolunteerLimit() != null && currentEvent.getVolunteerLimit() > 0) {
            limitText = String.valueOf(currentEvent.getVolunteerLimit());
        } else {
            limitText = getString(R.string.no_limit_text); // "Unlimited" or similar
        }
        int currentRegistered = currentEvent.getRegisteredVolunteersCount() != null ? currentEvent.getRegisteredVolunteersCount() : 0;
        binding.layoutVolunteerSlots.setVisibility(View.VISIBLE);
        binding.textViewEventDetailSlots.setText(getString(R.string.label_slots_colon_xml, currentRegistered, limitText));

        // Displaying "Registered Volunteers" instead of "Applied" for clarity based on EventDetails.registeredVolunteersCount
        // If you need to show PENDING applicants, use currentEvent.getAppliedVolunteerUids().size()
        binding.layoutVolunteersApplied.setVisibility(View.VISIBLE); // Consider renaming this layout/textview in XML to reflect "Registered"
        binding.textViewVolunteersAppliedCount.setText(getString(R.string.label_registered_volunteers_colon_xml, String.valueOf(currentRegistered)));

        // Event Status
        if (currentEvent.getStatus() != null && !currentEvent.getStatus().isEmpty()) {
            binding.layoutEventStatus.setVisibility(View.VISIBLE);
            binding.textViewEventDetailStatus.setText(getString(R.string.label_status_colon_xml, currentEvent.getStatus()));
        } else {
            binding.layoutEventStatus.setVisibility(View.GONE);
        }

        // Payment Information
        if (currentEvent.getPayment() != null && currentEvent.getPayment() > 0) {
            binding.layoutPayment.setVisibility(View.VISIBLE);
            binding.textViewEventDetailPayment.setText(getString(R.string.label_payment_colon_xml, String.format(java.util.Locale.getDefault(), "%.2f", currentEvent.getPayment())));
        } else {
            binding.layoutPayment.setVisibility(View.GONE);
        }

        // Required Volunteers (if different from limit, or an estimate)
        if (currentEvent.getRequiredVolunteers() != null && currentEvent.getRequiredVolunteers() > 0) {
            binding.layoutRequiredVolunteers.setVisibility(View.VISIBLE);
            binding.textViewEventDetailRequiredVolunteers.setText(getString(R.string.label_required_volunteers_colon_xml, String.valueOf(currentEvent.getRequiredVolunteers())));
        } else {
            binding.layoutRequiredVolunteers.setVisibility(View.GONE);
        }


        // Event Banner/Image
        if (currentEvent.getImageUrl() != null && !currentEvent.getImageUrl().isEmpty()) {
            Glide.with(requireContext())
                    .load(currentEvent.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder) // Ensure this drawable exists
                    .error(R.drawable.ic_image_error)           // Ensure this drawable exists
                    .into(binding.imageViewEventDetailHeader);
        } else {
            binding.imageViewEventDetailHeader.setImageResource(R.drawable.ic_image_placeholder); // Default image
        }

        updateActionButton();
    }

    private void checkIfUserIsOrganizer() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null && currentEvent != null && currentEvent.getOrganizerId() != null) {
            isOrganizer = firebaseUser.getUid().equals(currentEvent.getOrganizerId());
        } else {
            isOrganizer = false;
        }
        Log.d(TAG, "Current user is organizer: " + isOrganizer);
        updateActionButton(); // Update button based on organizer status
        if (isAdded()) { // Ensure fragment is added before invalidating options menu
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void handleActionButtonClick() {
        if (currentEvent == null || !isAdded() || getContext() == null) {
            Toast.makeText(getContext(), R.string.event_details_not_loaded_yet, Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.please_log_in_action, Toast.LENGTH_SHORT).show();
            // Optionally navigate to login screen
            return;
        }

        if (isOrganizer) {
            // Organizer's action: e.g., view applicants/manage event
            // Example: Navigate to a screen to view applicants
            // EventDetailFragmentDirections.ActionViewApplicants action = EventDetailFragmentDirections.actionViewApplicants(currentEvent.getEventId());
            // navController.navigate(action);
            Toast.makeText(getContext(), R.string.organizer_action_placeholder_manage_event, Toast.LENGTH_SHORT).show();
        } else {
            // Volunteer's action: apply, withdraw, etc.
            // This requires more complex logic based on currentEvent.isOpenForApplication(),
            // currentEvent.getAppliedVolunteerUids().contains(currentUser.getUid()), etc.
            if (currentEvent.getAppliedVolunteerUids().contains(currentUser.getUid())) {
                Toast.makeText(getContext(), R.string.volunteer_action_placeholder_withdraw, Toast.LENGTH_SHORT).show();
                // TODO: Implement withdrawal logic
            } else if (currentEvent.getAcceptedVolunteerUids().contains(currentUser.getUid())) {
                Toast.makeText(getContext(), R.string.volunteer_action_placeholder_already_accepted, Toast.LENGTH_SHORT).show();
            } else if (!currentEvent.isOpenForApplication()) {
                Toast.makeText(getContext(), R.string.event_applications_closed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.volunteer_action_placeholder_apply, Toast.LENGTH_SHORT).show();
                // TODO: Implement application logic (e.g., add currentUserId to appliedVolunteerUids in Firestore)
                // db.collection("events").document(eventId)
                //   .update("appliedVolunteerUids", FieldValue.arrayUnion(currentUser.getUid()))
                //   .addOnSuccessListener(...)
                //   .addOnFailureListener(...);
            }
        }
    }

    private void updateActionButton() {
        if (binding == null || currentEvent == null || !isAdded() || getContext() == null) {
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        binding.buttonEventDetailAction.setVisibility(View.VISIBLE); // Generally visible

        if (isOrganizer) {
            binding.buttonEventDetailAction.setText(R.string.manage_event_button); // Organizer action
        } else if (currentUser != null) {
            // Volunteer logic
            if (currentEvent.getAcceptedVolunteerUids().contains(currentUser.getUid())) {
                binding.buttonEventDetailAction.setText(R.string.status_accepted_button);
                binding.buttonEventDetailAction.setEnabled(false); // Already accepted
            } else if (currentEvent.getAppliedVolunteerUids().contains(currentUser.getUid())) {
                binding.buttonEventDetailAction.setText(R.string.withdraw_application_button);
                binding.buttonEventDetailAction.setEnabled(true);
            } else if (!currentEvent.isOpenForApplication()) {
                binding.buttonEventDetailAction.setText(R.string.applications_closed_button);
                binding.buttonEventDetailAction.setEnabled(false);
            } else {
                binding.buttonEventDetailAction.setText(R.string.apply_for_event_button);
                binding.buttonEventDetailAction.setEnabled(true);
            }
        } else {
            // No user logged in, or details not loaded
            binding.buttonEventDetailAction.setText(R.string.apply_for_event_button); // Default or "Login to Apply"
            binding.buttonEventDetailAction.setEnabled(currentUser != null); // Enable if logged in, but not organizer
        }
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.event_detail_organizer_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem editItem = menu.findItem(R.id.action_edit_event_details);
        MenuItem deleteItem = menu.findItem(R.id.action_delete_event_details);

        boolean showOrganizerOptions = currentEvent != null && isOrganizer;
        if (editItem != null) editItem.setVisible(showOrganizerOptions);
        if (deleteItem != null) deleteItem.setVisible(showOrganizerOptions);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_edit_event_details) {
            if (isOrganizer && currentEvent != null) {
                openEditEventActivity();
            }
            return true;
        } else if (itemId == R.id.action_delete_event_details) {
            if (isOrganizer && currentEvent != null) {
                showDeleteConfirmationDialog();
            }
            return true;
        }
        // Allow NavigationUI to handle Up button and other menu items if not handled above
        return NavigationUI.onNavDestinationSelected(item, navController) || super.onOptionsItemSelected(item);
    }

    private void openEditEventActivity() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(getContext(), R.string.cannot_edit_missing_details, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(getActivity(), EditEventActivity.class);
        intent.putExtra(EditEventActivity.EXTRA_EDIT_EVENT_ID, currentEvent.getEventId());
        editEventLauncher.launch(intent);
    }

    private void showDeleteConfirmationDialog() {
        if (!isAdded() || getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete_event_title)
                .setMessage(R.string.confirm_delete_event_message)
                .setPositiveButton(R.string.delete_confirm, (dialog, which) -> deleteEvent())
                .setNegativeButton(android.R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert) // Optional: add an icon
                .show();
    }

    private void deleteEvent() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            if (isAdded() && getContext() != null) Toast.makeText(getContext(), R.string.cannot_delete_missing_id, Toast.LENGTH_SHORT).show();
            return;
        }
        setLoadingState(true);

        String eventIdToDelete = currentEvent.getEventId();
        String imageUrlToDelete = currentEvent.getImageUrl();

        db.collection("events").document(eventIdToDelete)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event document successfully deleted: " + eventIdToDelete);
                    if (imageUrlToDelete != null && !imageUrlToDelete.isEmpty() && imageUrlToDelete.startsWith("gs://")) {
                        deleteImageFromStorage(imageUrlToDelete, eventIdToDelete); // Pass eventId for context in messages
                    } else {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), R.string.delete_event_success_no_image_delete, Toast.LENGTH_SHORT).show();
                        setLoadingState(false);
                        if (navController != null) navController.popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setLoadingState(false);
                    Log.e(TAG, "Error deleting event document: " + eventIdToDelete, e);
                    Toast.makeText(getContext(), getString(R.string.delete_event_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteImageFromStorage(String imageUrl, String eventIdForContext) {
        // Validation for imageUrl already done before calling this, but defensive check is fine.
        StorageReference photoRef = storage.getReferenceFromUrl(imageUrl);
        photoRef.delete().addOnCompleteListener(task -> {
            // setLoadingState(false) and navigation should happen regardless of image deletion outcome,
            // as the primary action (event document deletion) was successful or handled.

            if (!isAdded()) return; // Check if fragment is still added

            if (task.isSuccessful()) {
                Log.d(TAG, "Event image successfully deleted for event " + eventIdForContext + ": " + imageUrl);
                Toast.makeText(getContext(), R.string.delete_event_and_image_success, Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "Error deleting event image for event " + eventIdForContext + ": " + imageUrl, task.getException());
                // Inform user document was deleted, but image failed.
                Toast.makeText(getContext(), R.string.delete_event_success_image_fail, Toast.LENGTH_LONG).show();
            }
            setLoadingState(false);
            if (navController != null) navController.popBackStack();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Important to prevent memory leaks
    }

    // --- Ensure these string resources are defined in res/values/strings.xml ---
    // <string name="label_location_colon_xml">Location: %1$s</string>
    // <string name="label_hosted_by_colon_xml">Hosted by: %1$s</string>
    // <string name="label_type_colon_xml">Type: %1$s</string>
    // <string name="label_skills_colon_xml">Skills Required: %1$s</string>
    // <string name="label_slots_colon_xml">Slots: %1$d / %2$s</string>
    // <string name="label_registered_volunteers_colon_xml">Registered: %1$s</string>
    // <string name="label_status_colon_xml">Status: %1$s</string>
    // <string name="label_payment_colon_xml">Payment: $%1$s</string>
    // <string name="label_required_volunteers_colon_xml">Needs: %1$s volunteers</string>
    // <string name="no_limit_text">Unlimited</string>
    // <string name="not_available_short">N/A</string>
    // <string name="manage_event_button">Manage Event</string>
    // <string name="apply_for_event_button">Apply</string>
    // <string name="withdraw_application_button">Withdraw Application</string>
    // <string name="applications_closed_button">Applications Closed</string>
    // <string name="status_accepted_button">Accepted</string>
    // <string name="event_applications_closed">Applications for this event are currently closed.</string>
    // <string name="please_log_in_action">Please log in to perform this action.</string>
    // <string name="organizer_action_placeholder_manage_event">Manage Event (Organizer Action)</string>
    // <string name="volunteer_action_placeholder_withdraw">Withdraw Application (Volunteer Action)</string>
    // <string name="volunteer_action_placeholder_already_accepted">You are already accepted for this event.</string>
    // <string name="volunteer_action_placeholder_apply">Apply for Event (Volunteer Action)</string>
    // <string name="delete_event_success_no_image_delete">Event deleted. Image was not present or not deleted.</string>
    // <string name="delete_event_and_image_success">Event and associated image deleted successfully.</string>
    // <string name="delete_event_success_image_fail">Event document deleted, but failed to remove image from storage.</string>
    // <string name="error_loading_event_details_generic">Could not load event details.</string>
    // <string name="event_updated_successfully">Event updated successfully.</string>
    // <string name="event_deleted_successfully">Event deleted successfully.</string>
    // <string name="event_not_found_error">Event not found.</string>
    // <string name="error_loading_event_details">Error loading event details: %1$s</string>
    // <string name="unknown_error">An unknown error occurred.</string>
    // <string name="event_details_not_loaded_yet">Event details are not loaded yet.</string>
    // <string name="cannot_edit_missing_details">Cannot edit: event details are missing.</string>
    // <string name="confirm_delete_event_title">Confirm Delete</string>
    // <string name="confirm_delete_event_message">Are you sure you want to delete this event? This action cannot be undone.</string>
    // <string name="delete_confirm">Delete</string>
    // <string name="cannot_delete_missing_id">Cannot delete: event ID is missing.</string>
    // <string name="delete_event_error">Error deleting event: %1$s</string>
    // Drawables: ic_image_placeholder, ic_image_error
}

