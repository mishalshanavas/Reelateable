package com.reelcounter.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun ReelCoachChatCard() {
    var message by remember { mutableStateOf(getKarikkuChatMessage()) }

    // Optional: auto-update every 15 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            message = getKarikkuChatMessage()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💬 Reel Coach says:",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChatBubble(message = message)
        }
    }
}

@Composable
fun ChatBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp
            )
        }
    }
}

fun getKarikkuChatMessage(): String {
    val messages = listOf(
        "Nissaram, ini 1200 reels und. Easy aanu 😎",
        "Daivame, ithrayum reels kazhinjittu randu koode kaanamo? Kaanam 🔥",
        "Ullil superstar und. Appo enthina fear? Swipe cheyy! 🎬",
        "Ini onchirikanam. Algorithm kaananam njangal arannu! 🧠",
        "Nee cool aanu. Pakshe reels kaanathe aa cool maintain cheyyan patilla 😤",
        "Ithu kaanathe urangano? Nee aara 😒",
        "Chetan ready aano? Algorithm already wait cheyyunnu 😈",
        "Thumb workout miss cheyyaruthu innu 💪📱",
        "Daivam kandittu daivam aaki. Ippo reels kaananam 😌",
        "Innathe quota full complete cheyyanam. Allenkil streak poykotte 😢",
        "Swipe cheyy, productivity enna vishayam shesham nokkam 😏",
        "Just 3 more reels, promise. Athu kazhinjal mathram kazhinjilla 😂"
    )
    return messages.random()
}
