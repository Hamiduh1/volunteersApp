package com.example.volunteersApp.ui.profile;

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
import com.example.volunteersApp.databinding.FragmentNotificationSettingsBinding;

public class NotificationSettingsFragment extends Fragment {

    private FragmentNotificationSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Use a different binding class name, as the layout file name will change
        binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupToolbar();

        // Your switch/preference logic would go here, for example:
        // binding.switchEmailNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
        //     Log.d("SettingsFragment", "Email notifications toggled: " + isChecked);
        //     // Save this setting
        // });
    }

    private void setupToolbar() {
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).setSupportActionBar(binding.toolbarNotificationSettings);
        }

        NavController navController = NavHostFragment.findNavController(this);
        // This configuration ensures the "Up" button appears and is handled by NavController
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(binding.toolbarNotificationSettings, navController, appBarConfiguration);

        binding.toolbarNotificationSettings.setTitle(getString(R.string.title_notification_settings));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks
    }
}
