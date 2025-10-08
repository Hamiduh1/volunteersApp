package com.example.volunteersApp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView; // For currency icon
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
// Import your drawable for currency icon if needed
// import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions; // For SetOptions.merge()

import java.text.NumberFormat;
import java.util.HashMap; // For creating new maps
import java.util.Locale;
import java.util.Map;

public class Wallet extends AppCompatActivity {

    private static final String TAG = "WalletActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference userDocRef; // Changed from userWalletDocRef for clarity (it's the user's doc)
    private TextView balanceTextView;
    private ImageView currencyIconImageView; // For the currency icon
    private ListenerRegistration walletListenerRegistration;

    // Default currency - could be fetched from user preferences or app config later
    private static final String DEFAULT_CURRENCY_CODE = "USD"; // Example

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.wallet_title)); // Use string resource
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        balanceTextView = findViewById(R.id.balance);
        currencyIconImageView = findViewById(R.id.currency_icon); // Initialize currency icon
        CardView transactCardView = findViewById(R.id.transact);

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // Path: users/{userId} - Assuming your collection is named "users" (case-sensitive)
            // If it's "Users" as in your original code, keep it that way.
            // It's common practice to use lowercase for collection names.
            userDocRef = db.collection("users").document(currentUser.getUid());
            setupWalletListener();
        } else {
            Log.w(TAG, "User is not signed in. Cannot fetch wallet balance.");
            Toast.makeText(this, R.string.sign_in_to_view_wallet, Toast.LENGTH_LONG).show(); // Use string resource
            balanceTextView.setText(getString(R.string.default_balance_not_signed_in));
            // Consider disabling the transact button if not signed in
            transactCardView.setEnabled(false);
            transactCardView.setAlpha(0.5f); // Visually indicate it's disabled
        }

        transactCardView.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() != null) { // Double-check before navigating
                Intent intent = new Intent(Wallet.this, TransactActivity.class);
                // You might want to pass the current balance or user ID to TransactActivity
                // intent.putExtra("USER_ID", mAuth.getCurrentUser().getUid());
                startActivity(intent);
            } else {
                Toast.makeText(Wallet.this, R.string.sign_in_to_transact, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupWalletListener() {
        if (userDocRef == null) {
            Log.e(TAG, "userDocRef is null, cannot setup wallet listener.");
            return;
        }

        if (walletListenerRegistration != null) {
            walletListenerRegistration.remove();
        }

        walletListenerRegistration = userDocRef.addSnapshotListener((documentSnapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed.", e);
                handleErrorState(getString(R.string.failed_to_load_wallet_balance));
                return;
            }

            if (documentSnapshot != null && documentSnapshot.exists()) {
                Object walletObject = documentSnapshot.get("wallet");

                if (walletObject instanceof Map) {
                    Map<String, Object> walletMap = (Map<String, Object>) walletObject;
                    Object balanceObject = walletMap.get("balance");
                    String currencyCode = (String) walletMap.get("currency");
                    if (currencyCode == null || currencyCode.isEmpty()) {
                        currencyCode = DEFAULT_CURRENCY_CODE; // Fallback to default
                    }

                    updateCurrencyIcon(currencyCode); // Update icon based on currency

                    if (balanceObject instanceof Number) {
                        double balanceValue = ((Number) balanceObject).doubleValue();
                        NumberFormat currencyFormat = getCurrencyFormatForCode(currencyCode);
                        balanceTextView.setText(currencyFormat.format(balanceValue));
                        Log.d(TAG, "Wallet balance updated: " + currencyFormat.format(balanceValue));
                    } else {
                        Log.w(TAG, "Balance data is null or not numeric in wallet map for user: " + userDocRef.getId());
                        handleErrorState(getString(R.string.default_balance_format_error));
                        // Optionally initialize if balance field is missing but wallet map exists
                        // initializeBalanceField(walletMap);
                    }
                } else {
                    Log.w(TAG, "'wallet' field is not a Map or is missing for user: " + userDocRef.getId());
                    handleErrorState(getString(R.string.default_balance_not_found));
                    initializeWalletStructure(userDocRef.getId()); // Initialize wallet structure
                }
            } else {
                Log.w(TAG, "User document not found for UID: " + userDocRef.getId() + ". Initializing document with wallet.");
                handleErrorState(getString(R.string.default_balance_not_found));
                initializeUserDocumentWithWallet(userDocRef.getId()); // Initialize user document and wallet
            }
        });
    }

    private void initializeUserDocumentWithWallet(String userId) {
        if (userDocRef == null) return;

        Map<String, Object> initialWalletData = new HashMap<>();
        initialWalletData.put("balance", 0.0);
        initialWalletData.put("currency", DEFAULT_CURRENCY_CODE);
        // Add other default wallet fields if any (e.g., lastTransactionDate: FieldValue.serverTimestamp())

        Map<String, Object> initialUserData = new HashMap<>();
        initialUserData.put("wallet", initialWalletData);
        // Important: Add other essential user fields that should exist upon creation,
        // for example, userRole, name (if available from Auth or a previous step).
        // initialUserData.put("userRole", "organizer"); // Example
        // FirebaseUser currentUser = mAuth.getCurrentUser();
        // if (currentUser != null) {
        //     initialUserData.put("email", currentUser.getEmail());
        //     if (currentUser.getDisplayName() != null) {
        //         initialUserData.put("name", currentUser.getDisplayName());
        //     }
        // }


        userDocRef.set(initialUserData, SetOptions.merge()) // Use SetOptions.merge() to be safe
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User document with wallet initialized for user: " + userId))
                .addOnFailureListener(ex -> Log.e(TAG, "Error initializing user document with wallet", ex));
    }

    private void initializeWalletStructure(String userId) {
        if (userDocRef == null) return;

        Map<String, Object> walletData = new HashMap<>();
        walletData.put("balance", 0.0);
        walletData.put("currency", DEFAULT_CURRENCY_CODE);

        Map<String, Object> update = new HashMap<>();
        update.put("wallet", walletData);

        userDocRef.set(update, SetOptions.merge()) // Use merge to add/update the wallet field without overwriting other user data
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Wallet structure initialized for user: " + userId))
                .addOnFailureListener(ex -> Log.e(TAG, "Error initializing wallet structure", ex));
    }

    // Optional: If only the balance field is missing within an existing wallet map
    private void initializeBalanceField(Map<String, Object> walletMap, String userId) {
        if (userDocRef == null || walletMap.get("balance") != null) return;

        walletMap.put("balance", 0.0);
        // If currency is also potentially missing from an existing wallet map
        if (walletMap.get("currency") == null) {
            walletMap.put("currency", DEFAULT_CURRENCY_CODE);
        }
        userDocRef.update("wallet", walletMap)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Balance field initialized in wallet for user: " + userId))
                .addOnFailureListener(ex -> Log.e(TAG, "Error initializing balance field in wallet", ex));
    }


    private void updateCurrencyIcon(String currencyCode) {
        // Simple example, you might have more sophisticated logic or specific icons
        if ("USD".equalsIgnoreCase(currencyCode)) {
            currencyIconImageView.setImageResource(R.drawable.ic_us_dollar); // Ensure this drawable exists
            currencyIconImageView.setContentDescription(getString(R.string.cd_currency_icon_usd));
        } else if ("EUR".equalsIgnoreCase(currencyCode)) {
            currencyIconImageView.setImageResource(R.drawable.ic_euro_symbol); // Create this drawable
            currencyIconImageView.setContentDescription(getString(R.string.cd_currency_icon_eur));
        } else {
            currencyIconImageView.setImageResource(R.drawable.ic_default_currency); // A generic currency icon
            currencyIconImageView.setContentDescription(getString(R.string.cd_currency_icon_default));
        }
    }

    private NumberFormat getCurrencyFormatForCode(String currencyCode) {
        // This is a simplified example. For robust multi-currency support,
        // you might need a mapping or more complex logic.
        // java.util.Currency can be used for more features.
        Locale locale;
        switch (currencyCode.toUpperCase()) {
            case "USD":
                locale = Locale.US;
                break;
            case "EUR":
                locale = Locale.GERMANY; // Or Locale.FRANCE, etc., for Euro formatting
                break;
            // Add more cases for other currencies
            default:
                locale = Locale.getDefault(); // Fallback
                break;
        }
        return NumberFormat.getCurrencyInstance(locale);
    }

    private void handleErrorState(String message) {
        balanceTextView.setText(message);
        // You might want to set a default currency icon here as well
        updateCurrencyIcon(DEFAULT_CURRENCY_CODE); // Or a specific error icon
        Toast.makeText(Wallet.this, message, Toast.LENGTH_SHORT).show();

    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Instead of finish(), consider NavUtils.navigateUpFromSameTask(this)
            // or NavController.navigateUp() if this Activity is part of a NavGraph
            // For simplicity, finish() works if it's a standalone activity launched from a main one.
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-attach listener if userDocRef is available (e.g., after onResume if user logs in)
        // However, the listener is typically setup once in onCreate if user is logged in.
        // If you allow login within this activity and then want to load, you'd call setupWalletListener() after login.
        // For now, assuming login happens before reaching this activity.
        // If the listener was removed in onStop and user is still logged in, you might re-attach:
        // if (mAuth.getCurrentUser() != null && userDocRef != null && walletListenerRegistration == null) {
        //     setupWalletListener();
        // }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // It's good practice to remove listeners in onStop and re-attach in onStart
        // to save resources when the activity is not visible,
        // though for critical data like balance, keeping it in onDestroy is also common.
        // For simplicity and to ensure data is always fresh when activity is visible,
        // we'll primarily rely on onDestroy. If you want to optimize for background,
        // move remove() to onStop and add setupWalletListener() logic in onStart.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (walletListenerRegistration != null) {
            walletListenerRegistration.remove();
            Log.d(TAG, "Wallet SnapshotListener removed in onDestroy.");
        }
    }
}
