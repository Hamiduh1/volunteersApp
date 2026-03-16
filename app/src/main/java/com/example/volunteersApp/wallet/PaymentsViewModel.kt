package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PaymentsViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _cards = MutableStateFlow<List<PaymentMethod>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        fetchPaymentMethods()
    }

    /**
     * UPDATED: Real-time listener now supports all PaymentMethod types.
     */
    fun fetchPaymentMethods() {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true

        db.collection("users").document(uid).collection("payment_methods")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PaymentsVM", "Listen failed", error)
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    when (doc.getString("type")) {
                        "BANK" -> doc.toObject(PaymentMethod.BankAccount::class.java)
                        // ADDED: Logic to handle the MobileMoney class
                        "MOBILE_MONEY" -> doc.toObject(PaymentMethod.MobileMoney::class.java)
                        "CARD" -> doc.toObject(PaymentMethod.CreditCard::class.java)
                        else -> doc.toObject(PaymentMethod.CreditCard::class.java) // Default fallback
                    }
                } ?: emptyList()

                _cards.value = list
                _isLoading.value = false
            }
    }

    /**
     * NEW: Adds a Mobile Money account as a payment method.
     */
    fun addMobileMoneyAccount(phone: String, network: String, registeredName: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val newMobileMoney = hashMapOf(
                    "type" to "MOBILE_MONEY",
                    "label" to "$network ($phone)",
                    "phoneNumber" to phone,
                    "network" to network,
                    "registeredName" to registeredName,
                    "isDefault" to _cards.value.isEmpty()
                )

                db.collection("users").document(uid).collection("payment_methods")
                    .add(newMobileMoney).await()
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Add mobile money failed", e)
            }
        }
    }

    fun addCard(name: String, number: String, expiry: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val lastFour = if (number.length >= 4) number.takeLast(4) else number
                val newCard = hashMapOf(
                    "type" to "CARD",
                    "label" to "Card ending in $lastFour",
                    "cardHolderName" to name,
                    "cardNumber" to "**** **** **** $lastFour",
                    "expiryDate" to expiry,
                    "cardType" to "VISA",
                    "isDefault" to _cards.value.isEmpty()
                )

                db.collection("users").document(uid).collection("payment_methods")
                    .add(newCard).await()
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Add card failed", e)
            }
        }
    }

    fun addBankAccount(bankName: String, accountHolder: String, accountNumber: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val lastFour = if (accountNumber.length >= 4) accountNumber.takeLast(4) else accountNumber
                val newBank = hashMapOf(
                    "type" to "BANK",
                    "label" to bankName,
                    "bankName" to bankName,
                    "accountHolderName" to accountHolder,
                    "accountNumber" to "********$lastFour",
                    "isDefault" to false
                )

                db.collection("users").document(uid).collection("payment_methods")
                    .add(newBank).await()
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Add bank failed", e)
            }
        }
    }

    fun deleteCard(methodId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .collection("payment_methods").document(methodId)
                    .delete().await()
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Delete failed", e)
            }
        }
    }

    fun setAsDefault(methodId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val ref = db.collection("users").document(uid).collection("payment_methods")

                _cards.value.forEach { method ->
                    val docRef = ref.document(method.id)
                    batch.update(docRef, "isDefault", method.id == methodId)
                }

                batch.commit().await()
            } catch (e: Exception) {
                Log.e("PaymentsVM", "Default update failed", e)
            }
        }
    }
}
