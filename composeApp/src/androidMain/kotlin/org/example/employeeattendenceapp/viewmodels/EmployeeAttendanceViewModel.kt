package org.example.employeeattendenceapp.viewmodels

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalTime
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EmployeeAttendanceViewModel @Inject constructor() : ViewModel() {

    private val _attendanceMarked = MutableStateFlow(false)
    private val _attendanceMarkedTime = MutableStateFlow<LocalTime?>(null)
    private val _statusText = MutableStateFlow("Active")
    private val _withinZoneVisible = MutableStateFlow(true)
    private val _checkInTime = MutableStateFlow<LocalTime?>(null)
    private val _attendanceStatus = MutableStateFlow("Absent")
    private val _workingHours = MutableStateFlow("0h 0m 0s")
    private val _lastAttendanceDay = MutableStateFlow(LocalDate.now())
    private val _showSnackbar = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow("")
    private val _isInOfficeZone = MutableStateFlow(false)
    private val _hasInternetConnection = MutableStateFlow(true)
    private val _hasLocationServices = MutableStateFlow(true)
    private val _isTrackingActive = MutableStateFlow(false)


    val attendanceMarked: StateFlow<Boolean> = _attendanceMarked.asStateFlow()
    val attendanceMarkedTime: StateFlow<LocalTime?> = _attendanceMarkedTime.asStateFlow()
    val statusText: StateFlow<String> = _statusText.asStateFlow()
    val withinZoneVisible: StateFlow<Boolean> = _withinZoneVisible.asStateFlow()
    val checkInTime: StateFlow<LocalTime?> = _checkInTime.asStateFlow()
    val attendanceStatus: StateFlow<String> = _attendanceStatus.asStateFlow()
    val workingHours: StateFlow<String> = _workingHours.asStateFlow()
    val lastAttendanceDay: StateFlow<LocalDate> = _lastAttendanceDay.asStateFlow()
    val showSnackbar: StateFlow<Boolean> = _showSnackbar.asStateFlow()
    val snackbarMessage: StateFlow<String> = _snackbarMessage.asStateFlow()
    val isInOfficeZone: StateFlow<Boolean> = _isInOfficeZone.asStateFlow()
    val hasInternetConnection: StateFlow<Boolean> = _hasInternetConnection.asStateFlow()

    val hasLocationServices: StateFlow<Boolean> = _hasLocationServices.asStateFlow()
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
        _showSnackbar.value = true
    }

    fun hideSnackbar() {
        _showSnackbar.value = false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun markAttendance() {
        val currentTime = LocalTime.now()
        // RESET all tracking variables first
        _accumulatedWorkingHours.value = java.time.Duration.ZERO
        _lastPauseTime.value = null
        _lastCheckInTime.value = null

        // THEN set new values
        _checkInTime.value = currentTime
        _lastCheckInTime.value = currentTime
        _attendanceMarked.value = true
        _attendanceMarkedTime.value = currentTime
        _attendanceStatus.value = "Present"
        _lastAttendanceDay.value = LocalDate.now()
        _isTrackingActive.value = true
        _workingHours.value = "0h 0m 0s" // Explicitly reset to zero
    }

    fun isAttendanceMarkedToday(): Boolean {
        return _attendanceMarked.value &&
                _lastAttendanceDay.value == LocalDate.now()
    }

    private val _accumulatedWorkingHours = MutableStateFlow(java.time.Duration.ZERO)
    private val _lastPauseTime = MutableStateFlow<LocalTime?>(null)

    fun updateWorkingHours(currentTime: LocalTime) {
        if (!_isTrackingActive.value) {
            // When tracking is paused, store the pause time but don't update working hours
            if (_lastPauseTime.value == null) {
                _lastPauseTime.value = currentTime
            }
            return
        }

        // If we were paused and now resumed, add the paused duration to accumulated time
        if (_lastPauseTime.value != null) {
            val pauseDuration = java.time.Duration.between(_lastPauseTime.value, currentTime)
            _accumulatedWorkingHours.value = _accumulatedWorkingHours.value.plus(pauseDuration)
            _lastPauseTime.value = null
        }

        val checkIn = _lastCheckInTime.value
        if (checkIn == null || !_attendanceMarked.value) {
            _workingHours.value = "0h 0m 0s"
            return
        }

        if (currentTime.isBefore(checkIn)) {
            _workingHours.value = "0h 0m 0s"
            return
        }

        // Calculate only the current session duration
        val sessionDuration = java.time.Duration.between(checkIn, currentTime)

        // Total duration = accumulated time + current session
        val totalDuration = _accumulatedWorkingHours.value.plus(sessionDuration)

        val hours = totalDuration.toHours()
        val minutes = totalDuration.toMinutes() % 60
        val seconds = totalDuration.seconds % 60

        _workingHours.value = "${hours}h ${minutes}m ${seconds}s"
    }


    private fun updateTrackingStatus() {
        val shouldTrack = _isInOfficeZone.value &&
                _hasInternetConnection.value &&
                _hasLocationServices.value &&
                _attendanceMarked.value

        handleTrackingStateChange(shouldTrack)
    }


    fun resetForNewDay() {
        _checkInTime.value = null
        _lastCheckInTime.value = null
        _attendanceMarked.value = false
        _attendanceMarkedTime.value = null
        _attendanceStatus.value = "Absent"
        _workingHours.value = "0h 0m 0s"
        _statusText.value = "--"
        _withinZoneVisible.value = false
        _accumulatedWorkingHours.value = java.time.Duration.ZERO
        _lastPauseTime.value = null
        _isTrackingActive.value = false
    }

    private fun calculateWorkingHours(currentTime: LocalTime, isInOfficeZone: Boolean): String {
        val checkIn = _checkInTime.value
        if (checkIn == null || !_attendanceMarked.value) {
            return "0h 0m 0s"
        }

        // Ensure current time is after check-in time to avoid negative duration
        if (currentTime.isBefore(checkIn)) {
            return "0h 0m 0s"
        }

        val duration = java.time.Duration.between(checkIn, currentTime)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return "${hours}h ${minutes}m ${seconds}s"
    }

    fun resumeTracking() {
        if (_isInOfficeZone.value && _hasInternetConnection.value &&
            _hasLocationServices.value && _attendanceMarked.value) {
            handleTrackingStateChange(true)
        }
    }



    fun updateOfficeZoneStatus(inOfficeZone: Boolean) {
        _isInOfficeZone.value = inOfficeZone
        updateTrackingStatus()
    }

    fun updateInternetStatus(connected: Boolean) {
        _hasInternetConnection.value = connected
        updateTrackingStatus()
    }

    fun updateLocationServicesStatus(enabled: Boolean) {
        _hasLocationServices.value = enabled
        updateTrackingStatus()
    }

    private val _lastCheckInTime = MutableStateFlow<LocalTime?>(null)

    fun setStatusActive() { _statusText.value = "Active" }
    fun setStatusPresent() { _statusText.value = "Present" }
    fun setStatusAbsent() { _statusText.value = "Absent" }
    fun setStatusDash() { _statusText.value = "--" }
    fun resetZoneVisibility() { _withinZoneVisible.value = false }
    fun setLocationEnabled(enabled: Boolean) {
        _hasLocationServices.value = enabled
        updateTrackingStatus()
    }
    fun setInternetConnected(connected: Boolean) {
        _hasInternetConnection.value = connected
        updateTrackingStatus()
    }
    fun setWorkingHours(hours: String) {
        _workingHours.value = hours
    }

    private fun handleTrackingStateChange(shouldTrack: Boolean) {
        val wasTracking = _isTrackingActive.value
        val currentTime = LocalTime.now()

        if (wasTracking && !shouldTrack) {
            // Tracking just stopped - record the pause time and accumulate current session
            val checkIn = _lastCheckInTime.value ?: _checkInTime.value
            if (checkIn != null) {
                val sessionDuration = java.time.Duration.between(checkIn, currentTime)
                _accumulatedWorkingHours.value = _accumulatedWorkingHours.value.plus(sessionDuration)
                _lastPauseTime.value = currentTime
            }
            showSnackbar("Tracking paused. Working hours stopped.")
        } else if (!wasTracking && shouldTrack) {
            // Tracking just resumed - continue from accumulated time, don't reset
            _lastCheckInTime.value = currentTime // Start new session from now
            _lastPauseTime.value = null
            showSnackbar("Tracking resumed. Working hours updated.")
        }

        _isTrackingActive.value = shouldTrack
    }

}