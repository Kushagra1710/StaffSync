package org.example.employeeattendenceapp.ui.employee.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.example.employeeattendenceapp.R
import org.example.employeeattendenceapp.data.model.Task
import org.example.employeeattendenceapp.ui.employee.TaskEmployeeViewModel
import org.jetbrains.compose.resources.painterResource

@Composable
fun EmployeeTaskView(
    viewModel: TaskEmployeeViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val selectedTask by viewModel.selectedTask.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Get current employee ID
    val employeeId = remember {
        FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")?.lowercase() ?: ""
    }

    Column(modifier = modifier) {  // Remove any verticalScroll from here
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Your Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.loadTasksForEmployee(employeeId)
                        snackbarHostState.showSnackbar("Refreshing tasks...")
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,  // Using Material Icons
                    contentDescription = "Refresh"
                )
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),  // Fixed height for loading
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            tasks.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),  // Fixed height for empty state
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No tasks assigned yet")
                        Button(
                            onClick = {
                                viewModel.loadTasksForEmployee(employeeId)
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Check Again")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)  // Constrained height
                ) {
                    items(tasks) { task ->
                        TaskCard(
                            task = task,
                            onClick = { viewModel.selectTask(task) }
                        )
                    }
                }
            }
        }
    }

    // Task Detail Dialog
    selectedTask?.let { task ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSelection() },
            title = { Text(task.title) },
            text = {
                Column {
                    Text(task.description)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Assigned by: ${task.adminName}")
                    Text("Assigned on: ${task.assignedDate}")
                    Text("Due date: ${task.dueDate}")
                    Text("Status: ${task.status}")

                    if (task.employeeResponse.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Your Response:")
                        Text(task.employeeResponse)
                    }

                    if (task.status != "Completed") {
                        var response by remember { mutableStateOf("") }

                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = response,
                            onValueChange = { response = it },
                            label = { Text("Update or comment") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    viewModel.updateTaskStatus(
                                        task = task,
                                        status = "In Progress",
                                        response = response,
                                        onComplete = {
                                            viewModel.clearSelection()
                                            // Send notification to admin
                                            viewModel.sendTaskUpdateNotification(
                                                adminId = task.adminId,
                                                employeeName = task.employeeName,
                                                taskTitle = task.title,
                                                newStatus = "In Progress",
                                                comment = response
                                            )
                                        }
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "In Progress")
                                Spacer(Modifier.width(8.dp))
                                Text("In Progress")
                            }

                            Button(
                                onClick = {
                                    viewModel.updateTaskStatus(
                                        task = task,
                                        status = "Completed",
                                        response = response,
                                        onComplete = {
                                            viewModel.clearSelection()
                                            // Send notification to admin
                                            viewModel.sendTaskUpdateNotification(
                                                adminId = task.adminId,
                                                employeeName = task.employeeName,
                                                taskTitle = task.title,
                                                newStatus = "Completed",
                                                comment = response
                                            )
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Complete")
                                Spacer(Modifier.width(8.dp))
                                Text("Complete")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (task.status == "Completed") {
                    Button(onClick = { viewModel.clearSelection() }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun TaskCard(task: Task, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    task.status,
                    color = when (task.status) {
                        "Completed" -> Color(0xFF388E3C)
                        "In Progress" -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Due: ${task.dueDate}")
        }
    }
}