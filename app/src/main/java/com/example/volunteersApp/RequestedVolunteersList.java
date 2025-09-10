package com.example.volunteersApp;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Firestore Imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.List;
// It's good practice to remove unused imports, but Objects might be used if equals/hashCode were overridden.
// import java.util.Objects; // Not strictly used in current code, can be removed if not planned.

public class RequestedVolunteersList extends ArrayAdapter<String> {

    private static final String TAG = "ReqVolunteersFirestore";

    private final Activity context;
    private final List<String> volunteerUserIds; // List of UIDs for pending volunteers
    private final String eventId;
    private final String organizerId; // Added organizerId field

    // Firestore instance and references
    private final FirebaseFirestore db;
    private final DocumentReference eventDocRef; // DocumentReference to the specific event

    // Updated constructor to accept organizerId
    public RequestedVolunteersList(@NonNull Activity context, @NonNull List<String> volunteerUserIds, @NonNull String eventId, @NonNull String organizerId) {
        super(context, R.layout.customlayout2, volunteerUserIds); // R.layout.customlayout2 is your item layout
        this.context = context;
        this.volunteerUserIds = volunteerUserIds;
        this.eventId = eventId;
        this.organizerId = organizerId; // Store the passed organizerId

        this.db = FirebaseFirestore.getInstance();
        // Assuming 'events' is your top-level collection for events
        // The path to the event document does not typically include the organizerId unless your Firestore structure dictates it.
        // If organizerId is part of the event document's ID or path, you'd incorporate it here.
        // For now, assuming eventId is unique and sufficient to locate the document.
        this.eventDocRef = db.collection("events").document(this.eventId);

        // You can log or use this.organizerId if needed for adapter-specific logic,
        // e.g., if Firestore rules depend on it for read/write operations initiated by this adapter,
        // or if certain UI elements should behave differently based on the host.
        Log.d(TAG, "Adapter initialized for event: " + this.eventId + " by host: " + this.organizerId);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listViewItem = convertView;
        ViewHolder holder;

        if (listViewItem == null) {
            LayoutInflater inflater = context.getLayoutInflater();
            listViewItem = inflater.inflate(R.layout.customlayout2, parent, false);
            holder = new ViewHolder();
            // Ensure these IDs match your R.layout.customlayout2
            holder.textViewUserName = listViewItem.findViewById(R.id.textView_userId); // For volunteer's name
            holder.buttonAccept = listViewItem.findViewById(R.id.Button_acceptReq);
            holder.buttonReject = listViewItem.findViewById(R.id.Button_rejectReq);
            listViewItem.setTag(holder);
        } else {
            holder = (ViewHolder) listViewItem.getTag();
        }

        final String volunteerId = volunteerUserIds.get(position);

        // Fetch and display volunteer's name from Firestore 'users' collection
        // Cancel any previous listener for this view holder to prevent memory leaks or incorrect updates
        if (holder.userNameListenerRegistration != null) {
            holder.userNameListenerRegistration.remove();
        }

        DocumentReference userDocRef = db.collection("users").document(volunteerId);
        // Add a snapshot listener to get real-time updates for the user's name
        holder.userNameListenerRegistration = userDocRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed for user " + volunteerId, error);
                if (holder.textViewUserName != null) { // Check if view is still valid
                    holder.textViewUserName.setText(context.getString(R.string.error_loading_name)); // Use string resource
                }
                return;
            }

            if (holder.textViewUserName == null) return; // View might have been recycled

