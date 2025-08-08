package com.reelcounter

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker

class ReelReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        val notificationManager = ReelNotificationManager(applicationContext)
        notificationManager.sendReelReminder()
        return Result.success()
    }
}