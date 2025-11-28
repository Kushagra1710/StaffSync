package org.example.employeeattendenceapp.Navigation

import com.arkivanov.decompose.ComponentContext

class SignupComponent(
    componentContext: ComponentContext,
    val role: String,
    val onNavigateBack: () -> Unit,
    val onNavigateToLogin: () -> Unit
) : ComponentContext by componentContext 