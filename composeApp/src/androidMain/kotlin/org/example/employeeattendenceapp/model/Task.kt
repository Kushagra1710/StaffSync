package org.example.employeeattendenceapp.data.model

import java.util.*
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Task(
    val id: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val title: String = "",
    val description: String = "",
    val assignedDate: String = "",
    val dueDate: String = "",
    val status: String = "Pending", // Pending, In Progress, Completed
    val employeeResponse: String = "",
    val lastUpdated: String = ""
) {
    // Add this empty constructor for Firebase
    constructor() : this("", "", "", "", "", "", "", "", "", "Pending", "", "")

    companion object {
        fun getStatusOptions(): List<String> {
            return listOf("Pending", "In Progress", "Completed")
        }

        fun createNewTask(
            employeeId: String,
            employeeName: String,
            adminId: String,
            adminName: String,
            title: String,
            description: String,
            dueDate: String
        ): Task {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            return Task(
                id = UUID.randomUUID().toString(),
                employeeId = employeeId,
                employeeName = employeeName,
                adminId = adminId,
                adminName = adminName,
                title = title,
                description = description,
                assignedDate = dateFormat.format(Date()),
                dueDate = dueDate,
                status = "Pending",
                employeeResponse = "",
                lastUpdated = timeFormat.format(Date())
            )
        }
    }
}