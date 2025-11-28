package org.example.employeeattendenceapp.Repo

import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.example.employeeattendenceapp.data.model.Task
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    private val tasksRef: DatabaseReference by lazy {
        database.getReference("tasks")
    }

    suspend fun createTask(task: Task): Result<Unit> {
        return try {
            tasksRef.child(task.id).setValue(task).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(task: Task): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "title" to task.title,
                "description" to task.description,
                "dueDate" to task.dueDate,
                "status" to task.status,
                "employeeResponse" to task.employeeResponse,
                "lastUpdated" to task.lastUpdated
            )
            tasksRef.child(task.id).updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTasksForEmployee(employeeId: String): Flow<List<Task>> = callbackFlow {
        // Remove the case conversion here since it's now handled in ViewModel
        Log.d("TaskRepository", "Querying tasks for employee: $employeeId")

        val query = tasksRef.orderByChild("employeeId").equalTo(employeeId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasks = mutableListOf<Task>()
                snapshot.children.forEach { child ->
                    val task = child.getValue(Task::class.java)
                    if (task != null) {
                        tasks.add(task)
                        Log.d("TaskRepository", "Found task: ${task.title} for ${task.employeeId}")
                    }
                }
                Log.d("TaskRepository", "Total tasks found: ${tasks.size}")
                trySend(tasks)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("TaskRepository", "Error loading tasks: ${error.message}")
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)
        awaitClose { tasksRef.removeEventListener(listener) }
    }

    fun sendTaskUpdateNotification(adminId: String, employeeName: String, taskTitle: String, newStatus: String, comment: String) {
        val notificationRef = database.getReference("notifications").push()
        val notification = mapOf(
            "adminId" to adminId,
            "employeeName" to employeeName,
            "taskTitle" to taskTitle,
            "newStatus" to newStatus,
            "comment" to comment,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        notificationRef.setValue(notification)
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            tasksRef.child(taskId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}