package com.example.volunteersApp.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.JobDetailsActivity;
import com.example.volunteersApp.R;
import com.example.volunteersApp.models.JobPost;
import com.example.volunteersApp.models.EventModel; // Assuming JobPost has getJobPostId() and equals()

import java.util.Objects;

public class JobPostSimpleAdapter extends ListAdapter<JobPost, JobPostSimpleAdapter.JobPostSimpleViewHolder> {

    private OnJobPostClickListener listener; // The listener instance

    /**
     * Interface for click events on job posts.
     */
    public interface OnJobPostClickListener {
        void onJobPostClick(@NonNull JobPost jobPost);
    }

    /**
     * Constructor for ListAdapter when a custom click listener is provided by the calling Activity/Fragment.
     *
     * @param listener The callback that will be invoked when an item is clicked.
     */
    public JobPostSimpleAdapter(OnJobPostClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /**
     * Constructor for ListAdapter when no custom click listener is provided.
     * In this case, the adapter will handle clicks with a default action (e.g., navigating to JobDetailsActivity).
     */
    public JobPostSimpleAdapter() {
        super(DIFF_CALLBACK);
        this.listener = null; // No external listener, ViewHolder will use default navigation
    }


    private static final DiffUtil.ItemCallback<JobPost> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<JobPost>() {
                @Override
                public boolean areItemsTheSame(@NonNull JobPost oldItem, @NonNull JobPost newItem) {
                    // Use the unique ID from JobPost model for item identity
                    return Objects.equals(oldItem.getJobPostId(), newItem.getJobPostId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull JobPost oldItem, @NonNull JobPost newItem) {
                    // Uses the equals() method implemented in JobPost model to check for content changes
                    return oldItem.equals(newItem);
                }
            };

    @NonNull
    @Override
    public JobPostSimpleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_job_post_simple, parent, false);
        // Pass the listener (which might be null) to the ViewHolder
        return new JobPostSimpleViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull JobPostSimpleViewHolder holder, int position) {
        JobPost jobPost = getItem(position); // getItem() is provided by ListAdapter
        if (jobPost != null) { // ListAdapter generally ensures non-null items if list is not empty
            holder.bind(jobPost);
        }
        // No explicit 'else' needed here, ListAdapter and ViewHolder should handle item states.
    }

    // getItemCount() is automatically handled by ListAdapter.
    // No need for a custom updateData() method; use submitList(List<JobPost> list) from the Activity/Fragment.


    static class JobPostSimpleViewHolder extends RecyclerView.ViewHolder {
        TextView textViewJobTitleSimple;
        TextView textViewJobLocationSimple;
        TextView textViewJobTypeSimple;
        private OnJobPostClickListener currentViewHolderListener; // Store the listener for this specific ViewHolder

        public JobPostSimpleViewHolder(@NonNull View itemView, OnJobPostClickListener passedListener) {
            super(itemView);
            this.currentViewHolderListener = passedListener; // Store the listener passed from the adapter

            // Initialize views from item_job_post_simple.xml
            // Ensure these IDs exist in your R.layout.item_job_post_simple
            textViewJobTitleSimple = itemView.findViewById(R.id.textViewJobTitleSimple);
            textViewJobLocationSimple = itemView.findViewById(R.id.textViewJobLocationSimple);
            textViewJobTypeSimple = itemView.findViewById(R.id.textViewJobTypeSimple);
        }

        public void bind(@NonNull final JobPost jobPost) {
            // Bind data from the JobPost model to the views
            textViewJobTitleSimple.setText(jobPost.getTitle());

            if (jobPost.getLocationName() != null && !jobPost.getLocationName().isEmpty()) {
                textViewJobLocationSimple.setText(jobPost.getLocationName());
                textViewJobLocationSimple.setVisibility(View.VISIBLE);
            } else {
                textViewJobLocationSimple.setVisibility(View.GONE);
            }

            if (jobPost.getJobType() != null && !jobPost.getJobType().isEmpty()) {
                textViewJobTypeSimple.setText(jobPost.getJobType());
                textViewJobTypeSimple.setVisibility(View.VISIBLE);
            } else {
                textViewJobTypeSimple.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition(); // Use getBindingAdapterPosition() for safety
                if (position != RecyclerView.NO_POSITION) {
                    // The 'jobPost' object passed to bind() is the correct one for this item.

                    if (currentViewHolderListener != null) {
                        // If an external listener was provided to the adapter, invoke it
                        currentViewHolderListener.onJobPostClick(jobPost);
                    } else {
                        // Default action if no specific listener was provided to the adapter:
                        // Navigate to JobDetailsActivity
                        Context context = v.getContext();
                        Intent intent = new Intent(context, JobDetailsActivity.class);
                        intent.putExtra(JobDetailsActivity.EXTRA_JOB_POST_ID, jobPost.getJobPostId());

                        // Pass employerUid if JobDetailsActivity needs it and JobPost model has it
                        if (jobPost.getEmployerUid() != null && !jobPost.getEmployerUid().isEmpty()) {
                            intent.putExtra(JobDetailsActivity.EXTRA_EMPLOYER_UID, jobPost.getEmployerUid());
                        }
                        context.startActivity(intent);
                    }
                }
            });
        }
    }
}