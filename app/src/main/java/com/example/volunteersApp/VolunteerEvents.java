/** VolunteerEvents.java(activity) has been replaced by VolunteeringFragment.java
 * VolunteeringFragment.java is the modern replacement that fits within single-activity,
 * navigation-component-based app


package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.volunteersApp.databinding.ActivityVolunteereventsBinding;
import com.example.volunteersApp.models.EventModel;
import com.example.volunteersApp.ui.eventdetails.EventDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
// FirebaseFirestoreException is not explicitly used but good for context if listener errors were handled differently
// import com.google.firebase.firestore.FirebaseFirestoreException;
// import com.google.firebase.Timestamp; // Not directly used in this version's logic

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;


public class VolunteerEvents extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private static final String TAG = "VolunteerEvents";
    private static final String EVENTS_COLLECTION = "events";
    private static final String FIELD_ORGANIZER_ID = "organizerId";
    private static final String FIELD_EVENT_DATETIME = "eventDateTime"; // Consistent with EventModel

    private ActivityVolunteereventsBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration eventsListenerRegistration;

    private VolunteerEventAdapter eventListAdapter; // Ensure this is your ListAdapter
    private final List<VolunteerEventItem> allEventItems = new ArrayList<>();

    // Spinner Strings
    private String SORT_BY_PROMPT;
    private String SORT_TITLE;
    private String SORT_PAYMENT_HIGH_LOW;
    private String SORT_LOCATION;
    private String SORT_DATE_NEWEST;
    private String SORT_DATE_OLDEST;

    private String FILTER_BY_PROMPT;
    private String FILTER_CATEGORY_CHARITY;
    private String FILTER_CATEGORY_SPORTS;
    private String FILTER_CATEGORY_CULTURAL;
    private String FILTER_CATEGORY_OTHERS;
    private String FILTER_ALL_CATEGORIES;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVolunteereventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeSpinnerStrings(); // Make sure this is called

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_volunteer_opportunities));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupSpinners();
        if (binding.searchViewEvents != null) {
            binding.searchViewEvents.setOnQueryTextListener(this);
        }
        setupSpinnerListeners();

        if (eventListAdapter != null) {
            eventListAdapter.setOnItemClickListener(volunteerEventItem -> {
                if (volunteerEventItem == null || volunteerEventItem.getEventDetails() == null) {
                    Log.w(TAG, "Clicked item or its details are null.");
                    Toast.makeText(this, R.string.error_event_data_missing, Toast.LENGTH_SHORT).show();
                    return;
                }
                EventModel details = volunteerEventItem.getEventDetails();
                if (details.getEventId() != null && !details.getEventId().isEmpty()) {
                    Log.d(TAG, "Clicked event: " + details.getTitle() + " with ID: " + details.getEventId());
                    // Navigate to EventDetailActivity (or Fragment if using Navigation Component)
                    Intent intent = new Intent(VolunteerEvents.this, EventDetailActivity.class);
                    // Define a constant for "EVENT_ID" in a common place or use EventDetailActivity.EXTRA_EVENT_ID
                    intent.putExtra("EVENT_ID", details.getEventId());
                    startActivity(intent);
                } else {
                    Log.w(TAG, "Clicked an event with null or empty eventId.");
                    Toast.makeText(this, R.string.error_event_id_missing, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void initializeSpinnerStrings() {
        // Sort Strings
        SORT_BY_PROMPT = getString(R.string.sort_by_prompt);
        SORT_TITLE = getString(R.string.sort_title);
        SORT_PAYMENT_HIGH_LOW = getString(R.string.sort_payment_high_low);
        SORT_LOCATION = getString(R.string.sort_location);
        SORT_DATE_NEWEST = getString(R.string.sort_date_newest);
        SORT_DATE_OLDEST = getString(R.string.sort_date_oldest);

        // Filter Strings - RESTORED THIS PART
        FILTER_BY_PROMPT = getString(R.string.filter_by_prompt);
        FILTER_ALL_CATEGORIES = getString(R.string.filter_all_categories); // Ensure this string exists
        FILTER_CATEGORY_CHARITY = getString(R.string.filter_category_charity);
        FILTER_CATEGORY_SPORTS = getString(R.string.filter_category_sports);
        FILTER_CATEGORY_CULTURAL = getString(R.string.filter_category_cultural);
        FILTER_CATEGORY_OTHERS = getString(R.string.filter_category_others);
    }


    private void setupRecyclerView() {
        // Pass a new instance of the callback if your adapter requires it for DiffUtil
        // For ListAdapter, the ItemCallback is usually passed in the constructor.
        eventListAdapter = new VolunteerEventAdapter(new VolunteerEventItemCallback());
        binding.recyclerViewVolunteerEvents.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewVolunteerEvents.setAdapter(eventListAdapter);
        binding.recyclerViewVolunteerEvents.setHasFixedSize(true);
    }

    private void setupSpinners() {
        // Sort Spinner
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{SORT_BY_PROMPT, SORT_TITLE, SORT_LOCATION, SORT_PAYMENT_HIGH_LOW, SORT_DATE_NEWEST, SORT_DATE_OLDEST});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.sortSpinner.setAdapter(sortAdapter);
        binding.sortSpinner.setSelection(0, false); // Avoid initial trigger

        // Filter Spinner
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{FILTER_BY_PROMPT, FILTER_ALL_CATEGORIES, FILTER_CATEGORY_CHARITY, FILTER_CATEGORY_SPORTS, FILTER_CATEGORY_CULTURAL, FILTER_CATEGORY_OTHERS});
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.filterSpinner.setAdapter(filterAdapter);
        binding.filterSpinner.setSelection(0, false); // Avoid initial trigger
    }

    private void setupSpinnerListeners() {
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            private boolean isInitialTriggerSort = true;
            private boolean isInitialTriggerFilter = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (binding == null) return; // Guard against null binding

                boolean isSortSpinner = parent.getId() == binding.sortSpinner.getId();
                boolean isFilterSpinner = parent.getId() == binding.filterSpinner.getId();

                if (isSortSpinner) {
                    if (isInitialTriggerSort && position == 0) {
                        isInitialTriggerSort = false;
                        return;
                    }
                    isInitialTriggerSort = false;
                } else if (isFilterSpinner) {
                    if (isInitialTriggerFilter && position == 0) {
                        isInitialTriggerFilter = false;
                        return;
                    }
                    isInitialTriggerFilter = false;
                }
                applyFilteringAndSorting();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {  }
        };
        if (binding != null) { // Guard against null binding
            binding.sortSpinner.setOnItemSelectedListener(listener);
            binding.filterSpinner.setOnItemSelectedListener(listener);
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "User not signed in. Cannot view events.");
            Toast.makeText(this, getString(R.string.toast_sign_in_to_view_events), Toast.LENGTH_LONG).show();
            synchronized (allEventItems) {
                allEventItems.clear();
            }
            applyFilteringAndSorting(); // Update UI to show empty state
            return;
        }
        attachFirestoreReadListener(currentUser.getUid());
    }

    private void attachFirestoreReadListener(String currentUserId) {
        if (eventsListenerRegistration != null) {
            Log.d(TAG, "Firestore listener already attached.");
            return;
        }

        Log.d(TAG, "Attaching Firestore listener for events NOT hosted by user: " + currentUserId);
        Query eventsQuery = db.collection(EVENTS_COLLECTION)
                .whereNotEqualTo(FIELD_ORGANIZER_ID, currentUserId)
                .orderBy(FIELD_EVENT_DATETIME, Query.Direction.ASCENDING);

        eventsListenerRegistration = eventsQuery.addSnapshotListener(this, (snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Firestore listen failed.", e);
                Toast.makeText(VolunteerEvents.this,
                        getString(R.string.toast_failed_to_load_events, e.getMessage()),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (snapshots == null) {
                Log.w(TAG, "Firestore snapshots were null.");
                return;
            }

            Log.d(TAG, "Data received from Firestore. Processing " + snapshots.size() + " documents.");
            List<VolunteerEventItem> newEventItems = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshots) {
                try {
                    EventModel eventDetails = doc.toObject(EventModel.class);
                    // eventDetails.setEventId(doc.getId()); // Handled by @DocumentId if EventModel uses it

                    if (eventDetails.getOrganizerId() == null || eventDetails.getOrganizerId().trim().isEmpty()){
                        Log.w(TAG, "Event " + eventDetails.getEventId() + " missing organizerId. Skipping or handling as needed.");
                        // Continue to next iteration if this event is problematic, or handle as needed
                        // continue;
                    }

                    VolunteerEventItem item = new VolunteerEventItem(
                            eventDetails.getEventId(), // Assuming eventId is set by @DocumentId or manually
                            eventDetails.getOrganizerId(),
                            eventDetails
                    );
                    newEventItems.add(item);

                } catch (Exception parseError) {
                    Log.e(TAG, "Error processing document " + doc.getId() + ": " + parseError.getMessage(), parseError);
                }
            }

            synchronized (allEventItems) {
                allEventItems.clear();
                allEventItems.addAll(newEventItems);
            }
            Log.d(TAG, "Processed " + newEventItems.size() + " event items. Total in allEventItems: " + allEventItems.size());
            applyFilteringAndSorting();
        });
    }

    private void applyFilteringAndSorting() {
        if (binding == null) {
            Log.w(TAG, "applyFilteringAndSorting: Binding is null, cannot proceed.");
            return;
        }

        String currentSearchQuery = "";
        if (binding.searchViewEvents != null && binding.searchViewEvents.getQuery() != null) {
            currentSearchQuery = binding.searchViewEvents.getQuery().toString().toLowerCase(Locale.ROOT).trim();
        }

        String selectedCategoryFilter = FILTER_ALL_CATEGORIES; // Default to all
        if (binding.filterSpinner.getSelectedItem() != null &&
                !Objects.equals(binding.filterSpinner.getSelectedItem().toString(), FILTER_BY_PROMPT)) {
            selectedCategoryFilter = binding.filterSpinner.getSelectedItem().toString();
        }

        String selectedSortType = SORT_TITLE; // Default sort if not specified or prompt selected
        if (binding.sortSpinner.getSelectedItem() != null &&
                !Objects.equals(binding.sortSpinner.getSelectedItem().toString(), SORT_BY_PROMPT)) {
            selectedSortType = binding.sortSpinner.getSelectedItem().toString();
        }

        List<VolunteerEventItem> itemsToDisplay;
        synchronized (allEventItems) {
            itemsToDisplay = new ArrayList<>(allEventItems);
        }

        // Apply Search Filter
        if (!currentSearchQuery.isEmpty()) {
            final String finalQuery = currentSearchQuery;
            itemsToDisplay = itemsToDisplay.stream()
                    .filter(item -> {
                        EventModel details = item.getEventDetails();
                        return details != null && details.getTitle() != null &&
                                details.getTitle().toLowerCase(Locale.ROOT).contains(finalQuery);
                    })
                    .collect(Collectors.toList());
        }

        // Apply Category Filter
        if (!selectedCategoryFilter.equals(FILTER_ALL_CATEGORIES)) {
            final String finalCategoryFilter = selectedCategoryFilter;
            itemsToDisplay = itemsToDisplay.stream()
                    .filter(item -> {
                        EventModel details = item.getEventDetails();
                        return details != null && details.getCategory() != null &&
                                details.getCategory().equalsIgnoreCase(finalCategoryFilter);
                    })
                    .collect(Collectors.toList());
        }

        // Apply Sorting
        applySortingToList(itemsToDisplay, selectedSortType);

        if (eventListAdapter != null) {
            eventListAdapter.submitList(itemsToDisplay); // For ListAdapter
        }

        // Update "No events" text visibility
        // Ensure binding.textViewNoEvents is the correct ID from your layout
        if (binding.textViewNoEvents != null) {
            if (itemsToDisplay.isEmpty()) {
                binding.textViewNoEvents.setVisibility(View.VISIBLE);
                boolean originalListWasEmpty;
                synchronized (allEventItems) {
                    originalListWasEmpty = allEventItems.isEmpty();
                }
                // Check if Firestore listener is active and the original list was empty.
                if (originalListWasEmpty && eventsListenerRegistration != null && !snapshotsWereNullDuringLastFetch()) {
                    binding.textViewNoEvents.setText(getString(R.string.text_no_events_available_at_all));
                } else {
                    binding.textViewNoEvents.setText(getString(R.string.text_no_events_match_filters));
                }
            } else {
                binding.textViewNoEvents.setVisibility(View.GONE);
            }
        }
        Log.d(TAG, "Displayed items after filter/sort/search: " + itemsToDisplay.size());
    }

    private boolean snapshotsWereNullDuringLastFetch() {
        return snapshotsWereNullDuringLastFetch;
    }

    // Helper method to track if snapshots were null (optional, for more precise "no events" message)
    private boolean snapshotsWereNullDuringLastFetch = false; // You'd set this in the listener

    private void applySortingToList(List<VolunteerEventItem> listToSort, String sortType) {
        if (listToSort == null || listToSort.isEmpty()) {
            return;
        }
        Comparator<VolunteerEventItem> comparator;
        if (Objects.equals(SORT_TITLE, sortType)) {
            comparator = new SortByTitle();
        } else if (Objects.equals(SORT_LOCATION, sortType)) {
            comparator = new SortByLocation();
        } else if (Objects.equals(SORT_PAYMENT_HIGH_LOW, sortType)) {
            comparator = new SortByPayment();
        } else if (Objects.equals(SORT_DATE_NEWEST, sortType)) {
            comparator = new SortByDate(false); // false for descending
        } else if (Objects.equals(SORT_DATE_OLDEST, sortType)) {
            comparator = new SortByDate(true);  // true for ascending
        } else {
            Log.w(TAG, "Unknown sort type: " + sortType + ". Defaulting to SortByTitle.");
            comparator = new SortByTitle(); // Default comparator
        }
        try {
            Collections.sort(listToSort, comparator);
        } catch (Exception ex) {
            Log.e(TAG, "Error during sorting list: " + ex.getMessage(), ex);
        }
    }


    @Override
    public boolean onQueryTextSubmit(String query) {
        applyFilteringAndSorting();
        if (binding != null && binding.searchViewEvents != null) {
            binding.searchViewEvents.clearFocus();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        applyFilteringAndSorting();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (eventsListenerRegistration != null) {
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null;
            Log.d(TAG, "Firestore listener removed in onStop.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventsListenerRegistration != null) { // Safeguard
            eventsListenerRegistration.remove();
            eventsListenerRegistration = null;
        }
        binding = null;
        Log.d(TAG, "VolunteerEvents onDestroy called.");
    }

    // --- Static Comparators ---

    static class SortByTitle implements Comparator<VolunteerEventItem> {
        @Override
        public int compare(VolunteerEventItem a, VolunteerEventItem b) {
            String titleA = "";
            String titleB = "";
            if (a != null && a.getEventDetails() != null && a.getEventDetails().getTitle() != null) {
                titleA = a.getEventDetails().getTitle();
            }
            if (b != null && b.getEventDetails() != null && b.getEventDetails().getTitle() != null) {
                titleB = b.getEventDetails().getTitle();
            }
            return titleA.compareToIgnoreCase(titleB);
        }
    }

    static class SortByLocation implements Comparator<VolunteerEventItem> {
        @Override
        public int compare(VolunteerEventItem a, VolunteerEventItem b) {
            String locA = "";
            String locB = "";
            // Assuming EventModel has getLocationName() or similar for the displayable location string
            if (a != null && a.getEventDetails() != null && a.getEventDetails().getLocationName() != null) {
                locA = a.getEventDetails().getLocationName();
            }
            if (b != null && b.getEventDetails() != null && b.getEventDetails().getLocationName() != null) {
                locB = b.getEventDetails().getLocationName();
            }
            return locA.compareToIgnoreCase(locB);
        }
    }


    static class SortByPayment implements Comparator<VolunteerEventItem> {
        @Override
        public int compare(VolunteerEventItem a, VolunteerEventItem b) {
            Double paymentA = 0.0;
            Double paymentB = 0.0;

            if (a != null && a.getEventDetails() != null && a.getEventDetails().getPayment() != null) {
                paymentA = a.getEventDetails().getPayment();
            }
            if (b != null && b.getEventDetails() != null && b.getEventDetails().getPayment() != null) {
                paymentB = b.getEventDetails().getPayment();
            }
            return Double.compare(paymentB, paymentA); // High to low
        }
    }

    static class SortByDate implements Comparator<VolunteerEventItem> {
        private final boolean ascending;

        public SortByDate(boolean ascending) {
            this.ascending = ascending;
        }

        @Override
        public int compare(VolunteerEventItem aItem, VolunteerEventItem bItem) {
            Date dateA = null;
            Date dateB = null;

            // Assuming EventModel has getEventDateObject() that converts Timestamp to Date
            if (aItem != null && aItem.getEventDetails() != null && aItem.getEventDetails().getEventDateObject() != null) {
                dateA = aItem.getEventDetails().getEventDateObject();
            }
            if (bItem != null && bItem.getEventDetails() != null && bItem.getEventDetails().getEventDateObject() != null) {
                dateB = bItem.getEventDetails().getEventDateObject();
            }

            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1; // nulls last
            if (dateB == null) return -1; // nulls last

            return ascending ? dateA.compareTo(dateB) : dateB.compareTo(dateA);
        }
    }
}
**/