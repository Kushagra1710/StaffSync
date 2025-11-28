package org.example.employeeattendenceapp.Navigation

import com.arkivanov.decompose.ComponentContext

// shared/src/commonMain/kotlin/components/DashboardComponent.kt
class DashboardComponent(
    componentContext: ComponentContext,
    val onNavigateToLogin: (String) -> Unit
) : ComponentContext by componentContext {

    fun onAdminClick() = onNavigateToLogin("admin")
    fun onEmployeeClick() = onNavigateToLogin("employee")
}