package com.example.volunteersApp.ui.hostevent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
// import androidx.core.content.ContextCompat; // Not used
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.CalenderActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentHostEventBinding;
import com.example.volunteersApp.models.EventModel; // Ensure this is the correct EventModel
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
// import com.google.firebase.firestore.GeoPoint; // If you plan to use it

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects; // For requireNonNull

public class HostEventFragment extends Fragment {
    private static final String TAG = "HostEventFragment";

    // View Binding (using _binding pattern)
    private FragmentHostEventBinding _binding;
    private FragmentHostEventBinding getBinding() { return _binding; } // Helper for non-null access after onViewCreated

    // UI related
    private ArrayList<String> typeList;
    private String eventTypeSelected;
    private String currentSelectedDateString; // From calendar picker
    private Date eventDateObject;             // Parsed date object
    private int eventTimeHours, eventTimeMinutes;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private CollectionReference eventsCollectionRef;
    private CollectionReference usersCollectionRef;

    // Navigation
    private NavController navController;
    private ActivityResultLauncher<Intent> calendarActivityResultLauncher;

    // State for editing
    private String existingEventId = null;
    private EventModel eventToEdit = null;

    // Constants for Firestore
    private static final String EVENTS_COLLECTION = "events";
    private static final String USERS_COLLECTION = "users";
    private static final String USER_HOSTED_EVENTS_SUBCOLLECTION = "hostedEvents";
    // private static final String ARG_EVENT_ID_NAV = "eventId"; // If passing eventId via nav args for editing

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true); // If you plan to add options menu items to the toolbar

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        eventsCollectionRef = db.collection(EVENTS_COLLECTION);
        usersCollectionRef = db.collection(USERS_COLLECTION);

        // Initialize lists and other non-view related members
        typeList = new ArrayList<>();

        // Handle arguments for editing
        if (getArguments() != null) {
            // Assuming you use Safe Args for passing eventId for editing:
            // existingEventId = HostEventFragmentArgs.fromBundle(getArguments()).getEventId();
            // If not using Safe Args for initial argument, use the old way:
            existingEventId = getArguments().getString("eventId"); // Ensure key matches how it's passed
            Log.d(TAG, "Received existingEventId for editing: " + existingEventId);
        }

        initializeCalendarLauncher();
    }

    private void initializeCalendarLauncher() {
        calendarActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String selectedDateFromCalendar = result.getData().getStringExtra("selected_date");
                        if (selectedDateFromCalendar != null && !selectedDateFromCalendar.isEmpty()) {
                            currentSelectedDateString = selectedDateFromCalendar;
                            if (_binding != null) {
                                getBinding().HostAnEventDate.setText(currentSelectedDateString);
                                getBinding().HostAnEventDate.setError(null); // Clear previous error
                            }
                            parseDateString(currentSelectedDateString);
                        } else {
                            Log.w(TAG, "Received null or empty date from calendar activity.");
                            Toast.makeText(getContext(), R.string.no_date_selected, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        _binding = FragmentHostEventBinding.inflate(inflater, container, false);
        return getBinding().getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        currentUser = mAuth.getCurrentUser(); // Refresh current user state

        setupToolbar();
        setupSpinner(); // Initialize spinner data and listeners

        if (existingEventId != null) {
            getBinding().toolbar.setTitle(getString(R.string.edit_event_title));
            getBinding().NextStep.setText(getString(R.string.update_event_button));
            loadEventForEditing();
        } else {
            getBinding().toolbar.setTitle(getString(R.string.host_an_event_title));
            getBinding().NextStep.setText(getString(R.string.next_step_button));
            loadPassedDataOrDefaultDate(); // For new event, check passed data or set default date
            setupTimePicker();          // Setup default time for new event
            if (currentUser != null && currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                getBinding().HostAnEventHostName.setText(currentUser.getDisplayName());
            }
        }
        setupButtonListeners();
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(getBinding().toolbar);
        }
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(getBinding().toolbar, navController, appBarConfiguration);
        // Title is set in onViewCreated based on mode (new/edit)
    }

    private void setupSpinner() {
        typeList.clear();
        // Consider adding a prompt as the first item if desired
        // typeList.add(getString(R.string.select_event_type_prompt));
        typeList.add("Charity");
        typeList.add("Sports");
        typeList.add("Cultural");
        typeList.add("Community Service");
        typeList.add("Education & Workshops");
        typeList.add("Environmental");
        typeList.add("Health & Wellness");
        typeList.add("Others");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, typeList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        getBinding().types.setAdapter(dataAdapter);
        getBinding().types.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // If you had a prompt at position 0:
                // if (position == 0 && typeList.get(0).equals(getString(R.string.select_event_type_prompt))) {
                //     eventTypeSelected = null;
                // } else {
                eventTypeSelected = (String) parent.getItemAtPosition(position);
                // }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                eventTypeSelected = null;
            }
        });
    }

    private void loadPassedDataOrDefaultDate() {
        Bundle arguments = getArguments();
        String dateFromArgs = null;
        if (arguments != null && arguments.containsKey("date")) { // Check if "date" key exists
            dateFromArgs = arguments.getString("date");
        }

        if (dateFromArgs != null && !dateFromArgs.isEmpty()) {
            currentSelectedDateString = dateFromArgs;
        } else if (getActivity() != null && getActivity().getIntent().hasExtra("date")) { // Legacy from direct Activity start
            currentSelectedDateString = getActivity().getIntent().getStringExtra("date");
        }

        if (currentSelectedDateString != null && !currentSelectedDateString.isEmpty()) {
            getBinding().HostAnEventDate.setText(currentSelectedDateString);
            parseDateString(currentSelectedDateString);
        } else {
            // Default to today's date if nothing is passed
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            currentSelectedDateString = sdf.format(cal.getTime());
            getBinding().HostAnEventDate.setText(currentSelectedDateString);
            eventDateObject = cal.getTime(); // Initialize eventDateObject with today
        }
    }


    private void setupTimePicker() {
        Calendar cal = Calendar.getInstance();
        // If editing and time already set, use that, otherwise default to current time
        if (eventToEdit == null) { // Only for new events or if eventToEdit time isn't set
            eventTimeHours = cal.get(Calendar.HOUR_OF_DAY);
            eventTimeMinutes = cal.get(Calendar.MINUTE);
        }
        // else, eventTimeHours/Minutes would have been set in populateFieldsForEditing

        getBinding().HostAnEventTime.setIs24HourView(true); // Or false based on preference
        getBinding().HostAnEventTime.setHour(eventTimeHours);
        getBinding().HostAnEventTime.setMinute(eventTimeMinutes);

        getBinding().HostAnEventTime.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            eventTimeHours = hourOfDay;
            eventTimeMinutes = minute;
        });
    }

    private void setupButtonListeners() {
        getBinding().GoToCalender.setOnClickListener(v -> {
            Intent intentToCalendar = new Intent(requireActivity(), CalenderActivity.class);
            if (currentSelectedDateString != null) {
                intentToCalendar.putExtra("current_date", currentSelectedDateString);
            }
            calendarActivityResultLauncher.launch(intentToCalendar);
        });

        getBinding().CancelHost.setOnClickListener(v -> navController.popBackStack());
        getBinding().NextStep.setOnClickListener(v -> saveEventToFirestore());
    }

    private void loadEventForEditing() {
        if (existingEventId == null) {
            Log.w(TAG, "existingEventId is null, cannot load event for editing.");
            Toast.makeText(getContext(), R.string.error_event_id_missing_for_edit, Toast.LENGTH_SHORT).show();
            return;
        }
        getBinding().progressBarHostEvent.setVisibility(View.VISIBLE);
        eventsCollectionRef.document(existingEventId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded() || _binding == null) return; // Fragment not attached or binding null
                    getBinding().progressBarHostEvent.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        eventToEdit = documentSnapshot.toObject(EventModel.class);
                        if (eventToEdit != null) {
                            // The @DocumentId in EventModel should set its eventId.
                            // If not, or for belt-and-suspenders:
                            // eventToEdit.setEventId(documentSnapshot.getId());
                            populateFieldsForEditing();
                        } else {
                            Toast.makeText(getContext(), R.string.error_loading_event_data, Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Failed to parse document to EventModel for ID: " + existingEventId);
                        }
                    } else {
                        Toast.makeText(getContext(), R.string.event_not_found, Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "Event document not found for ID: " + existingEventId);
                        // Optional: Navigate back if event not found
                        // navController.popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || _binding == null) return;
                    getBinding().progressBarHostEvent.setVisibility(View.GONE);
                    Log.e(TAG, "Error fetching event for editing with ID: " + existingEventId, e);
                    Toast.makeText(getContext(), R.string.error_loading_event_data, Toast.LENGTH_SHORT).show();
                });
    }

    private void populateFieldsForEditing() {
        if (eventToEdit == null || _binding == null) return;

        getBinding().HostAnEventName.setText(eventToEdit.getTitle());
        getBinding().HostAnEventHostName.setText(eventToEdit.getOrganizerName());
        getBinding().HostAnEventLocation.setText(eventToEdit.getLocationName());
        getBinding().HostAnEventDescription.setText(eventToEdit.getDescription());

        if (eventToEdit.getPayment() != null) {
            getBinding().HostAnEventMoney.setText(String.format(Locale.US, "%.2f", eventToEdit.getPayment()));
        } else {
            getBinding().HostAnEventMoney.setText("");
        }

        if (eventToEdit.getRequiredSkills() != null && !eventToEdit.getRequiredSkills().isEmpty()) {
            getBinding().HostAnEventSkillsRequired.setText(String.join(", ", eventToEdit.getRequiredSkills()));
        } else {
            getBinding().HostAnEventSkillsRequired.setText("");
        }
        getBinding().HostAnEventImageUrl.setText(eventToEdit.getImageUrl() != null ? eventToEdit.getImageUrl() : "");

        // In HostEventFragment.java (this will now work if volunteerLimit is Integer)
        if (eventToEdit.getVolunteerLimit() != null) {
            getBinding().HostAnEventVolunteerLimit.setText(String.valueOf(eventToEdit.getVolunteerLimit()));
        } else {
            getBinding().HostAnEventVolunteerLimit.setText("");
        }


        // Type Spinner
        if (eventToEdit.getCategory() != null) {
            int spinnerPosition = typeList.indexOf(eventToEdit.getCategory());
            if (spinnerPosition >= 0) {
                getBinding().types.setSelection(spinnerPosition); // This will also trigger onItemSelected
                eventTypeSelected = eventToEdit.getCategory();
            } else {
                Log.w(TAG, "Category from eventToEdit ('" + eventToEdit.getCategory() + "') not found in typeList.");
                getBinding().types.setSelection(0); // Default to first item or prompt
                eventTypeSelected = typeList.isEmpty() ? null : typeList.get(0);
            }
        }

        // Date and Time
        if (eventToEdit.getEventDateTime() != null) {
            Date eventDate = eventToEdit.getEventDateTime().toDate();
            Calendar cal = Calendar.getInstance();
            cal.setTime(eventDate);

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            currentSelectedDateString = dateFormat.format(eventDate);
            getBinding().HostAnEventDate.setText(currentSelectedDateString);
            eventDateObject = cal.getTime();

            eventTimeHours = cal.get(Calendar.HOUR_OF_DAY);
            eventTimeMinutes = cal.get(Calendar.MINUTE);
            // setupTimePicker() will be called generally, but we need to ensure values are set for existing event.
            // If setupTimePicker() is called after this, it might override these if not handled.
            // So, ensure TimePicker is updated here or that setupTimePicker reads these values.
            getBinding().HostAnEventTime.setHour(eventTimeHours);
            getBinding().HostAnEventTime.setMinute(eventTimeMinutes);
        }
    }

    private boolean validateInputs() {
        if (_binding == null) return false;

        boolean isValid = true;

        // Date
        if (eventDateObject == null) {
            getBinding().HostAnEventDate.requestFocus();
            getBinding().HostAnEventDate.setError(getString(R.string.error_select_event_date));
            Toast.makeText(getContext(), R.string.error_select_event_date_toast, Toast.LENGTH_LONG).show();
            isValid = false;
        } else {
            getBinding().HostAnEventDate.setError(null);
        }

        // Event Name (Title)
        if (TextUtils.isEmpty(Objects.requireNonNull(getBinding().HostAnEventName.getText()).toString().trim())) {
            getBinding().HostAnEventNameLayout.setError(getString(R.string.error_event_name_required));
            if (isValid) getBinding().HostAnEventName.requestFocus(); // Only request focus on first error
            isValid = false;
        } else {
            getBinding().HostAnEventNameLayout.setError(null);
        }

        // Organizer Name
        if (TextUtils.isEmpty(Objects.requireNonNull(getBinding().HostAnEventHostName.getText()).toString().trim())) {
            getBinding().HostAnEventHostNameLayout.setError(getString(R.string.error_organizer_name_required));
            if (isValid) getBinding().HostAnEventHostName.requestFocus();
            isValid = false;
        } else {
            getBinding().HostAnEventHostNameLayout.setError(null);
        }

        // Location Name
        if (TextUtils.isEmpty(Objects.requireNonNull(getBinding().HostAnEventLocation.getText()).toString().trim())) {
            getBinding().HostAnEventLocationLayout.setError(getString(R.string.error_event_location_required));
            if (isValid) getBinding().HostAnEventLocation.requestFocus();
            isValid = false;
        } else {
            getBinding().HostAnEventLocationLayout.setError(null);
        }

        // Type
        if (eventTypeSelected == null || eventTypeSelected.isEmpty() /* || (getBinding().types.getSelectedItemPosition() == 0 && typeList.get(0).equals(getString(R.string.select_event_type_prompt))) */) {
            Toast.makeText(getContext(), R.string.error_select_event_type, Toast.LENGTH_SHORT).show();
            if (isValid) getBinding().types.requestFocus();
            isValid = false;
        }

        // Description
        if (TextUtils.isEmpty(Objects.requireNonNull(getBinding().HostAnEventDescription.getText()).toString().trim())) {
            getBinding().HostAnEventDescriptionLayout.setError(getString(R.string.error_description_required));
            if (isValid) getBinding().HostAnEventDescription.requestFocus();
            isValid = false;
        } else {
            getBinding().HostAnEventDescriptionLayout.setError(null);
        }

        // Payment (Optional, validate if present)
        String paymentStr = Objects.requireNonNull(getBinding().HostAnEventMoney.getText()).toString().trim();
        if (!paymentStr.isEmpty()) {
            try {
                double paymentAmount = Double.parseDouble(paymentStr);
                if (paymentAmount < 0) {
                    getBinding().HostAnEventMoneyLayout.setError(getString(R.string.error_payment_negative));
                    if (isValid) getBinding().HostAnEventMoney.requestFocus();
                    isValid = false;
                } else {
                    getBinding().HostAnEventMoneyLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                getBinding().HostAnEventMoneyLayout.setError(getString(R.string.error_invalid_payment_number));
                if (isValid) getBinding().HostAnEventMoney.requestFocus();
                isValid = false;
            }
        } else {
            getBinding().HostAnEventMoneyLayout.setError(null); // Clear error if empty and optional
        }

        // Volunteer Limit
        String volunteerLimitStr = Objects.requireNonNull(getBinding().HostAnEventVolunteerLimit.getText()).toString().trim();
        if (TextUtils.isEmpty(volunteerLimitStr)) {
            getBinding().HostAnEventVolunteerLimitLayout.setError(getString(R.string.error_volunteer_limit_required));
            if (isValid) getBinding().HostAnEventVolunteerLimit.requestFocus();
            isValid = false;
        } else {
            try {
                int volLim = Integer.parseInt(volunteerLimitStr);
                if (volLim <= 0) { // Limit should typically be positive
                    getBinding().HostAnEventVolunteerLimitLayout.setError(getString(R.string.error_volunteer_limit_positive));
                    if (isValid) getBinding().HostAnEventVolunteerLimit.requestFocus();
                    isValid = false;
                } else {
                    getBinding().HostAnEventVolunteerLimitLayout.setError(null);
                }
            } catch (NumberFormatException e) {
                getBinding().HostAnEventVolunteerLimitLayout.setError(getString(R.string.error_invalid_volunteer_limit));
                if (isValid) getBinding().HostAnEventVolunteerLimit.requestFocus();
                isValid = false;
            }
        }

        // Past Date/Time Check (only for new events)
        if (isValid && eventDateObject != null && existingEventId == null) {
            Calendar eventCalForValidation = Calendar.getInstance();
            eventCalForValidation.setTime(eventDateObject);
            eventCalForValidation.set(Calendar.HOUR_OF_DAY, eventTimeHours);
            eventCalForValidation.set(Calendar.MINUTE, eventTimeMinutes);
            eventCalForValidation.set(Calendar.SECOND, 0);
            eventCalForValidation.set(Calendar.MILLISECOND, 0);

            Calendar nowCal = Calendar.getInstance();
            // Clear time from nowCal for just date comparison if event time is at start of day
            nowCal.set(Calendar.HOUR_OF_DAY, 0);
            nowCal.set(Calendar.MINUTE, 0);
            nowCal.set(Calendar.SECOND, 0);
            nowCal.set(Calendar.MILLISECOND, 0);


            if (eventCalForValidation.before(nowCal)) {
                // Check if the event date is before today (ignoring time for this specific check, if intended for "today or future")
                // Or if you want to be precise including time:
                // Calendar preciseNow = Calendar.getInstance();
                // if(eventCalForValidation.before(preciseNow)) { ... }
                getBinding().HostAnEventDate.setError(getString(R.string.error_event_in_past));
                Toast.makeText(getContext(), R.string.error_event_in_past_toast, Toast.LENGTH_LONG).show();
                if (isValid) getBinding().HostAnEventDate.requestFocus();
                isValid = false;
            } else {
                getBinding().HostAnEventDate.setError(null); // Clear if previously set
            }
        }
        return isValid;
    }


    private void saveEventToFirestore() {
        Log.d(TAG, "saveEventToFirestore: Attempting to save event.");
        if (_binding == null) {
            Log.e(TAG, "Binding is null during save. View might be destroyed.");
            if (getContext() != null) Toast.makeText(getContext(), R.string.error_view_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!validateInputs()) {
            Log.w(TAG, "Input validation failed before saving.");
            // ProgressBar and button state are handled inside validateInputs if it shows Toasts/sets errors
            getBinding().progressBarHostEvent.setVisibility(View.GONE);
            getBinding().NextStep.setEnabled(true);
            return;
        }

        getBinding().progressBarHostEvent.setVisibility(View.VISIBLE);
        getBinding().NextStep.setEnabled(false);


        if (currentUser == null) {
            Log.e(TAG, "User not authenticated for saving.");
            Toast.makeText(getContext(), R.string.error_user_not_authenticated_login, Toast.LENGTH_LONG).show();
            getBinding().progressBarHostEvent.setVisibility(View.GONE);
            getBinding().NextStep.setEnabled(true);
            return;
        }
        String organizerId = currentUser.getUid();
        String organizerNameStr = (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                ? currentUser.getDisplayName()
                : Objects.requireNonNull(getBinding().HostAnEventHostName.getText()).toString().trim(); // Fallback to field if display name is null/empty

        // --- Collect data from UI ---
        final String eventTitleStr = Objects.requireNonNull(getBinding().HostAnEventName.getText()).toString().trim();
        // final String organizerNameStr = Objects.requireNonNull(getBinding().HostAnEventHostName.getText()).toString().trim(); // Replaced with above
        final String locationNameStr = Objects.requireNonNull(getBinding().HostAnEventLocation.getText()).toString().trim();
        final String locationAddressStr = ""; // Get from another UI field if you add one.
        final String descriptionStr = Objects.requireNonNull(getBinding().HostAnEventDescription.getText()).toString().trim();
        final String paymentStr = Objects.requireNonNull(getBinding().HostAnEventMoney.getText()).toString().trim();
        final String imageUrlStr = Objects.requireNonNull(getBinding().HostAnEventImageUrl.getText()).toString().trim();
        final String skillsRequiredInput = Objects.requireNonNull(getBinding().HostAnEventSkillsRequired.getText()).toString().trim();
        final String volunteerLimitStr = Objects.requireNonNull(getBinding().HostAnEventVolunteerLimit.getText()).toString().trim();

        List<String> skillsList = new ArrayList<>();
        if (!skillsRequiredInput.isEmpty()) {
            skillsList.addAll(Arrays.asList(skillsRequiredInput.split("\\s*,\\s*")));
        }

        Calendar calendar = Calendar.getInstance();
        if (eventDateObject == null) {
            Log.e(TAG, "Event date object is critically null before creating timestamp. Should have been caught by validation.");
            Toast.makeText(getContext(), R.string.error_critical_event_date_missing, Toast.LENGTH_SHORT).show();
            getBinding().progressBarHostEvent.setVisibility(View.GONE);
            getBinding().NextStep.setEnabled(true);
            return;
        }
        calendar.setTime(eventDateObject);
        calendar.set(Calendar.HOUR_OF_DAY, eventTimeHours);
        calendar.set(Calendar.MINUTE, eventTimeMinutes);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Timestamp eventFirebaseTimestamp = new Timestamp(calendar.getTime());

        EventModel eventData;
        DocumentReference eventDocRef;
        boolean isNewEvent = (existingEventId == null);

        if (isNewEvent) { // Creating new event
            eventData = new EventModel();
            eventDocRef = eventsCollectionRef.document(); // Generate new ID
            eventData.setEventId(eventDocRef.getId());    // Set the auto-generated ID to the model
            eventData.setOrganizerId(organizerId);
            eventData.setParticipantsCount(0);
            eventData.setActive(true);
            eventData.setStatus("UPCOMING");
            // For new events, createdAt and lastUpdatedAt can be set by @ServerTimestamp in EventModel
            // If not using @ServerTimestamp, set them manually:
            // eventData.setCreatedAt(new Timestamp(new Date()));
            // eventData.setLastUpdatedAt(new Timestamp(new Date()));
        } else { // Editing existing event
            if (eventToEdit == null) { // Should not happen if loadEventForEditing was successful
                Log.e(TAG, "eventToEdit is null during save operation for existing event: " + existingEventId);
                Toast.makeText(getContext(), R.string.error_saving_event_data_missing, Toast.LENGTH_SHORT).show();
                getBinding().progressBarHostEvent.setVisibility(View.GONE);
                getBinding().NextStep.setEnabled(true);
                return;
            }
            eventData = eventToEdit;
            eventDocRef = eventsCollectionRef.document(existingEventId);
            // If not using @ServerTimestamp for lastUpdatedAt on updates:
            // eventData.setLastUpdatedAt(new Timestamp(new Date()));
        }

        // Set/Update common fields
        eventData.setTitle(eventTitleStr);
        eventData.setOrganizerName(organizerNameStr);
        eventData.setCategory(eventTypeSelected);
        eventData.setDescription(descriptionStr);
        eventData.setLocationName(locationNameStr);
        eventData.setLocationAddress(locationAddressStr);
        // eventData.setGeoPoint(null); // Set if you collect GeoPoint
        eventData.setEventDateTime(eventFirebaseTimestamp);
        // eventData.setDuration(""); // Set if you collect duration

        if (!paymentStr.isEmpty()) {
            try {
                eventData.setPayment(Double.parseDouble(paymentStr));
            } catch (NumberFormatException e) { eventData.setPayment(0.0); /* Or null */ }
        } else {
            eventData.setPayment(null); // Or 0.0 if you prefer non-null double
        }

        eventData.setImageUrl(imageUrlStr.isEmpty() ? null : imageUrlStr); // Store null if empty
        eventData.setRequiredSkills(skillsList.isEmpty() ? null : skillsList); // Store null if empty

        try {
            eventData.setVolunteerLimit(Integer.parseInt(volunteerLimitStr));
        } catch (NumberFormatException e) {
            eventData.setVolunteerLimit(0); // Default or error state
            Log.w(TAG, "Invalid volunteer limit string: " + volunteerLimitStr + ", defaulting to 0.");
        }

        // --- Firestore Write Batch ---
        WriteBatch batch = db.batch();
        batch.set(eventDocRef, eventData); // Create or Overwrite main event document

        // For user's hosted events subcollection, only add/update if it's related to the current user
        // This check is more relevant if an admin could edit any event. For self-hosting, it's implicit.
        if (organizerId.equals(eventData.getOrganizerId())) {
            Map<String, Object> hostedEventData = new HashMap<>();
            // Only store essential reference data or data needed for quick display in a list
            hostedEventData.put("title", eventData.getTitle());
            hostedEventData.put("eventDateTime", eventData.getEventDateTime());
            hostedEventData.put("locationName", eventData.getLocationName());
            // hostedEventData.put("category", eventData.getCategory()); // Optional

            DocumentReference userHostedEventRef = usersCollectionRef.document(organizerId)
                    .collection(USER_HOSTED_EVENTS_SUBCOLLECTION).document(eventDocRef.getId());
            batch.set(userHostedEventRef, hostedEventData);
        } else {
            Log.w(TAG, "Organizer ID mismatch. Current user: " + organizerId + ", Event's organizerId: " + eventData.getOrganizerId() + ". Skipping user hosted event update.");
        }


        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Event successfully " + (isNewEvent ? "created" : "updated") + " with ID: " + eventDocRef.getId());
                    Toast.makeText(getContext(),
                            getString(isNewEvent ? R.string.event_created_successfully : R.string.event_updated_successfully),
                            Toast.LENGTH_SHORT).show();
                    if (_binding != null) {
                        getBinding().progressBarHostEvent.setVisibility(View.GONE);
                        // NextStep button remains disabled to prevent double submission, navigation will occur
                    }

                    if (navController != null && getContext() != null &&
                            navController.getCurrentDestination() != null &&
                            navController.getCurrentDestination().getId() == R.id.nav_host_event) { // Ensure current destination ID
                        try {
                            // !!! THIS IS THE LINE WITH THE ERROR !!!
                            // Make sure 'actionNavHostEventToNavHostFinal' and its parameters
                            // correctly match your navigation graph definition.
                            // The parameter names in the generated method will be based on the
                            // 'android:name' of the arguments in your nav_host_final destination.
                            // Example: If nav_host_final expects "eventId" and "eventTitle"// HostEventFragmentDirections.actionNavHostEventToNavHostFinal(eventId, eventTitle)



// NavDirections action = HostEventFragmentDirections.actionNavHostEventToNavHostFinal(...); // OLD - WRONG for this graph
                          //  NavDirections action = HostEventFragmentDirections.actionNavHostEventOrganizerSideToHostFinalOrganizerSide(
                               //     eventDocRef.getId(), // This will be passed as 'eventId' to HostFinalFragment
                          //         eventTitleStr        // This will be passed as 'eventTitle' to HostFinalFragment
                         //   );
                         //   navController.navigate(action);

                          //  NavDirections action = HostEventFragmentDirections.actionNavHostEventToNavHostFinal(
                           //         eventDocRef.getId(), // Parameter 1 (e.g., eventId)
                           //         eventTitleStr        // Parameter 2 (e.g., eventTitle)
                           // );
                            //  navController.navigate(action);




                        } catch (IllegalArgumentException navEx) {
                            Log.e(TAG, "Navigation failed (IllegalArgumentException): " + navEx.getMessage() + ". Check arguments for action to nav_host_final. Navigating back.", navEx);
                            navController.popBackStack(); // Fallback
                        } catch (Exception generalNavEx) { // Catch other potential navigation exceptions
                            Log.e(TAG, "General navigation error: " + generalNavEx.getMessage() + ". Navigating back.", generalNavEx);
                            navController.popBackStack();
                        }
                    } else if (navController != null) {
                        Log.d(TAG, "Not on host event screen or context is null, popping back stack.");
                        navController.popBackStack(); // Fallback if not on the expected screen
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error " + (isNewEvent ? "creating" : "updating") + " event", e);
                    if (getContext() != null) Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (_binding != null) {
                        getBinding().progressBarHostEvent.setVisibility(View.GONE);
                        getBinding().NextStep.setEnabled(true); // Re-enable button on failure
                    }
                });
    }

    private void parseDateString(String dateString) {
        if (dateString == null || dateString.equalsIgnoreCase(getString(R.string.dd_mm_yyyy)) || dateString.trim().isEmpty()) {
            eventDateObject = null;
            Log.d(TAG, "Date string is empty or default, eventDateObject set to null.");
            return;
        }
        // Using a consistent date format
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        format.setLenient(false); // Strict parsing
        try {
            eventDateObject = format.parse(dateString);
            Log.d(TAG, "Parsed date: " + eventDateObject);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date: " + dateString, e);
            if (getContext() != null) Toast.makeText(getContext(), getString(R.string.invalid_date_format_toast, dateString), Toast.LENGTH_SHORT).show();
            eventDateObject = null;
            if (_binding != null) {
                getBinding().HostAnEventDate.setText(getString(R.string.dd_mm_yyyy)); // Reset to placeholder
                getBinding().HostAnEventDate.setError(getString(R.string.invalid_date_reselect));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        _binding = null; // Crucial for preventing memory leaks with ViewBinding
        Log.d(TAG, "onDestroyView called, _binding set to null.");
    }
}
