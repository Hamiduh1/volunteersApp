package com.example.volunteersApp.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.EventModel;
// Import Glide or Picasso if you plan to use them for image loading.
// e.g., import com.bumptech.glide.Glide;
// e.g., import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.FirebaseStorage;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class EventAdapter extends ListAdapter<EventModel, EventAdapter.EventViewHolder> {

    private static final String TAG = "EventAdapter";


    private final Context context;
    private final OnEventListener onEventListener;
    private final boolean isOrganizerView;
    private Set<String> appliedEventIds;
    private final FirebaseStorage storage; // Keep if you use it for Glide/Picasso with StorageReference

    public interface OnEventListener {
        void onEventClick(EventModel event, int position);
        void onApplyClick(EventModel event, int position);
        void onViewApplicantsClick(EventModel event, int position);
        void onEventOptionsClicked(EventModel event, View anchorView);
    }

    public EventAdapter(@NonNull Context context, boolean isOrganizerView,
                        @NonNull OnEventListener onEventListener,
                        @Nullable Set<String> initialAppliedEventIds, // Made nullable for clarity
                        @Nullable FirebaseStorage storage) { // Made nullable
        super(EVENT_DIFF_CALLBACK);
        this.context = context;
        this.isOrganizerView = isOrganizerView;
        this.onEventListener = onEventListener;
        this.appliedEventIds = (initialAppliedEventIds != null) ? new HashSet<>(initialAppliedEventIds) : new HashSet<>();
        this.storage = storage; // Can be null if not loading from Firebase Storage via Glide/Picasso StorageReference
        Log.d(TAG, "Adapter created. Organizer View: " + isOrganizerView);
    }

    public void setAppliedEventIds(Set<String> newAppliedEventIds) {
        this.appliedEventIds.clear();
        if (newAppliedEventIds != null) {
            this.appliedEventIds.addAll(newAppliedEventIds);
        }
        Log.d(TAG, "Applied event IDs updated. Count: " + this.appliedEventIds.size());
        // Notify adapter about changes. ListAdapter handles diffing efficiently
        // so a simple notifyDataSetChanged() might be okay for external data changes like this,
        // or be more specific if possible.
        // For simplicity, if the list isn't huge, a full rebind of visible items is acceptable.
        notifyItemRangeChanged(0, getItemCount()); // Rebinds all currently visible items
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view, onEventListener, isOrganizerView, context, storage);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventModel event = getItem(position);

        if (event == null) {
            Log.w(TAG, "onBindViewHolder for position " + position + ". Event is NULL.");
            holder.bind(null, false);
            return;
        }

        Log.d(TAG, "onBindViewHolder for pos " + position + ". Event: " + event.getTitle() + ", ID: " + event.getEventId());

        boolean hasApplied = false;
        if (!isOrganizerView && event.getEventId() != null) {
            hasApplied = appliedEventIds.contains(event.getEventId());
        }
        holder.bind(event, hasApplied);
    }

    // getItemCount() is handled by ListAdapter


    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView textViewEventTitle, textViewEventDate, textViewEventLocation, textViewApplicantsInfo;
        Button buttonApply, buttonViewApplicants;
        ImageView imageViewEventOptions, imageViewEventBanner;
        View eventItemContainer;

        private final OnEventListener listenerCallback;
        private final boolean isOrganizerModeViewHolder;
        private final Context viewHolderContext;
        private final FirebaseStorage viewHolderStorage; // Keep if using for Glide/Picasso

        public EventViewHolder(@NonNull View itemView, OnEventListener listener,
                               boolean isOrganizerMode, Context context, FirebaseStorage storage) {
            super(itemView);
            this.listenerCallback = listener;
            this.isOrganizerModeViewHolder = isOrganizerMode;
            this.viewHolderContext = context;
            this.viewHolderStorage = storage; // Can be null

            eventItemContainer = itemView.findViewById(R.id.eventItemContainer);
            textViewEventTitle = itemView.findViewById(R.id.textViewEventTitle);
            textViewEventDate = itemView.findViewById(R.id.textViewEventDate);
            textViewEventLocation = itemView.findViewById(R.id.textViewEventLocation);
            buttonApply = itemView.findViewById(R.id.buttonApplyForEvent);
            buttonViewApplicants = itemView.findViewById(R.id.buttonViewApplicants);
            textViewApplicantsInfo = itemView.findViewById(R.id.textViewApplicantsInfo);
            imageViewEventOptions = itemView.findViewById(R.id.imageViewEventOptions);
            imageViewEventBanner = itemView.findViewById(R.id.imageViewEventBanner);

            if (eventItemContainer == null || textViewEventTitle == null || textViewEventDate == null || textViewEventLocation == null) {
                Log.e(TAG, "ViewHolder: One or more critical views not found. Check item_event.xml IDs.");
                // Depending on requirements, you might throw an exception or handle this more gracefully.
            }
        }

        void bind(final EventModel event, boolean hasApplied) {
            if (event == null) {
                // Set views to a default/error state
                if (textViewEventTitle != null) textViewEventTitle.setText(R.string.error_loading_event_details);
                if (textViewEventDate != null) textViewEventDate.setText("");
                if (textViewEventLocation != null) textViewEventLocation.setText("");
                if (imageViewEventBanner != null) imageViewEventBanner.setVisibility(View.GONE);
                if (buttonApply != null) buttonApply.setVisibility(View.GONE);
                if (buttonViewApplicants != null) buttonViewApplicants.setVisibility(View.GONE);
                if (textViewApplicantsInfo != null) textViewApplicantsInfo.setVisibility(View.GONE);
                if (imageViewEventOptions != null) imageViewEventOptions.setVisibility(View.GONE);
                if (eventItemContainer != null) eventItemContainer.setOnClickListener(null);
                return;
            }

            // Reset listeners to avoid issues with recycled views
            if (eventItemContainer != null) eventItemContainer.setOnClickListener(null);
            if (buttonApply != null) buttonApply.setOnClickListener(null);
            if (buttonViewApplicants != null) buttonViewApplicants.setOnClickListener(null);
            if (imageViewEventOptions != null) imageViewEventOptions.setOnClickListener(null);


            // Bind data
            if (textViewEventTitle != null) {
                textViewEventTitle.setText(event.getTitle() != null ? event.getTitle() : viewHolderContext.getString(R.string.name_not_available));
            }

            if (textViewEventDate != null) {
                if (event.getEventDateTime() != null) {
                    try {
                        Date eventDate = event.getEventDateTime().toDate(); // Convert Timestamp to Date
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                        textViewEventDate.setText(sdf.format(eventDate));
                    } catch (Exception e) {
                        Log.e(TAG, "Error formatting date: " + event.getEventDateTime(), e);
                        textViewEventDate.setText(R.string.date_not_available);
                    }
                } else {
                    textViewEventDate.setText(R.string.date_not_available);
                }
            }

            if (textViewEventLocation != null) {
                textViewEventLocation.setText(event.getLocationName() != null ? event.getLocationName() : viewHolderContext.getString(R.string.location_not_available));
            }


            // Image Loading (Placeholder - Implement with Glide/Picasso)
            if (imageViewEventBanner != null) {
                if (event.getImageUrl() != null && !event.getImageUrl().isEmpty()) {
                    imageViewEventBanner.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Image loading with Glide/Picasso for URL: " + event.getImageUrl());
                    // Example with Glide:
                    // if (viewHolderContext != null) { // Ensure context is not null
                    //    Glide.with(viewHolderContext)
                    //        .load(event.getImageUrl()) // Assumes getImageUrl() returns a public HTTPS URL
                    //        // If getImageUrl() is a Firebase Storage gs:// URL and viewHolderStorage is not null:
                    //        // .load(viewHolderStorage.getReferenceFromUrl(event.getImageUrl()))
                    //        .placeholder(R.drawable.placeholder_event_banner) // Ensure these drawables exist
                    //        .error(R.drawable.error_event_banner)
                    //        .into(imageViewEventBanner);
                    // }
                    // For now, simple placeholder:
                    imageViewEventBanner.setImageResource(R.drawable.ic_launcher_background); // Replace with actual placeholder
                } else {
                    imageViewEventBanner.setVisibility(View.GONE); // Or set a default placeholder image
                }
            }


            // Click listener for the whole item
            if (eventItemContainer != null && listenerCallback != null) {
                eventItemContainer.setOnClickListener(v -> {
                    if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                        Log.d(TAG, "Item clicked: " + event.getTitle());
                        listenerCallback.onEventClick(event, getBindingAdapterPosition());
                    }
                });
            }

            // UI based on Organizer or Volunteer view
            if (isOrganizerModeViewHolder) {
                if (buttonApply != null) buttonApply.setVisibility(View.GONE);
                if (imageViewEventOptions != null) imageViewEventOptions.setVisibility(View.VISIBLE);
                if (buttonViewApplicants != null) buttonViewApplicants.setVisibility(View.VISIBLE);

                if (buttonViewApplicants != null && listenerCallback != null) {
                    buttonViewApplicants.setEnabled(true);
                    buttonViewApplicants.setOnClickListener(v -> {
                        if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                            Log.d(TAG, "View Applicants clicked for: " + event.getTitle());
                            listenerCallback.onViewApplicantsClick(event, getBindingAdapterPosition());
                        }
                    });
                }

                if (textViewApplicantsInfo != null) {
                    textViewApplicantsInfo.setVisibility(View.VISIBLE);
                    textViewApplicantsInfo.setText(
                            viewHolderContext.getString(R.string.applicants_info_organizer,
                                    event.getParticipantsCount(), event.getVolunteerLimit())
                    );
                }

                if (imageViewEventOptions != null && listenerCallback != null) {
                    imageViewEventOptions.setOnClickListener(v -> {
                        if (getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                            Log.d(TAG, "Options icon clicked for: " + event.getTitle());
                            listenerCallback.onEventOptionsClicked(event, v);
                        }
                    });
                }

            } else { // Volunteer View
                if (buttonViewApplicants != null) buttonViewApplicants.setVisibility(View.GONE);
                if (imageViewEventOptions != null) imageViewEventOptions.setVisibility(View.GONE);
                if (buttonApply != null) buttonApply.setVisibility(View.VISIBLE);

                if (buttonApply != null) {
                    if (event.getEventId() == null) {
                        Log.w(TAG, "ViewHolder bind: Event ID is NULL for '" + event.getTitle() + "'. Disabling Apply button.");
                        buttonApply.setText(R.string.apply_button_text); // Default text
                        buttonApply.setEnabled(false);
                        if (textViewApplicantsInfo != null) textViewApplicantsInfo.setVisibility(View.GONE);
                    } else if (hasApplied) {
                        buttonApply.setText(R.string.applied_status);
                        buttonApply.setEnabled(false);
                        if (textViewApplicantsInfo != null) {
                            textViewApplicantsInfo.setVisibility(View.VISIBLE);
                            textViewApplicantsInfo.setText(R.string.already_applied);
                        }
                    } else if (event.getVolunteerLimit() > 0 && event.getParticipantsCount() >= event.getVolunteerLimit()) {
                        buttonApply.setText(R.string.event_full);
                        buttonApply.setEnabled(false);
                        if (textViewApplicantsInfo != null) {
                            textViewApplicantsInfo.setVisibility(View.VISIBLE);
                            textViewApplicantsInfo.setText(viewHolderContext.getString(R.string.slots_info, event.getParticipantsCount(), event.getVolunteerLimit()));
                        }
                    } else if (event.getVolunteerLimit() == 0 && event.getParticipantsCount() > 0) { // No slots defined, but people applied (edge case?)
                        buttonApply.setText(R.string.event_full); // Or some other status like "Accepting Waitlist"
                        buttonApply.setEnabled(false);
                        if (textViewApplicantsInfo != null) {
                            textViewApplicantsInfo.setVisibility(View.VISIBLE);
                            textViewApplicantsInfo.setText(R.string.event_full); // Or specific message
                        }
                    }
                    else { // Available to apply
                        buttonApply.setText(R.string.apply_button_text);
                        buttonApply.setEnabled(true);
                        if (textViewApplicantsInfo != null) {
                            textViewApplicantsInfo.setVisibility(View.VISIBLE);
                            textViewApplicantsInfo.setText(viewHolderContext.getString(R.string.slots_info, event.getParticipantsCount(), event.getVolunteerLimit()));
                        }
                    }

                    if (listenerCallback != null && buttonApply.isEnabled()) {
                        buttonApply.setOnClickListener(v -> {
                            if (getBindingAdapterPosition() != RecyclerView.NO_POSITION && event.getEventId() != null) {
                                Log.d(TAG, "Apply button clicked for: " + event.getTitle());
                                listenerCallback.onApplyClick(event, getBindingAdapterPosition());
                            } else {
                                Log.e(TAG, "Apply button clicked but event ID or position invalid for event: " + event.getTitle());
                                Toast.makeText(viewHolderContext, R.string.error_cannot_apply, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }
        }
    }


    private static final DiffUtil.ItemCallback<EventModel> EVENT_DIFF_CALLBACK =
            new DiffUtil.ItemCallback<EventModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull EventModel oldItem, @NonNull EventModel newItem) {
                    // Event ID is the primary unique identifier.
                    // Handle cases where IDs might be null if events are not yet saved to Firestore.
                    if (oldItem.getEventId() != null && newItem.getEventId() != null) {
                        return Objects.equals(oldItem.getEventId(), newItem.getEventId());
                    }
                    // If IDs are null, fallback to object reference,
                    // or if it's a new item not yet in list, it's different.
                    return oldItem == newItem;
                }

                // In EventAdapter.java
                //@SuppressLint("DiffUtilEquals") // Suppress if Lint is still picky about the primitive '=='
                @SuppressLint("DiffUtilEquals")
                @Override
                public boolean areContentsTheSame(@NonNull EventModel oldItem, @NonNull EventModel newItem) {
                    // Compare all fields that affect UI representation.
                    // Using Objects.equals for null safety for Object types.
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                            Objects.equals(oldItem.getEventDateTime(), newItem.getEventDateTime()) &&
                            Objects.equals(oldItem.getLocationName(), newItem.getLocationName()) &&
                            Objects.equals(oldItem.getImageUrl(), newItem.getImageUrl()) &&
                            // For primitive int, == is correct
                            oldItem.getParticipantsCount() == newItem.getParticipantsCount() &&
                            // For Integer object type, Objects.equals is correct
                            Objects.equals(oldItem.getVolunteerLimit(), newItem.getVolunteerLimit()) &&
                            // For primitive boolean, == is correct
                            oldItem.isActive() == newItem.isActive() &&
                            Objects.equals(oldItem.getStatus(), newItem.getStatus()) &&
                            Objects.equals(oldItem.getDescription(), newItem.getDescription());
                }
            };
}

