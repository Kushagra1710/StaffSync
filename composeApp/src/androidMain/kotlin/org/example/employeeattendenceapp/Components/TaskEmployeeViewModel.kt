package org.example.employeeattendenceapp.ui.employee

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.example.employeeattendenceapp.Repo.TaskRepository
import org.example.employeeattendenceapp.data.model.Task
import javax.inject.Inject

@HiltViewModel
class TaskEmployeeViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _selectedTask = MutableStateFlow<Task?>(null)
    val selectedTask: StateFlow<Task?> = _selectedTask

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun selectTask(task: Task) {
        _selectedTask.value = task
    }

    fun clearSelection() {
        _selectedTask.value = null
    }

    fun loadTasksForEmployee(employeeId: String) {
        viewModelScope.launch {
            Log.d("TaskEmployeeViewModel", "Loading tasks for employee: $employeeId")
            _isLoading.value = true
            try {
                taskRepository.getTasksForEmployee(employeeId).collect { tasks ->
                    Log.d("TaskEmployeeViewModel", "Received ${tasks.size} tasks")
                    _tasks.value = tasks
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Log.e("TaskEmployeeViewModel", "Error loading tasks", e)
                _isLoading.value = false
            }
        }
    }

    fun updateTaskStatus(
        task: Task,
        status: String,
        response: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val updatedTask = task.copy(
                status = status,
                employeeResponse = response
            )
            taskRepository.updateTask(updatedTask)
            _isLoading.value = false
            onComplete()
        }
    }

    fun sendTaskUpdateNotification(
        adminId: String,
        employeeName: String,
        taskTitle: String,
        newStatus: String,
        comment: String
    ) {
        viewModelScope.launch {
            taskRepository.sendTaskUpdateNotification(adminId, employeeName, taskTitle, newStatus, comment)
        }
    }
}