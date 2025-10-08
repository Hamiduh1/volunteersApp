package com.example.volunteersApp.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.volunteersApp.R;
import com.example.volunteersApp.databinding.FragmentSettingsBinding;
// Import the other activities this screen navigates to
import com.example.volunteersApp.accountSettings;
import com.example.volunteersApp.ProfileSupportActivity;
import com.example.volunteersApp.PrivacySecurityActivity;
import com.example.volunteersApp.HowToUseActivity;
import com.example.volunteersApp.TermsConditionsActivity;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private NavController navController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = NavHostFragment.findNavController(this);

        setupToolbar();
        setupClickListeners();
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbarSettings);
        }
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(binding.toolbarSettings, navController, appBarConfiguration);
        binding.toolbarSettings.setTitle(R.string.settings); // Use string resource
    }

    private void setupClickListeners() {
        // Correct navigation for Notification Settings
        binding.settingsNotification.setOnClickListener(v ->
                navController.navigate(R.id.action_settingsFragment_to_notificationSettingsFragment)
        );

        // For other options that are still Activities
        binding.settingsAccount.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), accountSettings.class))
        );
        binding.settingsSupport.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), ProfileSupportActivity.class))
        );
        binding.settingsPrivacySecurity.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), PrivacySecurityActivity.class))
        );
        binding.settingsHowToUse.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), HowToUseActivity.class))
        );
        binding.settingsTermsAndConditions.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), TermsConditionsActivity.class))
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }
}
