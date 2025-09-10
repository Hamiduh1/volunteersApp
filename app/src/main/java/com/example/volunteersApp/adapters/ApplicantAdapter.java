package com.example.volunteersApp.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.Application;
import com.example.volunteersApp.models.ApplicationWithUserDetails;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ApplicantAdapter extends ListAdapter<ApplicationWithUserDetails, ApplicantAdapter.ApplicantViewHolder> {

    private final OnApplicantActionListener actionListener; // Renamed from 'listener' to avoid confusion with the parameter
    private final Context context; // Keep context if needed for inflation or resources not accessible via itemView.getContext()
    private static final String TAG = "ApplicantAdapter";

    // Interface for click handling, to be implemented by the Activity/Fragment
    public interface OnApplicantActionListener {
        void onApplicantClick(ApplicationWithUserDetails applicant, int position);
        void onAcceptApplicant(ApplicationWithUserDetails applicant);
        void onRejectApplicant(ApplicationWithUserDetails applicant);
        void onContactApplicant(ApplicationWithUserDetails applicant);
    }

    // Corrected constructor
    public ApplicantAdapter(@NonNull Context context, @NonNull OnApplicantActionListener listener) {
        super(DIFF_CALLBACK); // Pass the DiffUtil.ItemCallback to the ListAdapter's constructor
        this.context = context; // Store context if needed widely
        this.actionListener = listener; // Store the listener
        // The List<ApplicationWithUserDetails> is managed by ListAdapter itself via submitList(),
        // so you don't need to store 'applicantDetailsList' as a separate member variable here.
    }

    @NonNull
    @Override
    public ApplicantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use parent.getContext() for inflater unless 'this.context' is specifically needed for a different theme/resources
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_applicant, parent, false);
        // Pass the adapter-level listener to the ViewHolder
        return new ApplicantViewHolder(view, actionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ApplicantViewHolder holder, int position) {
        ApplicationWithUserDetails currentApplicantDetails = getItem(position); // getItem() from ListAdapter
        if (currentApplicantDetails != null) {
            holder.bind(currentApplicantDetails); // The listener is already passed to ViewHolder constructor
        } else {
            Log.w(TAG, "currentApplicantDetails is null at position: " + position + ". Binding placeholder.");
            holder.bindPlaceholder();
        }
    }

    // ViewHolder class
    class ApplicantViewHolder extends RecyclerView.ViewHolder {
        TextView textViewApplicantName, textViewApplicantEmail, textViewApplicationDate, textViewApplicationStatus;
        ImageView imageViewApplicantProfile;
        ImageButton buttonAcceptApplicant, buttonRejectApplicant, buttonContactApplicant;
        Context itemViewContext; // Good for ViewHolder-specific context needs

        // ViewHolder now receives the listener passed from the adapter
        public ApplicantViewHolder(@NonNull View itemView, final OnApplicantActionListener passedListener) {
            super(itemView);
            this.itemViewContext = itemView.getContext(); // Cache itemView's context

            textViewApplicantName = itemView.findViewById(R.id.textViewApplicantName);
            textViewApplicantEmail = itemView.findViewById(R.id.textViewApplicantEmail);
            textViewApplicationDate = itemView.findViewById(R.id.textViewApplicationDate);
            textViewApplicationStatus = itemView.findViewById(R.id.textViewApplicationStatus);
            imageViewApplicantProfile = itemView.findViewById(R.id.imageViewApplicantProfile);
            buttonAcceptApplicant = itemView.findViewById(R.id.buttonAcceptApplicant);
            buttonRejectApplicant = itemView.findViewById(R.id.buttonRejectApplicant);
            buttonContactApplicant = itemView.findViewById(R.id.buttonContactApplicant);

            itemView.setOnClickListener(v -> {
                int currentPosition = getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION && passedListener != null) {
                    ApplicationWithUserDetails item = getItem(currentPosition); // Get item safely using ListAdapter's getItem
                    if (item != null) {
                        passedListener.onApplicantClick(item, currentPosition);
                    }
                }
            });

            if (buttonAcceptApplicant != null) {
                buttonAcceptApplicant.setOnClickListener(v -> {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION && passedListener != null) {
                        ApplicationWithUserDetails item = getItem(currentPosition);
                        if (item != null) {
                            passedListener.onAcceptApplicant(item);
                        }
                    }
                });
            }

            if (buttonRejectApplicant != null) {
                buttonRejectApplicant.setOnClickListener(v -> {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION && passedListener != null) {
                        ApplicationWithUserDetails item = getItem(currentPosition);
                        if (item != null) {
                            passedListener.onRejectApplicant(item);
                        }
                    }
                });
            }

            if (buttonContactApplicant != null) {
                buttonContactApplicant.setOnClickListener(v -> {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION && passedListener != null) {
                        ApplicationWithUserDetails item = getItem(currentPosition);
                        if (item != null) {
                            passedListener.onContactApplicant(item);
                        }
                    }
                });
            }
        }

        void bindPlaceholder() {
            textViewApplicantName.setText(itemViewContext.getString(R.string.placeholder_na));
            textViewApplicantEmail.setText(itemViewContext.getString(R.string.placeholder_na));
            if (textViewApplicationDate != null) {
                textViewApplicationDate.setText(itemViewContext.getString(R.string.applied_on_placeholder_na));
            }
            if (textViewApplicationStatus != null) {
                textViewApplicationStatus.setText(itemViewContext.getString(R.string.status_placeholder_na));
            }
            imageViewApplicantProfile.setImageResource(R.drawable.default_profile_image);

            if (buttonAcceptApplicant != null) buttonAcceptApplicant.setVisibility(View.GONE);
            if (buttonRejectApplicant != null) buttonRejectApplicant.setVisibility(View.GONE);
            if (buttonContactApplicant != null) buttonContactApplicant.setVisibility(View.GONE);
        }

        // The listener is no longer passed here; it's set in the ViewHolder's constructor
        void bind(final ApplicationWithUserDetails details) {
            textViewApplicantName.setText(details.getTitle() != null ? details.getTitle() : itemViewContext.getString(R.string.placeholder_na));
            textViewApplicantEmail.setText(details.getEmail() != null ? details.getEmail() : itemViewContext.getString(R.string.placeholder_na));

            Application application = details.getApplication();
            if (textViewApplicationDate != null && application != null && application.getApplicationTimestamp() != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    String formattedDate = sdf.format(application.getApplicationTimestamp().toDate());
                    textViewApplicationDate.setText(itemViewContext.getString(R.string.applied_on_prefix, formattedDate));
                } catch (Exception e) {
                    Log.e(TAG, "Error formatting date from Firebase Timestamp", e);
                    textViewApplicationDate.setText(itemViewContext.getString(R.string.applied_on_prefix, itemViewContext.getString(R.string.placeholder_na_short)));
                }
            } else if (textViewApplicationDate != null) {
                textViewApplicationDate.setText(itemViewContext.getString(R.string.applied_on_prefix, itemViewContext.getString(R.string.placeholder_na_short)));
            }

            String status = (application != null && application.getStatus() != null && !application.getStatus().isEmpty())
                    ? application.getStatus()
                    : "pending";
            if (textViewApplicationStatus != null) {
                String capitalizedStatus = status.substring(0, 1).toUpperCase() + status.substring(1);
                textViewApplicationStatus.setText(itemViewContext.getString(R.string.status_prefix, capitalizedStatus));
            }

            updateButtonStates(status);

            String imageUrl = details.getProfileImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Picasso.get()
                        .load(imageUrl)
                        .placeholder(R.drawable.default_profile_image)
                        .error(R.drawable.default_profile_image)
                        .fit().centerCrop()
                        .into(imageViewApplicantProfile);
            } else {
                imageViewApplicantProfile.setImageResource(R.drawable.default_profile_image);
            }
            // Listener setup is now done in the ViewHolder's constructor
        }

        private void updateButtonStates(String status) {
            if (buttonAcceptApplicant == null || buttonRejectApplicant == null) return;

            boolean isPending = "pending".equalsIgnoreCase(status);
            boolean isAccepted = "accepted".equalsIgnoreCase(status);
            boolean isRejected = "rejected".equalsIgnoreCase(status);

            buttonAcceptApplicant.setVisibility(View.VISIBLE);
            buttonAcceptApplicant.setEnabled(true);
            buttonRejectApplicant.setVisibility(View.VISIBLE);
            buttonRejectApplicant.setEnabled(true);
            // Contact button visibility should be decided based on your logic
            if (buttonContactApplicant != null) {
                buttonContactApplicant.setVisibility(View.VISIBLE); // Or base on status
            }


            if (isAccepted) {
                buttonAcceptApplicant.setEnabled(false);
            } else if (isRejected) {
                buttonRejectApplicant.setEnabled(false);
                buttonAcceptApplicant.setVisibility(View.GONE); // Example: hide accept if rejected
                buttonRejectApplicant.setVisibility(View.GONE); // Example: hide reject if rejected
            } else if (isPending) {
                // Both are visible and enabled by default for pending
            }
        }
    }

    private static final DiffUtil.ItemCallback<ApplicationWithUserDetails> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ApplicationWithUserDetails>() {
                @Override
                public boolean areItemsTheSame(@NonNull ApplicationWithUserDetails oldItem, @NonNull ApplicationWithUserDetails newItem) {
                    if (oldItem.getApplication() == null || newItem.getApplication() == null) {
                        // If one or both are null, they are not the same unless both are null in some specific logic
                        return oldItem.getApplication() == null && newItem.getApplication() == null && oldItem.getUid().equals(newItem.getUid());
                    }
                    if (oldItem.getApplication().getApplicationId() == null || newItem.getApplication().getApplicationId() == null) {
                        // If IDs are null, fall back to another comparison or consider them not the same identifiable item.
                        // For instance, compare based on userId and perhaps another key if eventId is part of ApplicationWithUserDetails
                        return oldItem.getUid().equals(newItem.getUid()); // Example fallback
                    }
                    return oldItem.getApplication().getApplicationId().equals(newItem.getApplication().getApplicationId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ApplicationWithUserDetails oldItem, @NonNull ApplicationWithUserDetails newItem) {
                    // Make sure ApplicationWithUserDetails has a well-defined equals() method,
                    // or compare fields manually.
                    return oldItem.equals(newItem);
                }
            };
}
