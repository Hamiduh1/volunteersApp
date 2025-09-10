package com.example.volunteersApp.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.R;
import com.example.volunteersApp.models.JobPost;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.Objects;

public class JobPostAdapter extends ListAdapter<JobPost, JobPostAdapter.JobPostViewHolder> {

    private static final String TAG = "JobPostAdapter";
    private static final String JOB_POSTINGS_COLLECTION = "job_postings";
    private static final String APPLICATIONS_COLLECTION = "applications";
    private static final String FIELD_JOB_POST_ID = "jobPostId";
    private static final String FIELD_STATUS = "status";

    private final OnJobPostActionListener actionListener;
    private final boolean isEmployerView; // To distinguish behavior for employer view

    public interface OnJobPostActionListener {
        void onViewDetailsClicked(@NonNull JobPost jobPost, int position);
        void onJobPostDelete(@NonNull JobPost jobPost, int position);
    }

    /**
     * Primary constructor for JobPostAdapter.
     *
     * @param listener The listener for item actions.
     * @param isEmployerView True if the adapter is used in an employer's view (e.g., to show delete buttons), false otherwise.
     */
    public JobPostAdapter(@NonNull OnJobPostActionListener listener, boolean isEmployerView) {
        super(DIFF_CALLBACK); // Crucial call to the ListAdapter constructor
        this.actionListener = listener;
        this.isEmployerView = isEmployerView;
    }

    // Default constructor for non-employer views (e.g., job listings for volunteers)
    // where delete functionality is not needed.
    public JobPostAdapter(@NonNull OnJobPostActionListener listener) {
        this(listener, false); // Defaults to not being an employer view
    }


