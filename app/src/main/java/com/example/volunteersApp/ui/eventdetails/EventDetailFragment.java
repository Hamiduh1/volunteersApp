/**  simplify the fragment to use the new ViewModel logic.
It no longer fetches data itself; it just tells the ViewModel to start listening
**/
package com.example.volunteersApp.ui.eventdetails;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import com.bumptech.glide.Glide;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentEventDetailBinding;
import com.example.volunteersApp.models.EventDetails;
import com.example.volunteersApp.viewmodels.EventDetailViewModel; // Use your new ViewModel
import com.google.firebase.auth.FirebaseAuth;

public class EventDetailFragment extends Fragment {

    private static final String TAG = "EventDetailFragment";
    private FragmentEventDetailBinding binding;
    private EventDetailViewModel viewModel;
    private String eventId;

    public EventDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(EventDetailViewModel.class);

        // Get eventId from Safe Args
        if (getArguments() != null) {
            eventId = EventDetailFragmentArgs.fromBundle(getArguments()).getEventId();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEventDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupToolbar();
        observeViewModel();

        if (eventId != null && !eventId.isEmpty()) {
            // MODIFIED: Tell the ViewModel to start listening
            viewModel.listenForEventDetails(eventId);
        } else {
            showErrorState(getString(R.string.event_not_found_or_invalid_id));
        }

        binding.buttonEventDetailAction.setOnClickListener(v -> handleActionButtonClick());
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBarEventDetail.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            if (isLoading) {
                binding.scrollViewEventDetail.setVisibility(View.INVISIBLE);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                showErrorState(error);
            }
        });

        viewModel.getEventDetails().observe(getViewLifecycleOwner(), event -> {
            if (event != null) {
                showContentState();
                populateEventDetails(event);
            } else {
                // The errorMessage LiveData will handle showing the error UI if event becomes null
            }
        });

        viewModel.getVolunteerApplicationStatus().observe(getViewLifecycleOwner(), status -> {
            // Every time the status changes (e.g., from APPLIED to APPROVED),
            // this will be called, and the button will update.
            updateActionButton();
        });

        viewModel.getIsOrganizer().observe(getViewLifecycleOwner(), isOrganizer -> {
            // This will also be called on data change to update UI accordingly
            updateActionButton();
        });
    }

    private void populateEventDetails(@NonNull EventDetails event) {
        if (!isAdded() || binding == null) return;

        binding.collapsingToolbarEventDetail.setTitle(event.getTitle());
        binding.textViewEventDetailName.setText(event.getTitle());
        binding.textViewEventDetailDescription.setText(event.getDescription());
        binding.textViewEventDetailLocation.setText(getString(R.string.label_location_colon_xml, event.getLocationName()));
        binding.textViewEventDetailOrganizer.setText(getString(R.string.label_hosted_by_colon_xml, event.getOrganizerName()));

        if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
            Glide.with(requireContext())
                    .load(event.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .into(binding.imageViewEventDetailHeader);
        } else {
            binding.imageViewEventDetailHeader.setImageResource(R.drawable.ic_image_placeholder);
        }

        // Add any other UI population logic here
    }

    private void updateActionButton() {
        if (!isAdded() || viewModel.getEventDetails().getValue() == null) {
            if (binding != null) { // Add a null check for binding
                binding.buttonEventDetailAction.setVisibility(View.GONE);
            }
            return;
        }

        Boolean isOrganizer = viewModel.getIsOrganizer().getValue();
        // Use the fully qualified name for the enum type
        EventDetailViewModel.ApplicationStatus status = viewModel.getVolunteerApplicationStatus().getValue();

        if (Boolean.TRUE.equals(isOrganizer)) {
            // Organizer's View: maybe a "View Applicants" button
            binding.buttonEventDetailAction.setVisibility(View.VISIBLE);
            binding.buttonEventDetailAction.setText("View Applicants");
            // Set OnClickListener for viewing applicants
            binding.buttonEventDetailAction.setEnabled(true); // Ensure it's enabled for the organizer
        } else {
            // Volunteer's View
            binding.buttonEventDetailAction.setVisibility(View.VISIBLE);
            if (status != null) {
                // FIXED: Use the full enum name (ClassName.VALUE) in the case statements
                switch (status) {
                    case ACCEPTED: // This was named ACCEPTED in the ViewModel
                        binding.buttonEventDetailAction.setText("Approved");
                        binding.buttonEventDetailAction.setEnabled(false);
                        break;
                    case APPLIED:
                        binding.buttonEventDetailAction.setText("Application Sent");
                        binding.buttonEventDetailAction.setEnabled(false);
                        break;
                    case CAN_APPLY: // This was named CAN_APPLY in the ViewModel
                        binding.buttonEventDetailAction.setText("Apply");
                        binding.buttonEventDetailAction.setEnabled(true);
                        break;
                    case NOT_APPLIED_CLOSED: // This was named NOT_APPLIED_CLOSED in the ViewModel
                        binding.buttonEventDetailAction.setText("Event Closed");
                        binding.buttonEventDetailAction.setEnabled(false);
                        break;
                    case UNKNOWN:
                        binding.buttonEventDetailAction.setVisibility(View.GONE); // Hide button if status is unknown
                        break;
                }
            }
        }
    }


    private void handleActionButtonClick() {
        // Logic for applying to the event
        Toast.makeText(getContext(), "Apply button clicked!", Toast.LENGTH_SHORT).show();
    }

    private void showContentState() {
        binding.scrollViewEventDetail.setVisibility(View.VISIBLE);
        binding.textViewErrorEventDetail.setVisibility(View.GONE);
    }

    private void showErrorState(String message) {
        if (!isAdded() || binding == null) return;
        binding.progressBarEventDetail.setVisibility(View.GONE);
        binding.scrollViewEventDetail.setVisibility(View.GONE);
        binding.textViewErrorEventDetail.setVisibility(View.VISIBLE);
        binding.textViewErrorEventDetail.setText(message);
        binding.buttonEventDetailAction.setVisibility(View.GONE);
    }

    private void setupToolbar() {
        // Simplified toolbar setup
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbarEventDetail);
            NavController navController = Navigation.findNavController(requireView());
            NavigationUI.setupActionBarWithNavController(((AppCompatActivity) getActivity()), navController);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Crucial for preventing memory leaks
    }
}
