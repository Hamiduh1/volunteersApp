// File: ProfileSupportAdapter.java
package com.example.volunteersApp;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ProfileSupportAdapter extends RecyclerView.Adapter<ProfileSupportAdapter.ViewHolder> {

    private static final String TAG = "ProfileSupportAdapter";
    private ArrayList<SupportItem> supportItems;
    private Context context;

    public ProfileSupportAdapter(ArrayList<SupportItem> supportItems, Context context) {
        this.supportItems = supportItems;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_profile_support, parent, false); // Create list_item_profile_support.xml
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SupportItem currentItem = supportItems.get(position);
        holder.textView.setText(currentItem.getText());
        holder.imageView.setImageResource(currentItem.getImageResourceId());

        holder.itemView.setOnClickListener(v -> {
            // Handle item click
            String itemName = currentItem.getText();
            Toast.makeText(context, "Clicked: " + itemName, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Clicked item: " + itemName);

            if ("Feedback".equals(itemName)) {
                // Example: Navigate to a FeedbackActivity
                // Intent feedbackIntent = new Intent(context, FeedbackActivity.class);
                // context.startActivity(feedbackIntent);
                Log.i(TAG, "Feedback option selected.");
            } else if ("Report".equals(itemName)) {
                // Example: Navigate to a ReportProblemActivity
                // Intent reportIntent = new Intent(context, ReportProblemActivity.class);
                // context.startActivity(reportIntent);
                Log.i(TAG, "Report option selected.");
            }
            // Add more conditions for other items if needed
        });
    }

    @Override
    public int getItemCount() {
        return supportItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.support_item_image); // Ensure these IDs exist in your list_item_profile_support.xml
            textView = itemView.findViewById(R.id.support_item_text);   // Ensure these IDs exist in your list_item_profile_support.xml
        }
    }
}