    private static final DiffUtil.ItemCallback<JobPost> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<JobPost>() {
                @Override
                public boolean areItemsTheSame(@NonNull JobPost oldItem, @NonNull JobPost newItem) {
                    return Objects.equals(oldItem.getJobPostId(), newItem.getJobPostId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull JobPost oldItem, @NonNull JobPost newItem) {
                    return oldItem.equals(newItem); // Requires a well-implemented equals() in JobPost
                }
            };

    @NonNull
    @Override
    public JobPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_post, parent, false);
        // Pass the adapter-level listener and the isEmployerView flag to the ViewHolder
        return new JobPostViewHolder(view, actionListener, isEmployerView);
    }

    @Override
    public void onBindViewHolder(@NonNull JobPostViewHolder holder, int position) {
        JobPost jobPost = getItem(position); // getItem() is from ListAdapter
        // ListAdapter's getItem() should handle nulls appropriately if the list has placeholders,
        // but it's good practice to ensure jobPost is not null before binding.
        // However, with ListAdapter, if an item is at a position, it's generally non-null unless
        // you've submitted a list with nulls.
        if (jobPost != null) {
            holder.bind(jobPost);
        } else {
            // This case should be rare with ListAdapter unless you explicitly submit nulls.
            Log.w(TAG, "JobPost item at position " + position + " is unexpectedly null.");
            // Optionally, clear the ViewHolder or hide it.
            holder.itemView.setVisibility(View.GONE);
        }
    }

    // getItemCount() is handled by ListAdapter.
    // submitList(List<JobPost> list) is used from the Fragment/Activity to update data.

    static class JobPostViewHolder extends RecyclerView.ViewHolder {
        TextView textViewTitle, textViewOrgName, textViewLocation, textViewDate, textViewStatus;
        ImageButton buttonDeleteJobPost;
        Button buttonViewDetails;
        private final Context context; // For AlertDialogs, Toasts, string resources

        // These are passed from the adapter and specific to this ViewHolder instance's configuration
        private final OnJobPostActionListener currentActionListener;
        private final boolean isCurrentEmployerView;

        public JobPostViewHolder(@NonNull View itemView,
                                 @NonNull OnJobPostActionListener listener,
                                 boolean isEmployerViewFlag) { // Renamed for clarity
            super(itemView);
            this.context = itemView.getContext();
            this.currentActionListener = listener;
            this.isCurrentEmployerView = isEmployerViewFlag;

            textViewTitle = itemView.findViewById(R.id.textViewItemJobTitle);
            textViewOrgName = itemView.findViewById(R.id.textViewItemOrgName);
            textViewLocation = itemView.findViewById(R.id.textViewItemLocation);
            textViewDate = itemView.findViewById(R.id.textViewItemDate);
            textViewStatus = itemView.findViewById(R.id.textViewItemStatus);

            buttonDeleteJobPost = itemView.findViewById(R.id.buttonDeleteJobPost);
            buttonViewDetails = itemView.findViewById(R.id.buttonViewDetails);
        }

        public void bind(@NonNull final JobPost jobPost) {
            itemView.setVisibility(View.VISIBLE); // Ensure visible

            textViewTitle.setText(jobPost.getJobTitle() != null ? jobPost.getJobTitle() : jobPost.getTitle());
            textViewOrgName.setText(jobPost.getOrganizationName());
            textViewLocation.setText(jobPost.getLocationName());
            textViewDate.setText(jobPost.getDate());

            if (textViewStatus != null) {
                textViewStatus.setText(context.getString(R.string.status_prefix_label_caps,
                        (jobPost.getStatus() != null ? jobPost.getStatus().toUpperCase() : "N/A")));
            }

            if (buttonViewDetails != null) {
                buttonViewDetails.setOnClickListener(v -> {
                    if (currentActionListener != null) {
                        int currentPos = getBindingAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            currentActionListener.onViewDetailsClicked(jobPost, currentPos);
                        }
                    }
                });
            } else {
                Log.w(TAG, "buttonViewDetails is null. Check item_job_post.xml.");
            }

            if (buttonDeleteJobPost != null) {
                if (isCurrentEmployerView) {
                    buttonDeleteJobPost.setVisibility(View.VISIBLE);
                    buttonDeleteJobPost.setOnClickListener(v -> {
                        int currentPos = getBindingAdapterPosition();
                        if (currentPos != RecyclerView.NO_POSITION) {
                            showDeleteConfirmationDialog(jobPost, currentPos);
                        }
                    });
                } else {
                    buttonDeleteJobPost.setVisibility(View.GONE);
                }
            } else {
                Log.w(TAG, "buttonDeleteJobPost is null. Check item_job_post.xml.");
            }
        }

        private void showDeleteConfirmationDialog(@NonNull final JobPost jobPost, final int position) {
            if (context == null) {
                Log.e(TAG, "Context is null in showDeleteConfirmationDialog.");
                return;
            }
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.delete_job_confirmation_title))
                    .setMessage(context.getString(R.string.delete_job_confirmation_message))
                    .setPositiveButton(context.getString(R.string.delete_job_positive_button), (dialog, which) -> {
                        deleteJobPostAndApplicationsWithFirestore(jobPost, position);
                        dialog.dismiss();
                    })
                    .setNegativeButton(context.getString(R.string.delete_job_negative_button), (dialog, which) -> dialog.dismiss())
                    .show();
        }

        private void deleteJobPostAndApplicationsWithFirestore(@NonNull final JobPost jobPost, final int position) {
            final String jobPostId = jobPost.getJobPostId();
            if (jobPostId == null || jobPostId.isEmpty()) {
                Toast.makeText(context, "Error: Job ID is missing for deletion.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "JobPostId is null or empty, cannot delete.");
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            CollectionReference applicationsRef = db.collection(APPLICATIONS_COLLECTION);
            CollectionReference jobPostingsRef = db.collection(JOB_POSTINGS_COLLECTION);

            applicationsRef.whereEqualTo(FIELD_JOB_POST_ID, jobPostId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        WriteBatch batch = db.batch();
                        int updatedApplicationsCount = 0;
                        for (QueryDocumentSnapshot appSnapshot : queryDocumentSnapshots) {
                            batch.update(appSnapshot.getReference(), FIELD_STATUS, "job_cancelled");
                            batch.update(appSnapshot.getReference(), "cancellationReason", "The job post was removed by the employer.");
                            updatedApplicationsCount++;
                        }

                        int finalUpdatedApplicationsCount = updatedApplicationsCount;
                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    if (finalUpdatedApplicationsCount > 0) {
                                        Log.d(TAG, "Successfully updated status of " + finalUpdatedApplicationsCount + " applications for job " + jobPostId);
                                    } else {
                                        Log.d(TAG, "No applications found to update for job " + jobPostId);
                                    }
                                    deleteActualJobPostWithFirestore(jobPostingsRef, jobPostId, jobPost, position);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to update applications for job " + jobPostId, e);
                                    Toast.makeText(context, "Failed to update job applications. Job not deleted.", Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error querying applications for job " + jobPostId, e);
                        Toast.makeText(context, "Failed to query applications. Job not deleted.", Toast.LENGTH_LONG).show();
                    });
        }

        private void deleteActualJobPostWithFirestore(CollectionReference jobPostParentNodeRef,
                                                      final String jobPostId,
                                                      @NonNull final JobPost jobPostToDelete,
                                                      final int position) {
            jobPostParentNodeRef.document(jobPostId).delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(context, context.getString(R.string.job_deleted_successfully), Toast.LENGTH_SHORT).show();
                        if (currentActionListener != null) {
                            currentActionListener.onJobPostDelete(jobPostToDelete, position);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete job post " + jobPostId, e);
                        Toast.makeText(context, context.getString(R.string.job_deletion_failed), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
