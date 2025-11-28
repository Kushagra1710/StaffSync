package org.example.employeeattendenceapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform