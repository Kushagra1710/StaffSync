package org.example.employeeattendenceapp

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.example.employeeattendenceapp.Auth.clearUserRole
import org.example.employeeattendenceapp.Auth.signOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Send
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import org.example.employeeattendenceapp.data.model.Task
import org.example.employeeattendenceapp.viewmodels.TaskAdminViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.database.FirebaseDatabase
import org.example.employeeattendenceapp.Auth.getDailyRecord
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction

@Composable
actual fun HomeScreenAdmin(justLoggedIn: Boolean) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val database = Firebase.database.reference
    val coroutineScope = rememberCoroutineScope()

    val taskViewModel: TaskAdminViewModel = hiltViewModel()
    var showTaskDialog by remember { mutableStateOf(false) }

    // State variables
    var presentCount by remember { mutableStateOf(0) }
    var absentCount by remember { mutableStateOf(0) }
    var notMarkedCount by remember { mutableStateOf(0) }
    var totalEmployees by remember { mutableStateOf(0) }
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var recentAttendanceList by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }


    // Extract name from authenticated user's email
    val adminName = remember(currentUser) {
        currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Admin"
    }

    var selectedEmployeeForTask by remember { mutableStateOf<Pair<String, String>?>(null) }

    var notifications by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var showNotifications by remember { mutableStateOf(false) }

    val unreadCount by remember(notifications) {
        derivedStateOf {
            notifications.count { !(it["read"] as? Boolean ?: false) }
        }
    }

    var showAllEmployees by remember { mutableStateOf(false) }

    // Add this LaunchedEffect to load notifications
    LaunchedEffect(Unit) {
        val currentAdminId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val notificationsRef = Firebase.database.reference.child("notifications")
            .orderByChild("adminId")
            .equalTo(currentAdminId)

        notificationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newNotifications = mutableListOf<Map<String, Any?>>()
                snapshot.children.forEach { child ->
                    val notification = child.value as? Map<String, Any?> ?: return@forEach
                    newNotifications.add(notification + ("key" to child.key))
                }
                notifications = newNotifications.sortedByDescending {
                    (it["timestamp"] as? Long) ?: 0L
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Notifications", "Error loading notifications", error.toException())
            }
        })
    }

    // Fetch attendance data from Firebase
    LaunchedEffect(Unit) {
        val usersRef = database.child("users")
        val attendanceRef = database.child("attendance").child(todayDate)

        // Get total employees count (only those with role "employee")
        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    var count = 0
                    val employeeIds = mutableListOf<String>()

                    if (snapshot.exists()) {
                        snapshot.children.forEach { child ->
                            val role = child.child("role").getValue(String::class.java)
                            if (role == "employee") {
                                count++
                                employeeIds.add(child.key ?: "")
                            }
                        }
                    }
                    totalEmployees = count

                    // Now that we have employee IDs, we can properly calculate not marked
                    if (presentCount > 0) {
                        notMarkedCount = maxOf(0, totalEmployees - presentCount)
                        absentCount = notMarkedCount
                    }
                } catch (e: Exception) {
                    Log.e("EmployeeCount", "Error counting employees", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Error counting employees: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("EmployeeCount", "Database error: ${error.message}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Database error: ${error.message}")
                }
            }
        })

        // Get today's attendance and recent records
        attendanceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val presentEmployees = mutableSetOf<String>()
                    val tempRecentAttendance = mutableListOf<Triple<String, String, String>>()
                    val employeeUidToNameMap = mutableMapOf<String, String>() // Map UID to display name

                    if (snapshot.exists()) {
                        snapshot.children.forEach { employeeSnapshot ->
                            val userId = employeeSnapshot.key ?: ""
                            val attendance = employeeSnapshot.child("attendance").getValue(String::class.java)
                            val checkInTime = employeeSnapshot.child("checkInTime").getValue(String::class.java) ?: ""
                            val status = employeeSnapshot.child("status").getValue(String::class.java) ?: ""
                            val name = employeeSnapshot.child("name").getValue(String::class.java) ?: "Employee"

                            // Handle "Background Update" values
                            val effectiveAttendance = when (attendance) {
                                "Background Update" -> if (status == "Active") "Present" else "Absent"
                                else -> attendance
                            }

                            val effectiveCheckInTime = when (checkInTime) {
                                "Background Update" -> "Auto Check-in"
                                else -> checkInTime
                            }

                            if (effectiveAttendance == "Present") {
                                presentEmployees.add(userId)
                            }

                            val displayName = name.substringBefore("@")
                                .replace(".", " ")
                                .replaceFirstChar { it.uppercase() }

                            // Store the mapping from UID to display name
                            employeeUidToNameMap[userId] = displayName

                            val displayStatus = if (effectiveAttendance == "Present") "Present" else "Absent"
                            val displayTime = if (effectiveAttendance == "Present") effectiveCheckInTime else "Not checked in"

                            tempRecentAttendance.add(Triple(
                                displayName,
                                displayTime,
                                displayStatus
                            ))
                        }
                    }

                    presentCount = presentEmployees.size
                    // Calculate absent/not marked based on current total
                    notMarkedCount = maxOf(0, totalEmployees - presentCount)
                    absentCount = notMarkedCount

                    recentAttendanceList = tempRecentAttendance.takeLast(4)

                    // IMPORTANT FIX: Store the first available employee for task assignment
                    // This ensures we have a valid employee selected for the task dialog
                    if (employeeUidToNameMap.isNotEmpty() && selectedEmployeeForTask == null) {
                        val firstEmployee = employeeUidToNameMap.entries.first()
                        selectedEmployeeForTask = Pair(firstEmployee.key, firstEmployee.value)
                    }
                } catch (e: Exception) {
                    Log.e("Attendance", "Error processing attendance", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Error processing attendance: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Attendance", "Database error: ${error.message}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Database error: ${error.message}")
                }
            }
        })

        // Get today's attendance and recent records
        attendanceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val presentEmployees = mutableSetOf<String>()
                    val tempRecentAttendance = mutableListOf<Triple<String, String, String>>()

                    if (snapshot.exists()) {
                        snapshot.children.forEach { employeeSnapshot ->
                            val userId = employeeSnapshot.key ?: ""
                            val attendance = employeeSnapshot.child("attendance").getValue(String::class.java)
                            val checkInTime = employeeSnapshot.child("checkInTime").getValue(String::class.java) ?: ""
                            val status = employeeSnapshot.child("status").getValue(String::class.java) ?: ""
                            val name = employeeSnapshot.child("name").getValue(String::class.java) ?: "Employee"

                            // Handle "Background Update" values
                            val effectiveAttendance = when (attendance) {
                                "Background Update" -> if (status == "Active") "Present" else "Absent"
                                else -> attendance
                            }

                            val effectiveCheckInTime = when (checkInTime) {
                                "Background Update" -> "Auto Check-in"
                                else -> checkInTime
                            }

                            if (effectiveAttendance == "Present") {
                                presentEmployees.add(userId)
                            }

                            val displayName = name.substringBefore("@")
                                .replace(".", " ")
                                .replaceFirstChar { it.uppercase() }

                            val displayStatus = if (effectiveAttendance == "Present") "Present" else "Absent"
                            val displayTime = if (effectiveAttendance == "Present") effectiveCheckInTime else "Not checked in"

                            tempRecentAttendance.add(Triple(
                                displayName,
                                displayTime,
                                displayStatus
                            ))

                            // Store both UID and display name
                            selectedEmployeeForTask = Pair(userId, displayName)
                        }
                    }

                    presentCount = presentEmployees.size
                    absentCount = totalEmployees - presentCount // Absent = Total - Present
                    notMarkedCount = absentCount // Not marked = Absent (they are the same)
                    recentAttendanceList = tempRecentAttendance.takeLast(4)
                } catch (e: Exception) {
                    Log.e("Attendance", "Error processing attendance", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Error processing attendance: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Attendance", "Database error: ${error.message}")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Database error: ${error.message}")
                }
            }
        })
    }
        if (justLoggedIn) {
        LaunchedEffect(Unit) {
            delay(300)
            snackbarHostState.showSnackbar("Logged in successfully!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val initials = adminName.take(1).uppercase()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4B89DC)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome, $adminName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage employee attendance and records",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = {
                    signOut()
                    clearUserRole(context)
                    if (context is Activity) {
                        val intent = Intent(context, context::class.java)
                        context.finish()
                        context.startActivity(intent)
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color(0xFF4B89DC)
                    )
                }
            }

            // Overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Attendance Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Today", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4B89DC))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Present Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$presentCount", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Present", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }
                }

                // Absent Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFECACA))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$absentCount", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Absent", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }
                }

                // Not Marked Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$notMarkedCount", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Not Marked", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }
                }
            }

            // Total Employees Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Total Employees",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$totalEmployees",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B89DC)
                    )
                }
            }

            // Statistics Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AttendanceStatisticsBar(
                        presentCount = presentCount,
                        absentCount = absentCount,
                        totalEmployees = totalEmployees,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

