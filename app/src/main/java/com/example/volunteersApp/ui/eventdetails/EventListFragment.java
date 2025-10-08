/** we no longer use EventListFragment.java instead we use EventDetailFragment.java

package com.example.volunteersApp.ui.eventdetails;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.R; // Your R file

import com.example.volunteersApp.adapters.EventAdapter; // Your existing event adapter
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.viewmodels.MyEventsViewModel; // Assuming this ViewModel exists

import java.util.ArrayList;
import java.util.HashSet; // For appliedEventIds if your adapter needs it

public class EventListFragment extends Fragment {

    private static final String ARG_EVENT_TYPE = "event_type";
    private String eventType;

    private RecyclerView recyclerView;
    private EventAdapter eventAdapter; // Your main EventAdapter
    private MyEventsViewModel myEventsViewModel;
    private TextView textViewEmptyList;


    public static EventListFragment newInstance(String eventType) {
        EventListFragment fragment = new EventListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_TYPE, eventType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventType = getArguments().getString(ARG_EVENT_TYPE);
        }
        // ViewModel scoped to the parent Fragment (MyEventsFragment) or Activity
        myEventsViewModel = new ViewModelProvider(requireParentFragment()).get(MyEventsViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_event_list, container, false); // Create this layout
        recyclerView = view.findViewById(R.id.recyclerViewEventList);
        textViewEmptyList = view.findViewById(R.id.textViewEmptyList);
        setupRecyclerView();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        observeViewModel();
    }

    private void setupRecyclerView() {
        // Initialize your EventAdapter.
        // The 'isOrganizerView' would be false.
        // The 'appliedEventIds' might not be directly relevant here unless displaying general events.
        // For 'My Events', the lists are already filtered, so 'hasApplied' in adapter logic might need adjustment.
        eventAdapter = new EventAdapter(
                requireContext(),
                false, // Not organizer view
                new EventAdapter.OnEventListener() {
                    @Override
                    public void onEventClick(EventModel event, int position) {
                        Toast.makeText(getContext(), "Clicked: " + event.getTitle(), Toast.LENGTH_SHORT).show();
                        // TODO: Navigate to Event Details

                new HashSet<>(), // Empty set for initial applied IDs as lists are pre-filtered
                null // FirebaseStorage instance, if your adapter needs it directly
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(eventAdapter);
    }

    private void observeViewModel() {
        if (eventType == null) return;

        switch (eventType) {
            case "APPLIED_PENDING":
                myEventsViewModel.appliedEvents.observe(getViewLifecycleOwner(), events -> {
                    if (events != null) {
                        eventAdapter.submitList(new ArrayList<>(events)); // ListAdapter needs a new list
                        textViewEmptyList.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                        if(events.isEmpty()) textViewEmptyList.setText("No events currently awaiting approval.");
                    }
                });
                break;
            case "APPROVED_UPCOMING":
                myEventsViewModel.approvedUpcomingEvents.observe(getViewLifecycleOwner(), events -> {
                    if (events != null) {
                        eventAdapter.submitList(new ArrayList<>(events));
                        textViewEmptyList.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                        if(events.isEmpty()) textViewEmptyList.setText("No upcoming approved events.");
                    }
                });
                break;
            case "PAST_ATTENDED":
                myEventsViewModel.pastAttendedEvents.observe(getViewLifecycleOwner(), events -> {
                    if (events != null) {
                        eventAdapter.submitList(new ArrayList<>(events));
                        textViewEmptyList.setVisibility(events.isEmpty() ? View.VISIBLE : View.GONE);
                        if(events.isEmpty()) textViewEmptyList.setText("No past attended events found.");
                    }
                });
                break;
        }
    }
}

**/