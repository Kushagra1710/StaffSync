package org.example.employeeattendenceapp

import androidx.compose.runtime.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.employeeattendenceapp.Auth.getUserRole
import org.example.employeeattendenceapp.Navigation.RootComponent
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext

@Composable
@Preview
fun App() {
    val context = LocalContext.current
    val root = remember {
        val storedRole = getUserRole(context)
        val initialRole = storedRole ?: "login"
        RootComponent(
            componentContext = DefaultComponentContext(
                lifecycle = LifecycleRegistry()
            ),
            initialRole = initialRole
        )
    }

    Children(root.stack) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Dashboard ->
                DashboardSection(component = instance.component)
            is RootComponent.Child.Login ->
                LoginScreen(component = instance.component)
            is RootComponent.Child.Signup ->
                SignUp(component = instance.component)
            is RootComponent.Child.Home ->
                if (instance.role == "admin") {
                    HomeScreenAdmin(justLoggedIn = instance.justLoggedIn)
                } else {
                    HomeScreenEmployee(justLoggedIn = instance.justLoggedIn)
                }
        }
    }
}
