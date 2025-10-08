package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar; // Added for ProgressBar
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar; // Added for Toolbar

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration; // For wallet listener
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
// Removed WriteBatch as transaction is preferred for atomic read/write

import java.text.NumberFormat; // For displaying insufficient balance
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TransactActivity extends AppCompatActivity {

    private static final String TAG = "TransactActivity";
    // Consistent naming with your Firestore structure discussed for Wallet.java
    private static final String USERS_COLLECTION = "users";
    private static final String WALLET_MAP_FIELD = "wallet"; // The map containing balance and currency
    private static final String BALANCE_FIELD_IN_WALLET = "balance"; // Field inside the wallet map
    private static final String CURRENCY_FIELD_IN_WALLET = "currency"; // Field inside the wallet map
    // Assuming email is stored directly under user document, not 'mail' as in original
    // If it's 'mail', change this back.
    private static final String EMAIL_FIELD = "email";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private DocumentReference senderUserDocRef;

    private Button sendButton;
    private TextInputEditText receiverEmailEditText, amountEditText;
    private ProgressBar loadingProgressBar; // Added ProgressBar

    private ListenerRegistration senderWalletListener;
    private Double senderCurrentWalletBalance = 0.0;
    private String senderCurrency = "USD"; // Default, will be updated by listener

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transact);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.transaction_activity_title)); // Use string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, R.string.auth_user_not_authenticated, Toast.LENGTH_LONG).show();
            // Consider navigating to login screen:
            // Intent intent = new Intent(this, LoginActivity.class);
            // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            // startActivity(intent);
            finish();
            return;
        }

        senderUserDocRef = db.collection(USERS_COLLECTION).document(currentUser.getUid());

        amountEditText = findViewById(R.id.receivermoney);
        receiverEmailEditText = findViewById(R.id.receivermail);
        sendButton = findViewById(R.id.sendmoney);
        loadingProgressBar = findViewById(R.id.loadingProgressBar); // Initialize ProgressBar

        attachSenderWalletListener();

        sendButton.setOnClickListener(v -> attemptTransaction());
    }

    private void attachSenderWalletListener() {
        if (senderUserDocRef == null) return;

        if (senderWalletListener != null) {
            senderWalletListener.remove();
        }

        // Listen to the sender's user document for wallet updates
        senderWalletListener = senderUserDocRef.addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed for sender's wallet.", error);
                Toast.makeText(TransactActivity.this, R.string.error_fetching_wallet_balance, Toast.LENGTH_SHORT).show();
                senderCurrentWalletBalance = -1.0; // Indicate error
                sendButton.setEnabled(false); // Disable sending if balance is unknown
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                Object walletObj = snapshot.get(WALLET_MAP_FIELD);
                if (walletObj instanceof Map) {
                    Map<String, Object> walletMap = (Map<String, Object>) walletObj;
                    Object balanceObj = walletMap.get(BALANCE_FIELD_IN_WALLET);
                    if (balanceObj instanceof Number) {
                        senderCurrentWalletBalance = ((Number) balanceObj).doubleValue();
                        senderCurrency = (String) walletMap.get(CURRENCY_FIELD_IN_WALLET);
                        if (senderCurrency == null || senderCurrency.isEmpty()) {
                            senderCurrency = "USD"; // Default
                        }
                        Log.d(TAG, "Sender's current wallet balance: " + senderCurrentWalletBalance + " " + senderCurrency);
                        sendButton.setEnabled(true); // Enable button once balance is known
                    } else {
                        Log.w(TAG, "Balance field is null or not a number in sender's wallet map.");
                        initializeWalletForUser(senderUserDocRef, "Sender"); // Initialize if balance field missing
                        senderCurrentWalletBalance = 0.0; // Assume 0 until initialized
                        sendButton.setEnabled(true); // Can still try to send 0 or if other logic handles it
                    }
                } else {
                    Log.w(TAG, "'wallet' map is missing for sender. Initializing.");
                    initializeWalletForUser(senderUserDocRef, "Sender");
                    senderCurrentWalletBalance = 0.0; // Assume 0 until initialized
                }
            } else {
                Log.d(TAG, "Sender document does not exist. Initializing.");
                initializeUserDocumentWithWallet(senderUserDocRef, "Sender");
                senderCurrentWalletBalance = 0.0; // Assume 0 until initialized
            }
        });
    }

    private void attemptTransaction() {
        String receiverEmail = Objects.requireNonNull(receiverEmailEditText.getText()).toString().trim();
        String amountString = Objects.requireNonNull(amountEditText.getText()).toString().trim();

        if (TextUtils.isEmpty(receiverEmail)) {
            receiverEmailEditText.setError(getString(R.string.error_receiver_email_required));
            receiverEmailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(amountString)) {
            amountEditText.setError(getString(R.string.error_amount_required));
            amountEditText.requestFocus();
            return;
        }

        double amountToSend; // Use double for amount, consistent with balance
        try {
            amountToSend = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            amountEditText.setError(getString(R.string.error_invalid_amount_format));
            amountEditText.requestFocus();
            return;
        }

        if (amountToSend <= 0) {
            amountEditText.setError(getString(R.string.error_amount_greater_than_zero));
            amountEditText.requestFocus();
            return;
        }

        if (senderCurrentWalletBalance < amountToSend) {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(senderCurrency.isEmpty() ? "en-US" : senderCurrency)); // Basic locale from currency
            String formattedBalance = currencyFormat.format(senderCurrentWalletBalance);
            Toast.makeText(TransactActivity.this, getString(R.string.error_insufficient_balance, formattedBalance), Toast.LENGTH_LONG).show();
            return;
        }

        if (currentUser != null && receiverEmail.equals(currentUser.getEmail())) {
            Toast.makeText(TransactActivity.this, R.string.error_cannot_send_to_self, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoadingState(true);
        db.collection(USERS_COLLECTION)
                .whereEqualTo(EMAIL_FIELD, receiverEmail) // Ensure EMAIL_FIELD matches your Firestore structure
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            DocumentSnapshot receiverDocSnapshot = querySnapshot.getDocuments().get(0);
                            String receiverUid = receiverDocSnapshot.getId();

                            if (receiverUid.equals(currentUser.getUid())) {
                                setLoadingState(false);
                                Toast.makeText(TransactActivity.this, R.string.error_cannot_send_to_self, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            DocumentReference receiverUserDocRef = db.collection(USERS_COLLECTION).document(receiverUid);
                            executeFirestoreTransaction(senderUserDocRef, receiverUserDocRef, amountToSend);
                        } else {
                            setLoadingState(false);
                            Toast.makeText(TransactActivity.this, R.string.error_receiver_email_not_found, Toast.LENGTH_SHORT).show();
                            receiverEmailEditText.requestFocus();
                        }
                    } else {
                        setLoadingState(false);
                        Log.e(TAG, "Error finding receiver by email: ", task.getException());
                        Toast.makeText(TransactActivity.this, R.string.error_finding_receiver, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void executeFirestoreTransaction(
            final DocumentReference senderRef,
            final DocumentReference receiverRef,
            final double amount // Use double for amount
    ) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot senderSnapshot = transaction.get(senderRef);
            DocumentSnapshot receiverSnapshot = transaction.get(receiverRef);

            // --- Sender Wallet Logic ---
            Map<String, Object> senderWalletMap = (Map<String, Object>) senderSnapshot.get(WALLET_MAP_FIELD);
            if (senderWalletMap == null || !(senderWalletMap.get(BALANCE_FIELD_IN_WALLET) instanceof Number)) {
                Log.e(TAG, "Sender's wallet or balance is malformed or missing.");
                // Initialize if completely missing
                if (senderWalletMap == null) initializeWalletForUser(senderRef, "Sender (transaction)", transaction);
                throw new FirebaseFirestoreException("Sender's wallet is not properly initialized.",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            double senderBalance = ((Number) senderWalletMap.get(BALANCE_FIELD_IN_WALLET)).doubleValue();
            if (senderBalance < amount) {
                throw new FirebaseFirestoreException("Insufficient funds in sender's wallet.",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            Map<String, Object> updatedSenderWallet = new HashMap<>(senderWalletMap);
            updatedSenderWallet.put(BALANCE_FIELD_IN_WALLET, senderBalance - amount);
            transaction.update(senderRef, WALLET_MAP_FIELD, updatedSenderWallet);

            // --- Receiver Wallet Logic ---
            Map<String, Object> receiverWalletMap = (Map<String, Object>) receiverSnapshot.get(WALLET_MAP_FIELD);
            double receiverOldBalance = 0.0;
            String receiverCurrency = "USD"; // Default

            if (receiverWalletMap != null && receiverWalletMap.get(BALANCE_FIELD_IN_WALLET) instanceof Number) {
                receiverOldBalance = ((Number) receiverWalletMap.get(BALANCE_FIELD_IN_WALLET)).doubleValue();
                if (receiverWalletMap.get(CURRENCY_FIELD_IN_WALLET) != null) {
                    receiverCurrency = (String) receiverWalletMap.get(CURRENCY_FIELD_IN_WALLET);
                }
            } else {
                Log.i(TAG, "Receiver's wallet map or balance not found/malformed. Initializing.");
                // Ensure the receiver document itself exists before trying to initialize wallet within it
                if (!receiverSnapshot.exists()) {
                    initializeUserDocumentWithWallet(receiverRef, "Receiver (transaction)", transaction);
                } else {
                    initializeWalletForUser(receiverRef, "Receiver (transaction)", transaction);
                }
                // Since initialization happens in a separate step within the transaction if needed,
                // we'll assume for this update pass it's 0 if not found initially.
                // The transaction will fail and retry if initialization was needed.
                // Or, for simplicity here, we assume if we reach here, wallet map will be created by initializeWalletForUser.
            }
            // For this transaction, we assume the receiver's currency remains their default.
            // Currency conversion would be a separate, complex step.
            Map<String, Object> updatedReceiverWallet = receiverWalletMap != null ? new HashMap<>(receiverWalletMap) : new HashMap<>();
            updatedReceiverWallet.put(BALANCE_FIELD_IN_WALLET, receiverOldBalance + amount);
            if(updatedReceiverWallet.get(CURRENCY_FIELD_IN_WALLET) == null) { // Ensure currency is set
                updatedReceiverWallet.put(CURRENCY_FIELD_IN_WALLET, receiverCurrency);
            }
            transaction.update(receiverRef, WALLET_MAP_FIELD, updatedReceiverWallet);


            // Optional: Log the transaction in a separate 'transactions' collection
            DocumentReference newTransactionRef = db.collection("transactions").document();
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("senderUid", senderRef.getId());
            transactionData.put("receiverUid", receiverRef.getId());
            transactionData.put("amount", amount);
            transactionData.put("currency", senderCurrency); // Log the currency of transaction
            transactionData.put("timestamp", FieldValue.serverTimestamp());
            transactionData.put("status", "completed");
            transaction.set(newTransactionRef, transactionData);

            return null;
        }).addOnSuccessListener(aVoid -> {
            setLoadingState(false);
            Log.d(TAG, "Transaction successful!");
            Toast.makeText(TransactActivity.this, R.string.success_money_sent, Toast.LENGTH_SHORT).show();
            if (amountEditText.getText() != null) amountEditText.getText().clear();
            if (receiverEmailEditText.getText() != null) receiverEmailEditText.getText().clear();
            // finish(); // Optionally close activity after success
        }).addOnFailureListener(e -> {
            setLoadingState(false);
            Log.e(TAG, "Transaction failed: ", e);
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("Insufficient funds")) {
                    Toast.makeText(TransactActivity.this, R.string.transaction_failed_insufficient_funds, Toast.LENGTH_LONG).show();
                } else if (errorMessage.contains("Sender's wallet") || errorMessage.contains("Receiver's wallet")) {
                    Toast.makeText(TransactActivity.this, R.string.transaction_failed_wallet_issue, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(TransactActivity.this, getString(R.string.transaction_failed_generic, errorMessage), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(TransactActivity.this, getString(R.string.transaction_failed_unknown), Toast.LENGTH_LONG).show();
            }
        });
    }

    // Helper to initialize wallet structure if missing (within a transaction or standalone)
    private void initializeWalletForUser(DocumentReference userRef, String userTypeLogPrefix) {
        initializeWalletForUser(userRef, userTypeLogPrefix, null);
    }
    private void initializeWalletForUser(DocumentReference userRef, String userTypeLogPrefix, @Nullable Transaction transaction) {
        Log.i(TAG, "Attempting to initialize wallet for " + userTypeLogPrefix + ": " + userRef.getId());
        Map<String, Object> defaultWallet = new HashMap<>();
        defaultWallet.put(BALANCE_FIELD_IN_WALLET, 0.0);
        defaultWallet.put(CURRENCY_FIELD_IN_WALLET, "USD"); // Default currency

        if (transaction != null) {
            transaction.set(userRef, new HashMap<String, Object>() {{ put(WALLET_MAP_FIELD, defaultWallet); }}, SetOptions.merge());
        } else {
            userRef.set(new HashMap<String, Object>() {{ put(WALLET_MAP_FIELD, defaultWallet); }}, SetOptions.merge())
                    .addOnSuccessListener(v -> Log.i(TAG, userTypeLogPrefix + "'s wallet initialized with default values."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to initialize " + userTypeLogPrefix + "'s wallet.", e));
        }
    }

    // Helper to initialize user document if missing (within a transaction or standalone)
    private void initializeUserDocumentWithWallet(DocumentReference userRef, String userTypeLogPrefix) {
        initializeUserDocumentWithWallet(userRef, userTypeLogPrefix, null);
    }

    private void initializeUserDocumentWithWallet(DocumentReference userRef, String userTypeLogPrefix, @Nullable Transaction transaction) {
        Log.i(TAG, "Attempting to initialize document with wallet for " + userTypeLogPrefix + ": " + userRef.getId());
        Map<String, Object> defaultWallet = new HashMap<>();
        defaultWallet.put(BALANCE_FIELD_IN_WALLET, 0.0);
        defaultWallet.put(CURRENCY_FIELD_IN_WALLET, "USD");

        Map<String, Object> initialUserData = new HashMap<>();
        initialUserData.put(WALLET_MAP_FIELD, defaultWallet);
        // Add other default user fields like email if creating the user doc for the first time
        // String userEmail = ""; // Get email if available for a new user, e.g. from receiverEmailEditText if it's a new receiver
        // initialUserData.put(EMAIL_FIELD, userEmail);

        if (transaction != null) {
            transaction.set(userRef, initialUserData); // Don't merge if creating a new document
        } else {
            userRef.set(initialUserData) // Don't merge if creating a new document
                    .addOnSuccessListener(v -> Log.i(TAG, userTypeLogPrefix + "'s document and wallet initialized."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to initialize " + userTypeLogPrefix + "'s document and wallet.", e));
        }
    }


    private void setLoadingState(boolean isLoading) {
        sendButton.setEnabled(!isLoading);
        amountEditText.setEnabled(!isLoading);
        receiverEmailEditText.setEnabled(!isLoading);
        loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Or navigate up if part of a larger flow
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (senderWalletListener != null) {
            senderWalletListener.remove();
            senderWalletListener = null; // Clear the reference
            Log.d(TAG, "Sender wallet listener removed in onStop.");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-attach listener if it was removed in onStop and user is still valid
        if (currentUser != null && senderUserDocRef != null && senderWalletListener == null) {
            attachSenderWalletListener();
            Log.d(TAG, "Sender wallet listener re-attached in onStart.");
        }
    }
}

