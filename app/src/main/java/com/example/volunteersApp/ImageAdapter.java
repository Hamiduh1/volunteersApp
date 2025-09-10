package com.example.volunteersApp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso; // Ensure Picasso is correctly added as a dependency

import java.util.List;
import java.util.Objects; // For potential null checks if needed, or use if/else

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    //private final Context mContext; // Consider if mContext is truly needed if not used beyond LayoutInflater
    private final List<ImgUpload> mUploads;
    private OnItemClickListener mListener; // For handling item clicks

    // Interface for item click listener
    public interface OnItemClickListener {
        void onItemClick(int position);
        // You can add other callbacks here like onLongItemClick, onDeleteClick etc.
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        mListener = listener;
    }

    public ImageAdapter(Context context, List<ImgUpload> uploads) {
        // It's good practice to assign context from the parent in onCreateViewHolder
        // mContext = context; // This is fine, but can also be obtained from parent.getContext()
        mUploads = uploads;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use parent.getContext() to get the context for the LayoutInflater
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.image_item, parent, false);
        return new ImageViewHolder(v, mListener); // Pass listener to ViewHolder
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        ImgUpload uploadCurrent = mUploads.get(position);

        if (uploadCurrent != null) {
            // Use the getter for name
            holder.textViewName.setText(uploadCurrent.getTitle());

            // Use the getter for the image URL (e.g., getImageUrl())
            // Add error handling and placeholders for Picasso
            if (uploadCurrent.getImageUrl() != null && !uploadCurrent.getImageUrl().isEmpty()) {
                Picasso.get()
                        .load(uploadCurrent.getImageUrl())
                        .placeholder(R.drawable.ic_placeholder_image) // Add a placeholder drawable
                        .error(R.drawable.ic_error_image)         // Add an error drawable
                        .fit() // Adjust as needed (fit, centerCrop, centerInside)
                        .centerCrop() // Or centerInside() depending on your ImageView scaleType
                        .into(holder.imageView);
            } else {
                // Handle case where URL is null or empty, e.g., set a default image
                holder.imageView.setImageResource(R.drawable.ic_placeholder_image);
            }
        } else {
            // Handle the case where uploadCurrent is null, if that's possible
            // For example, set placeholder text and image
            holder.textViewName.setText("N/A");
            holder.imageView.setImageResource(R.drawable.ic_placeholder_image);
        }
    }

    @Override
    public int getItemCount() {
        return mUploads != null ? mUploads.size() : 0; // Handle null list
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        public TextView textViewName;
        public ImageView imageView;

        public ImageViewHolder(View itemView, final OnItemClickListener listener) {
            super(itemView);

            textViewName = itemView.findViewById(R.id.event_image_name);
            imageView = itemView.findViewById(R.id.event_image);

            // Set up click listener for the item view
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) { // Check if position is valid
                            listener.onItemClick(position);
                        }
                    }
                }
            });
        }
    }

    // Helper method to update the list of uploads if needed
    public void updateUploads(List<ImgUpload> newUploads) {
        mUploads.clear();
        if (newUploads != null) {
            mUploads.addAll(newUploads);
        }
        notifyDataSetChanged(); // Or use more specific notify methods for better performance
    }
}