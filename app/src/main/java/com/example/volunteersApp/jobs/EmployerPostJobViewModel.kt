package com.example.volunteersApp.jobs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.volunteersApp.models.Resource
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class EmployerPostJobUiState(
    val organizationName: String = "",
    val opportunityTitle: String = "",
    val jobTitle: String = "",
    val description: String = "",
    val jobDateTime: Calendar? = null,
    val dateInput: String = "", // For manual date entry
    val timeInput: String = "", // For manual time entry
    val location: String = "",
    val category: String = "",
    val volunteersNeeded: String = "",
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val postingId: String? = null,
    val currentStatus: String = "open",
    val jobCategories: List<String> = listOf("Technology", "Health", "Education", "Environment", "Community", "Animals", "Arts & Culture", "Seniors", "Other")
)

class EmployerPostJobViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val TAG = "EmployerPostJobVM"

    private val _uiState = MutableStateFlow(EmployerPostJobUiState())
    val uiState = _uiState.asStateFlow()

    private val _actionResult = MutableSharedFlow<Resource<String>>()
    val actionResult = _actionResult.asSharedFlow()

    // --- State Updaters ---
    fun updateOrganizationName(value: String) { _uiState.update { it.copy(organizationName = value) } }
    fun updateOpportunityTitle(value: String) { _uiState.update { it.copy(opportunityTitle = value) } }
    fun updateJobTitle(value: String) { _uiState.update { it.copy(jobTitle = value) } }
    fun updateDescription(value: String) { _uiState.update { it.copy(description = value) } }
    fun updateLocation(value: String) { _uiState.update { it.copy(location = value) } }
    fun updateCategory(value: String) { _uiState.update { it.copy(category = value) } }
    fun updateVolunteersNeeded(value: String) { _uiState.update { it.copy(volunteersNeeded = value) } }

    // Manual input handlers
    fun onDateInputChanged(input: String) {
        _uiState.update { it.copy(dateInput = input) }
    }
    fun onTimeInputChanged(input: String) {
        _uiState.update { it.copy(timeInput = input) }
    }

    // Picker handlers
    fun updateJobDate(millis: Long?) {
        millis ?: return
        val newCalendar = (_uiState.value.jobDateTime?.clone() as? Calendar) ?: Calendar.getInstance()
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = millis

        newCalendar.set(
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH),
            utcCalendar.get(Calendar.DAY_OF_MONTH)
        )
        
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        _uiState.update { it.copy(jobDateTime = newCalendar, dateInput = dateFormat.format(newCalendar.time)) }
    }

    fun updateJobTime(hour: Int, minute: Int) {
        val newCalendar = (_uiState.value.jobDateTime?.clone() as? Calendar) ?: Calendar.getInstance()
        newCalendar.set(Calendar.HOUR_OF_DAY, hour)
        newCalendar.set(Calendar.MINUTE, minute)
        
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
        _uiState.update { it.copy(jobDateTime = newCalendar, timeInput = timeFormat.format(newCalendar.time)) }
    }

    fun loadJobForEdit(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditMode = true, postingId = id) }
            try {
                val doc = db.collection("jobs").document(id).get().await()
                val job = doc.toObject(JobPosting::class.java)
                job?.let { j ->
                    val parsedCalendar = if (j.date != null && j.time != null) {
                        try {
                            SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).parse("${j.date} ${j.time}")?.let { date ->
                                Calendar.getInstance().apply { time = date }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not parse date and time from Firestore: ${j.date} ${j.time}", e)
                            null
                        }
                    } else {
                        null
                    }

                    _uiState.update { it.copy(
                        organizationName = j.organizationName ?: "",
                        opportunityTitle = j.title ?: "",
                        jobTitle = j.jobTitle ?: "",
                        description = j.description ?: "",
                        jobDateTime = parsedCalendar,
                        dateInput = j.date ?: "",
                        timeInput = j.time ?: "",
                        location = j.locationName ?: "",
                        category = j.category ?: "",
                        volunteersNeeded = j.volunteersNeeded.toString(),
                        currentStatus = j.status ?: "open",
                        isLoading = false
                    )}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load job", e)
                _uiState.update { it.copy(isLoading = false) }
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Failed to load job"))
            }
        }
    }

    fun submitJob() {
        val state = _uiState.value
        val userId = auth.currentUser?.uid ?: return

        if (state.opportunityTitle.isBlank() || state.description.isBlank() || state.dateInput.isBlank() || state.timeInput.isBlank() ||
            state.location.isBlank() || state.category.isBlank() || state.volunteersNeeded.isBlank()) {
            viewModelScope.launch { _actionResult.emit(Resource.Error("Please fill all required fields")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Validate date/time format before submission
                val combinedDateTimeString = "${state.dateInput} ${state.timeInput}"
                val dateTimeFormat = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).apply { isLenient = false }
                try {
                    dateTimeFormat.parse(combinedDateTimeString)
                } catch (e: Exception) {
                    _actionResult.emit(Resource.Error("Invalid date or time. Use MM/dd/yyyy and hh:mm a."))
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val jobPosting = JobPosting(
                    employerUid = userId,
                    organizationName = state.organizationName.ifBlank { auth.currentUser?.displayName ?: "" },
                    title = state.opportunityTitle,
                    jobTitle = state.jobTitle,
                    description = state.description,
                    date = state.dateInput,
                    time = state.timeInput,
                    locationName = state.location,
                    category = state.category,
                    volunteersNeeded = state.volunteersNeeded.toIntOrNull() ?: 0,
                    status = state.currentStatus
                )

                val collectionRef = db.collection("jobs")
                if (state.isEditMode && state.postingId != null) {
                    collectionRef.document(state.postingId)
                        .set(jobPosting, SetOptions.merge()).await()
                    _actionResult.emit(Resource.Success("Job/Opportunity updated successfully!"))
                } else {
                    val newJobRef = collectionRef.document()
                    val finalJob = jobPosting.copy(postingId = newJobRef.id)
                    newJobRef.set(finalJob).await()
                    _actionResult.emit(Resource.Success("Job/Opportunity posted successfully!"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post job", e)
                _actionResult.emit(Resource.Error(e.localizedMessage ?: "Failed to post job"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
