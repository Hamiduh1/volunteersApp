package com.example.volunteersApp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.HowToUseTip; // Import your HowToUseTip model
import com.example.volunteersApp.R; // For accessing R.layout.item_how_to_use_tip

import java.util.List;

public class HowToUseAdapter extends RecyclerView.Adapter<HowToUseAdapter.TipViewHolder> {

    private Context context;
    private List<HowToUseTip> tipList;

    // Constructor
    public HowToUseAdapter(Context context, List<HowToUseTip> tipList) {
        this.context = context;
        this.tipList = tipList;
    }

    @NonNull
    @Override
    public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the custom layout (item_how_to_use_tip.xml)
        View view = LayoutInflater.from(context).inflate(R.layout.item_how_to_use_tip, parent, false);
        return new TipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
        // Get the data model based on position
        HowToUseTip currentTip = tipList.get(position);

        // Set item views based on your views and data model
        holder.titleTextView.setText(currentTip.getTitle());
        holder.contentTextView.setText(currentTip.getContent());

        // You could also set an OnClickListener for each item if needed
        // holder.itemView.setOnClickListener(v -> {
        //     // Handle item click, e.g., open details, show a Toast
        //     Toast.makeText(context, "Clicked: " + currentTip.getTitle(), Toast.LENGTH_SHORT).show();
        // });
    }

    @Override
    public int getItemCount() {
        return (tipList != null) ? tipList.size() : 0;
    }

    // Method to update the list of tips (optional, but good for refreshing data)
    public void updateTips(List<HowToUseTip> newTips) {
        if (newTips != null) {
            this.tipList.clear();
            this.tipList.addAll(newTips);
            notifyDataSetChanged(); // Notify the adapter to refresh the view
        }
    }


    // ViewHolder class
    // This holds the TextViews that will be displaying your data.
    public static class TipViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView contentTextView;

        public TipViewHolder(@NonNull View itemView) {
            super(itemView);
            // Initialize the views from the item_how_to_use_tip.xml layout
            titleTextView = itemView.findViewById(R.id.textViewTipTitle);
            contentTextView = itemView.findViewById(R.id.textViewTipContent);
        }
    }
}
