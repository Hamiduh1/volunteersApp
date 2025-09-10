package com.example.volunteersApp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import com.example.volunteersApp.R;
import com.example.volunteersApp.SupportItem;
import com.example.volunteersApp.adapters.SupportItemsAdapter;

public class SupportActivity extends AppCompatActivity {

    private static final String TAG = "SupportActivity";
    private static final String SUPPORT_ITEMS_COLLECTION = "general_support_items";

    private RecyclerView recyclerViewSupportItems;
    private SupportItemsAdapter itemsAdapter;
    private List<SupportItem> supportItemList; // You might still want this to hold the current list
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView textViewError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);

        Toolbar toolbar = findViewById(R.id.toolbar_support);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.title_general_support);
        }

        recyclerViewSupportItems = findViewById(R.id.recyclerViewSupportItems);
        progressBar = findViewById(R.id.progressBarSupportActivity);
        textViewError = findViewById(R.id.textViewErrorSupportActivity);

        db = FirebaseFirestore.getInstance();
        supportItemList = new ArrayList<>(); // Initialize the local list

        // --- CORRECTED ADAPTER INITIALIZATION ---
        // Use the no-argument constructor for ListAdapter
        itemsAdapter = new SupportItemsAdapter();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewSupportItems.setLayoutManager(layoutManager);
        recyclerViewSupportItems.setHasFixedSize(true);
        recyclerViewSupportItems.setAdapter(itemsAdapter);

        // Set the item click listener
        itemsAdapter.setOnItemClickListener(item -> {
            Log.d(TAG, "Clicked support item: " + item.getText());
            Toast.makeText(this, "Clicked: " + item.getText(), Toast.LENGTH_SHORT).show();
            // TODO: Implement action for clicked item, e.g., open a new activity or show details
            // For example:
            // if ("Feedback".equals(item.getText())) {
            // Intent feedbackIntent = new Intent(SupportActivity.this, FeedbackActivity.class);
            // startActivity(feedbackIntent);
            // }
        });

        loadItemsFromFirestore();
    }

    private void loadItemsFromFirestore() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (textViewError != null) textViewError.setVisibility(View.GONE);
        if (recyclerViewSupportItems != null) recyclerViewSupportItems.setVisibility(View.GONE);

        db.collection(SUPPORT_ITEMS_COLLECTION)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No items found in Firestore collection: " + SUPPORT_ITEMS_COLLECTION);
                        if (textViewError != null) {
                            textViewError.setText(R.string.no_support_information_available);
                            textViewError.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(SupportActivity.this, R.string.no_support_information_available, Toast.LENGTH_SHORT).show();
                        }
                        // Submit an empty list if no items are found
                        if (itemsAdapter != null) {
                            itemsAdapter.submitList(new ArrayList<>());
                        }
                        supportItemList.clear(); // Clear local list as well
                        return;
                    }

                    List<SupportItem> newItems = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        SupportItem item = document.toObject(SupportItem.class);
                        if (item != null) {
                            item.setImageResourceId(getDrawableResourceIdByName(this, item.getIconName()));
                            newItems.add(item);
                        }
                    }

                    supportItemList.clear();
                    supportItemList.addAll(newItems); // Update local list

                    // --- CORRECT WAY TO UPDATE LISTADAPTER ---
                    if (itemsAdapter != null) {
                        itemsAdapter.submitList(newItems); // Pass the new list to the adapter
                    }

                    if (recyclerViewSupportItems != null) recyclerViewSupportItems.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error fetching items from " + SUPPORT_ITEMS_COLLECTION, e);
                    String errorMessage = getString(R.string.error_loading_data_message, e.getLocalizedMessage());
                    if (textViewError != null) {
                        textViewError.setText(errorMessage);
                        textViewError.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(SupportActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                    // Optionally submit an empty list on failure too
                    if (itemsAdapter != null) {
                        itemsAdapter.submitList(new ArrayList<>());
                    }
                    supportItemList.clear(); // Clear local list on failure
                });
    }

    public static int getDrawableResourceIdByName(Context context, @Nullable String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) {
            Log.w(TAG, "Resource name is null or empty, returning default placeholder.");
            return R.drawable.ic_default_placeholder;
        }
        try {
            int resourceId = context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
            if (resourceId == 0) {
                Log.w(TAG, "Drawable resource not found for name: " + resourceName + ". Returning default placeholder.");
                return R.drawable.ic_default_placeholder;
            }
            return resourceId;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get resource ID for: " + resourceName, e);
            return R.drawable.ic_default_placeholder;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}