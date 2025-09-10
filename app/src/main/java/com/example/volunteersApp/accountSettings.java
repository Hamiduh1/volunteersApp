package com.example.volunteersApp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

// Import the AccountAdapter to access its inner classes
// No need to import AccountOption or OptionType separately if they are inner to AccountAdapter
// and AccountAdapter itself is imported or in the same package.

public class accountSettings extends AppCompatActivity {

    private RecyclerView accountRecyclerView;
    private AccountAdapter accountAdapter;
    // This list will hold AccountAdapter.AccountOption objects
    private List<AccountAdapter.AccountOption> accountOptionList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accountsettings); // Ensure you have this layout file

        Toolbar toolbar = findViewById(R.id.toolbar); // Ensure R.id.toolbar exists
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Account Settings");
        }

        accountRecyclerView = findViewById(R.id.accountlistview); // Ensure R.id.accountlistview exists

        setupAccountOptions(); // This will populate accountOptionList

        // Using VERTICAL layout for settings, which is typical
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        accountRecyclerView.setLayoutManager(layoutManager);
        accountRecyclerView.setHasFixedSize(true); // Optimization

        // Initialize and set the adapter
        if (accountOptionList != null) {
            // Pass the list of AccountAdapter.AccountOption to the adapter
            accountAdapter = new AccountAdapter(accountOptionList, this);
            accountRecyclerView.setAdapter(accountAdapter);
        } else {
            // Handle the case where the list might still be null, though setupAccountOptions should initialize it.
            // This could be a log message or a placeholder view.
            android.util.Log.e("accountSettings", "accountOptionList is null after setupAccountOptions");
        }
    }

    private void setupAccountOptions() {
        accountOptionList = new ArrayList<>();

        // Populate the list with AccountAdapter.AccountOption objects
        // Ensure you have the corresponding drawable resources (e.g., R.drawable.ic_username)

        // Using placeholder drawables (R.drawable.ic_launcher_foreground).
        // Replace these with your actual drawable resources.
        accountOptionList.add(new AccountAdapter.AccountOption("Change Username",
                R.drawable.ic_launcher_foreground, // Replace with actual drawable R.drawable.ic_username_edit
                AccountAdapter.OptionType.CHANGE_USERNAME));

        accountOptionList.add(new AccountAdapter.AccountOption("Change Password",
                R.drawable.ic_launcher_foreground, // Replace with actual drawable R.drawable.ic_password_edit
                AccountAdapter.OptionType.CHANGE_PASSWORD));

        accountOptionList.add(new AccountAdapter.AccountOption("Change Phone Number",
                R.drawable.ic_launcher_foreground, // Replace with actual drawable R.drawable.ic_phone_edit
                AccountAdapter.OptionType.CHANGE_PHONE_NUMBER));

        accountOptionList.add(new AccountAdapter.AccountOption("Remove Profile Picture",
                R.drawable.ic_launcher_foreground, // Replace with actual drawable R.drawable.ic_remove_photo
                AccountAdapter.OptionType.REMOVE_PROFILE_PIC));

        // Example of adding another option if your AccountAdapter.OptionType supports it:
        // accountOptionList.add(new AccountAdapter.AccountOption("View Profile",
        //         R.drawable.ic_profile_view, // Replace with actual drawable
        //         AccountAdapter.OptionType.VIEW_PROFILE)); // Assuming VIEW_PROFILE is added to enum
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handle the Up button in the toolbar to navigate back
        onBackPressed();
        return true;
    }

    // The AccountOption and OptionType inner classes that were previously here
    // have been removed, as the intention is to use the ones defined
    // as inner classes within AccountAdapter.java.
    // If AccountAdapter.java does not define them, this code will not compile
    // until AccountAdapter.java is created/updated with those inner classes.
}