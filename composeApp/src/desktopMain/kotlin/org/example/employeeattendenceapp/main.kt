package org.example.employeeattendenceapp

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Employee Attendance App",
    ) {
        App()
    }
}