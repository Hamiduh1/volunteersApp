package com.example.volunteersApp.events

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.firebase.CallableFunction
import com.example.volunteersApp.firebase.FunctionsClient
import com.example.volunteersApp.jobs.JobDetailViewModel.ApplicationStatus
import com.example.volunteersApp.models.EventModel
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.volunteersApp.firebase.FirestoreCollection
import com.example.volunteersApp.firebase.FirestoreSubcollection
import com.example.volunteersApp.wallet.isPendingCommercePaymentStatus
import com.example.volunteersApp.wallet.normalizeCommercePaymentStatus
import com.example.volunteersApp.wallet.parseProviderCollectionOutcome

data class EventDetailUiState(
    val event: EventModel? = null,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val error: String? = null,
    val isOrganizer: Boolean = false,
    val applicationStatus: ApplicationStatus = ApplicationStatus.UNKNOWN,
    val paymentCollectionStatus: String? = null,
    val isPaymentCollectionPending: Boolean = false,
    val paymentCollectionDetail: String? = null,
    val checkoutUrl: String? = null
)

class EventDetailViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "EventDetailViewModel"

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<Unit>>()
    val actionResult = _actionResult.asSharedFlow()

    fun loadEventDetails(eventId: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.update { it.copy(error = "User not authenticated.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val eventDocDeferred = db.collection(FirestoreCollection.EVENTS).document(eventId).get()
                val applicationDocDeferred = db.collection(FirestoreCollection.EVENTS).document(eventId)
                    .collection(FirestoreSubcollection.APPLICATIONS).document(userId).get()

                val eventDoc = eventDocDeferred.await()
                val applicationDoc = applicationDocDeferred.await()

                val event = eventDoc.toObject(EventModel::class.java)
                if (event == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Event not found.") }
                    return@launch
                }

                val isOrganizer = event.organizerId == userId
                val paymentStatus = normalizeCommercePaymentStatus(
                    applicationDoc.getString("paymentStatus")
                        ?: applicationDoc.getString("paymentCollectionStatus")
                        ?: applicationDoc.getString("collectionStatus")
                )
                val paymentPending = isPendingCommercePaymentStatus(paymentStatus)
                val paymentDetail = applicationDoc.getString("paymentDetail")
                    ?: applicationDoc.getString("paymentMessage")

                val status = if (applicationDoc.exists()) {
                    when (applicationDoc.getString("status")?.lowercase()) {
                        "pending", "pending_payment" -> ApplicationStatus.APPLIED_PENDING
                        "approved", "accepted" -> ApplicationStatus.APPROVED
                        "rejected" -> ApplicationStatus.REJECTED
                        else -> ApplicationStatus.UNKNOWN
                    }
                } else if (event.closeEntries) {
                    ApplicationStatus.JOB_CLOSED
                } else {
                    ApplicationStatus.CAN_APPLY
                }

                _uiState.update {
                    it.copy(
                        event = event,
                        isOrganizer = isOrganizer,
                        applicationStatus = status,
                        paymentCollectionStatus = paymentStatus,
                        isPaymentCollectionPending = paymentPending,
                        paymentCollectionDetail = paymentDetail,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching event details", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun applyForEvent(eventId: String) {
        if (auth.currentUser == null) return
        val currentState = _uiState.value
        val event = currentState.event ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                val result = FunctionsClient.callMap(
                    CallableFunction.APPLY_FOR_EVENT,
                    mapOf("eventId" to eventId)
                )
                val outcome = parseProviderCollectionOutcome(result)
                val success = result?.get("success") as? Boolean ?: outcome.isAccessUnlocked
                val checkoutUrl = result?.get("checkoutUrl") as? String
                if (!success && !outcome.isPending) {
                    throw IllegalStateException(
                        outcome.message.ifBlank { "Event signup failed. Please try again." }
                    )
                }

                val message = when {
                    event.eventFee <= 0 -> "Application submitted."
                    outcome.isAccessUnlocked -> outcome.message.ifBlank { "Payment confirmed. Application submitted." }
                    outcome.isPending -> outcome.message.ifBlank {
                        "Provider payment is processing. Your application unlocks after confirmation."
                    }
                    else -> outcome.message.ifBlank { "Application submitted." }
                }
                _actionResult.emit(Resource.Success(Unit))
                if (outcome.isPending || !checkoutUrl.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isPaymentCollectionPending = true,
                            paymentCollectionStatus = outcome.paymentStatus ?: "pending",
                            paymentCollectionDetail = message,
                            checkoutUrl = checkoutUrl
                        )
                    }
                }
                loadEventDetails(eventId)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply for event", e)
                _actionResult.emit(Resource.Error("Failed to apply: ${e.localizedMessage}"))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }

    fun consumeCheckoutUrl() {
        _uiState.update { it.copy(checkoutUrl = null) }
    }

    fun updateEntryStatus(eventId: String, closeEntries: Boolean) {
        if (!_uiState.value.isOrganizer) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            try {
                db.collection(FirestoreCollection.EVENTS).document(eventId)
                    .update("closeEntries", closeEntries)
                    .await()

                _uiState.update {
                    it.copy(event = it.event?.copy(closeEntries = closeEntries))
                }
                _actionResult.emit(Resource.Success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update entry status", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Update failed."))
            } finally {
                _uiState.update { it.copy(isActionLoading = false) }
            }
        }
    }
}
