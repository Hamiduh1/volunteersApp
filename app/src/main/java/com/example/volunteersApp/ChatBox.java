package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button; // Kept for sendButton
import android.widget.EditText; // Kept for messageEditText
// import android.widget.ListView; // REMOVED
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager; // ADDED
import androidx.recyclerview.widget.RecyclerView; // ADDED

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
// import com.google.firebase.firestore.DocumentSnapshot; // Not directly used in the simplified listener
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
// import com.google.firebase.firestore.ServerTimestamp; // Already imported

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
// import java.util.Date; // Already imported
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatBox extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "com.example.volunteersApp.EVENT_ID"; // Choose a unique string
    public static final String EXTRA_USER_ID = "com.example.volunteersApp.USER_ID";   // If you also need this for receiving


    private static final String TAG = "ChatBoxActivity";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    // Firestore Collection/Field Constants
    private static final String EVENTS_COLLECTION = "events";
    private static final String MESSAGES_SUBCOLLECTION = "messages";
    private static final String USERS_COLLECTION = "users";
    private static final String USER_NAME_FIELD = "name";
    private static final String MESSAGE_TIMESTAMP_FIELD = "timestamp"; // Make sure this matches your FriendlyMessage POJO field for sorting

    private MessageAdapter mMessageAdapter; // This will be your RecyclerView.Adapter
    private EditText mMessageEditText;
    private Button mSendButton;
    // private ListView mMessageListView; // REMOVED
    private RecyclerView mMessageRecyclerView; // ADDED
    private ProgressBar mProgressBar;

    private List<FriendlyMessage> friendlyMessagesList; // ADDED: Data source for the adapter

    // Firebase Auth
    private FirebaseAuth mAuth;
    private String mCurrentUserId;
    private String mUsername = "Anonymous";

    // Firestore
    private FirebaseFirestore db;
    private CollectionReference messagesCollectionRef;
    private ListenerRegistration mMessagesListener;
    private ListenerRegistration mUserListener;
    private String eventId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chatbox); // Your layout should have R.id.messageRecyclerView

        String eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in. Please log in to chat.", Toast.LENGTH_LONG).show();
            mCurrentUserId = "UNKNOWN_USER";
            // finish(); // Consider finishing or navigating to login
            // return;
        } else {
            mCurrentUserId = currentUser.getUid();
        }

        eventId = getIntent().getStringExtra("EventId");
        if (TextUtils.isEmpty(eventId)) {
            Log.e(TAG, "EventId is null or empty. Cannot initialize chat.");
            Toast.makeText(this, "Error: Event ID missing.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setTitle("Chat: Event " + eventId.substring(0, Math.min(eventId.length(), 6)) + "...");

        messagesCollectionRef = db.collection(EVENTS_COLLECTION).document(eventId)
                .collection(MESSAGES_SUBCOLLECTION);

        // Initialize Views
        mProgressBar = findViewById(R.id.progressBar);
        mMessageRecyclerView = findViewById(R.id.messageRecyclerView); // UPDATED ID
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

        // Initialize message RecyclerView and its adapter
        friendlyMessagesList = new ArrayList<>(); // INITIALIZE THE LIST
        mMessageAdapter = new MessageAdapter(this, friendlyMessagesList, mCurrentUserId); // Use RecyclerView Adapter constructor

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // Optional: To make messages fill from bottom and scroll to new ones at bottom
        // layoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(layoutManager);
        mMessageRecyclerView.setAdapter(mMessageAdapter);

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSendButton.setEnabled(!s.toString().trim().isEmpty());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        mSendButton.setOnClickListener(view -> sendMessage());

        if (!mCurrentUserId.equals("UNKNOWN_USER")) {
            attachUserListener();
        } else {
            mUsername = "Anonymous";
        }
        // Messages listener is attached in onStart
    }

    private void attachUserListener() {
        if (mUserListener == null && mCurrentUserId != null && !mCurrentUserId.equals("UNKNOWN_USER")) {
            DocumentReference userDocRef = db.collection(USERS_COLLECTION).document(mCurrentUserId);
            mUserListener = userDocRef.addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    Log.e(TAG, "Listen failed for user data.", error);
                    mUsername = (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null)
                            ? mAuth.getCurrentUser().getDisplayName() : "User (Error)";
                    return;
                }
                if (snapshot != null && snapshot.exists()) {
                    String nameFromDb = snapshot.getString(USER_NAME_FIELD);
                    mUsername = (nameFromDb != null && !nameFromDb.isEmpty()) ? nameFromDb : "User (No Name)";
                    Log.d(TAG, "Username fetched: " + mUsername);
                } else {
                    Log.w(TAG, "User document does not exist or name field is missing for UID: " + mCurrentUserId);
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
            Toast.makeText(this, "Cannot send empty message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUser == null || mCurrentUserId.equals("UNKNOWN_USER")) {
            Toast.makeText(this, "You must be logged in to send messages", Toast.LENGTH_SHORT).show();
            return;
        }

        String photoUrl = (currentUser.getPhotoUrl() != null) ? currentUser.getPhotoUrl().toString() : null;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("text", messageText);
        messageData.put("name", mUsername);
        messageData.put("userId", mCurrentUserId);
        messageData.put("photoUrl", photoUrl);
        messageData.put(MESSAGE_TIMESTAMP_FIELD, FieldValue.serverTimestamp()); // Ensure "timestamp" is the field name in Firestore and FriendlyMessage for @ServerTimestamp

        messagesCollectionRef.add(messageData)
                .addOnSuccessListener(documentReference -> {
                    mMessageEditText.setText("");
                    Log.d(TAG, "Message sent successfully with ID: " + documentReference.getId());
                    // The listener will pick up the new message and update the UI
                    // Optional: immediately scroll to bottom if layoutManager.setStackFromEnd(true) is not used
                    // or if you want an explicit scroll after your own message.
                    // if (!friendlyMessagesList.isEmpty()) {
                    //    mMessageRecyclerView.smoothScrollToPosition(mMessageAdapter.getItemCount() - 1);
                    // }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send message.", e);
                    Toast.makeText(ChatBox.this, "Failed to send message.", Toast.LENGTH_SHORT).show();
                });
    }

    private void attachMessagesListener() {
        if (mMessagesListener == null && messagesCollectionRef != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            Query query = messagesCollectionRef.orderBy(MESSAGE_TIMESTAMP_FIELD, Query.Direction.ASCENDING)
                    .limitToLast(50); // Get the last 50 messages

            mMessagesListener = query.addSnapshotListener(new EventListener<QuerySnapshot>() {
                @Override
                public void onEvent(@Nullable QuerySnapshot snapshots,
                                    @Nullable FirebaseFirestoreException e) {
                    if (e != null) {
                        Log.e(TAG, "Listen error for messages", e);
                        mProgressBar.setVisibility(View.GONE);
                        Toast.makeText(ChatBox.this, "Failed to load messages.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots == null) {
                        Log.w(TAG, "Messages snapshot was null");
                        mProgressBar.setVisibility(View.GONE);
                        return;
                    }
                    mProgressBar.setVisibility(View.GONE);

                    boolean newMessagesAdded = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        FriendlyMessage message = dc.getDocument().toObject(FriendlyMessage.class);
                        if (message == null) {
                            Log.w(TAG, "FriendlyMessage was null after conversion.");
                            continue;
                        }
                        // If your FriendlyMessage needs the document ID, set it here:
                        // message.setId(dc.getDocument().getId());

                        // Assuming your FriendlyMessage POJO has a 'timestamp' field of type Date
                        // that is populated by @ServerTimestamp or correctly from Firestore.
                        // If not, you might need to handle the com.google.firebase.Timestamp manually here.

                        switch (dc.getType()) {
                            case ADDED:
                                // To avoid duplicates if listener fires with existing local data initially
                                // A more robust check might involve message IDs if you store them.
                                boolean exists = false;
                                for (FriendlyMessage fm : friendlyMessagesList) {
                                    // Basic check, assumes text and timestamp might be enough to identify
                                    // A proper ID check is better: if (fm.getId() != null && fm.getId().equals(message.getId()))
                                    if (fm.getText().equals(message.getText()) && fm.getTimestamp().equals(message.getTimestamp())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    friendlyMessagesList.add(message);
                                    newMessagesAdded = true;
                                    Log.d(TAG, "New message added: " + message.getText());
                                }
                                break;
                            case MODIFIED:
                                Log.d(TAG, "Modified message: " + message.getText());
                                // Find and update the message in friendlyMessagesList by a unique ID
                                // For simplicity, this example just reloads on modification if needed,
                                // or you'd implement a find-and-replace.
                                for (int i = 0; i < friendlyMessagesList.size(); i++) {
                                    // Assuming FriendlyMessage has getId() and it's set from document ID
                                    // if (friendlyMessagesList.get(i).getId().equals(dc.getDocument().getId())) {
                                    // friendlyMessagesList.set(i, message);
                                    // newMessagesAdded = true; // To trigger notifyDataSetChanged
                                    // break;
                                    // }
                                }
                                break;
                            case REMOVED:
                                Log.d(TAG, "Removed message: " + message.getText());
                                // Remove the message from friendlyMessagesList by a unique ID
                                // friendlyMessagesList.removeIf(fm -> fm.getId().equals(dc.getDocument().getId())); // Java 8+
                                // newMessagesAdded = true; // To trigger notifyDataSetChanged
                                break;
                        }
                    }

                    // Sort messages by timestamp after processing all changes
                    // Ensure your FriendlyMessage's getTimestamp() returns a java.util.Date
                    // and that it's correctly populated from Firestore (e.g., via @ServerTimestamp)
                    Collections.sort(friendlyMessagesList, Comparator.comparing(
                            FriendlyMessage::getTimestamp,
                            Comparator.nullsLast(Comparator.naturalOrder()) // Handles cases where timestamp might be null
                    ));

                    mMessageAdapter.notifyDataSetChanged(); // Notify the adapter that the data set has changed

                    // Scroll to the bottom if new messages were added or the list is not empty
                    if (newMessagesAdded && mMessageAdapter.getItemCount() > 0) {
                        mMessageRecyclerView.smoothScrollToPosition(mMessageAdapter.getItemCount() - 1);
                    } else if (mMessageAdapter.getItemCount() > 0 && snapshots.getDocumentChanges().size() == snapshots.size()) {
                        // If it's the initial load, scroll to bottom
                        mMessageRecyclerView.scrollToPosition(mMessageAdapter.getItemCount() - 1);
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
    protected void onStart() {
        super.onStart();
        // User listener is attached in onCreate based on current user status.
        // Re-check current user for adapter if it can change (e.g. login/logout within app without recreating activity)
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String newUserId = (currentUser != null) ? currentUser.getUid() : "UNKNOWN_USER";
        if (!mCurrentUserId.equals(newUserId)) {
            Log.d(TAG, "User changed, updating adapter. Old: " + mCurrentUserId + ", New: " + newUserId);
            mCurrentUserId = newUserId;
            // Update adapter's current user ID and refresh data
            if (mMessageAdapter != null) {
                mMessageAdapter.updateCurrentUserId(mCurrentUserId); // Assumes MessageAdapter has this method
            }
            // If user changed, messages might need to be re-fetched or re-evaluated for view types
            // For simplicity, detaching and re-attaching listener can reload messages.
            detachMessagesListener();
            friendlyMessagesList.clear(); // Clear existing messages
            if (mMessageAdapter != null) mMessageAdapter.notifyDataSetChanged();
        }
        attachMessagesListener(); // Always attach or re-attach messages listener
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachMessagesListener();
        detachUserListener();
        // No need to clear adapter here, onStart will handle data loading.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu); // Ensure res/menu/main_menu.xml exists
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        // Example: Add a sign-out option
        // if (item.getItemId() == R.id.action_sign_out) { // Assuming you add R.id.action_sign_out to main_menu.xml
        // mAuth.signOut();
        // // Navigate to login screen, clear user data, etc.
        // Intent intent = new Intent(ChatBox.this, LoginActivity.class); // Replace LoginActivity with your actual login screen
        // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // startActivity(intent);
        // finish();
        // return true;
        // }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Your FriendlyMessage POJO needs to be compatible with Firestore.
     * 1. It should have a public no-argument constructor.
     * 2. Public getters for all fields that you want to be serialized/deserialized.
     * 3. For timestamps from the server, use @ServerTimestamp on a field of type java.util.Date or com.google.firebase.Timestamp.
     *
     * Example structure for FriendlyMessage:
     * (Ensure this matches your actual FriendlyMessage.java)
     *
     * import com.google.firebase.firestore.ServerTimestamp;
     * import java.util.Date;
     *
     * public class FriendlyMessage {
     *     private String text;
     *     private String name;
     *     private String photoUrl;
     *     private String userId;
     *     private @ServerTimestamp Date timestamp; // Firestore will populate this if it's null when sent
     *     // private String id; // Optional: to store document ID locally
     *
     *     public FriendlyMessage() {} // Needed for Firestore
     *
     *     // Constructor used for sending messages
     *     public FriendlyMessage(String text, String name, String photoUrl, String userId) {
     *         this.text = text;
     *         this.name = name;
     *         this.photoUrl = photoUrl;
     *         this.userId = userId;
     *         // this.timestamp can be null, @ServerTimestamp will handle it server-side
     *     }
     *
     *     public String getText() { return text; }
     *     public void setText(String text) { this.text = text; }
     *
     *     public String getTitle() { return name; }
     *     public void setName(String name) { this.name = name; }
     *
     *     public String getPhotoUrl() { return photoUrl; }
     *     public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
     *
     *     public String getUserId() { return userId; }
     *     public void setUserId(String userId) { this.userId = userId; }
     *
     *     public Date getTimestamp() { return timestamp; } // Ensure this getter exists and returns Date
     *     public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
     *
     *     // public String getId() { return id; }
     *     // public void setId(String id) { this.id = id; }
     *
     *     // Implement equals() and hashCode() if you add messages to a Set or
     *     // perform specific .contains() or .remove() operations on the list
     *     // based on object equality rather than just ID.
     * }
     */
}