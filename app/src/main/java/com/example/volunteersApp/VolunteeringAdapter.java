// com/example/volunteersApp/VolunteeringAdapter.java
package com.example.volunteersApp;

import android.content.Context; // For context
import android.view.LayoutInflater;
// import android.view.View; // Not directly used
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.databinding.ItemVolunteerOpportunityBinding;
import com.example.volunteersApp.models.VolunteerOpportunity;
import com.google.firebase.Timestamp; // For Timestamp
import java.text.SimpleDateFormat; // For date formatting
import java.util.Locale; // For Locale

public class VolunteeringAdapter extends ListAdapter<VolunteerOpportunity, VolunteeringAdapter.OpportunityViewHolder> {

    private OnOpportunityClickListener listener;

    public interface OnOpportunityClickListener {
        void onOpportunityClick(VolunteerOpportunity opportunity);
    }

    public void setOnOpportunityClickListener(OnOpportunityClickListener listener) {
        this.listener = listener;
    }

    public VolunteeringAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<VolunteerOpportunity> DIFF_CALLBACK = new DiffUtil.ItemCallback<VolunteerOpportunity>() {
        @Override
        public boolean areItemsTheSame(@NonNull VolunteerOpportunity oldItem, @NonNull VolunteerOpportunity newItem) {
            // ID should not be null if data is correctly processed
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull VolunteerOpportunity oldItem, @NonNull VolunteerOpportunity newItem) {
            return oldItem.equals(newItem); // Relies on VolunteerOpportunity.equals()
        }
    };

    @NonNull
    @Override
    public OpportunityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVolunteerOpportunityBinding binding = ItemVolunteerOpportunityBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new OpportunityViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull OpportunityViewHolder holder, int position) {
        VolunteerOpportunity currentOpportunity = getItem(position);
        if (currentOpportunity != null) { // Add null check
            holder.bind(currentOpportunity, listener);
        }
    }

    // com/example/volunteersApp/VolunteeringAdapter.java
// ...
    static class OpportunityViewHolder extends RecyclerView.ViewHolder {
        private final ItemVolunteerOpportunityBinding binding;

        public OpportunityViewHolder(ItemVolunteerOpportunityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final VolunteerOpportunity opportunity, final OnOpportunityClickListener listener) {
            Context context = itemView.getContext();

            binding.textViewOpportunityTitle.setText(opportunity.getTitle() != null ? opportunity.getTitle() : context.getString(R.string.text_name_unavailable));
            binding.textViewOpportunityOrganization.setText(opportunity.getHostName() != null ? opportunity.getHostName() : (opportunity.getLocationName() != null ? opportunity.getLocationName() : context.getString(R.string.text_organization_unavailable)));

            if (binding.getRoot().findViewById(R.id.textViewOpportunityDate) != null && opportunity.getEventTimestamp() != null) {
                com.google.android.material.textview.MaterialTextView dateView = binding.getRoot().findViewById(R.id.textViewOpportunityDate);
                Timestamp eventTimestamp = opportunity.getEventTimestamp();
                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault());
                dateView.setText(sdf.format(eventTimestamp.toDate()));
            } else if (binding.getRoot().findViewById(R.id.textViewOpportunityDate) != null){
                com.google.android.material.textview.MaterialTextView dateView = binding.getRoot().findViewById(R.id.textViewOpportunityDate);
                dateView.setText(context.getString(R.string.text_date_unavailable));
            }

            if (binding.getRoot().findViewById(R.id.textViewOpportunityType) != null && opportunity.getType() != null) {
                com.google.android.material.textview.MaterialTextView typeView = binding.getRoot().findViewById(R.id.textViewOpportunityType);
                typeView.setText(String.format(context.getString(R.string.label_category_format), opportunity.getType()));
            } else if (binding.getRoot().findViewById(R.id.textViewOpportunityType) != null) {
                com.google.android.material.textview.MaterialTextView typeView = binding.getRoot().findViewById(R.id.textViewOpportunityType);
                typeView.setText(context.getString(R.string.text_category_unavailable));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    // Corrected line:
                    listener.onOpportunityClick(opportunity);
                }
            });
        }
    }
// ...

}
