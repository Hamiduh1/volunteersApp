package com.example.volunteersApp; // Use your actual package name

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
// Toolbar is accessed via binding, so direct import might not be needed at class level
// import androidx.appcompat.widget.Toolbar;
import com.example.volunteersApp.databinding.ActivityNotificationSettingsBinding; // Import generated binding class

public class NotificationSettingsActivity extends AppCompatActivity {

    private ActivityNotificationSettingsBinding binding; // Declare binding variable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationSettingsBinding.inflate(getLayoutInflater()); // Inflate binding
        setContentView(binding.getRoot()); // Set content view from binding

        setSupportActionBar(binding.toolbarNotificationSettings); // Use binding

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Ensure you have R.string.title_notification_settings defined
            getSupportActionBar().setTitle(getString(R.string.title_notification_settings));
        }

        // If you have other UI elements in activity_notification_settings.xml
        // that need interaction (e.g., Switches for notification preferences),
        // you would access them via binding.something.
        // For example:
        // binding.switchEmailNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
        //     // Save preference (e.g., to SharedPreferences or Firestore if user-specific and synced)
        // });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            navigateToProfile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        navigateToProfile();
        // super.onBackPressed(); // Call super.onBackPressed() AFTER starting the new activity
        // if you want this activity to finish.
        // The current code finishes this activity by calling navigateToProfile which includes finish().
    }

    private void navigateToProfile() {
        Intent intent = new Intent(NotificationSettingsActivity.this, Profile.class);
        // Optional: Clear other activities on top of ProfileActivity if you
        // want ProfileActivity to be the new top.
        // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish(); // Finish NotificationSettingsActivity
    }
}