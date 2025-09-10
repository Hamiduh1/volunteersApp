package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
// Firestore specific imports
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration; // For managing listeners

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map; // For accessing nested map

public class Wallet extends AppCompatActivity {

    private static final String TAG = "WalletActivity";

    private FirebaseAuth mAuth;
    // Firestore instance and DocumentReference
    private FirebaseFirestore db;
    private DocumentReference userWalletDocRef; // Reference to the user's document
    private TextView balanceTextView;
    private ListenerRegistration walletListenerRegistration; // Firestore listener registration

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Wallet");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance(); // Initialize Firestore

        FirebaseUser currentUser = mAuth.getCurrentUser();
        balanceTextView = findViewById(R.id.balance);
        CardView transactCardView = findViewById(R.id.transact);

        if (currentUser != null) {
            // Path: users/{userId}
            userWalletDocRef = db.collection("Users").document(currentUser.getUid());
            setupWalletListener();
        } else {
            Log.w(TAG, "User is not signed in. Cannot fetch wallet balance.");
            Toast.makeText(this, "Please sign in to view your wallet.", Toast.LENGTH_LONG).show();
            balanceTextView.setText(getString(R.string.default_balance_not_signed_in));
        }

        transactCardView.setOnClickListener(v -> {
            Intent intent = new Intent(Wallet.this, TransactActivity.class);
            startActivity(intent);
        });
    }

    private void setupWalletListener() {
        if (userWalletDocRef == null) return;

        // Remove any existing listener before attaching a new one
        if (walletListenerRegistration != null) {
            walletListenerRegistration.remove();
        }

        walletListenerRegistration = userWalletDocRef.addSnapshotListener((documentSnapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed.", e);
                Toast.makeText(Wallet.this, "Failed to load wallet balance. Please try again.", Toast.LENGTH_SHORT).show();
                balanceTextView.setText(getString(R.string.default_balance_error));
                return;
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                // Assuming the structure is: Users/{UID}/wallet (Map) -> balance (Number)
                Object walletObject = documentSnapshot.get("wallet"); // Get the 'wallet' map
                if (walletObject instanceof Map) {
                    Map<String, Object> walletMap = (Map<String, Object>) walletObject;
                    Object balanceObject = walletMap.get("balance");

                    if (balanceObject instanceof Number) {
                        Double balanceValue = ((Number) balanceObject).doubleValue();
                        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault());
                        balanceTextView.setText(currencyFormat.format(balanceValue));
                        Log.d(TAG, "Wallet balance updated: " + currencyFormat.format(balanceValue));
                    } else {
                        Log.w(TAG, "Balance data is null or not in expected numeric format within the wallet map.");
                        balanceTextView.setText(getString(R.string.default_balance_format_error));
                        // Toast.makeText(Wallet.this, "Balance data format error.", Toast.LENGTH_SHORT).show(); // Optional, can be verbose
                        // Consider setting a default balance or creating the wallet structure if it's missing
                        // E.g., userWalletDocRef.set(new HashMap<String, Object>() {{ put("wallet", new HashMap<String, Object>() {{ put("balance", 0.0); }}); }}, SetOptions.merge());
                    }
                } else {
                    Log.w(TAG, "'wallet' field is not a Map or is missing for user: " + documentSnapshot.getId());
                    balanceTextView.setText(getString(R.string.default_balance_not_found)); // Or a more specific error
                    // Toast.makeText(Wallet.this, "Wallet structure error.", Toast.LENGTH_SHORT).show(); // Optional
                    //  Initialize wallet structure if it's expected but missing
                    //  Map<String, Object> initialWallet = new HashMap<>();
                    //  Map<String, Object> walletData = new HashMap<>();
                    //  walletData.put("balance", 0.0); // Default balance
                    //  initialWallet.put("wallet", walletData);
                    //  userWalletDocRef.set(initialWallet, SetOptions.merge()) // SetOptions.merge() is important
                    //      .addOnSuccessListener(aVoid -> Log.d(TAG, "Wallet initialized for user " + documentSnapshot.getId()))
                    //      .addOnFailureListener(ex -> Log.e(TAG, "Error initializing wallet", ex));
                }
            } else {
                Log.w(TAG, "Wallet document not found for user.");
                balanceTextView.setText(getString(R.string.default_balance_not_found));
                // Optionally, create the user document with a default wallet here if it's the first time
                //  Map<String, Object> initialUserData = new HashMap<>();
                //  Map<String, Object> walletData = new HashMap<>();
                //  walletData.put("balance", 0.0); // Default balance
                //  initialUserData.put("wallet", walletData);
                //  // You might want to add other default user fields here too
                //  userWalletDocRef.set(initialUserData)
                //      .addOnSuccessListener(aVoid -> Log.d(TAG, "User document with wallet initialized for user " + userWalletDocRef.getId()))
                //      .addOnFailureListener(ex -> Log.e(TAG, "Error initializing user document", ex));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove Firestore listener to prevent memory leaks and unwanted background updates
        if (walletListenerRegistration != null) {
            walletListenerRegistration.remove();
            Log.d(TAG, "Wallet SnapshotListener removed.");
        }
    }
}