package com.example.volunteersApp.wallet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection

class TransactionHistoryViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val historyQueryBuilders: List<(String) -> Query> = listOf(
        { uid ->
            db.collection(FirestoreCollection.USERS).document(uid).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        },
        { uid ->
            db.collection(FirestoreCollection.USERS).document(uid).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        },
        { uid ->
            db.collection(FirestoreCollection.USERS).document(uid).collection(FirestoreSubcollection.TRANSACTIONS)
                .orderBy("lastUpdatedAt", Query.Direction.DESCENDING)
        },
        { uid -> db.collection(FirestoreCollection.USERS).document(uid).collection(FirestoreSubcollection.TRANSACTIONS) }
    )

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var allTransactions = emptyList<Transaction>()
    private var historyListener: ListenerRegistration? = null

    init {
        fetchHistory()
    }

    private fun fetchHistory() {
        _isLoading.value = true
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _transactions.value = emptyList()
            _isLoading.value = false
            return
        }
        historyListener?.remove()
        attachHistoryListener(uid = uid, queryIndex = 0)
    }

    private fun attachHistoryListener(uid: String, queryIndex: Int) {
        if (queryIndex > historyQueryBuilders.lastIndex) {
            _isLoading.value = false
            return
        }
        val query = historyQueryBuilders[queryIndex](uid)
        historyListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (queryIndex < historyQueryBuilders.lastIndex) {
                    Log.w(
                        "HistoryVM",
                        "Transaction history query fallback ${queryIndex + 1}/${historyQueryBuilders.size} failed; trying next.",
                        error
                    )
                    historyListener?.remove()
                    attachHistoryListener(uid = uid, queryIndex = queryIndex + 1)
                    return@addSnapshotListener
                }
                Log.e("HistoryVM", "Listen failed.", error)
                _isLoading.value = false
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                }
                allTransactions = list
                _transactions.value = consolidateTransferActivity(list)
            }
            _isLoading.value = false
        }
    }

    fun refresh() {
        fetchHistory()
    }

    fun filterHistory(filter: String) {
        val base = consolidateTransferActivity(allTransactions)
        _transactions.value = if (filter == "ALL") {
            base
        } else {
            base.filter { it.type == filter }
        }
    }

    override fun onCleared() {
        historyListener?.remove()
        historyListener = null
        super.onCleared()
    }
}