            if (snapshot != null && snapshot.exists()) {
                // Assuming user document has a "name" field. Adjust if your field name is different.
                String userName = snapshot.getString("name"); // "name" should be the field key in your Firestore 'users' document
                holder.textViewUserName.setText(userName != null && !userName.isEmpty() ? userName : context.getString(R.string.unknown_user));
            } else {
                Log.d(TAG, "User document not found for UID: " + volunteerId);
                holder.textViewUserName.setText(context.getString(R.string.unknown_user)); // Use string resource
            }
        });

        // --- Button Accept Logic ---
        holder.buttonAccept.setOnClickListener(v -> {
            // Disable buttons to prevent multiple clicks during the Firestore operation
            holder.buttonAccept.setEnabled(false);
            holder.buttonReject.setEnabled(false);

            WriteBatch batch = db.batch();

            // 1. Add volunteerId to 'approvedVolunteerIds' array in the event document
            batch.update(eventDocRef, "approvedVolunteerIds", FieldValue.arrayUnion(volunteerId));
            // 2. Remove volunteerId from 'pendingVolunteerIds' array in the event document
            batch.update(eventDocRef, "pendingVolunteerIds", FieldValue.arrayRemove(volunteerId));

            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        String successMsg = String.format(context.getString(R.string.volunteer_approved_toast), volunteerId);
                        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Volunteer " + volunteerId + " approved for event " + eventId);

                        // If your Activity is listening to changes on 'pendingVolunteerIds' via a snapshot listener,
                        // the list should update automatically.
                        // Otherwise, you might need to manually remove the item from 'volunteerUserIds'
                        // and call notifyDataSetChanged() if the list is not automatically refreshed
                        // by the hosting Activity/Fragment.
                        // Example (if not using Firestore live updates for the source list in Activity):
                        // if (volunteerUserIds.remove(volunteerId)) {
                        //     notifyDataSetChanged();
                        // }
                        // Re-enable buttons only after the operation is complete.
                        // However, since the item might be removed, re-enabling might not always be necessary
                        // if the view gets recycled or removed. Consider the UX.
                        // holder.buttonAccept.setEnabled(true); // Usually not needed if item is removed/list refreshed
                        // holder.buttonReject.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        String errorMsg = String.format(context.getString(R.string.failed_to_approve_toast), e.getMessage());
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to approve volunteer " + volunteerId + " for event " + eventId, e);
                        // Re-enable buttons on failure so the user can try again
                        holder.buttonAccept.setEnabled(true);
                        holder.buttonReject.setEnabled(true);
                    });
        });

        // --- Button Reject Logic ---
        holder.buttonReject.setOnClickListener(v -> {
            holder.buttonAccept.setEnabled(false);
            holder.buttonReject.setEnabled(false);

            WriteBatch batch = db.batch();

            // 1. Remove volunteerId from 'pendingVolunteerIds' array
            batch.update(eventDocRef, "pendingVolunteerIds", FieldValue.arrayRemove(volunteerId));
            // 2. Optionally, add to a 'rejectedVolunteerIds' array if you need to track rejections
            // batch.update(eventDocRef, "rejectedVolunteerIds", FieldValue.arrayUnion(volunteerId));

            batch.commit()
                    .addOnSuccessListener(aVoid -> {
                        String successMsg = String.format(context.getString(R.string.volunteer_rejected_toast), volunteerId);
                        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Volunteer " + volunteerId + " rejected for event " + eventId);
                        // Similar to accept, notify or refresh if needed and not handled by live updates
                        // Example:
                        // if (volunteerUserIds.remove(volunteerId)) {
                        //    notifyDataSetChanged();
                        // }
                        // holder.buttonAccept.setEnabled(true);
                        // holder.buttonReject.setEnabled(true);
                    })
                    .addOnFailureListener(e -> {
                        String errorMsg = String.format(context.getString(R.string.failed_to_reject_toast), e.getMessage());
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to reject volunteer " + volunteerId + " for event " + eventId, e);
                        holder.buttonAccept.setEnabled(true);
                        holder.buttonReject.setEnabled(true);
                    });
        });

        return listViewItem;
    }

    // ViewHolder pattern to improve ListView performance by caching view lookups
    static class ViewHolder {
        TextView textViewUserName;
        Button buttonAccept;
        Button buttonReject;
        ListenerRegistration userNameListenerRegistration; // To store Firestore listener for removal
    }

    /**
     * Call this method from your Activity's onDestroy or when the adapter is no longer needed
     * to clean up Firestore listeners attached in getView(). This is crucial to prevent
     * memory leaks and unnecessary background activity.
     *
     * @param listView The ListView instance this adapter is attached to.
     */
    public void cleanupListeners(@Nullable ViewGroup listView) {
        if (listView == null) {
            Log.d(TAG, "ListView is null, cannot cleanup listeners.");
            return;
        }
        Log.d(TAG, "Attempting to cleanup listeners for " + listView.getChildCount() + " visible items.");
        for (int i = 0; i < listView.getChildCount(); i++) {
            View childView = listView.getChildAt(i);
            if (childView != null && childView.getTag() instanceof ViewHolder) {
                ViewHolder holder = (ViewHolder) childView.getTag();
                if (holder.userNameListenerRegistration != null) {
                    holder.userNameListenerRegistration.remove();
                    Log.d(TAG, "Cleaned up username listener for visible item at position " + i);
                    holder.userNameListenerRegistration = null; // Help GC
                }
            } else {
                Log.w(TAG, "Child view or its tag is not a ViewHolder at position " + i);
            }
        }
        Log.d(TAG, "CleanupListeners called for visible items.");
    }
}