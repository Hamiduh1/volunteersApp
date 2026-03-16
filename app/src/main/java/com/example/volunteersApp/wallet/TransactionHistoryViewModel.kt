package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransactionHistoryViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var allTransactions = emptyList<Transaction>()

    init {
        fetchHistory()
    }

    private fun fetchHistory() {
        _isLoading.value = true
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HistoryVM", "Listen failed.", error)
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                    }
                    allTransactions = list
                    _transactions.value = list
                }
                _isLoading.value = false
            }
    }

    fun filterHistory(filter: String) {
        _transactions.value = if (filter == "ALL") {
            allTransactions
        } else {
            allTransactions.filter { it.type == filter }
        }
    }
}

