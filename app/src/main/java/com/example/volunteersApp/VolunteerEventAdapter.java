package com.example.volunteersApp;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.volunteersApp.databinding.ItemVolunteerEventBinding;
import com.google.firebase.Timestamp; // Already correctly imported

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import com.example.volunteersApp.models.EventModel;

// This adapter works with VolunteerEventItem, which wraps EventModel
public class VolunteerEventAdapter extends ListAdapter<VolunteerEventItem, VolunteerEventAdapter.EventViewHolder> {

    private static final String TAG = "VolunteerEventAdapter";
    private OnItemClickListener<VolunteerEventItem> listener; // Generic type for listener

    // Default constructor using VolunteerEventItemCallback
    public VolunteerEventAdapter() {
        super(new VolunteerEventItemCallback());
    }

    // Constructor to allow providing a custom DiffUtil.ItemCallback
    public VolunteerEventAdapter(@NonNull DiffUtil.ItemCallback<VolunteerEventItem> diffCallback) {
        super(diffCallback);
    }

    public void setOnItemClickListener(OnItemClickListener<VolunteerEventItem> listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVolunteerEventBinding itemBinding = ItemVolunteerEventBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new EventViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        VolunteerEventItem currentItem = getItem(position);
        if (currentItem != null) { // Ensure currentItem is not null
            holder.bind(currentItem);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null && holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                    // currentItem is already checked for nullability above
                    listener.onItemClick(currentItem);
                }
            });
        } else {
            // Handle null item if necessary, though ListAdapter should generally not provide null items.
            Log.w(TAG, "onBindViewHolder received a null item at position: " + position);
            // Optionally, clear the views in the holder or set to a default state.
            holder.clear(); // Example: you would need to implement clear() in EventViewHolder
        }
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        private final ItemVolunteerEventBinding binding;

        public EventViewHolder(ItemVolunteerEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(VolunteerEventItem item) {
            Context context = itemView.getContext();

            // Defensive check for item and its details
            if (item == null || item.getEventDetails() == null) {
                Log.w(TAG, "Attempting to bind a null VolunteerEventItem or item with null details.");
                clear(); // Clear views if item is invalid
                return;
            }

            EventModel details = item.getEventDetails(); // This is your EventModel object

            // Set Event Name
            // This line assumes EventModel has a public String getTitle() method.
            binding.textViewEventNameItem.setText(
                    details.getTitle() != null ? details.getTitle() : context.getString(R.string.text_name_unavailable)
            );

            // Set Event Date using getEventTimestamp()
            // This assumes EventModel has a public Timestamp getEventTimestamp() method.
            //Timestamp eventTimestamp = details.getEventTimestamp();
           // Set Event Date using getEventTimestamp()
                    // This assumes EventModel has a public Timestamp getEventTimestamp() method.
            Timestamp eventTimestamp = details.getEventDateTime();

            if (eventTimestamp != null) {
                SimpleDateFormat displayFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
                try {
                    String formattedDate = displayFormat.format(eventTimestamp.toDate());
                    binding.textViewEventDateItem.setText(
                            String.format(context.getString(R.string.label_date_format), formattedDate)
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Error formatting event timestamp for date", e);
                    binding.textViewEventDateItem.setText(
                            String.format(context.getString(R.string.label_date_format), context.getString(R.string.text_invalid_date)) // Use a string resource
                    );
                }
            } else {
                binding.textViewEventDateItem.setText(
                        String.format(context.getString(R.string.label_date_format), context.getString(R.string.text_data_na)) // Use a string resource
                );
            }

            // Set Event Location
            // This assumes EventModel has a public String getLocationName() method.
            binding.textViewEventLocationItem.setText(
                    String.format(context.getString(R.string.label_location_format),
                            details.getLocationName() != null ? details.getLocationName() : context.getString(R.string.text_data_na)) // Use a string resource
            );

            // Set Event Payment with robust handling
            // This assumes EventModel has a public Object getPayment() method.
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.getDefault());
            String formattedPayment;
            Object paymentObject = details.getPayment();

            if (paymentObject != null) {
                if (paymentObject instanceof Number) {
                    formattedPayment = currencyFormatter.format(((Number) paymentObject).doubleValue());
                } else if (paymentObject instanceof String) {
                    try {
                        // Attempt to remove non-numeric characters before parsing for more flexibility
                        String cleanPaymentString = ((String) paymentObject).replaceAll("[^\\d.,]", "");
                        if (!cleanPaymentString.isEmpty()) {
                            double paymentValue = Double.parseDouble(cleanPaymentString.replace(',', '.')); // Handle comma as decimal separator
                            formattedPayment = currencyFormatter.format(paymentValue);
                        } else {
                            formattedPayment = (String) paymentObject; // If cleaning results in empty, show original
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Payment string is not a valid number: " + paymentObject, e);
                        formattedPayment = (String) paymentObject; // Display as is if parsing fails
                    }
                } else {
                    Log.w(TAG, "Payment is an unexpected type: " + paymentObject.getClass().getName());
                    formattedPayment = String.valueOf(paymentObject);
                }
            } else {
                formattedPayment = context.getString(R.string.text_data_na); // Use a string resource
            }
            binding.textViewEventPaymentItem.setText(
                    String.format(context.getString(R.string.label_payment_format), formattedPayment)
            );
        }

        // Method to clear views, useful for null items or error states
        public void clear() {
            Context context = itemView.getContext();
            binding.textViewEventNameItem.setText(context.getString(R.string.text_event_details_unavailable));
            binding.textViewEventDateItem.setText(context.getString(R.string.text_data_unavailable_short));
            binding.textViewEventLocationItem.setText(context.getString(R.string.text_data_unavailable_short));
            binding.textViewEventPaymentItem.setText(context.getString(R.string.text_data_unavailable_short));
        }
    }

    // Generic interface for item clicks
    public interface OnItemClickListener<T> {
        void onItemClick(T item);
    }

    // DiffUtil.ItemCallback for VolunteerEventItem
    public static class VolunteerEventItemCallback extends DiffUtil.ItemCallback<VolunteerEventItem> {
        @Override
        public boolean areItemsTheSame(@NonNull VolunteerEventItem oldItem, @NonNull VolunteerEventItem newItem) {
            // IDs are crucial for DiffUtil to correctly identify items.
            // Ensure VolunteerEventItem's getEventId() and getorganizerId() are reliable and non-null.
            boolean eventIdSame = Objects.equals(oldItem.getEventId(), newItem.getEventId());
            boolean organizerIdSame = Objects.equals(oldItem.getOrganizerId(), newItem.getOrganizerId());

            // If organizerId can be null or is not always part of the unique identity with eventId, adjust this.
            // For example, if eventId is globally unique:
            // return Objects.equals(oldItem.getEventId(), newItem.getEventId());
            return eventIdSame && organizerIdSame;
        }

        @Override
        public boolean areContentsTheSame(@NonNull VolunteerEventItem oldItem, @NonNull VolunteerEventItem newItem) {
            // This relies on VolunteerEventItem having a well-implemented equals() method,
            // which in turn should ideally rely on EventModel having a good equals() method.
            // If not, you must compare individual fields of EventModel here.
            return Objects.equals(oldItem, newItem);
        }
    }
}
