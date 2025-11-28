package org.example.employeeattendenceapp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.Composable // only if supported by JetBrains Compose Multiplatform
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

class EmployeeAttendanceState {
    private val _statusText = MutableStateFlow("Active")
    val statusText: StateFlow<String> = _statusText

    private val _markAttendanceEnabled = MutableStateFlow(true)
    val markAttendanceEnabled: StateFlow<Boolean> = _markAttendanceEnabled

    private val _withinZoneVisible = MutableStateFlow(true)
    val withinZoneVisible: StateFlow<Boolean> = _withinZoneVisible

    // New properties for attendance tracking
    private val _checkInTime = MutableStateFlow<LocalTime?>(null)
    val checkInTime: StateFlow<LocalTime?> = _checkInTime

    private val _attendanceStatus = MutableStateFlow<String>("Absent")
    val attendanceStatus: StateFlow<String> = _attendanceStatus

    private val _lastAttendanceDate = MutableStateFlow<LocalDate?>(null)
    val lastAttendanceDate: StateFlow<LocalDate?> = _lastAttendanceDate

    private val _attendanceMarkedTime = MutableStateFlow<String?>(null)
    val attendanceMarkedTime: StateFlow<String?> = _attendanceMarkedTime

    // Working hours tracking
    private val _checkInTimeStamp = MutableStateFlow<LocalTime?>(null)
    val checkInTimeStamp: StateFlow<LocalTime?> = _checkInTimeStamp

    private val _workingHours = MutableStateFlow("0h 0m 0s")
    val workingHours: StateFlow<String> = _workingHours

    // New: Accumulated working duration and last zone entry time
    private var totalWorkingDuration: Duration = Duration.ZERO
    private var lastZoneEntryTime: LocalTime? = null
    private var wasInOfficeZone: Boolean = false

    private val _isInternetConnected = MutableStateFlow(true)
    private val _lastWorkingTime = MutableStateFlow<LocalTime?>(null)
    private val _totalPausedDuration = MutableStateFlow(Duration.ZERO)

    private val _isLocationEnabled = MutableStateFlow(true)
    private val _lastLocationTime = MutableStateFlow<LocalTime?>(null)
    private val _totalLocationPausedDuration = MutableStateFlow(Duration.ZERO)

    fun setLocationEnabled(enabled: Boolean) {
        val wasEnabled = _isLocationEnabled.value
        _isLocationEnabled.value = enabled

        if (wasEnabled && !enabled) {
            // Location just went offline - pause counter
            _lastLocationTime.value = LocalTime.now()
        } else if (!wasEnabled && enabled) {
            // Location just came back online - resume counter
            _lastLocationTime.value?.let { lastTime ->
                val pauseDuration = Duration.between(lastTime, LocalTime.now())
                _totalLocationPausedDuration.value = _totalLocationPausedDuration.value.plus(pauseDuration)
                _lastLocationTime.value = null
            }
        }
    }

    // handle internet connectivity changes
    fun setInternetConnected(connected: Boolean) {
        val wasConnected = _isInternetConnected.value
        _isInternetConnected.value = connected

        if (wasConnected && !connected) {
            // Internet just went offline - pause counter
            _lastWorkingTime.value = LocalTime.now()
        } else if (!wasConnected && connected) {
            // Internet just came back online - resume counter
            _lastWorkingTime.value?.let { lastTime ->
                val pauseDuration = Duration.between(lastTime, LocalTime.now())
                _totalPausedDuration.value = _totalPausedDuration.value.plus(pauseDuration)
                _lastWorkingTime.value = null
            }
        }
    }

    fun markAttendance() {
        _checkInTime.value = LocalTime.now()
        _attendanceStatus.value = "Present"
        _lastAttendanceDate.value = LocalDate.now()
        _attendanceMarkedTime.value = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US))
    }

    suspend fun updateWorkingHours(currentTime: LocalTime, isInOfficeZone: Boolean) {
        if (isAttendanceMarkedToday() && _isInternetConnected.value && _isLocationEnabled.value) {
            val checkIn = _checkInTime.value ?: return

            // Calculate total elapsed time since check-in
            val totalElapsedDuration = Duration.between(checkIn, currentTime)

            // Subtract both internet and location paused durations
            val actualWorkingDuration = totalElapsedDuration
                .minus(_totalPausedDuration.value)
                .minus(_totalLocationPausedDuration.value)

            val hours = actualWorkingDuration.toHours()
            val minutes = actualWorkingDuration.toMinutes() % 60
            val seconds = actualWorkingDuration.seconds % 60

            _workingHours.value = "${hours}h ${minutes}m ${seconds}s"
        }
    }

    fun resetZoneVisibility() {
        _withinZoneVisible.value = true
    }

    fun setStatusActive() {
        _statusText.value = "Active"
    }

    fun setStatusDash() {
        _statusText.value = "--"
    }

    fun setStatusAbsent() {
        if (_lastAttendanceDate.value != LocalDate.now()) {
            _attendanceStatus.value = "Absent"
        }
    }

    fun setStatusPresent() {
        _attendanceStatus.value = "Present"
    }

    fun isAttendanceMarkedToday(): Boolean {
        return _lastAttendanceDate.value == LocalDate.now()
    }

    fun resetForNewDay() {
        _checkInTime.value = null
        _checkInTimeStamp.value = null
        _attendanceStatus.value = "Absent"
        _attendanceMarkedTime.value = null
        _workingHours.value = "0h 0m 0s"
        _statusText.value = "--"
        _markAttendanceEnabled.value = true
        _withinZoneVisible.value = true
        _lastAttendanceDate.value = null
        totalWorkingDuration = Duration.ZERO
        lastZoneEntryTime = null
        wasInOfficeZone = false
        _totalPausedDuration.value = Duration.ZERO
        _lastWorkingTime.value = null
        _totalLocationPausedDuration.value = Duration.ZERO
        _lastLocationTime.value = null
        _isLocationEnabled.value = true
    }
}

@Composable
expect fun HomeScreenEmployee(justLoggedIn: Boolean = false)

