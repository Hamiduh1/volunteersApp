package com.example.volunteersApp.general

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for the Calender screen.
 * Handles parsing the initial date and maintaining the selected date state.
 */
class CalenderViewModel : ViewModel() {
    private val _selectedDateMillis = MutableStateFlow<Long?>(null)
    val selectedDateMillis: StateFlow<Long?> = _selectedDateMillis.asStateFlow()

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun setInitialDate(dateStr: String?) {
        if (dateStr.isNullOrBlank()) {
            _selectedDateMillis.value = System.currentTimeMillis()
            return
        }
        try {
            val date = sdf.parse(dateStr)
            _selectedDateMillis.value = date?.time
        } catch (e: Exception) {
            _selectedDateMillis.value = System.currentTimeMillis()
        }
    }

    fun onDateSelected(millis: Long?) {
        _selectedDateMillis.value = millis
    }

    fun getFormattedSelectedDate(): String {
        val millis = _selectedDateMillis.value ?: return ""
        return sdf.format(Date(millis))
    }
}
