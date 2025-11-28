package org.example.employeeattendenceapp.Auth

import kotlinx.browser.window
import kotlin.js.Promise

@JsModule("firebase/app")
@JsNonModule
external fun initializeApp(options: dynamic): dynamic

@JsModule("firebase/auth")
@JsNonModule
external val firebaseAuth: dynamic

actual fun signUpWithEmailPassword(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        firebaseAuth.getAuth().createUserWithEmailAndPassword(email, password)
            .then { _: dynamic -> onSuccess() }
            .catch { error: dynamic -> onError(error.message as String) }
    } catch (e: Throwable) {
        onError(e.message ?: "Unknown error")
    }
}

actual fun isUserLoggedIn(): Boolean = false
