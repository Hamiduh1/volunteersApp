package com.example.volunteersApp;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.volunteersApp.databinding.FragmentMessageBinding; // Ensure this matches your layout file name

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MessageFragment extends Fragment {

    private static final String TAG = "MessageFragment";
    // Firestore constants
    private static final String MESSAGES_COLLECTION = "messages"; // Or your chosen collection path
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;


    private FragmentMessageBinding binding; // ViewBinding variable
    private MessageAdapter messageAdapter;
    private List<FriendlyMessage> friendlyMessagesList; // Renamed for clarity
    private LinearLayoutManager linearLayoutManager; // For scrolling RecyclerView

    // Firebase Firestore components
    private FirebaseFirestore firestoreDb;
    private CollectionReference messagesCollectionRef;
    private ListenerRegistration messagesListenerRegistration;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    private String currentUserId;


    // Optional: If this fragment is for a specific chat room, you'd pass its ID
    // private String chatId; // Example: if messages are in "chats/{chatId}/messages"

    public MessageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize Firebase Auth and get current user
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            // Handle case where user is not logged in, perhaps navigate away or show login prompt
            Log.w(TAG, "User not logged in.");
            currentUserId = "ANONYMOUS"; // Or handle appropriately
            // Consider disabling messaging features if user is anonymous and not allowed to post
        }

        // Initialize Firestore
        firestoreDb = FirebaseFirestore.getInstance();
        // Define the collection reference.
        // If this is for a specific chat room, adjust the path:
        // e.g., firestoreDb.collection("chatRooms").document(chatId).collection(MESSAGES_COLLECTION);
        messagesCollectionRef = firestoreDb.collection(MESSAGES_COLLECTION);

        // If you need to get arguments (like a chatId)
        // if (getArguments() != null) {
        //     chatId = getArguments().getString("YOUR_CHAT_ID_KEY");
        //     messagesCollectionRef = firestoreDb.collection("chatRooms").document(chatId).collection(MESSAGES_COLLECTION);
        // } else {
        //     // Fallback or error handling if chatId is essential
        //     messagesCollectionRef = firestoreDb.collection(MESSAGES_COLLECTION); // Default
        // }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);
        View rootView = binding.getRoot();

        friendlyMessagesList = new ArrayList<>();
        // Make sure your MessageAdapter constructor matches: (Context, List<FriendlyMessage>, String currentUserId)
        messageAdapter = new MessageAdapter(requireContext(), friendlyMessagesList, currentUserId);

        linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setStackFromEnd(true); // New messages appear at the bottom

        // Ensure your layout XML uses RecyclerView with id "messageRecyclerView"
        binding.messageRecyclerView.setLayoutManager(linearLayoutManager);
        binding.messageRecyclerView.setAdapter(messageAdapter);
        binding.messageRecyclerView.setItemAnimator(null); // Optional: for performance

        // Setup send button click listener
        binding.sendButton.setOnClickListener(v -> sendMessage());

        // Enable/disable send button based on text input
        binding.messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                binding.sendButton.setEnabled(!TextUtils.isEmpty(charSequence.toString().trim()));
            }
            @Override
            public void afterTextChanged(Editable editable) {}
        });
        binding.messageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Initially disable send button if message box is empty
        binding.sendButton.setEnabled(!TextUtils.isEmpty(binding.messageEditText.getText().toString().trim()));

        return rootView;
    }

    private void sendMessage() {
        String messageText = binding.messageEditText.getText().toString().trim();
        currentUser = firebaseAuth.getCurrentUser(); // Re-check current user

        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(getContext(), "Cannot send empty message", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(getContext(), "You must be logged in to send messages", Toast.LENGTH_SHORT).show();
            // Optionally, navigate to login screen
            return;
        }

        String displayName = currentUser.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = "Anonymous"; // Fallback if display name is not set
        }
        String photoUrl = currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : null;

        // Create a new FriendlyMessage object for Firestore
        // The timestamp will be set by @ServerTimestamp on the FriendlyMessage POJO
        FriendlyMessage friendlyMessage = new FriendlyMessage(
                messageText,
                displayName,
                photoUrl,
                currentUser.getUid() // Ensure userId is set
        );

        // Add the message to Firestore
        messagesCollectionRef.add(friendlyMessage)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Message sent successfully with ID: " + documentReference.getId());
                    binding.messageEditText.setText(""); // Clear input box
                    // RecyclerView will auto-scroll due to LayoutManager's stackFromEnd
                    // or if we manually scroll after adapter updates.
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message.", e);
                    Toast.makeText(getContext(), "Failed to send message.", Toast.LENGTH_SHORT).show();
                });
    }

    private void attachFirestoreReadListener() {
        if (messagesCollectionRef == null) {
            Log.e(TAG, "Messages collection reference is null. Cannot attach listener.");
            return;
        }

        if (messagesListenerRegistration == null) {
            // Query to get messages, ordered by timestamp
            Query query = messagesCollectionRef.orderBy("timestamp", Query.Direction.ASCENDING).limitToLast(50); // Get last 50, adjust as needed

            messagesListenerRegistration = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable FirebaseFirestoreException e) {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        Toast.makeText(getContext(), "Failed to load messages.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null) {
                        Log.w(TAG, "Received null QuerySnapshot.");
                        return;
                    }

                    Log.d(TAG, "New messages received: " + snapshots.getDocumentChanges().size());

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        FriendlyMessage message = dc.getDocument().toObject(FriendlyMessage.class);
                        // Optional: Store document ID if needed later (e.g., for updates/deletes)
                        // message.setId(dc.getDocument().getId());

                        switch (dc.getType()) {
                            case ADDED:
                                // Check if message already exists to prevent duplicates from local cache on initial load
                                // This simple check might not be robust enough for all scenarios with local cache.
                                boolean exists = false;
                                for (FriendlyMessage fm : friendlyMessagesList) {
                                    // Assuming getTimestamp is not null and using it for comparison.
                                    // For a more robust ID, use Firestore document ID if you store it.
                                    if (fm.getTimestamp() != null && message.getTimestamp() != null &&
                                            fm.getTimestamp().equals(message.getTimestamp()) &&
                                            fm.getUserId().equals(message.getUserId())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists && message.getTimestamp() != null) { // Ensure timestamp exists for sorting
                                    friendlyMessagesList.add(message);
                                } else if (!exists && message.getTimestamp() == null){
                                    Log.w(TAG, "Message added without timestamp, might cause sorting issues.");
                                    friendlyMessagesList.add(message); // Add anyway, but log warning
                                }
                                break;
                            case MODIFIED:
                                // Handle modified messages (e.g., if you allow message editing)
                                // Find the message in the list and update it.
                                for (int i = 0; i < friendlyMessagesList.size(); i++) {
                                    // Assuming you have a unique ID for messages (e.g., Firestore document ID)
                                    // if (friendlyMessagesList.get(i).getId().equals(message.getId())) {
                                    //    friendlyMessagesList.set(i, message);
                                    //    break;
                                    // }
                                }
                                break;
                            case REMOVED:
                                // Handle removed messages
                                // Find the message in the list and remove it.
                                // friendlyMessagesList.removeIf(fm -> fm.getId().equals(message.getId())); // Java 8+
                                break;
                        }
                    }

                    // Sort messages by timestamp after processing all changes
                    // Only sort if new messages were added to avoid unnecessary sorts on modifications/removals
                    // if (snapshots.getDocumentChanges().stream().anyMatch(dc -> dc.getType() == DocumentChange.Type.ADDED)) {
                    Collections.sort(friendlyMessagesList, Comparator.comparing(FriendlyMessage::getTimestamp, Comparator.nullsFirst(Comparator.naturalOrder())));
                    // }
                    // Using nullsFirst to handle cases where timestamp might be temporarily null during server sync


                    messageAdapter.notifyDataSetChanged(); // More efficient updates are notifyItemInserted, etc.
                    // But for simplicity with sorting, this is okay for now.

                    // Scroll to the bottom to show the newest message
                    if (messageAdapter.getItemCount() > 0) {
                        binding.messageRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
                    }
                }
            });
            Log.d(TAG, "Attached Firestore SnapshotListener.");
        }
    }

    private void detachFirestoreReadListener() {
        if (messagesListenerRegistration != null) {
            messagesListenerRegistration.remove();
            messagesListenerRegistration = null;
            Log.d(TAG, "Detached Firestore SnapshotListener.");
        }
    }

    @Override
    public void onStart() { // Changed from onResume to onStart for listener attachment
        super.onStart();
        // Check if user is still logged in, maybe display name changed
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            // If adapter was created with a different userId, you might need to recreate or update it
            if (messageAdapter != null && !messageAdapter.getCurrentUserId().equals(currentUserId)) {
                // Re-create adapter or provide a method to update currentUserId in adapter
                friendlyMessagesList.clear(); // Clear old messages if user changed
                messageAdapter = new MessageAdapter(requireContext(), friendlyMessagesList, currentUserId);
                binding.messageRecyclerView.setAdapter(messageAdapter);
            }
        } else {
            currentUserId = "ANONYMOUS";
            // Handle no user: clear messages, disable input
            friendlyMessagesList.clear();
            if (messageAdapter != null) messageAdapter.notifyDataSetChanged();
            binding.messageEditText.setEnabled(false);
            binding.sendButton.setEnabled(false);
        }
        attachFirestoreReadListener(); // Attach listener in onStart
    }

    @Override
    public void onStop() { // Changed from onPause to onStop for listener detachment
        super.onStop();
        detachFirestoreReadListener(); // Detach listener in onStop
        // friendlyMessagesList.clear(); // Clearing here means messages reload every time
        // messageAdapter.notifyDataSetChanged(); // Consider if you want to keep messages when fragment is hidden
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Important to prevent memory leaks with ViewBinding
    }
}