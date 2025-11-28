package org.example.employeeattendenceapp.service

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.database
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notificationUtils: NotificationUtils

    override fun onNewToken(token: String) {
        // Save this token to your database for the current user
        Firebase.database.reference.child("user_tokens")
            .child(FirebaseAuth.getInstance().currentUser?.uid ?: return)
            .setValue(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let { notification ->
            notificationUtils.showNotification(
                title = notification.title ?: "New Notification",
                message = notification.body ?: "",
                channelId = "tasks_channel",
                notificationId = System.currentTimeMillis().toInt()
            )
        }
    }
}