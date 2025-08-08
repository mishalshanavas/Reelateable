package com.reelcounter

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelAccessibilityService : AccessibilityService() {

    private val seenReelIds = mutableSetOf<String>()
    private var totalDetections = 0
    private var uniqueReelCount = 0
    private var adCount = 0

    private var lastIdentifier = ""
    private var lastDetectionTime = 0L
    private val debounceMillis = 3000L

    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("reel_counter_prefs", MODE_PRIVATE)

        uniqueReelCount = prefs.getInt("unique_count", 0)
        totalDetections = prefs.getInt("total_detections", 0)
        adCount = prefs.getInt("ad_count", 0)
        seenReelIds.addAll(prefs.getStringSet("seen_ids", emptySet()) ?: emptySet())

        Log.d("ReelCounter", "✅ Service connected. Stats: $uniqueReelCount unique, $totalDetections total, $adCount ads")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val rootNode = rootInActiveWindow ?: return
        val uiDump = getAllNodeText(rootNode)

        if (isAd(uiDump)) {
            adCount++
            saveStats()
            Log.d("ReelCounter", "🛡️ Ad skipped. Total ads: $adCount")
            return
        }

        if (!uiDump.contains("Reels", ignoreCase = true)) return

        val identifier = extractStableReelIdentifier(uiDump)
        if (identifier.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < debounceMillis) {
            Log.d("ReelCounter", "⏳ Debounced. ID: $identifier")
            return
        }

        lastIdentifier = identifier
        lastDetectionTime = now
        totalDetections++

        if (seenReelIds.add(identifier)) {
            uniqueReelCount++
            Log.d("ReelCounter", "🎯 New Reel! Unique: $uniqueReelCount | Total: $totalDetections | ID: $identifier")

            when {
                uniqueReelCount % 50 == 0 -> Log.d("ReelCounter", "🎉 Milestone: $uniqueReelCount reels! You're a reel machine.")
                uniqueReelCount % 10 == 0 -> Log.d("ReelCounter", "🔥 $uniqueReelCount reels consumed. Dopamine's winning!")
            }
        } else {
            Log.d("ReelCounter", "🔁 Seen before. ID: $identifier")
        }

        saveStats()
    }

    override fun onInterrupt() {
        Log.d("ReelCounter", "⚠️ Accessibility service interrupted")
    }

    private fun saveStats() {
        prefs.edit().apply {
            putInt("unique_count", uniqueReelCount)
            putInt("total_detections", totalDetections)
            putInt("ad_count", adCount)
            putLong("last_update", System.currentTimeMillis())
            putStringSet("seen_ids", seenReelIds)
            apply()
        }
    }

    private fun extractStableReelIdentifier(uiDump: String): String {
        val username = Regex("@([a-zA-Z0-9_.]+)").find(uiDump)?.groupValues?.get(1)

        val likeCount = Regex("Like number is\\s*(\\d+)|(\\d+)\\s*likes?", RegexOption.IGNORE_CASE)
            .find(uiDump)
            ?.groupValues
            ?.firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
            ?.toIntOrNull()

        val commentCount = Regex("Comment number is\\s*(\\d+)|(\\d+)\\s*comments?", RegexOption.IGNORE_CASE)
            .find(uiDump)
            ?.groupValues
            ?.firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
            ?.toIntOrNull()

        val duration = Regex("(\\d+):(\\d+)").find(uiDump)?.value?.replace(":", "m")

        val captionWords = extractCaptionWords(uiDump)

        return when {
            username != null && likeCount != null && commentCount != null ->
                "reel_${username}_${getLikeRange(likeCount)}_${getCommentRange(commentCount)}"

            username != null && likeCount != null ->
                "reel_${username}_${getLikeRange(likeCount)}_0C"

            username != null && commentCount != null ->
                "reel_${username}_0L_${getCommentRange(commentCount)}"

            username != null && duration != null ->
                "reel_${username}_dur_${duration}_${captionWords.hashCode()}"

            username != null ->
                "reel_${username}_cap_${captionWords.hashCode()}"

            likeCount != null && commentCount != null ->
                "reel_anon_${getLikeRange(likeCount)}_${getCommentRange(commentCount)}"

            else -> "reel_stable_${(username ?: "")}_${captionWords.hashCode()}"
        }
    }

    private fun extractCaptionWords(uiDump: String): String {
        return uiDump.split("\\s+".toRegex())
            .filter { it.length > 3 && !it.contains("@") && it.any(Char::isLetter) }
            .take(3)
            .joinToString("_")
            .take(20)
            .ifEmpty { "nocap" }
    }

    private fun getLikeRange(likes: Int): String = when {
        likes < 10 -> "0-9L"
        likes < 100 -> "${(likes / 10) * 10}-${((likes / 10) + 1) * 10 - 1}L"
        likes < 1000 -> "${(likes / 100) * 100}-${((likes / 100) + 1) * 100 - 1}L"
        likes < 10000 -> "${(likes / 1000) * 1000}-${((likes / 1000) + 1) * 1000 - 1}L"
        else -> "${likes / 1000}kL"
    }

    private fun getCommentRange(comments: Int): String = when {
        comments < 10 -> "0-9C"
        comments < 100 -> "${(comments / 10) * 10}-${((comments / 10) + 1) * 10 - 1}C"
        else -> "${comments / 100 * 100}+C"
    }

    private fun isAd(uiDump: String): Boolean {
        val lower = uiDump.lowercase()
        val keywords = listOf("sponsored", "promoted", "paid partnership", "paid promotion")
        val found = keywords.any { lower.contains(it) }

        val labelMatch = Regex("(sponsored|promoted|paid partnership)\\s*(•|·)", RegexOption.IGNORE_CASE)
            .containsMatchIn(uiDump)

        if (found || labelMatch) {
            Log.d("ReelCounter", "🛡️ Ad detected using keywords")
        }

        return found || labelMatch
    }

    private fun getAllNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()

        node.text?.let { if (it.isNotBlank()) sb.append(it).append(" ") }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            sb.append(getAllNodeText(node.getChild(i)))
        }

        return sb.toString()
    }

    // For external UI (optional)
    fun getStats(): Triple<Int, Int, Int> = Triple(uniqueReelCount, totalDetections, adCount)

    fun clearHistory() {
        seenReelIds.clear()
        uniqueReelCount = 0
        totalDetections = 0
        adCount = 0
        lastIdentifier = ""
        lastDetectionTime = 0
        saveStats()
        Log.d("ReelCounter", "🧼 History wiped. Fresh start!")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ReelCounter", "💀 Accessibility service stopped")
    }
}
