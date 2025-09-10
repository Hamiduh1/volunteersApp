package com.example.volunteersApp.employer; // Adjust to your package structure

import android.content.Context;
import android.graphics.Color;
// import android.graphics.drawable.GradientDrawable; // Not used
import android.util.Log; // <<< CHANGED TO STANDARD ANDROID LOG
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
// import android.widget.LinearLayout; // Not used
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
//import androidx.media3.common.util.Log; // <<< REMOVED Media3 Log
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.R;
// JobPosting model import
// import com.example.volunteersApp.employer.JobPosting;

import java.util.Locale;
import java.util.Objects;

public class MyPostedJobsAdapter extends ListAdapter<JobPosting, MyPostedJobsAdapter.JobViewHolder> {

    private final Context context;
    private final OnJobActionListener listener;

    public interface OnJobActionListener {
        void onEditJob(JobPosting jobPosting);
        void onDeleteJob(JobPosting jobPosting, int position);
        void onToggleStatusJob(JobPosting jobPosting, int position);
        void onViewApplicants(JobPosting jobPosting);
    }

    public MyPostedJobsAdapter(@NonNull Context context, @NonNull OnJobActionListener listener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_job_posting, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        JobPosting job = getItem(position);

        if (job == null) {
            // Using android.util.Log - no opt-in needed
            Log.e("MyPostedJobsAdapter", "JobPosting item is null at position: " + position);
            return;
        }

        holder.textViewJobTitle.setText(job.getTitle());
        holder.textViewJobDate.setText(job.getDate());
        holder.textViewJobTime.setText(job.getTime());
        holder.textViewJobLocation.setText(job.getLocationName());
        holder.textViewJobStatus.setText(job.getStatus() != null ? job.getStatus().toUpperCase(Locale.ROOT) : "N/A");

        if ("open".equalsIgnoreCase(job.getStatus())) {
            holder.textViewJobStatus.setBackgroundResource(R.drawable.status_background_open);
            holder.textViewJobStatus.setTextColor(Color.WHITE);
            holder.buttonToggleStatusJob.setText(R.string.button_close_job);
        } else if ("closed".equalsIgnoreCase(job.getStatus())) {
            holder.textViewJobStatus.setBackgroundResource(R.drawable.status_background_closed);
            holder.textViewJobStatus.setTextColor(Color.WHITE);
            holder.buttonToggleStatusJob.setText(R.string.button_reopen_job);
        } else {
            holder.textViewJobStatus.setBackgroundColor(Color.LTGRAY);
            holder.textViewJobStatus.setTextColor(Color.BLACK);
            holder.buttonToggleStatusJob.setText(R.string.button_set_status);
        }

        holder.buttonViewApplicants.setOnClickListener(v -> listener.onViewApplicants(job));
        holder.buttonEditJob.setOnClickListener(v -> listener.onEditJob(job));
        holder.buttonToggleStatusJob.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                listener.onToggleStatusJob(getItem(currentPosition), currentPosition);
            }
        });
        holder.buttonDeleteJob.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                listener.onDeleteJob(getItem(currentPosition), currentPosition);
            }
        });
    }

    static class JobViewHolder extends RecyclerView.ViewHolder {
        TextView textViewJobTitle, textViewJobDate, textViewJobTime, textViewJobLocation, textViewJobStatus;
        Button buttonViewApplicants, buttonEditJob, buttonToggleStatusJob, buttonDeleteJob;

        public JobViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewJobTitle = itemView.findViewById(R.id.textViewJobTitle);
            textViewJobStatus = itemView.findViewById(R.id.textViewJobStatus);
            textViewJobDate = itemView.findViewById(R.id.textViewJobDate);
            textViewJobTime = itemView.findViewById(R.id.textViewJobTime);
            textViewJobLocation = itemView.findViewById(R.id.textViewJobLocation);
            buttonViewApplicants = itemView.findViewById(R.id.buttonViewApplicants);
            buttonEditJob = itemView.findViewById(R.id.buttonEditJob);
            buttonToggleStatusJob = itemView.findViewById(R.id.buttonToggleStatusJob);
            buttonDeleteJob = itemView.findViewById(R.id.buttonDeleteJob);
        }
    }

    private static final DiffUtil.ItemCallback<JobPosting> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<JobPosting>() {
                @Override
                public boolean areItemsTheSame(@NonNull JobPosting oldItem, @NonNull JobPosting newItem) {
                    return oldItem.getPostingId() != null &&
                            oldItem.getPostingId().equals(newItem.getPostingId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull JobPosting oldItem, @NonNull JobPosting newItem) {
                    return oldItem.equals(newItem);
                    // Or manual field comparison:
                    /*
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                           Objects.equals(oldItem.getDate(), newItem.getDate()) &&
                           // ... compare all relevant fields ...
                           Objects.equals(oldItem.getStatus(), newItem.getStatus());
                    */
                }
            };
}

