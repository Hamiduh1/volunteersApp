package com.example.volunteersApp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the RecyclerView in the Account Settings screen.
 * It displays a list of ic_account_vector.xml-related options.
 */
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountOptionViewHolder> {

    private final List<AccountOption> accountOptions; // Uses AccountAdapter.AccountOption
    private final Context context;
    private static final String USERS_COLLECTION = "Users"; // Or your actual users collection name
    private static final String PROFILE_PIC_URL_FIELD = "url"; // Or your actual field name for profile pic URL

    // --- Inner Data Class ---
    /**
     * Data class to represent an item in the ic_account_vector.xml settings list.
     * Defined as a static nested class because it doesn't need access to AccountAdapter's instance members.
     * If it needed access, it could be a non-static inner class.
     */
    public static class AccountOption { // This is AccountAdapter.AccountOption
        final String title;
        final int iconResId;
        final OptionType type; // This is AccountAdapter.OptionType

        public AccountOption(String title, int iconResId, OptionType type) {
            this.title = title;
            this.iconResId = iconResId;
            this.type = type;
        }

        // Getters can be useful, though not strictly necessary if only accessed internally by the adapter
        public String getTitle() {
            return title;
        }

        public int getIconResId() {
            return iconResId;
        }

        public OptionType getType() {
            return type;
        }
    }

    // --- Inner Enum ---
    /**
     * Enum to define the types of actions for ic_account_vector.xml options.
     * Defined as a static nested enum (enums are implicitly static when nested).
     */
    public enum OptionType { // This is AccountAdapter.OptionType
        CHANGE_USERNAME,
        CHANGE_PASSWORD,
        CHANGE_PHONE_NUMBER,
        REMOVE_PROFILE_PIC
        // Add other types here as needed, e.g., VIEW_PROFILE
    }


    /**
     * Constructor for AccountAdapter.
     * @param accountOptions List of {@link AccountOption} objects to display.
     * @param context The context, typically the calling Activity or Fragment.
     */
    public AccountAdapter(List<AccountOption> accountOptions, Context context) {
        this.accountOptions = accountOptions;
        this.context = context;
    }

    @NonNull
    @Override
    public AccountOptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure you have a layout file named 'account_item_view.xml' in res/layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.account_item_view, parent, false);
        return new AccountOptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountOptionViewHolder holder, int position) {
        // Get the data model based on position
        AccountOption currentOption = accountOptions.get(position);

        // Set item views based on your views and data model
        holder.textView.setText(currentOption.title);
        if (currentOption.iconResId != 0) { // Check if a valid icon resource ID is provided
            holder.imageView.setImageResource(currentOption.iconResId);
            holder.imageView.setVisibility(View.VISIBLE);
        } else {
            // If no icon, hide the ImageView or set a default placeholder
            holder.imageView.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Intent intent;

            // Use the OptionType defined within this Adapter
            switch (currentOption.type) {
                case CHANGE_USERNAME:
                    // Ensure 'changeUserName.class' exists and is an Activity
                    intent = new Intent(context, changeUserName.class);
                    context.startActivity(intent);
                    break;
                case CHANGE_PASSWORD:
                    // Ensure 'ChangePassword.class' exists and is an Activity
                    intent = new Intent(context, ChangePassword.class);
                    context.startActivity(intent);
                    break;
                case CHANGE_PHONE_NUMBER:
                    // Ensure 'ChangePhoneNumber.class' exists and is an Activity
                    intent = new Intent(context, ChangePhoneNumber.class);
                    context.startActivity(intent);
                    break;
                case REMOVE_PROFILE_PIC:
                    if (currentUser != null) {
                        String userId = currentUser.getUid();
                        removeProfilePictureUrlFirestore(userId);
                    } else {
                        // Consider using string resources for user-facing messages
                        Toast.makeText(context, "User not logged in.", Toast.LENGTH_SHORT).show();
                    }
                    break;
                default:
                    Toast.makeText(context, "Action not yet implemented.", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    /**
     * Removes the profile picture URL from the user's document in Firestore.
     * @param userId The UID of the user whose profile picture URL is to be removed.
     */
    private void removeProfilePictureUrlFirestore(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        // Using FieldValue.delete() removes the field from the document
        updates.put(PROFILE_PIC_URL_FIELD, FieldValue.delete());

        db.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid ->
                        // Consider using string resources
                        Toast.makeText(context, "Profile Picture Removed", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    // Log the error for debugging
                    android.util.Log.e("AccountAdapter", "Error removing profile picture", e);
                    // Consider using string resources
                    Toast.makeText(context, "Failed to remove Profile Picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    public int getItemCount() {
        return accountOptions != null ? accountOptions.size() : 0;
    }

    // --- ViewHolder Class (already an inner static class) ---
    /**
     * ViewHolder for ic_account_vector.xml option items. Caches view lookups.
     */
    public static class AccountOptionViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textView;

        public AccountOptionViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these IDs (account_item_icon, account_item_text) exist in your 'account_item_view.xml'
            imageView = itemView.findViewById(R.id.account_item_icon);
            textView = itemView.findViewById(R.id.account_item_text);

            // You could also set an OnClickListener for the whole item view here if preferred,
            // though setting it in onBindViewHolder is also common and allows access to the item's position.
        }
    }
}