package com.example.volunteersApp; // Ensure this matches your package structure

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import com.example.volunteersApp.models.EventDetails; // IMPORT YOUR EventDetails MODEL
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
// import java.util.Collections; // No longer needed for Collections.emptyList() for the main object
import java.util.Date;
import java.util.HashMap;
// import java.util.List; // No longer directly needed here for the main object
import java.util.Locale;
import java.util.Map;

public class HostAnEvent extends AppCompatActivity {
    private static final String TAG = "HostAnEventFirestore";
    public static final int REQUEST_CODE_CALENDAR = 1;

    // Firebase Auth
    FirebaseAuth mAuth;
    FirebaseUser currentUser;

    // Firestore
    FirebaseFirestore db;
    CollectionReference eventsCollectionRef;
    CollectionReference usersCollectionRef;

    // UI Elements
    EditText eventNameEditText, eventDescEditText, eventPayEditText,
            hostNameEditText, imageUrlEditText, requiredVolunteersEditText, // 'hostNameEditText' will be 'organizerName'
            volunteerLimitEditText, skillsRequiredEditText, locationEditText; // Added locationEditText
    TextView eventDateTextView;
    TimePicker hostAnEventTimePicker;
    Spinner typeSpinner;
    Button nextButton, goToCalendarButton, cancelHostButton;

    // Event Data
    Date eventDateObject;
    int eventTimeHours, eventTimeMinutes;
    String type;
    private String currentSelectedDateString;

