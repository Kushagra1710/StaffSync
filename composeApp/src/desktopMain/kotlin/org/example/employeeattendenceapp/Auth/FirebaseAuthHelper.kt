package org.example.employeeattendenceapp.Auth

actual fun signUpWithEmailPassword(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    onError("Sign-up not supported on Desktop.")
}

actual fun isUserLoggedIn(): Boolean = false