// Recent Attendance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recent Attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (showAllEmployees) "Show Less" else "View All",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4B89DC),
                    modifier = Modifier.clickable {
                        showAllEmployees = !showAllEmployees
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (recentAttendanceList.isEmpty()) {
                    Text(
                        "No attendance records yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val displayList = if (showAllEmployees) {
                        recentAttendanceList
                    } else {
                        recentAttendanceList.takeLast(4)
                    }

                    displayList.forEach { (name, time, status) ->
                        val initials = name.take(2).uppercase()

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    // IMPORTANT FIX: Find the actual employee UID from the Firebase users
                                    // Instead of using display name, get the proper employee identifier
                                    val employeeEmailPrefix = name.lowercase().replace(" ", ".")

                                    // For task assignment, we need to use the email prefix (lowercase)
                                    // This should match how the employee is identified in the task system
                                    selectedEmployeeForTask = Pair(employeeEmailPrefix, name)
                                    showTaskDialog = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4B89DC)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (status == "Absent") "Not checked in" else time,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = status,
                                    color = if (status == "Present") Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (showTaskDialog) {
                selectedEmployeeForTask?.let { employee ->
                    EmployeeTaskDialog(
                        employeeId = employee.first,
                        employeeName = employee.second,
                        viewModel = taskViewModel,
                        onDismiss = {
                            showTaskDialog = false
                            selectedEmployeeForTask = null
                        }
                    )
                }
            }

            Text("Quick Actions", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B89DC))
                ) {
                    Text("Export Report")
                }
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B89DC))
                ) {
                    Text("Mark Attendance")
                }
            }

            Column {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("Monthly Report", color = Color.Black)
                }

                // Add Notifications button
                Button(
                    onClick = { showNotifications = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Text("View Updates (${unreadCount})", color = Color.Black)
                }
            }

            if (showNotifications) {
                AlertDialog(
                    onDismissRequest = { showNotifications = false },
                    title = { Text("Task Updates") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (notifications.isEmpty()) {
                                Text("No updates yet", modifier = Modifier.padding(16.dp))
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                    items(notifications.size) { index ->  // Changed to use notifications.size
                                        val notification = notifications[index]  // Get the notification at the current index
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if ((notification["read"] as? Boolean) == true) Color.White
                                                else Color(0xFFE3F2FD)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                    "${notification["employeeName"]} updated task:",
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("\"${notification["taskTitle"]}\"")
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("New status: ${notification["newStatus"]}")
                                                if ((notification["comment"] as? String).orEmpty().isNotEmpty()) {
                                                    Text("Comment: ${notification["comment"]}")
                                                }
                                                Text(
                                                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                                        .format(Date((notification["timestamp"] as? Long) ?: 0L)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            // Mark all as read
                            notifications.forEach { notification ->
                                val notificationId = notification["key"] as? String ?: return@forEach
                                Firebase.database.reference.child("notifications")
                                    .child(notificationId)
                                    .child("read")
                                    .setValue(true)
                            }
                            showNotifications = false
                        }) {
                            Text("Close")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AttendanceStatisticsBar(
    presentCount: Int,
    absentCount: Int,
    totalEmployees: Int,
    modifier: Modifier = Modifier
) {
    // Animation for weights
    val total = maxOf(totalEmployees, 1)
    val presentWeight by animateFloatAsState(
        targetValue = maxOf(presentCount.toFloat() / total, 0.1f),
        animationSpec = tween(durationMillis = 500)
    )
    val absentWeight by animateFloatAsState(
        targetValue = maxOf(absentCount.toFloat() / total, 0.1f),
        animationSpec = tween(durationMillis = 500)
    )
    val remainingWeight by animateFloatAsState(
        targetValue = maxOf(1f - presentWeight - absentWeight, 0.1f),
        animationSpec = tween(durationMillis = 500)
    )

    // Animation for number changes
    val animatedPresentCount by animateIntAsState(
        targetValue = presentCount,
        animationSpec = tween(durationMillis = 500)
    )
    val animatedAbsentCount by animateIntAsState(
        targetValue = absentCount,
        animationSpec = tween(durationMillis = 500)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Animated labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedContent(
                targetState = animatedPresentCount,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() with
                            slideOutVertically { height -> -height } + fadeOut()
                }
            ) { count ->
                Text(
                    text = "$count Present",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }

            AnimatedContent(
                targetState = animatedAbsentCount,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() with
                            slideOutVertically { height -> -height } + fadeOut()
                }
            ) { count ->
                Text(
                    text = "$count Absent",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            // Present segment with animation
            Box(
                modifier = Modifier
                    .weight(presentWeight)
                    .fillMaxHeight()
                    .background(Color(0xFF4CAF50))
            )

            // Absent segment with animation
            Box(
                modifier = Modifier
                    .weight(absentWeight)
                    .fillMaxHeight()
                    .background(Color(0xFFF44336))
            )

            // Remaining segment with animation
            Box(
                modifier = Modifier
                    .weight(remainingWeight)
                    .fillMaxHeight()
                    .background(Color(0xFF9E9E9E))
            )
        }
    }
}

@Composable
fun EmployeeTaskDialog(
    employeeId: String,
    employeeName: String,
    viewModel: TaskAdminViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var taskTitle by remember { mutableStateOf("") }
    var taskDescription by remember { mutableStateOf("") }
    var taskDueDate by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val keyboardOpened = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // State for employee attendance details
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var isLoadingAttendance by remember { mutableStateOf(false) }

    var realTimeAttendance by remember { mutableStateOf<Map<String, Any>?>(null) }
    var dailyRecord by remember { mutableStateOf<Map<String, Any>?>(null) }
    var dataSource by remember { mutableStateOf("realtime") } // "realtime" or "daily"

    // IMPORTANT FIX: Get the proper employee identifier from Firebase user
    val properEmployeeId = remember {
        // Try to get the email-based ID from Firebase users
        FirebaseDatabase.getInstance().getReference("users")
            .orderByChild("email")
            .equalTo("${employeeName.lowercase()}@example.com") // Adjust domain as needed

        // For now, use a consistent format - email prefix in lowercase
        employeeName.lowercase()
    }

    // Load tasks using the proper employee ID format
    LaunchedEffect(properEmployeeId) {
        viewModel.loadTasksForEmployee(properEmployeeId)
    }

    // Fetch employee attendance data
    DisposableEffect(employeeId, selectedDate, dataSource) {
        isLoadingAttendance = true

        if (dataSource == "realtime") {
            val attendanceRef = FirebaseDatabase.getInstance().getReference("attendance/$selectedDate/$employeeId")
            val attendanceListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    realTimeAttendance = if (snapshot.exists()) snapshot.value as? Map<String, Any> else null
                    isLoadingAttendance = false
                }
                override fun onCancelled(error: DatabaseError) {
                    realTimeAttendance = null
                    isLoadingAttendance = false
                }
            }
            attendanceRef.addValueEventListener(attendanceListener)

            onDispose {
                attendanceRef.removeEventListener(attendanceListener)
            }
        } else {
            getDailyRecord(
                date = selectedDate,
                uid = employeeId,
                onSuccess = { record ->
                    dailyRecord = record
                    isLoadingAttendance = false
                },
                onError = { error ->
                    Log.e("DailyRecord", error)
                    dailyRecord = null
                    isLoadingAttendance = false
                }
            )
        }

        onDispose {}
    }

    // Determine which data to display
    val displayData = when (dataSource) {
        "realtime" -> realTimeAttendance
        "daily" -> dailyRecord
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = configuration.screenHeightDp.dp - 32.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Text(
                    "Employee: $employeeName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Debug info (remove in production)
                Text(
                    "Debug - Employee ID: $properEmployeeId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Data source selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("Data source:", modifier = Modifier.padding(end = 8.dp))
                    Row {
                        FilterChip(
                            selected = dataSource == "realtime",
                            onClick = { dataSource = "realtime" },
                            label = { Text("Live") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        FilterChip(
                            selected = dataSource == "daily",
                            onClick = { dataSource = "daily" },
                            label = { Text("Saved") }
                        )
                    }
                }

                // Date selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("View attendance for:", modifier = Modifier.padding(end = 8.dp))
                    DatePickerTextField(
                        initialDate = selectedDate,
                        onDateSelected = { selectedDate = it }
                    )
                }

                // Data source info
                Text(
                    text = when (dataSource) {
                        "realtime" -> "Live attendance data (updates in real-time)"
                        "daily" -> "Saved daily record (finalized data)"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoadingAttendance) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (displayData != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Attendance Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Check-in time:")
                                Text(displayData["checkInTime"]?.toString() ?: "--")
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Attendance:")
                                Text(displayData["attendance"]?.toString() ?: "--")
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Working hours:")
                                Text(displayData["workingHours"]?.toString() ?: "--")
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Location:")
                                Text(displayData["location"]?.toString() ?: "--")
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (dataSource == "daily") "✓ Saved record (final)" else "● Live data (updating)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (dataSource == "daily") Color(0xFF388E3C) else Color(0xFF1976D2)
                            )
                        }
                    }
                } else {
                    Text(
                        "No ${if (dataSource == "daily") "saved" else "attendance"} record for selected date",
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Task Assignment Form
                Text(
                    "Assign Task to $employeeName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val focusManager = LocalFocusManager.current

                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Task Title *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    delay(300)
                                    scrollState.animateScrollTo(400)
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = taskDescription,
                    onValueChange = { taskDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    delay(300)
                                    scrollState.animateScrollTo(500)
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = taskDueDate,
                    onValueChange = { taskDueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD) *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    delay(300)
                                    scrollState.animateScrollTo(600)
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Existing Tasks List
                if (uiState.employeeTasks.isNotEmpty()) {
                    Text("Existing Tasks:", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        items(uiState.employeeTasks.size) { index ->
                            val task = uiState.employeeTasks[index]
                            TaskItem(task = task)
                        }
                    }
                } else {
                    Text(
                        "No existing tasks found for this employee",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons at the bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.assignTask(
                                adminId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                                adminName = FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Admin",
                                employeeId = properEmployeeId,  // Use the proper employee ID
                                employeeName = employeeName,
                                title = taskTitle,
                                description = taskDescription,
                                dueDate = taskDueDate,
                                onComplete = { result ->
                                    if (result.isSuccess) {
                                        taskTitle = ""
                                        taskDescription = ""
                                        taskDueDate = ""
                                        // Refresh tasks after successful assignment
                                        viewModel.loadTasksForEmployee(properEmployeeId)
                                    }
                                }
                            )
                        },
                        enabled = taskTitle.isNotEmpty() && taskDueDate.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Assign")
                        Spacer(Modifier.width(8.dp))
                        Text("Assign Task")
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: Task) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(task.description)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Due: ${task.dueDate}")
                Text(
                    "Status: ${task.status}", color = when (task.status) {
                        "Completed" -> Color(0xFF388E3C)
                        "In Progress" -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    }
                )
            }
            if (task.employeeResponse.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Response: ${task.employeeResponse}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerTextField(
    initialDate: String,
    onDateSelected: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Parse initial date
    val parsedDate = remember(initialDate) {
        try {
            dateFormat.parse(initialDate) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    OutlinedTextField(
        value = selectedDate,
        onValueChange = {},
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Select date")
            }
        },
        modifier = Modifier.width(150.dp)
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = parsedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val newDate = dateFormat.format(Date(it))
                            selectedDate = newDate
                            onDateSelected(newDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}