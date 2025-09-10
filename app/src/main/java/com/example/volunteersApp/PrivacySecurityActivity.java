package com.example.volunteersApp; // Ensure this matches your package name

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

// import com.example.volunteersApp.databinding.ActivityPrivacySecurityBinding;

public class PrivacySecurityActivity extends AppCompatActivity {

    // private ActivityPrivacySecurityBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // binding = ActivityPrivacySecurityBinding.inflate(getLayoutInflater());
        // setContentView(binding.getRoot());
        setContentView(R.layout.activity_privacy_security); // Create this layout file

        Toolbar toolbar = findViewById(R.id.toolbar_privacy_security); // Assuming you add a Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Privacy & Security");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}