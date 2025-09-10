package com.example.volunteersApp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
// No longer need 'import java.util.List;' if we strictly use ArrayList for supportItemList

public class ProfileSupportActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSupportActivity";
    private static final String SUPPORT_ITEMS_COLLECTION = "support_items";

    private RecyclerView recyclerView;
    private ProfileSupportAdapter adapter;
    private ArrayList<SupportItem> supportItemList; // Changed from List to ArrayList
    private FirebaseFirestore db;
    private ProgressBar progressBar;
    private TextView textViewError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_support);

        Toolbar toolbar = findViewById(R.id.toolbar_profile_support);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_support));
        }

        recyclerView = findViewById(R.id.accountlistview1);
        progressBar = findViewById(R.id.progressBarSupport);
        textViewError = findViewById(R.id.textViewSupportError);

        db = FirebaseFirestore.getInstance();
        supportItemList = new ArrayList<>(); // Initialize as ArrayList

        // Now this should match if ProfileSupportAdapter expects ArrayList
        adapter = new ProfileSupportAdapter(supportItemList, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        loadSupportItemsFromFirestore();
    }

    private void loadSupportItemsFromFirestore() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (textViewError != null) textViewError.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        db.collection(SUPPORT_ITEMS_COLLECTION)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "No support items found in Firestore.");
                        if (textViewError != null) {
                            textViewError.setText(R.string.support_items_not_available);
                            textViewError.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(ProfileSupportActivity.this, R.string.support_items_not_available, Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    supportItemList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        SupportItem item = document.toObject(SupportItem.class);
                        if (item != null) {
                            item.setImageResourceId(getDrawableResourceIdByName(this, item.getIconName()));
                            supportItemList.add(item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    recyclerView.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Error fetching support items", e);
                    if (textViewError != null) {
                        // Corrected: Use activity's getString for formatted strings
                        textViewError.setText(getString(R.string.error_loading_support_items_message, e.getMessage()));
                        textViewError.setVisibility(View.VISIBLE);
                    } else {
                        // Corrected: Use activity's getString for formatted strings
                        Toast.makeText(ProfileSupportActivity.this,
                                getString(R.string.error_loading_support_items_message, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
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

    private void loadDefaultSupportItems() {
        supportItemList.clear();
        SupportItem feedbackItem = new SupportItem("Feedback", "feedback", 0);
        feedbackItem.setImageResourceId(R.drawable.feedback);
        supportItemList.add(feedbackItem);

        SupportItem reportItem = new SupportItem("Report a Problem", "report", 1);
        reportItem.setImageResourceId(R.drawable.report);
        supportItemList.add(reportItem);

        adapter.notifyDataSetChanged();
        recyclerView.setVisibility(View.VISIBLE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (textViewError != null) textViewError.setVisibility(View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}