package com.reelcounter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reelcounter.ui.theme.ReelCounterTheme
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class MainActivity : ComponentActivity() {
    
    private lateinit var notificationManager: ReelNotificationManager
    
    // Permission launcher for notifications
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scheduleReelReminders()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        notificationManager = ReelNotificationManager(this)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scheduleReelReminders()
            }
        } else {
            scheduleReelReminders()
        }

        setContent {
            ReelCounterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ReelCounterUI(
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenInstagram = {
                            openInstagram(this)
                        }
                    )
                }
            }
        }
    }
    
    private fun scheduleReelReminders() {
        // Cancel any existing work
        WorkManager.getInstance(this).cancelUniqueWork("reel_reminder")
        
                // Schedule notifications every 2 hours during "active" hours (9 AM - 11 PM)
        val reminderWork = PeriodicWorkRequestBuilder<ReelReminderWorker>(2, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .setInitialDelay(30, TimeUnit.MINUTES) // First notification after 30 minutes
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reel_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderWork
        )
    }

    private fun openInstagram(context: Context) {
        try {
            // Try to open Instagram app
            val instagramIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
            if (instagramIntent != null) {
                context.startActivity(instagramIntent)
                Toast.makeText(context, "🎯 Time to consume some reels! Happy scrolling! 📱", Toast.LENGTH_SHORT).show()
            } else {
                // Instagram not installed, open Play Store
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.instagram.android"))
                context.startActivity(playStoreIntent)
                Toast.makeText(context, "📲 Instagram not found! Install it to start your reel addiction journey!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Fallback to web version
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com"))
                context.startActivity(webIntent)
                Toast.makeText(context, "🌐 Opening Instagram in browser. App preferred for better tracking!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Unable to open Instagram. Check your internet connection!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun calculateStreak(prefs: SharedPreferences, todayCount: Int, goal: Int): Int {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayStr = sdf.format(Date())

    // Get yesterday's date string
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1)
    val yesterdayStr = sdf.format(calendar.time)

    val lastStreakDate = prefs.getString("last_streak_date", null)
    val currentStreak = prefs.getInt("current_streak", 0)

    // If the goal is met today
    if (todayCount >= goal) {
        // If the streak was already updated today, return current
        if (lastStreakDate == todayStr) {
            return currentStreak
        }
        // If last streak was yesterday, increment. Otherwise, start new streak at 1
        val newStreak = if (lastStreakDate == yesterdayStr) currentStreak + 1 else 1
        prefs.edit()
            .putString("last_streak_date", todayStr)
            .putInt("current_streak", newStreak)
            .apply()
        return newStreak
    }
    // Goal not met today
    else {
        // If last streak wasn't today or yesterday, streak is broken
        if (lastStreakDate != todayStr && lastStreakDate != yesterdayStr) {
            if (currentStreak != 0) {
                prefs.edit().putInt("current_streak", 0).apply()
            }
            return 0
        }
        // Streak from yesterday is still valid until day ends
        return currentStreak
    }
}

@Composable
fun ReelCounterUI(
    onOpenAccessibilitySettings: () -> Unit = {},
    onOpenInstagram: () -> Unit = {}
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(Triple(0, 0, 0)) } // uniqueCount, totalDetections, adCount
    var streak by remember { mutableStateOf(0) }
    var dailyGoal by remember { mutableStateOf("100") }
    var showGoalDialog by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var coachMessage by remember { mutableStateOf("Start watching reels to get motivated! 🎯") }
    var lastToastReelCount by remember { mutableStateOf(0) }
    var notificationsEnabled by remember { 
        mutableStateOf(
            context.getSharedPreferences("reel_counter_prefs", Context.MODE_PRIVATE)
                .getBoolean("notifications_enabled", true)
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check if Instagram is installed
    val isInstagramInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.instagram.android", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // Efficiently check accessibility and listen for preference changes
    DisposableEffect(lifecycleOwner, context) {
        val prefs = context.getSharedPreferences("reel_counter_prefs", Context.MODE_PRIVATE)

        // Function to update all state from SharedPreferences
        val updateState = {
            val uniqueCount = prefs.getInt("unique_count", 0)
            val totalDetections = prefs.getInt("total_detections", 0)
            val adCount = prefs.getInt("ad_count", 0)
            val savedGoal = prefs.getInt("daily_goal", 100)

            // Check if we crossed a 100 reels milestone
            if ((uniqueCount / 100) > (lastToastReelCount / 100)) {
                Toast.makeText(
                    context,
                    "🎉 WOW! You've watched $uniqueCount reels! Keep going, champion!",
                    Toast.LENGTH_LONG
                ).show()
                lastToastReelCount = uniqueCount
            }

            stats = Triple(uniqueCount, totalDetections, adCount)
            dailyGoal = savedGoal.toString()
            streak = calculateStreak(prefs, uniqueCount, savedGoal)

            // Update coach message based on current progress
            val remaining = savedGoal - uniqueCount
            coachMessage = if (remaining > 0) {
                getRandomCoachMessage(remaining)
            } else {
                "🎯 You've hit your goal of $savedGoal reels! Why stop now? Let's go for ${savedGoal + 50}! 🚀"
            }
        }

        // Initial state load
        updateState()
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)

        // Listener for changes in SharedPreferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updateState()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Lifecycle observer to check accessibility on resume
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                updateState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Cleanup when the composable leaves the screen
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Periodically refresh the coach message
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000) // Change message every 15 seconds
            val goal = dailyGoal.toIntOrNull() ?: 100
            val remaining = goal - stats.first
            if (remaining > 0) {
                coachMessage = getRandomCoachMessage(remaining)
            }
        }
    }

    val progress = stats.first.toFloat() / (dailyGoal.toIntOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f)

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Reelatable",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scroll untill you die... 📱✨",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { showGoalDialog = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Addiction Settings")
                }
            }

            // Reel Coach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🧠",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = "Your reel Coach says:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = coachMessage,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Main Counter
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stats.first}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "reels consumed today",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = when {
                            progress < 0.5f -> Color(0xFF4CAF50)
                            progress < 0.8f -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5722)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Daily Goal: $dailyGoal reels",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // GIF Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                val imageLoader = ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()

                AsyncImage(
                    model = getMotivationalGif(stats.first, dailyGoal.toIntOrNull() ?: 100),
                    contentDescription = "Motivational GIF",
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Gray.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop
                )
            }

            // Streak Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (streak > 0) Color(0xFFFF6D00).copy(alpha = 0.15f) 
                                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔥",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "$streak day addiction streak",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (streak > 0) "Keep the dopamine flowing!" else "Start your dependency journey",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Open Instagram Button
                Button(
                    onClick = onOpenInstagram,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary 
                    )
                ) {
                    Text(
                        text = if (isInstagramInstalled) "📱 Open Instagram" else "📲 Install Instagram",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                // Enable/Settings Button
                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = {
                            onOpenAccessibilitySettings()
                            Toast.makeText(context, "🎯 Enable 'Reelatable' in Accessibility Settings to start your addiction journey!", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "🚀 Enable Tracker",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Goal Dialog
    if (showGoalDialog) {
        var tempGoal by remember { mutableStateOf(dailyGoal) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Daily Addiction Goal 🎯") },
            text = {
                Column {
                    Text(
                        text = "How many reels do you want to waste your life on today?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempGoal,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() } || value.isEmpty()) {
                                tempGoal = value
                            }
                        },
                        label = { Text("Reels per day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Reel reminder notifications",
                            fontSize = 14.sp
                        )
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newGoal = tempGoal.toIntOrNull()?.coerceAtLeast(1) ?: 100
                        val prefs = context.getSharedPreferences("reel_counter_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putInt("daily_goal", newGoal)
                            .putBoolean("notifications_enabled", notificationsEnabled)
                            .apply()
                        dailyGoal = newGoal.toString()
                        showGoalDialog = false

                        // Update coach message with new goal
                        coachMessage = "🎉 Perfect! I'll help you waste even MORE time reaching $newGoal reels! No backing out now! 😈"
                    }
                ) {
                    Text("Commit to Addiction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Maybe Later")
                }
            }
        )
    }
}

// Check if the accessibility service is enabled
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityEnabled = try {
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
    } catch (e: Settings.SettingNotFoundException) {
        false
    }

    if (!accessibilityEnabled) return false

    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )

    return enabledServices?.contains(context.packageName) ?: false
}

fun getRandomCoachMessage(remaining: Int): String {
    val messages = listOf(
        "🎯 C'mon, only $remaining more reels to go today! You got this!...",
        "💤 Sleep is for people who don't grind reels! Only $remaining left!",
        "⚡ $remaining more reels? That's nothing for a champion like you!",
        "📱 Your feed misses you. It's been whole minutes! $remaining awaiting!",
        "🔥 Real legends watch reels until their battery dies. $remaining more!",
        "🎭 You're not addicted, you just appreciate short-form content! $remaining left!",
        "👀 Those $remaining reels won't watch themselves! goo watchh",
        "📊 Your productivity can wait. These $remaining reels can't!",
        "👍 Thumb getting tired? The algorithm demands sacrifice! $remaining left!", 
        "⏰ Time enjoyed wasting isn't wasted time... right? $remaining more!",
        "✨ Each reel brings you closer to digital enlightenment! $remaining away!",
        "🧟 Your brain cells are waiting for their next $remaining hits.",
        "🏅 Professional procrastinator achievement unlocked! $remaining more to go!"
        
    )
    return messages.random()
}

fun getMotivationalGif(currentCount: Int, goal: Int): Int {
    val progress = currentCount.toFloat() / goal.toFloat()
    
    return when {
        currentCount == 0 -> R.drawable.start_motivation
        progress < 0.25f -> R.drawable.early_progress
        progress < 0.5f -> R.drawable.keep_scrolling
        progress < 0.8f -> R.drawable.almost_there
        progress >= 1.0f -> R.drawable.celebration
        else -> R.drawable.phone_addiction
    }
}

@Preview(showBackground = true)
@Composable
fun ReelCounterUIPreview() {
    ReelCounterTheme {
        ReelCounterUI()
    }
}