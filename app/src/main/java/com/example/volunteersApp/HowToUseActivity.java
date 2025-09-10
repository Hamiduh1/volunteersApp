package com.example.volunteersApp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager; // Added for RecyclerView
// import androidx.recyclerview.widget.RecyclerView; // Already implicitly available through LinearLayoutManager

import com.example.volunteersApp.databinding.ActivityHowToUseBinding;
import com.example.volunteersApp.adapters.HowToUseAdapter; // Make sure this adapter exists

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HowToUseActivity extends AppCompatActivity {

    private static final String TAG = "HowToUseActivity";
    private ActivityHowToUseBinding binding; // ViewBinding
    private FirebaseFirestore db;
    private HowToUseAdapter adapter; // Adapter for the RecyclerView
    private List<HowToUseTip> tipList; // List to hold HowToUseTip objects

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inflate the layout using ViewBinding
        binding = ActivityHowToUseBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Setup Toolbar
        setSupportActionBar(binding.toolbarHowToUse);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.how_to_use_title)); // Assuming you have this string resource
        }

        // Initialize the list and adapter
        tipList = new ArrayList<>();
        // Make sure HowToUseAdapter constructor matches (e.g., HowToUseAdapter(Context context, List<HowToUseTip> list))
        adapter = new HowToUseAdapter(this, tipList);

        // Setup RecyclerView
        binding.recyclerViewTips.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewTips.setAdapter(adapter);

        // Initial UI state: show progress bar, hide list and "no tips" message
        binding.progressBarTips.setVisibility(View.VISIBLE);
        binding.recyclerViewTips.setVisibility(View.GONE);
        binding.textViewNoTips.setVisibility(View.GONE);

        // Load tips from Firestore
        loadTipsFromFirestore();
    }

    private void loadTipsFromFirestore() {
        binding.progressBarTips.setVisibility(View.VISIBLE);
        binding.recyclerViewTips.setVisibility(View.GONE);
        binding.textViewNoTips.setVisibility(View.GONE);

        db.collection("howToUseTips")
                .orderBy("order", Query.Direction.ASCENDING) // Order by the 'order' field
                .get()
                .addOnCompleteListener(task -> {
                    binding.progressBarTips.setVisibility(View.GONE); // Hide progress bar
                    if (task.isSuccessful() && task.getResult() != null) {
                        tipList.clear(); // Clear old data before adding new
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            HowToUseTip tip = document.toObject(HowToUseTip.class);
                            tipList.add(tip);
                            Log.d(TAG, document.getId() + " => Title: " + tip.getTitle());
                        }

                        if (tipList.isEmpty()) {
                            binding.textViewNoTips.setText(getString(R.string.no_how_to_use_tips)); // String resource
                            binding.textViewNoTips.setVisibility(View.VISIBLE);
                            binding.recyclerViewTips.setVisibility(View.GONE);
                            Log.d(TAG, "No 'How to Use' tips found in Firestore.");
                        } else {
                            adapter.notifyDataSetChanged(); // Notify adapter of data changes
                            binding.recyclerViewTips.setVisibility(View.VISIBLE);
                            binding.textViewNoTips.setVisibility(View.GONE);
                        }
                    } else {
                        Log.w(TAG, "Error getting documents: ", task.getException());
                        binding.textViewNoTips.setText(getString(R.string.error_loading_tips)); // String resource
                        binding.textViewNoTips.setVisibility(View.VISIBLE);
                        binding.recyclerViewTips.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish(); // Closes the current activity and returns to the previous one
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // Important for ViewBinding to avoid memory leaks
    }
}