    // Constants
    private static final String EVENTS_COLLECTION = "events";
    private static final String USERS_COLLECTION = "users";
    private static final String USER_HOSTED_EVENTS_SUBCOLLECTION = "hostedEvents";
    // DEFAULT_EVENT_LOCATION is no longer needed if we have an EditText
    // private static final String DEFAULT_EVENT_STATUS = "upcoming"; // This is handled by EventDetails constructor

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hostanevent);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Host An Event");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        eventsCollectionRef = db.collection(EVENTS_COLLECTION);
        usersCollectionRef = db.collection(USERS_COLLECTION);

        initializeUI();
        setupSpinner();
        loadIntentData();
        setupTimePicker();
        setupButtonListeners();

        // Pre-fill organizer/host name
        if (currentUser != null) {
            if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                hostNameEditText.setText(currentUser.getDisplayName());
            } else if (currentUser.getEmail() != null) {
                // Fallback to email if display name is not set, or leave blank for user to fill
                // hostNameEditText.setText(currentUser.getEmail());
            }
        }
    }

    private void initializeUI() {
        nextButton = findViewById(R.id.NextStep);
        eventNameEditText = findViewById(R.id.HostAnEventName);
        hostNameEditText = findViewById(R.id.HostAnEventHostName); // This will map to organizerName
        locationEditText = findViewById(R.id.HostAnEventLocation); // Make sure this ID exists in your XML
        eventDateTextView = findViewById(R.id.HostAnEventDate);
        eventDescEditText = findViewById(R.id.HostAnEventDescription);
        hostAnEventTimePicker = findViewById(R.id.HostAnEventTime);
        eventPayEditText = findViewById(R.id.HostAnEventMoney);
        imageUrlEditText = findViewById(R.id.HostAnEventImageUrl);
        requiredVolunteersEditText = findViewById(R.id.HostAnEventRequiredVolunteers);
        volunteerLimitEditText = findViewById(R.id.HostAnEventVolunteerLimit);
        skillsRequiredEditText = findViewById(R.id.HostAnEventSkillsRequired);
        goToCalendarButton = findViewById(R.id.GoToCalender);
        cancelHostButton = findViewById(R.id.CancelHost);
        typeSpinner = findViewById(R.id.types);
    }

    private void setupSpinner() {
        ArrayList<String> typeList = new ArrayList<>();
        typeList.add("Charity");
        typeList.add("Sports");
        typeList.add("Cultural");
        typeList.add("Community Service");
        typeList.add("Others"); // Ensure this matches what EventDetails expects or handles
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, typeList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(dataAdapter);
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                type = (String) parent.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { type = null; }
        });
    }

    private void loadIntentData() {
        Intent incoming = getIntent();
        currentSelectedDateString = incoming.getStringExtra("date");
        if (currentSelectedDateString != null && !currentSelectedDateString.isEmpty()) {
            eventDateTextView.setText(currentSelectedDateString);
            parseDateString(currentSelectedDateString);
        } else {
            eventDateTextView.setText(R.string.dd_mm_yyyy); // Make sure this string resource exists
        }
    }

    private void setupTimePicker() {
        Calendar cal = Calendar.getInstance();
        eventTimeHours = cal.get(Calendar.HOUR_OF_DAY);
        eventTimeMinutes = cal.get(Calendar.MINUTE);
        hostAnEventTimePicker.setIs24HourView(true);
        hostAnEventTimePicker.setHour(eventTimeHours);
        hostAnEventTimePicker.setMinute(eventTimeMinutes);
        hostAnEventTimePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            eventTimeHours = hourOfDay;
            eventTimeMinutes = minute;
            Log.d(TAG, "Time changed to: " + hourOfDay + ":" + minute);
        });
    }

    private void setupButtonListeners() {
        goToCalendarButton.setOnClickListener(v -> {
            Intent intent = new Intent(HostAnEvent.this, CalenderActivity.class);
            if (currentSelectedDateString != null) {
                intent.putExtra("current_date", currentSelectedDateString);
            }
            startActivityForResult(intent, REQUEST_CODE_CALENDAR);
        });
        cancelHostButton.setOnClickListener(v -> finish());
        nextButton.setOnClickListener(view -> {
            Log.d(TAG, "Next button clicked. Attempting to save event.");
            saveEventToFirestore();
        });
    }

    private void saveEventToFirestore() {
        Log.d(TAG, "saveEventToFirestore: Method started.");

        if (currentUser == null) {
            Log.e(TAG, "User not authenticated. Cannot save event.");
            Toast.makeText(HostAnEvent.this, "User not authenticated. Please login.", Toast.LENGTH_LONG).show();
            return;
        }
        String organizerIdStr = currentUser.getUid(); // This is the organizerId
        Log.d(TAG, "User authenticated. Organizer ID: " + organizerIdStr);

        if (!validateInputs()) {
            Log.w(TAG, "Input validation failed.");
            return;
        }
        Log.d(TAG, "Input validations passed.");

        // --- Collect data from UI ---
        final String eventNameStr = eventNameEditText.getText().toString().trim();
        final String organizerNameStr = hostNameEditText.getText().toString().trim(); // This is organizerName
        final String locationStr = locationEditText.getText().toString().trim();
        final String descriptionStr = eventDescEditText.getText().toString().trim();
        final String paymentStr = eventPayEditText.getText().toString().trim();
        final String imageUrlStr = imageUrlEditText.getText().toString().trim();
        final String requiredVolunteersStr = requiredVolunteersEditText.getText().toString().trim();
        final String volunteerLimitStr = volunteerLimitEditText.getText().toString().trim();
        final String skillsRequiredStr = skillsRequiredEditText.getText().toString().trim();

        // --- Prepare data for EventDetails object ---
        double paymentAmount = 0.0;
        if (!paymentStr.isEmpty()) {
            paymentAmount = Double.parseDouble(paymentStr); // Validation done in validateInputs
        }

        int requiredVolunteersInt = 0; // Defaulting to 0 as per EventDetails
        if (!requiredVolunteersStr.isEmpty()) {
            requiredVolunteersInt = Integer.parseInt(requiredVolunteersStr); // Validation done
        }

        Integer volunteerLimitInt = 0; // Defaulting as per EventDetails (0 can mean no limit or handled by model)
        if (!volunteerLimitStr.isEmpty()) {
            volunteerLimitInt = Integer.parseInt(volunteerLimitStr); // Validation done
        }

        Calendar eventDateTimeCalendar = Calendar.getInstance();
        eventDateTimeCalendar.setTime(eventDateObject); // Date part
        eventDateTimeCalendar.set(Calendar.HOUR_OF_DAY, eventTimeHours); // Time part
        eventDateTimeCalendar.set(Calendar.MINUTE, eventTimeMinutes);
        eventDateTimeCalendar.set(Calendar.SECOND, 0);
        eventDateTimeCalendar.set(Calendar.MILLISECOND, 0);
        Date fullEventDateTime = eventDateTimeCalendar.getTime();
        Timestamp eventFirebaseTimestamp = new Timestamp(fullEventDateTime);
        Log.d(TAG, "Firebase Timestamp CREATED: " + eventFirebaseTimestamp.toDate().toString());

        // --- Create EventDetails POJO instance ---
        DocumentReference newEventDocRef = eventsCollectionRef.document(); // Generate ID for the event
        String eventId = newEventDocRef.getId();

        EventDetails newEvent = new EventDetails(); // Uses the no-arg constructor which sets defaults

        // Set fields from UI, relying on EventDetails setters for any logic/defaults
        newEvent.setEventId(eventId); // Set the generated event ID
        newEvent.setEventName(eventNameStr);
        newEvent.setDescription(descriptionStr);
        newEvent.setLocation(locationStr);
        newEvent.setType(type); // From spinner

        if (!TextUtils.isEmpty(skillsRequiredStr)) {
            newEvent.setSkillsRequired(skillsRequiredStr);
        } else {
            newEvent.setSkillsRequired("None"); // Or whatever default EventDetails expects or handles
        }

        if (!TextUtils.isEmpty(imageUrlStr)) {
            newEvent.setImageUrl(imageUrlStr);
        } else {
            newEvent.setImageUrl(null); // Explicitly null if not provided
        }

        newEvent.setOrganizerId(organizerIdStr);
        newEvent.setOrganizerName(organizerNameStr);
        newEvent.setEventTimestamp(eventFirebaseTimestamp);
        newEvent.setVolunteerLimit(volunteerLimitInt);
        // newEvent.setRegisteredVolunteersCount(0); // Already defaulted by EventDetails constructor
        // newEvent.setStatus("upcoming"); // Already defaulted by EventDetails constructor
        newEvent.setPayment(paymentAmount);
        // newEvent.setAvgRating(0.0); // Already defaulted
        // newEvent.setCloseEntries(false); // Already defaulted
       // newEvent.setRequiredVolunteers(requiredVolunteersLong);
        newEvent.setRequiredVolunteers((long) requiredVolunteersInt);


        // newEvent.setRegisteredVolunteerUids(new ArrayList<>()); // Already defaulted by EventDetails constructor
        // newEvent.setAppliedVolunteerUids(new ArrayList<>()); // Already defaulted
        // newEvent.setAcceptedVolunteerUids(new ArrayList<>()); // Already defaulted

        Log.d(TAG, "EventDetails POJO to save: " + newEvent.toString());

        // --- Prepare for Firestore Batch Write ---
        DocumentReference userHostedEventRef = usersCollectionRef
                .document(organizerIdStr) // Use organizerIdStr here
                .collection(USER_HOSTED_EVENTS_SUBCOLLECTION)
                .document(eventId);

        WriteBatch batch = db.batch();

        // 1. Set the main event document using the EventDetails POJO
        batch.set(newEventDocRef, newEvent);

        // 2. Set the document in the user's hostedEvents subcollection
        Map<String, Object> hostedEventData = new HashMap<>();
        hostedEventData.put("eventName", newEvent.getTitle()); // Get from POJO for consistency
        hostedEventData.put("eventTimestamp", newEvent.getEventTimestamp()); // Get from POJO
        hostedEventData.put("location", newEvent.getLocationName()); // Optional: for quick display
        hostedEventData.put("createdAt", FieldValue.serverTimestamp()); // Audit field
        // Note: The document ID for this subcollection entry is eventId
        batch.set(userHostedEventRef, hostedEventData);

        Log.d(TAG, "Attempting batch.commit().");
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Batch commit SUCCESS. Event ID: " + eventId);
                    Toast.makeText(HostAnEvent.this, "Event created successfully!", Toast.LENGTH_LONG).show();

                    // Navigate to next activity
                    Intent i = new Intent(HostAnEvent.this, MainActivity.class); // Or to EventDetailsActivity
                    // i.putExtra("eventId", eventId); // If needed by next activity
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "**************** Firestore Write FAILED ****************", e);
                    Toast.makeText(HostAnEvent.this, "Failed to create event. " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
        Log.d(TAG, "saveEventToFirestore: Method finished (after calling commit).");
    }

    private boolean validateInputs() {
        if (eventDateObject == null) {
            eventDateTextView.requestFocus();
            eventDateTextView.setError("Please select a valid event date.");
            Toast.makeText(getApplicationContext(), "Select a valid Event Date!", Toast.LENGTH_LONG).show();
            return false;
        }

        if (TextUtils.isEmpty(eventNameEditText.getText().toString().trim())) {
            eventNameEditText.setError("Event name required"); eventNameEditText.requestFocus(); return false;
        }
        if (TextUtils.isEmpty(hostNameEditText.getText().toString().trim())) { // This is for organizerName
            hostNameEditText.setError("Organizer name required"); hostNameEditText.requestFocus(); return false;
        }
        if (TextUtils.isEmpty(locationEditText.getText().toString().trim())) {
            locationEditText.setError("Location required"); locationEditText.requestFocus(); return false;
        }
        if (TextUtils.isEmpty(eventDescEditText.getText().toString().trim())) {
            eventDescEditText.setError("Description required"); eventDescEditText.requestFocus(); return false;
        }
        if (type == null || type.isEmpty()) {
            Toast.makeText(this, "Select event type", Toast.LENGTH_SHORT).show();
            // Optionally set error on the spinner itself if you have a way to do it,
            // or just rely on the Toast.
            typeSpinner.requestFocus(); // Might not show visual error but sets focus
            return false;
        }

        // Skills required is optional in EventDetails (defaults to "None"), but if you want it mandatory here:
        // if (TextUtils.isEmpty(skillsRequiredEditText.getText().toString().trim())) {
        //     skillsRequiredEditText.setError("Skills required (or enter 'None')"); skillsRequiredEditText.requestFocus(); return false;
        // }

        String paymentStr = eventPayEditText.getText().toString().trim();
        if (!paymentStr.isEmpty()) {
            try {
                double paymentAmount = Double.parseDouble(paymentStr);
                if (paymentAmount < 0) {
                    eventPayEditText.setError("Payment cannot be negative"); eventPayEditText.requestFocus(); return false;
                }
            } catch (NumberFormatException e) {
                eventPayEditText.setError("Invalid number for payment"); eventPayEditText.requestFocus(); return false;
            }
        }

        // requiredVolunteers (int in EventDetails)
        String requiredVolunteersStr = requiredVolunteersEditText.getText().toString().trim();
        if (!requiredVolunteersStr.isEmpty()) {
            try {
                int reqVol = Integer.parseInt(requiredVolunteersStr);
                if (reqVol < 0) {
                    requiredVolunteersEditText.setError("Cannot be negative"); requiredVolunteersEditText.requestFocus(); return false;
                }
            } catch (NumberFormatException e) {
                requiredVolunteersEditText.setError("Invalid number"); requiredVolunteersEditText.requestFocus(); return false;
            }
        } else {
            // If requiredVolunteers is mandatory, uncomment below
            // requiredVolunteersEditText.setError("Required volunteers count needed"); requiredVolunteersEditText.requestFocus(); return false;
        }


        // volunteerLimit (Integer in EventDetails)
        String volunteerLimitStr = volunteerLimitEditText.getText().toString().trim();
        if (!volunteerLimitStr.isEmpty()) {
            try {
                int volLim = Integer.parseInt(volunteerLimitStr);
                if (volLim < 0) {
                    volunteerLimitEditText.setError("Cannot be negative"); volunteerLimitEditText.requestFocus(); return false;
                }
            } catch (NumberFormatException e) {
                volunteerLimitEditText.setError("Invalid number"); volunteerLimitEditText.requestFocus(); return false;
            }
        }
        // If empty, EventDetails constructor defaults volunteerLimit (e.g., to 0).
        // If you want it to be mandatory from UI:
        // else {
        //    volunteerLimitEditText.setError("Volunteer limit required (enter 0 for no limit)"); volunteerLimitEditText.requestFocus(); return false;
        // }


        Calendar eventCalForValidation = Calendar.getInstance();
        eventCalForValidation.setTime(eventDateObject);
        eventCalForValidation.set(Calendar.HOUR_OF_DAY, eventTimeHours);
        eventCalForValidation.set(Calendar.MINUTE, eventTimeMinutes);
        eventCalForValidation.set(Calendar.SECOND, 0);
        eventCalForValidation.set(Calendar.MILLISECOND, 0);

        Calendar nowCal = Calendar.getInstance();
        if (eventCalForValidation.before(nowCal)) {
            eventDateTextView.setError("Event date and time cannot be in the past.");
            eventDateTextView.requestFocus();
            Toast.makeText(getApplicationContext(), "Event date and time cannot be in the past.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void parseDateString(String dateStr) {
        if (dateStr != null && !dateStr.isEmpty()) {
            // Consistent with your previous format.
            // If EventDetails expects a different format for display, that's separate.
            DateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            df.setLenient(false);
            try {
                eventDateObject = df.parse(dateStr);
                Log.d(TAG, "Parsed date string '" + dateStr + "' to Date object: " + (eventDateObject != null ? eventDateObject.toString() : "null"));
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing date string: " + dateStr, e);
                eventDateObject = null;
                eventDateTextView.setError("Invalid date format (dd/MM/yyyy)");
            }
        } else {
            Log.w(TAG, "parseDateString called with null or empty dateStr.");
            eventDateObject = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CALENDAR && resultCode == Activity.RESULT_OK && data != null) {
            String selectedDateFromCalendar = data.getStringExtra("selected_date");
            if (selectedDateFromCalendar != null && !selectedDateFromCalendar.isEmpty()) {
                currentSelectedDateString = selectedDateFromCalendar;
                eventDateTextView.setText(currentSelectedDateString);
                eventDateTextView.setError(null);
                parseDateString(currentSelectedDateString);
                Log.d(TAG, "Date selected from calendar: " + currentSelectedDateString + ", parsed to: " + (eventDateObject != null ? eventDateObject.toString() : "null"));
            } else {
                Log.w(TAG, "Received null or empty date from calendar activity.");
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
