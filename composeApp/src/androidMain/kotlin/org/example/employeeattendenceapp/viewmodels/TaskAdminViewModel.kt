package org.example.employeeattendenceapp.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.example.employeeattendenceapp.Repo.TaskRepository
import org.example.employeeattendenceapp.data.model.Task
import javax.inject.Inject

@HiltViewModel
class TaskAdminViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskAdminUiState())
    val uiState: StateFlow<TaskAdminUiState> = _uiState.asStateFlow()

    private val _selectedEmployee = MutableStateFlow<Pair<String, String>?>(null)
    val selectedEmployee: StateFlow<Pair<String, String>?> = _selectedEmployee.asStateFlow()

    fun selectEmployee(employeeId: String, employeeName: String) {
        _selectedEmployee.value = Pair(employeeId, employeeName)
        loadTasksForEmployee(employeeId)
    }

    fun clearSelection() {
        _selectedEmployee.value = null
    }

    fun loadTasksForEmployee(employeeId: String) {
        viewModelScope.launch {
            Log.d("TaskAdminViewModel", "Loading tasks for employee: $employeeId")
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Convert to LOWERCASE to match how tasks are stored
                val dbEmployeeId = employeeId.lowercase()
                Log.d("TaskAdminViewModel", "Querying with formatted ID: $dbEmployeeId")

                taskRepository.getTasksForEmployee(dbEmployeeId).collect { tasks ->
                    Log.d("TaskAdminViewModel", "Received ${tasks.size} tasks")
                    _uiState.update { it.copy(employeeTasks = tasks, isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("TaskAdminViewModel", "Error loading tasks", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun assignTask(
        adminId: String,
        adminName: String,
        employeeId: String,
        employeeName: String,
        title: String,
        description: String,
        dueDate: String,
        onComplete: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Store employee ID in LOWERCASE for consistent querying
                val formattedEmployeeId = employeeId.lowercase()

                val task = Task.createNewTask(
                    employeeId = formattedEmployeeId, // Use lowercase ID
                    employeeName = employeeName,
                    adminId = adminId,
                    adminName = adminName,
                    title = title,
                    description = description,
                    dueDate = dueDate
                )
                taskRepository.createTask(task)
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

data class TaskAdminUiState(
    val employeeTasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)