package org.example.employeeattendenceapp.Navigation

import com.arkivanov.decompose.ComponentContext

// shared/src/commonMain/kotlin/components/LoginComponent.kt
class LoginComponent(
    componentContext: ComponentContext,
    val role: String,
    val onNavigateBack: () -> Unit,
    val onNavigateToSignup: () -> Unit,
    val onNavigateToHome: () -> Unit
) : ComponentContext by componentContext {
    val showSignUp: Boolean = role != "admin"
}