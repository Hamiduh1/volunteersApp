package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Firestore Imports
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;


import java.util.Objects;

public class TransactActivity extends AppCompatActivity {

    private static final String TAG = "TransactActivity";
    private static final String USERS_COLLECTION = "users"; // Firestore collection name
    private static final String WALLET_FIELD = "wallet";   // Field name for wallet balance
    private static final String EMAIL_FIELD = "mail";      // Field name for user email

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private DocumentReference senderDocRef; // Reference to the current user's document

    private Button sendButton;
    private TextInputEditText receiverEmailEditText, amountEditText;

    private long senderCurrentWalletBalance = 0; // Use long for currency to avoid precision issues with int for large sums
    // No need for 'flag' variable if logic is structured well

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transact); // Ensure this layout has the correct IDs

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated. Please login again.", Toast.LENGTH_LONG).show();
            // Optionally, navigate to login screen
            // startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        senderDocRef = db.collection(USERS_COLLECTION).document(currentUser.getUid());

        // Initialize UI elements
        amountEditText = findViewById(R.id.receivermoney); // ID from your original code
        receiverEmailEditText = findViewById(R.id.receivermail); // ID from your original code
        sendButton = findViewById(R.id.sendmoney);       // ID from your original code

        // Clear input fields (optional, but good practice if they might hold old data)
        if (amountEditText.getText() != null) amountEditText.getText().clear();
        if (receiverEmailEditText.getText() != null) receiverEmailEditText.getText().clear();

        // Listen for real-time updates to the sender's wallet balance
        attachWalletListener();

        sendButton.setOnClickListener(v -> attemptTransaction());
    }

    private void attachWalletListener() {
        senderDocRef.addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed for sender's wallet.", error);
                // Potentially disable transactions or show error if wallet can't be read
                Toast.makeText(TransactActivity.this, "Error fetching your wallet balance.", Toast.LENGTH_SHORT).show();
                senderCurrentWalletBalance = -1; // Indicate error or unknown state
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Long balance = snapshot.getLong(WALLET_FIELD);
                if (balance != null) {
                    senderCurrentWalletBalance = balance;
                    Log.d(TAG, "Current wallet balance: " + senderCurrentWalletBalance);
                } else {
                    Log.w(TAG, "Wallet field is null or not found for sender.");
                    senderCurrentWalletBalance = 0; // Default to 0 if not found, or handle as error
                    // You might need to create the wallet field if it doesn't exist for a new user
                }
            } else {
                Log.d(TAG, "Sender document does not exist yet.");
                senderCurrentWalletBalance = 0; // Or handle as an error/setup scenario
            }
        });
    }

    private void attemptTransaction() {
        String receiverEmail = Objects.requireNonNull(receiverEmailEditText.getText()).toString().trim();
        String amountString = Objects.requireNonNull(amountEditText.getText()).toString().trim();

        if (TextUtils.isEmpty(receiverEmail)) {
            receiverEmailEditText.setError("Receiver's email is required.");
            receiverEmailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(amountString)) {
            amountEditText.setError("Enter amount to send.");
            amountEditText.requestFocus();
            return;
        }

        long amountToSend;
        try {
            amountToSend = Long.parseLong(amountString);
        } catch (NumberFormatException e) {
            amountEditText.setError("Invalid amount format.");
            amountEditText.requestFocus();
            return;
        }

        if (amountToSend <= 0) {
            amountEditText.setError("Amount must be greater than zero.");
            amountEditText.requestFocus();
            return;
        }

        if (senderCurrentWalletBalance < amountToSend) {
            Toast.makeText(TransactActivity.this, "Insufficient balance.", Toast.LENGTH_SHORT).show();
            // Optionally, navigate to a top-up/wallet activity
            // Intent intent = new Intent(TransactActivity.this, WalletActivity.class); // Assuming you have WalletActivity
            // startActivity(intent);
            return;
        }

        if (currentUser != null && receiverEmail.equals(currentUser.getEmail())) {
            Toast.makeText(TransactActivity.this, "You cannot send money to yourself.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- Find Receiver by Email ---
        // It's generally better to transact using UIDs if possible, but if email is the only way:
        setLoadingState(true);
        db.collection(USERS_COLLECTION)
                .whereEqualTo(EMAIL_FIELD, receiverEmail)
                .limit(1) // Assuming email is unique
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot receiverDocSnapshot = querySnapshot.getDocuments().get(0);
                            String receiverUid = receiverDocSnapshot.getId();

                            if (receiverUid.equals(currentUser.getUid())) { // Double check
                                setLoadingState(false);
                                Toast.makeText(TransactActivity.this, "You cannot send money to yourself.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            DocumentReference receiverDocRef = db.collection(USERS_COLLECTION).document(receiverUid);
                            executeFirestoreTransaction(senderDocRef, receiverDocRef, amountToSend);

                        } else {
                            setLoadingState(false);
                            Toast.makeText(TransactActivity.this, "Receiver email not found.", Toast.LENGTH_SHORT).show();
                            receiverEmailEditText.requestFocus();
                        }
                    } else {
                        setLoadingState(false);
                        Log.e(TAG, "Error finding receiver by email: ", task.getException());
                        Toast.makeText(TransactActivity.this, "Error finding receiver. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void executeFirestoreTransaction(
            DocumentReference senderRef,
            DocumentReference receiverRef,
            long amount
    ) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot senderSnapshot = transaction.get(senderRef);
            DocumentSnapshot receiverSnapshot = transaction.get(receiverRef);

            // Validate sender's wallet
            Long senderBalance = senderSnapshot.getLong(WALLET_FIELD);
            if (senderBalance == null) {
                throw new FirebaseFirestoreException("Sender's wallet not found or is null.",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            if (senderBalance < amount) {
                throw new FirebaseFirestoreException("Insufficient funds in sender's wallet.",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            // Validate receiver's wallet (or create it if it doesn't exist)
            Long receiverBalance = receiverSnapshot.getLong(WALLET_FIELD);
            if (receiverBalance == null) {
                receiverBalance = 0L; // Initialize receiver's wallet if it doesn't exist
                Log.i(TAG, "Receiver's wallet not found, initializing to 0.");
            }

            // Perform the updates
            transaction.update(senderRef, WALLET_FIELD, senderBalance - amount);
            transaction.update(receiverRef, WALLET_FIELD, receiverBalance + amount);

            // Optional: Log the transaction in a separate 'transactions' collection for auditing
            // DocumentReference newTransactionRef = db.collection("transactions").document();
            // Map<String, Object> transactionData = new HashMap<>();
            // transactionData.put("senderUid", senderRef.getId());
            // transactionData.put("receiverUid", receiverRef.getId());
            // transactionData.put("amount", amount);
            // transactionData.put("timestamp", FieldValue.serverTimestamp());
            // transaction.set(newTransactionRef, transactionData);

            return null; // Indicates success
        }).addOnSuccessListener(aVoid -> {
            setLoadingState(false);
            Log.d(TAG, "Transaction successful!");
            Toast.makeText(TransactActivity.this, "Money sent successfully!", Toast.LENGTH_SHORT).show();
            // Clear fields after successful transaction
            if (amountEditText.getText() != null) amountEditText.getText().clear();
            if (receiverEmailEditText.getText() != null) receiverEmailEditText.getText().clear();
            // Optionally navigate away or update UI further
            // Intent intent1 = new Intent(TransactActivity.this, ProfileActivity.class); // Assuming ProfileActivity
            // startActivity(intent1);
            // finish();
        }).addOnFailureListener(e -> {
            setLoadingState(false);
            Log.e(TAG, "Transaction failed: ", e);
            if (e.getMessage() != null && e.getMessage().contains("Insufficient funds")) {
                Toast.makeText(TransactActivity.this, "Transaction failed: Insufficient funds.", Toast.LENGTH_LONG).show();
            } else if (e.getMessage() != null && e.getMessage().contains("Sender's wallet not found")) {
                Toast.makeText(TransactActivity.this, "Transaction failed: Your wallet data is missing.", Toast.LENGTH_LONG).show();
            }
            else {
                Toast.makeText(TransactActivity.this, "Transaction failed. Please try again. " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoadingState(boolean isLoading) {
        sendButton.setEnabled(!isLoading);
        amountEditText.setEnabled(!isLoading);
        receiverEmailEditText.setEnabled(!isLoading);
        // Add a ProgressBar visibility toggle if you have one in your layout
        // ProgressBar loadingProgressBar = findViewById(R.id.loadingProgressBar);
        // if (loadingProgressBar != null) {
        //     loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        // }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Listeners attached with an Activity context are automatically removed.
        // If you had used a global context or manual executor, you'd remove the listener here.
    }
}