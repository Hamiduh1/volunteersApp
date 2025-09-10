package com.example.volunteersApp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
// Import your R class if it's not automatically imported
// import com.example.volunteersApp.R;


public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<FriendlyMessage> messages;
    private String currentUserId; // Already present, will be used by the new getter
    private Context context;

    private static final int VIEW_TYPE_MY_MESSAGE = 1;
    private static final int VIEW_TYPE_OTHER_MESSAGE = 2;

    public MessageAdapter(Context context, List<FriendlyMessage> messages, String currentUserId) {
        this.context = context;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.currentUserId = currentUserId; // currentUserId is already being initialized
    }

    public void addMessage(FriendlyMessage message) {
        if (message != null) {
            messages.add(message);
            notifyItemInserted(messages.size() - 1);
        }
    }

    public void addMessages(List<FriendlyMessage> newMessages) {
        if (newMessages != null && !newMessages.isEmpty()) {
            int startPosition = messages.size();
            messages.addAll(newMessages);
            notifyItemRangeInserted(startPosition, newMessages.size());
        }
    }

    public void clearMessages() {
        if (messages != null) {
            messages.clear();
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        FriendlyMessage message = messages.get(position);
        // Ensure message and message.getUserId() are not null before calling equals
        if (message != null && message.getUserId() != null && message.getUserId().equals(currentUserId)) {
            return VIEW_TYPE_MY_MESSAGE;
        } else {
            return VIEW_TYPE_OTHER_MESSAGE;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context); // Use the stored context
        View view;
        if (viewType == VIEW_TYPE_MY_MESSAGE) {
            view = inflater.inflate(R.layout.item_my_message, parent, false);
        } else { // VIEW_TYPE_OTHER_MESSAGE
            view = inflater.inflate(R.layout.item_other_message, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        FriendlyMessage message = messages.get(position);
        if (message != null) {
            holder.bind(message);
            // Additional logic for "my message" styling if not fully handled by XML
            if (getItemViewType(position) == VIEW_TYPE_MY_MESSAGE) {
                if (holder.authorTextView != null) {
                    holder.authorTextView.setVisibility(View.GONE);
                }
            } else {
                // Ensure author name is visible for other messages
                if (holder.authorTextView != null) {
                    holder.authorTextView.setVisibility(View.VISIBLE);
                    holder.authorTextView.setText(message.getTitle());
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    // +++++++++++++ ADDED METHOD +++++++++++++ //
    /**
     * Returns the current user ID this adapter is configured for.
     * @return The string ID of the current user.
     */
    public String getCurrentUserId() {
        return this.currentUserId;
    }

    // +++++++++++++ OPTIONAL METHOD +++++++++++++ //
    /**
     * Updates the current user ID for the adapter.
     * This can be useful if the logged-in user changes without recreating the fragment/activity.
     * After calling this, you might need to refresh the adapter's data.
     * @param newUserId The new user ID.
     */
    public void updateCurrentUserId(String newUserId) {
        boolean userIdChanged = this.currentUserId == null || !this.currentUserId.equals(newUserId);
        this.currentUserId = newUserId;
        // If the user ID actually changed, the view types for existing messages might need to be re-evaluated.
        // A full notifyDataSetChanged() is the simplest way to ensure all items are rebound correctly.
        if (userIdChanged) {
            notifyDataSetChanged();
        }
    }


    public class MessageViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView;
        TextView authorTextView;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            authorTextView = itemView.findViewById(R.id.nameTextView);
        }

        void bind(FriendlyMessage message) {
            // Check for null message object
            if (message == null) {
                // Optionally, clear views or show an error state
                if (messageTextView != null) messageTextView.setText("");
                if (authorTextView != null) authorTextView.setText("");
                if (photoImageView != null) photoImageView.setImageDrawable(null);
                return;
            }

            boolean isPhoto = message.getPhotoUrl() != null && !message.getPhotoUrl().isEmpty();

            if (photoImageView != null) {
                if (isPhoto) {
                    photoImageView.setVisibility(View.VISIBLE);
                    Glide.with(photoImageView.getContext()) // itemView.getContext() is also fine
                            .load(message.getPhotoUrl())
                            .placeholder(R.drawable.ic_placeholder_image) // Make sure these drawables exist
                            .error(R.drawable.ic_error_image)         // Make sure these drawables exist
                            .into(photoImageView);
                    if (messageTextView != null) {
                        messageTextView.setVisibility(View.GONE);
                    }
                } else {
                    photoImageView.setVisibility(View.GONE);
                }
            }

            if (messageTextView != null) {
                if (!isPhoto) {
                    messageTextView.setText(message.getText());
                    messageTextView.setVisibility(View.VISIBLE);
                } else {
                    // If it's a photo and photoImageView is somehow null or gone,
                    // you might decide to show the text as a fallback or nothing.
                    // Current logic hides messageTextView if it's a photo and photoImageView is present.
                    // If photoImageView is missing from the layout, then this else block could handle text.
                    if (photoImageView == null || photoImageView.getVisibility() == View.GONE) {
                        messageTextView.setText(message.getText() != null ? message.getText() : "[Image]"); // Fallback text
                        messageTextView.setVisibility(View.VISIBLE);
                    } else {
                        messageTextView.setVisibility(View.GONE); // Default: hide text if image is shown
                    }
                }
            }
        }
    }

    // Optional: Helper method to format Firebase Timestamp
    // private String formatFirebaseTimestamp(com.google.firebase.Timestamp timestamp) { ... }
}
