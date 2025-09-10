package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
// import android.view.View; // Not explicitly used if only using lambda onClick
// import android.widget.Button; // Using MaterialButton
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
// import androidx.core.content.ContextCompat; // Not strictly needed

// Removed: import com.example.volunteersApp.fragments.MyEventsFragment;
// We will start an Activity that hosts this fragment.
import com.google.android.material.button.MaterialButton;
import com.example.volunteersApp.organizer.CreateEventActivity;

// **You will need to create this Activity separately to host MyEventsFragment**
// For example:
// import com.example.volunteersApp.MyEventsContainerActivity;


public class HostFinal extends AppCompatActivity {

    private static final String TAG = "HostFinal";

    // Define constants for intent extras to ensure consistency
    public static final String EXTRA_EVENT_ID = "EVENT_ID";
    public static final String EXTRA_EVENT_NAME = "EVENT_NAME";
    public static final String EXTRA_NEWLY_CREATED_EVENT_ID = "NewlyCreatedEventId"; // For passing to MyEventsContainerActivity

    private TextView textViewConfirmationMain;
    private TextView textViewConfirmationDetail;
    private String eventId;
    private String eventName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure you have a layout file named 'activity_final.xml'
        // and that it contains all the views referenced below.
        setContentView(R.layout.activity_final); // Ensure this layout exists

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar_host_final); // Ensure this ID exists
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            // Use string resources for titles
            getSupportActionBar().setTitle(getString(R.string.host_final_activity_title));
            // No back arrow usually on a final confirmation screen like this,
            // as the flow is forward or to a new task.
        }

        // Initialize views
        textViewConfirmationMain = findViewById(R.id.textView_confirmation_message_main);
        textViewConfirmationDetail = findViewById(R.id.textView_confirmation_message_detail);
        MaterialButton goToMyEventsButton = findViewById(R.id.button_go_to_my_events);
        MaterialButton hostAnotherEventButton = findViewById(R.id.button_host_another_event_final);

        // Get EventId and optionally EventName passed from CreateEventActivity
        Intent intent = getIntent();
        if (intent != null) {
            eventId = intent.getStringExtra(EXTRA_EVENT_ID);
            eventName = intent.getStringExtra(EXTRA_EVENT_NAME);
            Log.d(TAG, "Received Event ID: " + eventId + ", Event Name: " + eventName);
        } else {
            Log.w(TAG, "Intent was null, no data received for confirmation.");
        }

        // Set confirmation messages
        if (textViewConfirmationMain != null) {
            textViewConfirmationMain.setText(getString(R.string.successfully_hosted_main_title));
        } else {
            Log.e(TAG, "textViewConfirmationMain (R.id.textView_confirmation_message_main) not found in layout.");
        }

        if (textViewConfirmationDetail != null) {
            if (eventName != null && !eventName.isEmpty()) {
                textViewConfirmationDetail.setText(getString(R.string.host_final_congrats_with_event_name, eventName));
            } else if (eventId != null && !eventId.isEmpty()) {
                textViewConfirmationDetail.setText(getString(R.string.host_final_congrats_with_event_id, eventId));
            } else {
                textViewConfirmationDetail.setText(getString(R.string.host_final_congrats_generic));
            }
        } else {
            Log.e(TAG, "textViewConfirmationDetail (R.id.textView_confirmation_message_detail) not found in layout.");
        }

        // --- Set up button listeners ---

        if (goToMyEventsButton != null) {
            goToMyEventsButton.setOnClickListener(v -> {
                // **MODIFIED: Start MyEventsContainerActivity (which hosts MyEventsFragment)**
                Intent intentToMyEventsContainer = new Intent(HostFinal.this, MyEventsContainerActivity.class); // <<--- CHANGE HERE
                // Optionally pass the newly created eventId if MyEventsFragment (via its host) needs to highlight it
                if (eventId != null) {
                    intentToMyEventsContainer.putExtra(EXTRA_NEWLY_CREATED_EVENT_ID, eventId);
                }
                // Clear the task stack up to the new Activity and make it the new root, or just start it fresh.
                intentToMyEventsContainer.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intentToMyEventsContainer);
                finish(); // Finish HostFinal, user is navigating away.
            });
        } else {
            Log.e(TAG, "Go To My Events button (R.id.button_go_to_my_events) not found in layout.");
        }

        if (hostAnotherEventButton != null) {
            hostAnotherEventButton.setOnClickListener(v -> {
                Intent intentToHostEvent = new Intent(HostFinal.this, CreateEventActivity.class);
                // Clears the task and starts CreateEventActivity fresh.
                // Ensures that if the user presses back from CreateEventActivity,
                // they don't return to this HostFinal screen for the *previous* event.
                intentToHostEvent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intentToHostEvent);
                finish(); // Finish HostFinal, user is starting a new creation flow.
            });
        } else {
            Log.e(TAG, "Host Another Event button (R.id.button_host_another_event_final) not found in layout.");
        }
    }

    @Override
    public void onBackPressed() {
        // When back is pressed on this confirmation screen, navigate to a logical "home" or "list" screen.
        // **MODIFIED: Navigate to MyEventsContainerActivity instead of directly to a Fragment**
        super.onBackPressed();
        Intent intentToMyEventsContainer = new Intent(HostFinal.this, MyEventsContainerActivity.class); // <<--- CHANGE HERE
        intentToMyEventsContainer.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intentToMyEventsContainer);
        finishAffinity(); // Finishes this activity and all activities immediately below it in the task.
        // Good for ensuring a clean stack when navigating to a "home" type screen.
        // No call to super.onBackPressed() as we are fully handling it and finishing.
    }

    // Optional: If you had a toolbar up button and wanted custom behavior for it
    /*
    @Override
    public boolean onSupportNavigateUp() {
        // Similar to onBackPressed logic for this screen
        Intent intentToMyEventsContainer = new Intent(HostFinal.this, MyEventsContainerActivity.class); // <<--- CHANGE HERE
        intentToMyEventsContainer.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intentToMyEventsContainer);
        finishAffinity();
        return true; // Indicate that we've handled the up navigation
    }
    */

    // Example String Resources to add to res/values/strings.xml:
    // <string name="host_final_activity_title">Event Hosted!</string>
    // <string name="successfully_hosted_main_title">Event Successfully Hosted!</string>
    // <string name="host_final_congrats_with_event_name">Congratulations! Your event \'%1$s\' is now live.</string>
    // <string name="host_final_congrats_with_event_id">Congratulations! Your event (ID: %1$s) is now live.</string>
    // <string name="host_final_congrats_generic">Congratulations! Your event is now live.</string>
}

