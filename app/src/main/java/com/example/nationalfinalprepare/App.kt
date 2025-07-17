package com.example.nationalfinalprepare

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

const val recording_channel_name = "recording_notification_channel"

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            recording_channel_name,
            "Recording Service",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}