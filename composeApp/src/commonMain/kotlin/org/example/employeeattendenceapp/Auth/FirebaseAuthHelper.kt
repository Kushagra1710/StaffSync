package org.example.employeeattendenceapp.Auth

expect fun signUpWithEmailPassword(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
)

expect fun signInWithEmailPassword(
    email: String,
    password: String,
    expectedRole: String,
    onSuccess: () -> Unit,
    onRoleMismatch: () -> Unit,
    onError: (String) -> Unit
)

expect fun isUserLoggedIn(): Boolean

expect fun signOut()

expect fun saveUserRole(context: Any, role: String)
expect fun getUserRole(context: Any): String?
expect fun clearUserRole(context: Any)
expect fun getDailyRecord(
    date: String,
    uid: String,
    onSuccess: (Map<String, Any>?) -> Unit,
    onError: (String) -> Unit
)