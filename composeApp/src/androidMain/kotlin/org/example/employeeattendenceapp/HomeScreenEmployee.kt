package org.example.employeeattendenceapp

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.employeeattendenceapp.Auth.clearUserRole
import org.example.employeeattendenceapp.Auth.signOut
import org.example.employeeattendenceapp.service.AppBackgroundService
import org.example.employeeattendenceapp.ui.employee.TaskEmployeeViewModel
import org.example.employeeattendenceapp.ui.employee.components.EmployeeTaskView
import org.example.employeeattendenceapp.viewmodels.EmployeeAttendanceViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun HomeScreenEmployee(justLoggedIn: Boolean) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Use ViewModel instead of local state
    val attendanceViewModel: EmployeeAttendanceViewModel = hiltViewModel()

    // Collect state from ViewModel
    val statusText by attendanceViewModel.statusText.collectAsState()
    val withinZoneVisible by attendanceViewModel.withinZoneVisible.collectAsState()
    val checkInTime by attendanceViewModel.checkInTime.collectAsState()
    val attendanceStatus by attendanceViewModel.attendanceStatus.collectAsState()
    val workingHours by attendanceViewModel.workingHours.collectAsState()
    val isAttendanceMarkedToday by attendanceViewModel.attendanceMarked.collectAsState()
    val attendanceMarkedTime by attendanceViewModel.attendanceMarkedTime.collectAsState()
    val showSnackbar by attendanceViewModel.showSnackbar.collectAsState()
    val snackbarMessage by attendanceViewModel.snackbarMessage.collectAsState()
    val isTrackingActive by attendanceViewModel.isTrackingActive.collectAsState()

    // Handle snackbar display
    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            snackbarHostState.showSnackbar(snackbarMessage)
            attendanceViewModel.hideSnackbar()
        }
    }

    // Request location permission
    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    // State to control permission UI
    var showLocationSettingsDialog by remember { mutableStateOf(false) }

    val taskViewModel: TaskEmployeeViewModel = hiltViewModel()
    val employeeId = FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")?.lowercase() ?: ""

    val consistentEmployeeId = remember {
        FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")?.lowercase() ?: ""
    }

    LaunchedEffect(Unit) {
        taskViewModel.loadTasksForEmployee(consistentEmployeeId)
    }

    if (showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            title = { Text("Enable Location Services") },
            text = { Text("Location services are required for attendance tracking. Please enable location services.") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    showLocationSettingsDialog = false
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Request foreground location first, then background if needed
    val foregroundLocationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    val foregroundServicePermission = rememberPermissionState(
        Manifest.permission.FOREGROUND_SERVICE_LOCATION
    )

    // State to control permission UI
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Function to check location services and start tracking
    fun checkLocationServicesAndStart(context: Context) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showLocationSettingsDialog = true
        } else {
            LocationTrackingService.startService(context)
        }
    }

    // Start background service when composable launches
    LaunchedEffect(Unit) {
        AppBackgroundService.startService(context)
    }

    // Check permissions when composable launches
    LaunchedEffect(Unit) {
        if (!locationPermissionState.status.isGranted) {
            showPermissionRationale = true
        } else {
            checkLocationServicesAndStart(context)
        }
    }

    // Handle permission results
    LaunchedEffect(locationPermissionState.status) {
        when {
            locationPermissionState.status.isGranted -> {
                checkLocationServicesAndStart(context)
                showPermissionRationale = false
            }
            locationPermissionState.status is PermissionStatus.Denied -> {
                showPermissionRationale = true
            }
        }
    }

    // Show permission rationale UI if needed
    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onRequestPermission = {
                locationPermissionState.launchPermissionRequest()
            },
            onDismiss = {
                showPermissionRationale = false
            }
        )
        return
    }

    // Check permissions when composable launches
    LaunchedEffect(Unit) {
        if (!foregroundLocationPermission.status.isGranted) {
            showPermissionRationale = true
        }
    }

    // Handle permission results
    LaunchedEffect(foregroundLocationPermission.status) {
        when {
            foregroundLocationPermission.status.isGranted -> {
                // Got foreground location, now check foreground service permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !foregroundServicePermission.status.isGranted) {
                    foregroundServicePermission.launchPermissionRequest()
                } else {
                    // All required permissions granted
                    LocationTrackingService.startService(context)
                    showPermissionRationale = false
                }
            }
            foregroundLocationPermission.status is PermissionStatus.Denied -> {
                showPermissionRationale = true
            }
        }
    }

    // Show permission rationale UI if needed
    if (showPermissionRationale) {
        PermissionRationaleDialog(
            onRequestPermission = {
                // Request foreground location first
                foregroundLocationPermission.launchPermissionRequest()
            },
            onDismiss = {
                // Optional: handle user dismissing without granting
                showPermissionRationale = false
            }
        )
        return
    }

    // Use business logic state from commonMain
    val attendanceState = remember { EmployeeAttendanceState() }

    // Location state
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Only fetch location if permission is granted
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationRequest = remember {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
            setMinUpdateIntervalMillis(500)
        }.build()
    }

    // Show last known location immediately if available
    LaunchedEffect(Unit) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    locationError = null
                }
            }.addOnFailureListener {
                locationError = "Failed to get location"
            }
        } catch (e: SecurityException) {
            locationError = "Location permission not granted"
        }
    }

    // Start real-time location updates
    DisposableEffect(Unit) {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    locationError = null
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            locationError = "Location permission not granted"
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    // Helper to determine if we have a valid location
    val hasLocation = latitude != null && longitude != null && locationError == null

    // State to track if location services are enabled
    var locationServicesEnabled by remember { mutableStateOf(true) }

    // State to track if internet connectivity is available
    var internetConnected by remember { mutableStateOf(true) }

    LaunchedEffect(internetConnected) {
        attendanceViewModel.setInternetConnected(internetConnected)
        attendanceViewModel.updateInternetStatus(internetConnected)
    }


    // Helper to check if location services are enabled
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    // Helper to check if internet is connected
    fun isInternetConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    // Listen for location services changes
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                locationServicesEnabled = isLocationEnabled(context!!)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)

        // Set initial state
        locationServicesEnabled = isLocationEnabled(context)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Listen for internet connectivity changes
    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                internetConnected = true
            }

            override fun onLost(network: Network) {
                internetConnected = false
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Set initial state
        internetConnected = isInternetConnected(context)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // Clear location if services are off
    LaunchedEffect(locationServicesEnabled) {
        attendanceViewModel.setLocationEnabled(locationServicesEnabled)
        attendanceViewModel.updateLocationServicesStatus(locationServicesEnabled)
    }

    // Clear location if internet is off
    LaunchedEffect(internetConnected) {
        if (!internetConnected) {
            latitude = null
            longitude = null
            locationError = "No internet connection"
        }
    }

    // Show snackbar if just logged in
    if (justLoggedIn) {
        LaunchedEffect(justLoggedIn) {
            delay(300)
            attendanceViewModel.showSnackbar("Logged in successfully!")
        }
    }

    // Current time tracking
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000L)
        }
    }

    // Office hours and location
    val officeStartTime = LocalTime.of(9, 0)
    val officeEndTime = LocalTime.of(18, 0)
    val isOfficeTime = !now.isBefore(officeStartTime) && !now.isAfter(officeEndTime)
    val officeLat = 13.0175493 /*28.556180*/
    val officeLon = 77.6301157 /*77.442370*/

    // Helper to calculate distance between two lat/lon points
    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }

    // State: is user in office zone?
    val isInOfficeZone = hasLocation && distanceBetween(latitude!!, longitude!!, officeLat, officeLon) <= 100

    // Show loading spinner if user is near the office zone boundary
    val isNearOfficeZone = hasLocation && distanceBetween(latitude!!, longitude!!, officeLat, officeLon) in 10.0..20.0

    var isRefreshing by remember { mutableStateOf(false) }
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Track the last day when attendance was marked
    val lastAttendanceDay by attendanceViewModel.lastAttendanceDay.collectAsState()

    LaunchedEffect(now) {
        val today = LocalDate.now()
        if (today != lastAttendanceDay) {
            attendanceViewModel.resetForNewDay()
        }
    }

    // Update working hours
    LaunchedEffect(now, isTrackingActive) {
        if (isTrackingActive) {
            attendanceViewModel.updateWorkingHours(now)
        } else {
            // Even when not tracking, we need to update to handle pause/resume logic
            attendanceViewModel.updateWorkingHours(now)
        }
    }

    // Real-time status update
    LaunchedEffect(isInOfficeZone, isOfficeTime, internetConnected, locationServicesEnabled, isTrackingActive) {
        attendanceViewModel.updateOfficeZoneStatus(isInOfficeZone)
        attendanceViewModel.updateInternetStatus(internetConnected)
        attendanceViewModel.updateLocationServicesStatus(locationServicesEnabled)

        when {
            !internetConnected -> attendanceViewModel.setStatusDash()
            !locationServicesEnabled -> attendanceViewModel.setStatusDash()
            attendanceViewModel.isAttendanceMarkedToday() -> {
                attendanceViewModel.setStatusPresent()
                if (isInOfficeZone && isOfficeTime && isTrackingActive) {
                    attendanceViewModel.setStatusActive()
                } else {
                    attendanceViewModel.setStatusDash()
                }
            }
            !isOfficeTime -> {
                attendanceViewModel.setStatusAbsent()
                attendanceViewModel.setStatusDash()
            }
            isInOfficeZone && isOfficeTime && isTrackingActive -> attendanceViewModel.setStatusActive()
            else -> attendanceViewModel.setStatusDash()
        }
    }

    LaunchedEffect(isAttendanceMarkedToday) {
        if (isAttendanceMarkedToday) {
            // Small delay to ensure Firebase has processed the attendance
            delay(1000)
            taskViewModel.loadTasksForEmployee(consistentEmployeeId)
        }
    }

    LaunchedEffect(isTrackingActive) {
        if (isTrackingActive && isAttendanceMarkedToday) {
            delay(500) // Small delay for stability
            taskViewModel.loadTasksForEmployee(consistentEmployeeId)
        }
    }

    // Real-time database sync for attendance
    val userEmail = FirebaseAuth.getInstance().currentUser?.email
    val userName = userEmail?.substringBefore("@") ?: "Employee"
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val currentDate = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
    val formattedDate = currentDate.format(dateFormatter)
    val formattedDay = currentDate.format(dayFormatter)
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    LaunchedEffect(
        userName, formattedDate, formattedDay, checkInTime,
        workingHours, attendanceStatus, statusText, isTrackingActive
    ) {
        if (uid.isNotEmpty() && internetConnected) {
            val checkInTimeString = checkInTime?.format(timeFormatter) ?: "Not Marked"

            if (checkInTimeString != "Background Update" &&
                workingHours != "Background Update" &&
                attendanceStatus != "Background Update") {

                val updates = mapOf(
                    "name" to userName,
                    "date" to formattedDate,
                    "day" to formattedDay,
                    "checkInTime" to checkInTimeString,
                    "workingHours" to workingHours,
                    "attendance" to attendanceStatus,
                    "status" to if (isInOfficeZone && internetConnected && locationServicesEnabled) "In Office" else "Not in Office",
                    "trackingActive" to isTrackingActive, // Add tracking status
                    "lastUpdated" to System.currentTimeMillis(),
                    "updateSource" to "foreground"
                )

                FirebaseDatabase.getInstance().getReference("attendance/$formattedDate/$uid")
                    .updateChildren(updates)
                    .addOnFailureListener { e ->
                        Log.e("FirebaseUpdate", "Failed to update attendance: ${e.message}")
                    }
            }
        }
    }

    // Auto-resume tracking when conditions are restored
    LaunchedEffect(isInOfficeZone, internetConnected, locationServicesEnabled) {
        if (isInOfficeZone && internetConnected && locationServicesEnabled &&
            attendanceViewModel.isAttendanceMarkedToday() && !isTrackingActive) {
            // Small delay to ensure stable connection
            delay(2000)
            if (isInOfficeZone && internetConnected && locationServicesEnabled) {
                attendanceViewModel.resumeTracking()

                // Force an immediate update of working hours
                attendanceViewModel.updateWorkingHours(LocalTime.now())
            }
        }
    }

    // UI Components
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8FB))
    ) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                isRefreshing = true
                coroutineScope.launch {
                    val startTime = System.currentTimeMillis()
                    attendanceState.resetZoneVisibility()

                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                        .setMaxUpdates(1)
                        .build()

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val loc = result.lastLocation
                            if (loc != null) {
                                latitude = loc.latitude
                                longitude = loc.longitude
                                locationError = null
                            }
                            val elapsed = System.currentTimeMillis() - startTime
                            val remaining = 1000 - elapsed
                            coroutineScope.launch {
                                if (remaining > 0) delay(remaining)
                                isRefreshing = false
                            }
                            fusedLocationClient.removeLocationUpdates(this)
                        }
                    }

                    try {
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                    } catch (e: SecurityException) {
                        locationError = "Location permission not granted"
                        isRefreshing = false
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                HeaderSection(
                    adminName = userName,
                    onLogout = {
                        LocationTrackingService.stopService(context)
                        AppBackgroundService.stopService(context)
                        signOut()
                        clearUserRole(context)
                        if (context is Activity) {
                            val intent = Intent(context, context::class.java)
                            context.finish()
                            context.startActivity(intent)
                        }
                    }
                )

                // Location Card
                LocationCard(
                    internetConnected = internetConnected,
                    locationServicesEnabled = locationServicesEnabled,
                    locationError = locationError,
                    hasLocation = hasLocation,
                    latitude = latitude,
                    longitude = longitude,
                    isInOfficeZone = isInOfficeZone
                )

                // Mark Attendance Card
                MarkAttendanceCard(
                    internetConnected = internetConnected,
                    isOfficeTime = isOfficeTime,
                    isInOfficeZone = isInOfficeZone,
                    isNearOfficeZone = isNearOfficeZone,
                    isAttendanceMarkedToday = isAttendanceMarkedToday,
                    attendanceMarkedTime = attendanceMarkedTime?.let { time ->
                        time.format(timeFormatter) // Use the existing formatter
                    },
                    onMarkAttendance = {
                        try {
                            attendanceViewModel.markAttendance()
                            coroutineScope.launch {
                                val formattedTime = attendanceMarkedTime?.format(timeFormatter)
                                attendanceViewModel.showSnackbar("Marked at ${formattedTime ?: "unknown time"}")
                                delay(3000)
                                attendanceViewModel.resetZoneVisibility()

                                // CRITICAL FIX: Reload tasks after attendance is marked
                                delay(1000) // Give Firebase time to process
                                taskViewModel.loadTasksForEmployee(consistentEmployeeId)
                            }
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                attendanceViewModel.showSnackbar("Attendance failed: ${e.localizedMessage}")
                            }
                        }
                    },
                    onSignOff = {
                        coroutineScope.launch {
                            try {
                                if (uid.isEmpty()) {
                                    attendanceViewModel.showSnackbar("Error: User not authenticated")
                                    return@launch
                                }

                                val checkInTimeString = checkInTime?.let { time ->
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US)
                                    time.format(formatter)
                                } ?: "Not Marked"

                                org.example.employeeattendenceapp.Auth.saveDailyRecord(
                                    uid = uid,
                                    name = userName,
                                    date = formattedDate,
                                    day = formattedDay,
                                    checkInTime = checkInTimeString,
                                    workingHours = workingHours,
                                    attendance = attendanceStatus, // Use actual attendance status
                                    status = statusText,
                                    onSuccess = {
                                        coroutineScope.launch {
                                            org.example.employeeattendenceapp.Auth.updateEmployeeAttendance(
                                                uid = uid,
                                                name = userName,
                                                date = formattedDate,
                                                day = formattedDay,
                                                latitude = latitude,
                                                longitude = longitude,
                                                checkInTime = checkInTimeString,
                                                workingHours = workingHours,
                                                attendance = attendanceStatus, // Use actual attendance status
                                                status = statusText
                                            )

                                            // PROPERLY RESET THE VIEWMODEL STATE AFTER SIGNOFF
                                            attendanceViewModel.resetForNewDay()
                                            attendanceViewModel.showSnackbar("Signed off successfully! Data saved.")
                                        }
                                    },
                                    onError = { error ->
                                        coroutineScope.launch {
                                            attendanceViewModel.showSnackbar("Daily record error: $error")
                                            Log.e("SignOff", "Daily record save failed: $error")
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                attendanceViewModel.showSnackbar("Sign-off failed: ${e.localizedMessage}")
                                Log.e("SignOff", "Error during sign-off", e)
                            }
                        }
                    },
                    withinZoneVisible = withinZoneVisible && isInOfficeZone && locationServicesEnabled && internetConnected,
                    locationServicesEnabled = locationServicesEnabled,
                    attendanceViewModel = attendanceViewModel
                )

                // Today's Stats Card
                TodaysStatsCard(
                    checkInTime = checkInTime?.format(timeFormatter) ?: "Not marked",
                    workingHours = workingHours,
                    attendanceStatus = attendanceStatus,
                    statusText = if (isInOfficeZone && internetConnected && locationServicesEnabled) "In Office" else "Not in Office"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EmployeeTaskView(
                            viewModel = taskViewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Snackbar host
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Composable
private fun HeaderSection(
    adminName: String,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 24.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = adminName.firstOrNull()?.uppercaseChar()?.toString() ?: "E"
                val currentDate = LocalDate.now()
                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
                val formattedDate = currentDate.format(dateFormatter)

                // Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF4B89DC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Welcome, $adminName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            // Logout
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .background(Color(0xFFF6F8FB), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Log Out",
                    tint = Color(0xFF4B89DC)
                )
            }
        }
    }
}

@Composable
private fun LocationCard(
    internetConnected: Boolean,
    locationServicesEnabled: Boolean,
    locationError: String?,
    hasLocation: Boolean,
    latitude: Double?,
    longitude: Double?,
    isInOfficeZone: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status indicators row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Internet status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            id = if (internetConnected) R.drawable.ic_wifi_on
                            else R.drawable.ic_wifi_off
                        ),
                        contentDescription = "Internet Status",
                        tint = if (internetConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (internetConnected) "Online" else "Offline",
                        color = if (internetConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Location services status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(
                            id = if (locationServicesEnabled) R.drawable.ic_location_on
                            else R.drawable.ic_location_off
                        ),
                        contentDescription = "Location Status",
                        tint = if (locationServicesEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (locationServicesEnabled) "Location On" else "Location Off",
                        color = if (locationServicesEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "Current Location",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))
            when {
                !internetConnected -> LocationStatusText("No internet connection", Color.Red)
                !locationServicesEnabled -> LocationStatusText("Location services disabled", Color.Red)
                locationError != null -> LocationStatusText(locationError!!, Color.Red)
                hasLocation -> {
                    val color = if (isInOfficeZone) Color.Gray else Color.Red
                    Column {
                        LocationStatusText("Latitude: ${latitude?.let { String.format("%.6f", it) }}", color)
                        Spacer(modifier = Modifier.height(8.dp))
                        LocationStatusText("Longitude: ${longitude?.let { String.format("%.6f", it) }}", color)
                    }
                }
                else -> LocationStatusText("Waiting for location...", Color.Gray)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Image(
                painter = painterResource(id = R.drawable.map),
                contentDescription = "Location Map",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun LocationStatusText(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun MarkAttendanceCard(
    internetConnected: Boolean,
    isOfficeTime: Boolean,
    isInOfficeZone: Boolean,
    isNearOfficeZone: Boolean,
    isAttendanceMarkedToday: Boolean,
    attendanceMarkedTime: String?,
    onMarkAttendance: () -> Unit,
    onSignOff: () -> Unit,
    withinZoneVisible: Boolean,
    locationServicesEnabled: Boolean,
    attendanceViewModel: EmployeeAttendanceViewModel
) {
    var isSignedOff by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Mark Attendance Button
            Button(
                onClick = {
                    if (!internetConnected) {
                        attendanceViewModel.showSnackbar("No internet connection")
                    } else if (!locationServicesEnabled) {
                        attendanceViewModel.showSnackbar("Location services disabled")
                    } else if (!isOfficeTime) {
                        attendanceViewModel.showSnackbar("Outside office hours")
                    } else if (!isInOfficeZone) {
                        attendanceViewModel.showSnackbar("Not in office zone")
                    } else {
                        onMarkAttendance()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isOfficeTime && !isSignedOff) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInOfficeZone && internetConnected && locationServicesEnabled)
                        Color(0xFF4B89DC) else Color(0xFFBDBDBD)
                ),
                enabled = !isSignedOff &&
                        isOfficeTime &&
                        !isAttendanceMarkedToday &&
                        internetConnected &&
                        locationServicesEnabled &&
                        isInOfficeZone,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isNearOfficeZone) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isAttendanceMarkedToday) "Attendance Marked" else "Mark Attendance",
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Signing Off Button
            Button(
                onClick = onSignOff,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFd32f2f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Signing Off", color = Color.White)
            }

            // Zone Visibility Banner
            AnimatedVisibility(
                visible = withinZoneVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(64.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F6D6))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.check_circle),
                            contentDescription = "Within Zone",
                            tint = Color(0xFF38761D),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You are within allowed location zone",
                            color = Color(0xFF38761D),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaysStatsCard(
    checkInTime: String,
    workingHours: String,
    attendanceStatus: String,
    statusText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Today's Stats",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(28.dp))
            StatRow("Check-in time", checkInTime, checkInTime != "Not marked")
            Spacer(modifier = Modifier.height(6.dp))
            StatRow("Working hours", workingHours, workingHours != "0h 0m 0s")
            Spacer(modifier = Modifier.height(6.dp))
            StatRow("Attendance", attendanceStatus, attendanceStatus == "Present", isAttendance = true)
            Spacer(modifier = Modifier.height(6.dp))
            StatRow("Status", statusText, true)
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    isActive: Boolean,
    isAttendance: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = value,
            fontWeight = if (isAttendance) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isAttendance && value == "Present" -> Color(0xFF4B89DC)
                isAttendance && value == "Absent" -> Color.Red
                value == "In Office" -> Color(0xFF4CAF50)
                value == "Not in Office" -> Color(0xFFF44336)
                value == "--" -> Color.Gray
                isActive -> Color(0xFF4B89DC)
                else -> Color.Gray
            }
        )
    }
}

@Composable
private fun PermissionRationaleDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location Permission Required") },
        text = { Text("Location permissions are required for attendance tracking") },
        confirmButton = {
            Button(onClick = {
                onRequestPermission()
            }) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}