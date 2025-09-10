package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout; // Keep if you still prefer direct LinearLayout references
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

// It's highly recommended to use ViewBinding to avoid findViewById boilerplate
// import com.example.volunteersApp.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity implements View.OnClickListener {

    // Using ViewBinding would be:
    // private ActivitySettingsBinding binding;

    // Direct view references (if not using ViewBinding)
    private LinearLayout layoutAccount;
    private LinearLayout layoutSupport;
    private LinearLayout layoutNotification;
    private LinearLayout layoutPrivacySecurity;
    private LinearLayout layoutHowToUse;
    private LinearLayout layoutTermsAndConditions;
    private Toolbar toolbar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If using ViewBinding:
        // binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        // setContentView(binding.getRoot());
        // toolbar = binding.toolbarSettings; // Example with ViewBinding
        // layoutAccount = binding.settingsAccount;
        // ... and so on for other layouts

        setContentView(R.layout.activity_settings);

        toolbar = findViewById(R.id.toolbar_settings); // Make sure this ID is in your XML
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Title is set in XML, but can be overridden here if needed:
            // getSupportActionBar().setTitle("Settings");
        }


        // Initialize views using IDs from your activity_settings.xml
        layoutAccount = findViewById(R.id.settings_account);
        layoutSupport = findViewById(R.id.settings_support);
        layoutNotification = findViewById(R.id.settings_notification);
        layoutPrivacySecurity = findViewById(R.id.settings_privacy_security);
        layoutHowToUse = findViewById(R.id.settings_how_to_use);
        layoutTermsAndConditions = findViewById(R.id.settings_terms_and_conditions);

        // Set click listeners
        if (layoutAccount != null) layoutAccount.setOnClickListener(this);
        if (layoutSupport != null) layoutSupport.setOnClickListener(this);
        if (layoutNotification != null) layoutNotification.setOnClickListener(this);
        if (layoutPrivacySecurity != null) layoutPrivacySecurity.setOnClickListener(this);
        if (layoutHowToUse != null) layoutHowToUse.setOnClickListener(this);
        if (layoutTermsAndConditions != null) layoutTermsAndConditions.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        int id = v.getId();

        if (id == R.id.settings_account) {
            // As per your manifest, if 'AccountActivity' is the registered name for ic_account_vector.xml settings
            intent = new Intent(SettingsActivity.this, accountSettings.class);
        } else if (id == R.id.settings_support) {
            // As per your manifest, if 'SupportActivity' is the registered name for support
            intent = new Intent(SettingsActivity.this, ProfileSupportActivity.class);
        } else if (id == R.id.settings_notification) {
            intent = new Intent(SettingsActivity.this, NotificationSettingsActivity.class);
        } else if (id == R.id.settings_privacy_security) {
            intent = new Intent(SettingsActivity.this, PrivacySecurityActivity.class);
        } else if (id == R.id.settings_how_to_use) {
            intent = new Intent(SettingsActivity.this, HowToUseActivity.class);
        } else if (id == R.id.settings_terms_and_conditions) {
            // As per your manifest, if 'TermsConditionsActivity' is the registered name
            intent = new Intent(SettingsActivity.this, TermsConditionsActivity.class);
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // This will take the user back to the previous activity in the call stack
        onBackPressed();
        // Or if SettingsActivity is a top-level screen and you want to ensure it goes to a specific parent:
        // Intent upIntent = NavUtils.getParentActivityIntent(this);
        // if (upIntent != null) {
        //     NavUtils.navigateUpTo(this, upIntent);
        // } else {
        //     finish(); // Fallback if no parent specified or if it's the root
        // }
        return true;
    }
}