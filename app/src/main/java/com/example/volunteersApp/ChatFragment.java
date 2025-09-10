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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton; // If using ImageButton
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager; // If using RecyclerView
import androidx.recyclerview.widget.RecyclerView;       // If using RecyclerView
// import android.widget.ListView; // If using ListView

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatFragment extends Fragment {

    private static final String TAG = "ChatFragment";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String ARG_EVENT_ID = "event_id"; // Argument key

    // Firestore Collection/Field Constants
    private static final String EVENTS_COLLECTION = "events";
    private static final String MESSAGES_SUBCOLLECTION = "messages";
    private static final String USERS_COLLECTION = "users";
    private static final String USER_NAME_FIELD = "name";
    private static final String MESSAGE_TIMESTAMP_FIELD = "timestamp";

    // Views
    private RecyclerView mMessageRecyclerView; // Or ListView
    private MessageAdapter mMessageAdapter;    // Your RecyclerView.Adapter or ArrayAdapter
    private EditText mMessageEditText;
    private ImageButton mSendButton;        // Or Button
    private ProgressBar mProgressBar;

    // Firebase Auth
    private FirebaseAuth mAuth;
    private String mCurrentUserId;
    private String mUsername = "Anonymous";

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference messagesCollectionRef;
    private ListenerRegistration mMessagesListener;
    private ListenerRegistration mUserListener;

    private String eventId; // To store the event ID for this chat

    public ChatFragment() {
        // Required empty public constructor
    }

    /**
     * Factory method to create a new instance of this fragment using the provided parameters.
     * @param eventId The ID of the event/chat.
     * @return A new instance of fragment ChatFragment.
     */
    public static ChatFragment newInstance(String eventId) {
        ChatFragment fragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
        }

        if (TextUtils.isEmpty(eventId)) {
            Log.e(TAG, "EventId is null or empty. Cannot initialize chat in Fragment.");
            // Handle this error appropriately, maybe close fragment or show error message
        }

        // Initialize Firebase Auth & Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            mCurrentUserId = "UNKNOWN_USER";
            // Consider guiding user to login if chat is a core feature
        } else {
            mCurrentUserId = currentUser.getUid();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        // Initialize Views (findViewByID from the inflated view)
        mMessageRecyclerView = view.findViewById(R.id.messageRecyclerView); // Update ID
        mMessageEditText = view.findViewById(R.id.messageEditText);     // Update ID
        mSendButton = view.findViewById(R.id.sendButton);               // Update ID
        mProgressBar = view.findViewById(R.id.progressBar);             // Update ID

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (TextUtils.isEmpty(eventId)) {
            // Show an error message in the fragment's UI or disable chat features
            if (mMessageEditText != null) mMessageEditText.setEnabled(false);
            if (mSendButton != null) mSendButton.setEnabled(false);
            Toast.makeText(getContext(), "Error: Chat ID missing.", Toast.LENGTH_LONG).show();
            return;
        }

        // Set up messages collection reference
        messagesCollectionRef = db.collection(EVENTS_COLLECTION).document(eventId)
                .collection(MESSAGES_SUBCOLLECTION);

        // Initialize message RecyclerView and its adapter
        // Ensure you have a MessageAdapter that works with RecyclerView
        mMessageAdapter = new MessageAdapter(getContext(), new ArrayList<>(), mCurrentUserId); // <--- CORRECTED HERE
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setStackFromEnd(true); // For chat-like behavior
        mMessageRecyclerView.setLayoutManager(layoutManager);
        mMessageRecyclerView.setAdapter(mMessageAdapter);
        mMessageRecyclerView.setItemAnimator(null); // Can improve performance for frequent updates

        // TextWatcher for enabling/disabling send button
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mSendButton != null) { // Add null check for mSendButton
                    mSendButton.setEnabled(!s.toString().trim().isEmpty());
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        if (mMessageEditText != null) { // Add null check for mMessageEditText
            mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
        }

        // Send button click listener
        if (mSendButton != null) { // Add null check for mSendButton
            mSendButton.setOnClickListener(v -> sendMessage());
        }

        // Fetch user's name (mUsername)
        if (!mCurrentUserId.equals("UNKNOWN_USER")) {
            attachUserListener();
        } else {
            mUsername = "Anonymous";
            if (mSendButton != null) mSendButton.setEnabled(false); // Disable sending if user unknown
            if (mMessageEditText != null) mMessageEditText.setEnabled(false);
        }

        // Start listening for messages AFTER adapter and layout manager are set
        attachMessagesListener(); // Make sure this is called
    }
    private void attachUserListener() {
        if (mUserListener == null && mCurrentUserId != null && !mCurrentUserId.equals("UNKNOWN_USER")) {
            DocumentReference userDocRef = db.collection(USERS_COLLECTION).document(mCurrentUserId);
            mUserListener = userDocRef.addSnapshotListener(getActivity(), (snapshot, error) -> {
                // Check if fragment is still added before updating UI
                if (!isAdded() || error != null) {
                    if (error != null) Log.e(TAG, "Listen failed for user data.", error);
                    mUsername = (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null)
                            ? mAuth.getCurrentUser().getDisplayName() : "User (Error)";
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    String nameFromDb = snapshot.getString(USER_NAME_FIELD);
                    mUsername = (nameFromDb != null && !nameFromDb.isEmpty()) ? nameFromDb : "User (No Name)";
                    Log.d(TAG, "Username fetched for fragment: " + mUsername);
                } else {
                    Log.w(TAG, "User document does not exist for UID: " + mCurrentUserId);
                    mUsername = (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null)
                            ? mAuth.getCurrentUser().getDisplayName() : "User (Not Found)";
                }
            });
        }
    }

    private void detachUserListener() {
        if (mUserListener != null) {
            mUserListener.remove();
            mUserListener = null;
        }
    }

    private void sendMessage() {
        String messageText = mMessageEditText.getText().toString().trim();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(getContext(), "Cannot send empty message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUser == null || mCurrentUserId.equals("UNKNOWN_USER")) {
            Toast.makeText(getContext(), "You must be logged in to send messages", Toast.LENGTH_SHORT).show();
            return;
        }
        if (messagesCollectionRef == null) {
            Toast.makeText(getContext(), "Chat not initialized.", Toast.LENGTH_SHORT).show();
            return;
        }

        String photoUrl = (currentUser.getPhotoUrl() != null) ? currentUser.getPhotoUrl().toString() : null;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("text", messageText);
        messageData.put("name", mUsername);
        messageData.put("userId", mCurrentUserId);
        messageData.put("photoUrl", photoUrl);
        messageData.put(MESSAGE_TIMESTAMP_FIELD, FieldValue.serverTimestamp());

        messagesCollectionRef.add(messageData)
                .addOnSuccessListener(documentReference -> {
                    mMessageEditText.setText("");
                    Log.d(TAG, "Message sent successfully from fragment with ID: " + documentReference.getId());
                    // RecyclerView will scroll due to adapter changes if attachMessagesListener is active
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message from fragment.", e);
                    if(getContext() != null) { // Check context before showing Toast
                        Toast.makeText(getContext(), "Failed to send message.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void attachMessagesListener() {
        if (mMessagesListener == null && messagesCollectionRef != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            Query query = messagesCollectionRef.orderBy(MESSAGE_TIMESTAMP_FIELD, Query.Direction.ASCENDING)
                    .limitToLast(50); // Optional: limit

            // Pass getActivity() or requireActivity() as the first argument to addSnapshotListener
            // to automatically manage the listener's lifecycle with the fragment's lifecycle.
            // However, this means you don't manually remove it in onStop if you do it this way.
            // For explicit control, use the version without activity and manage in onStart/onStop.
            mMessagesListener = query.addSnapshotListener(getActivity(), (snapshots, e) -> {
                if (!isAdded() || e != null) { // Check if fragment is still added
                    if (e != null) Log.e(TAG, "Listen error for messages in fragment", e);
                    if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
                    if (getContext() != null) Toast.makeText(getContext(), "Failed to load messages.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
                if (snapshots == null) {
                    Log.w(TAG, "Messages snapshot was null in fragment");
                    return;
                }

                List<FriendlyMessage> newMessages = new ArrayList<>();
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        FriendlyMessage friendlyMessage = dc.getDocument().toObject(FriendlyMessage.class);
                        // Add document ID if needed in your POJO
                        // friendlyMessage.setId(dc.getDocument().getId());
                        if (friendlyMessage != null) {
                            newMessages.add(friendlyMessage);
                        } else {
                            Log.w(TAG, "FriendlyMessage was null after conversion in fragment");
                        }
                    }
                    // Handle MODIFIED or REMOVED if your adapter supports it efficiently
                }

                if (!newMessages.isEmpty() && mMessageAdapter != null) {
                    mMessageAdapter.addMessages(newMessages); // Assuming adapter has an addMessages method
                    if (mMessageRecyclerView != null) {
                        mMessageRecyclerView.smoothScrollToPosition(mMessageAdapter.getItemCount() - 1);
                    }
                }
            });
        }
    }

    private void detachMessagesListener() {
        if (mMessagesListener != null) {
            mMessagesListener.remove();
            mMessagesListener = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If eventId is valid, attach listeners
        if (!TextUtils.isEmpty(eventId)) {
            if (mCurrentUserId != null && !mCurrentUserId.equals("UNKNOWN_USER")) {
                attachUserListener(); // Re-attach or ensure it's listening
            }
            attachMessagesListener();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        detachMessagesListener();
        detachUserListener();
        // It's good practice to clear the adapter if the fragment is being stopped
        // to free up resources and avoid issues if it's re-shown without being fully destroyed.
        if (mMessageAdapter != null) {
            // mMessageAdapter.clearMessages(); // Assuming your adapter has a method to clear data
        }
    }

    // You'll need a MessageAdapter, similar to the one used in ChatBox Activity
    // but adapted for RecyclerView.
    // Example (very basic RecyclerView adapter):
    // public static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    //     private List<FriendlyMessage> messages;
    //     private String currentUserId;
    //
    //     public MessageAdapter(List<FriendlyMessage> messages, String currentUserId) {
    //         this.messages = messages;
    //         this.currentUserId = currentUserId;
    //     }
    //
    //     public void addMessages(List<FriendlyMessage> newMessages) {
    //         int startPosition = messages.size();
    //         messages.addAll(newMessages);
    //         notifyItemRangeInserted(startPosition, newMessages.size());
    //     }
    //
    //     public void clearMessages() {
    //        messages.clear();
    //        notifyDataSetChanged();
    //     }
    //
    //     @NonNull @Override
    //     public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    //         View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
    //         return new MessageViewHolder(view);
    //     }
    //
    //     @Override
    //     public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
    //         FriendlyMessage message = messages.get(position);
    //         holder.bind(message, currentUserId.equals(message.getUserId()));
    //     }
    //
    //     @Override public int getItemCount() { return messages.size(); }
    //
    //     static class MessageViewHolder extends RecyclerView.ViewHolder {
    //         TextView messageTextView, authorTextView; // Example views from item_message.xml
    //         // ImageView authorImageView;
    //
    //         public MessageViewHolder(@NonNull View itemView) {
    //             super(itemView);
    //             messageTextView = itemView.findViewById(R.id.messageTextView); // Update ID
    //             authorTextView = itemView.findViewById(R.id.nameTextView);    // Update ID
    //         }
    //
    //         void bind(FriendlyMessage message, boolean isCurrentUserMessage) {
    //             messageTextView.setText(message.getText());
    //             authorTextView.setText(message.getTitle());
    //             // Adjust layout/style based on isCurrentUserMessage if needed (e.g., align right/left)
    //         }
    //     }
    // }
}