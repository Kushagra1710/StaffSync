package org.example.employeeattendenceapp.Auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

private const val PREFS_NAME = "user_prefs"
private const val KEY_USER_ROLE = "user_role"

actual fun signUpWithEmailPassword(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // No email format validation - accept any email
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                val uid = user?.uid ?: run {
                    onError("Registration failed - no user ID")
                    return@addOnCompleteListener
                }

                // Create employee record in Realtime Database
                val userData = hashMapOf(
                    "email" to email,
                    "role" to "employee" // Auto-assign employee role
                )

                FirebaseDatabase.getInstance().getReference("users/$uid")
                    .setValue(userData)
                    .addOnSuccessListener {
                        Log.d("Auth", "Employee account created successfully")
                        onSuccess()
                    }
                    .addOnFailureListener { e ->
                        // Rollback auth creation if DB fails
                        user.delete()
                        Log.e("Auth", "Failed to create employee record", e)
                        onError("Failed to complete registration. Please try again.")
                    }
            } else {
                val error = task.exception
                val errorMessage = when {
                    error is FirebaseAuthUserCollisionException -> "Email already in use"
                    error?.message?.contains("password") == true -> "Weak password (min 6 chars)"
                    else -> "Sign-up failed: ${error?.localizedMessage}"
                }
                onError(errorMessage)
            }
        }
}

actual fun signInWithEmailPassword(
    email: String,
    password: String,
    expectedRole: String,
    onSuccess: () -> Unit,
    onRoleMismatch: () -> Unit,
    onError: (String) -> Unit
) {
    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                val uid = user?.uid ?: run {
                    onError("Authentication error")
                    return@addOnCompleteListener
                }

                // Check if user exists in database
                FirebaseDatabase.getInstance().getReference("users/$uid")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            // User exists in database - check role
                            val storedRole = snapshot.child("role").getValue(String::class.java)
                            when {
                                storedRole == "admin" -> {
                                    if (expectedRole == "admin") {
                                        onSuccess()
                                    } else {
                                        FirebaseAuth.getInstance().signOut()
                                        onRoleMismatch()
                                    }
                                }
                                storedRole == "employee" -> {
                                    if (expectedRole == "employee") {
                                        onSuccess()
                                    } else {
                                        FirebaseAuth.getInstance().signOut()
                                        onRoleMismatch()
                                    }
                                }
                                else -> {
                                    // Role not properly set - treat as employee
                                    if (expectedRole == "employee") {
                                        // Update the role to employee if missing
                                        snapshot.ref.child("role").setValue("employee")
                                            .addOnSuccessListener { onSuccess() }
                                            .addOnFailureListener {
                                                FirebaseAuth.getInstance().signOut()
                                                onError("Failed to assign role")
                                            }
                                    } else {
                                        FirebaseAuth.getInstance().signOut()
                                        onRoleMismatch()
                                    }
                                }
                            }
                        } else {
                            // User doesn't exist in database - must be manually created admin
                            if (expectedRole == "admin") {
                                // Create admin record
                                val adminData = hashMapOf(
                                    "email" to email,
                                    "role" to "admin"
                                )
                                FirebaseDatabase.getInstance().getReference("users/$uid")
                                    .setValue(adminData)
                                    .addOnSuccessListener { onSuccess() }
                                    .addOnFailureListener {
                                        FirebaseAuth.getInstance().signOut()
                                        onError("Failed to create admin record")
                                    }
                            } else {
                                // Regular employee - shouldn't be able to login without signup
                                FirebaseAuth.getInstance().signOut()
                                onError("Account not found. Please sign up first.")
                            }
                        }
                    }
                    .addOnFailureListener {
                        FirebaseAuth.getInstance().signOut()
                        onError("Failed to verify user account")
                    }
            } else {
                val error = task.exception
                val errorMessage = when {
                    error is FirebaseAuthInvalidUserException -> "Account not found"
                    error is FirebaseAuthInvalidCredentialsException -> "Invalid password"
                    else -> error?.localizedMessage ?: "Login failed"
                }
                onError(errorMessage)
            }
        }
}

actual fun isUserLoggedIn(): Boolean {
    return FirebaseAuth.getInstance().currentUser != null
}

actual fun signOut() {
    FirebaseAuth.getInstance().signOut()
}

fun getPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

actual fun saveUserRole(context: Any, role: String) {
    val ctx = context as Context
    getPrefs(ctx).edit().putString(KEY_USER_ROLE, role).apply()
}

actual fun getUserRole(context: Any): String? {
    val ctx = context as Context
    return getPrefs(ctx).getString(KEY_USER_ROLE, null)
}

actual fun clearUserRole(context: Any) {
    val ctx = context as Context
    getPrefs(ctx).edit().remove(KEY_USER_ROLE).apply()
}

// Update these functions in FirebaseAuthHelper.kt

fun updateEmployeeAttendance(
    uid: String,
    name: String,
    date: String,
    day: String,
    latitude: Double?,
    longitude: Double?,
    checkInTime: String,
    workingHours: String,
    attendance: String,
    status: String
) {
    val officeLat = 13.0175493 /*28.556180*/
    val officeLon = 77.6301157 /*77.442370*/
    val locationStatus = if (latitude != null && longitude != null) {
        val dist = FloatArray(1)
        android.location.Location.distanceBetween(latitude, longitude, officeLat, officeLon, dist)
        if (dist[0] <= 100) "In Office" else "Not in Office"
    } else "--"

    val attendanceRef = FirebaseDatabase.getInstance()
        .getReference("attendance")
        .child(date)
        .child(uid)

    val attendanceData = hashMapOf(
        "name" to name,
        "date" to date,
        "day" to day,
        "latitude" to latitude,
        "longitude" to longitude,
        "checkInTime" to checkInTime,
        "workingHours" to workingHours,
        "attendance" to attendance,
        "status" to status,
        "location" to locationStatus
    )

    attendanceRef.setValue(attendanceData)
        .addOnFailureListener { e ->
            Log.e("Firebase", "Error updating attendance: ${e.message}")
        }
}

fun saveDailyRecord(
    uid: String,
    name: String,
    date: String,
    day: String,
    checkInTime: String,
    workingHours: String,
    attendance: String,
    status: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val dailyRecordRef = FirebaseDatabase.getInstance()
        .getReference("daily_records")
        .child(date)
        .child(uid)
    dailyRecordRef.keepSynced(true)

    val recordData = hashMapOf(
        "name" to name,
        "date" to date,
        "day" to day,
        "checkInTime" to checkInTime,
        "workingHours" to workingHours,
        "attendance" to attendance,
        "status" to status,
        "location" to status
    )

    // Force write with setValue
    dailyRecordRef.setValue(recordData)
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { e ->
            onError(e.message ?: "Failed to save daily record")
            Log.e("Firebase", "Error saving daily record: ${e.message}")
        }
}

actual fun getDailyRecord(
    date: String,
    uid: String,
    onSuccess: (Map<String, Any>?) -> Unit,
    onError: (String) -> Unit
) {
    FirebaseDatabase.getInstance().getReference("daily_records/$date/$uid")
        .get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val record = snapshot.value as? Map<String, Any>
                onSuccess(record)
            } else {
                onSuccess(null)
            }
        }
        .addOnFailureListener { e ->
            onError("Failed to fetch daily record: ${e.message}")
        }
}