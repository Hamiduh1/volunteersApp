/**
  integrated to jetpack compose

package com.example.volunteersApp.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
import com.example.volunteersApp.ui.profile.SupportItem; // Ensure this has equals() and a unique ID getter

import java.util.Objects;

public class SupportItemsAdapter extends ListAdapter<SupportItem, SupportItemsAdapter.SupportItemViewHolder> {

    // private Context context; // Can be obtained from itemView.getContext() in ViewHolder
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(@NonNull SupportItem item);
    }

    // Constructor for ListAdapter
    public SupportItemsAdapter(OnItemClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    // Constructor if listener is optional (provides default Toast behavior)
    public SupportItemsAdapter() {
        super(DIFF_CALLBACK);
        this.listener = null;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<SupportItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SupportItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull SupportItem oldItem, @NonNull SupportItem newItem) {
                    // Assuming SupportItem.getText() can serve as a unique ID.
                    // If you have a dedicated ID (e.g., oldItem.getId()), use that instead.
                    return Objects.equals(oldItem.text, newItem.text);
                }

                @Override
                public boolean areContentsTheSame(@NonNull SupportItem oldItem, @NonNull SupportItem newItem) {
                    // Relies on the equals() method implemented in the SupportItem model.
                    return oldItem.equals(newItem);
                }
            };

    @NonNull
    @Override
    public SupportItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_support_list_item, parent, false);
        return new SupportItemViewHolder(view, listener); // Pass listener to ViewHolder
    }

    @Override
    public void onBindViewHolder(@NonNull SupportItemViewHolder holder, int position) {
        SupportItem currentItem = getItem(position); // getItem() from ListAdapter
        if (currentItem != null) {
            holder.bind(currentItem);
        }
    }

    // getItemCount() is handled by ListAdapter.
    // updateData() method is no longer needed; use submitList() from Fragment/Activity.

    static class SupportItemViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewSupportIcon;
        TextView textViewSupportText;
        private OnItemClickListener currentListener;
        private Context context; // For Toast messages

        SupportItemViewHolder(@NonNull View itemView, OnItemClickListener listener) {
            super(itemView);
            this.currentListener = listener;
            this.context = itemView.getContext(); // Get context for Toasts

            imageViewSupportIcon = itemView.findViewById(R.id.imageViewSupportIcon);
            textViewSupportText = itemView.findViewById(R.id.textViewSupportText);
        }

        public void bind(@NonNull final SupportItem item) {
            textViewSupportText.setText(item.text);

            if (item.imageResourceId != 0) {
                imageViewSupportIcon.setImageResource(item.imageResourceId);
            } else {
                imageViewSupportIcon.setImageResource(R.drawable.ic_default_placeholder);
            }

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // SupportItem clickedItem = ((SupportItemsAdapter) getBindingAdapter()).getItem(position);
                    // Using 'item' passed to bind() is generally safe for simple clicks.

                    if (currentListener != null) {
                        currentListener.onItemClick(item);
                    } else {
                        // Default click action if no specific listener is set
                        Toast.makeText(context, "Clicked: " + item.text, Toast.LENGTH_SHORT).show();
                        // Example navigation (ensure FeedbackActivity exists and is set up)
                        // if ("Feedback".equals(item.getText())) {
                        //     Intent intent = new Intent(context, FeedbackActivity.class);
                        //     context.startActivity(intent);
                        // }
                    }
                }
            });
        }
    }
}
 **/