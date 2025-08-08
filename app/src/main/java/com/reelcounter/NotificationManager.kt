package com.reelcounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlin.random.Random

class ReelNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "reel_reminder_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reel Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications to remind you to watch reels"
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun sendReelReminder() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call) // Use your app icon here
            .setContentTitle(getRandomReminderTitle())
            .setContentText(getRandomReminderMessage())
            .setStyle(NotificationCompat.BigTextStyle().bigText(getRandomReminderMessage()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Handle case where notification permission is not granted
        }
    }
    
    private fun getRandomReminderTitle(): String {
        val titles = listOf(
            "🎯 Time for your reel fix!",
            "📱 Your feed misses you!",
            "🔥 Reel break time!",
            "💊 Daily dopamine dose ready!",
            "🧠 Algorithm is waiting...",
            "⏰ Scroll o'clock!",
            "🎬 Show time!",
            "📺 Your stories await!"
        )
        return titles.random()
    }
    
    private fun getRandomReminderMessage(): String {
        val messages = listOf(
            "Don't break your streak! Come back and watch some reels 🎯",
            "Your daily goal awaits. Time to get scrolling! 📱✨",
            "The algorithm has fresh content just for you! 🤖",
            "Quick reel break? You know you want to... 😏",
            "Your thumb is getting weak! Time for some exercise 💪",
            "Warning: Productivity detected. Please resume scrolling immediately! ⚠️",
            "Your ancestors didn't fight for survival so you could be productive. Watch reels! 🦕",
            "Breaking news: New reels available! Your attention required 📰",
            "Reel withdrawal symptoms detected. Immediate scrolling recommended! 🚨",
            "Life's too short to not watch reels. Come back! 🌟"
        )
        return messages.random()
    }
}