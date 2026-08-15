package com.travelingsalesman.agap.core.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide emergency notification coordinator.
 *
 * Notification policy:
 * - 1-2 new SOS events in the batching window may be shown individually.
 * - 3+ new SOS events collapse into one summary notification.
 * - once a burst enters summary mode, subsequent events keep updating that one
 *   summary for a short period instead of rebuilding a notification storm.
 * - only one audible/vibration alert is allowed per minute during sustained bursts.
 *
 * A bulk Room/API synchronization can add hundreds of incidents in a few seconds.
 * This class intentionally keeps notification presentation bounded while leaving
 * incident, relay, ACK, and upload state untouched.
 */
@Singleton
class EmergencyNotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "agap_sos_channel"

        private const val SUMMARY_NOTIFICATION_ID = 0xA6A9
        private const val INDIVIDUAL_NOTIFICATION_ID_1 = 0xA6AA
        private const val INDIVIDUAL_NOTIFICATION_ID_2 = 0xA6AB

        /** 3 or more new incidents are always summarized. */
        private const val SUMMARY_THRESHOLD = 3

        private const val BATCH_WINDOW_MS = 2_500L
        private const val SUMMARY_MODE_HOLD_MS = 60_000L
        private const val MIN_AUDIBLE_ALERT_INTERVAL_MS = 60_000L
        private const val RECENT_KEY_TTL_MS = 10 * 60_000L
        private const val MAX_RECENT_KEYS = 2_048
        private const val MAX_LINES = 5
    }

    private data class AlertItem(
        val key: String,
        val line: String
    )

    private data class BatchSnapshot(
        val count: Int,
        val pending: Int?,
        val lines: List<String>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val recentKeys = LinkedHashMap<String, Long>()
    private val recentLines = ArrayDeque<String>(MAX_LINES)

    private var batchCount = 0
    private var pendingCountHint: Int? = null
    private var flushJob: Job? = null
    @Volatile
    private var lastAudibleAlertAt = 0L

    @Volatile
    private var summaryModeUntil = 0L

    /**
     * Adds a whole Room emission as one notification batch. Callers should pass
     * only incidents that are newly observed compared with their previous IDs.
     */
    fun recordIncidents(
        incidents: Collection<IncidentEntity>,
        pendingCount: Int? = null
    ) {
        if (incidents.isEmpty()) return
        val items = incidents.map { incident ->
            val stableIdentity = incident.packetHash.takeIf { it.isNotBlank() }
                ?: incident.incidentId
            val path = when {
                incident.channel.contains("BLE", ignoreCase = true) -> "Relay"
                incident.deliveryPath.contains("MESH", ignoreCase = true) -> "Relay"
                else -> "Online"
            }
            AlertItem(
                key = "SOS:$stableIdentity",
                line = "${incident.emergencyType} • $path • #${incident.incidentId.take(12)}"
            )
        }
        enqueue(items, pendingCount)
    }

    /**
     * Raw relay receive path. Uses the transport token as the same logical key
     * later used by IncidentEntity.packetHash, preventing BLE/Nearby + Room from
     * producing two alerts for one emergency.
     */
    fun recordRelayEmergency(
        transportToken: String,
        emergencyType: String,
        hopCount: Int
    ) {
        if (transportToken.isBlank()) return
        enqueue(
            listOf(
                AlertItem(
                    key = "SOS:$transportToken",
                    line = "$emergencyType • Relay hop $hopCount • ${transportToken.take(8)}"
                )
            ),
            pendingCount = null
        )
    }

    private fun enqueue(items: Collection<AlertItem>, pendingCount: Int?) {
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                pruneRecentLocked(now)

                items.forEach { item ->
                    val seenAt = recentKeys[item.key]
                    if (seenAt != null && now - seenAt <= RECENT_KEY_TTL_MS) {
                        return@forEach
                    }

                    recentKeys[item.key] = now
                    trimRecentKeysLocked()

                    // Count every unique alert, but retain only a tiny number of
                    // human-readable lines. Memory stays bounded even for a burst
                    // containing hundreds or thousands of SOS rows.
                    batchCount++
                    if (recentLines.size >= MAX_LINES) {
                        recentLines.removeFirst()
                    }
                    recentLines.addLast(item.line)
                }

                if (pendingCount != null) {
                    pendingCountHint = pendingCount.coerceAtLeast(0)
                }

                if (batchCount > 0 && flushJob?.isActive != true) {
                    flushJob = scope.launch {
                        delay(BATCH_WINDOW_MS)
                        flush()
                    }
                }
            }
        }
    }

    private suspend fun flush() {
        val snapshot = mutex.withLock {
            if (batchCount <= 0) {
                flushJob = null
                null
            } else {
                BatchSnapshot(
                    count = batchCount,
                    pending = pendingCountHint,
                    lines = recentLines.toList()
                ).also {
                    batchCount = 0
                    pendingCountHint = null
                    recentLines.clear()
                    flushJob = null
                }
            }
        } ?: return

        if (!canPostNotifications()) return

        val now = System.currentTimeMillis()
        val shouldAlert = now - lastAudibleAlertAt >= MIN_AUDIBLE_ALERT_INTERVAL_MS
        if (shouldAlert) {
            lastAudibleAlertAt = now
        }

        val useSummary = snapshot.count >= SUMMARY_THRESHOLD || now < summaryModeUntil
        if (snapshot.count >= SUMMARY_THRESHOLD) {
            summaryModeUntil = now + SUMMARY_MODE_HOLD_MS
        }

        if (useSummary) {
            postSummary(snapshot, shouldAlert)
        } else {
            postIndividual(snapshot, shouldAlert)
        }
    }

    /**
     * 3+ incidents become one card. The same notification ID is reused so 100
     * incidents update one notification instead of generating 100 SystemUI rows.
     */
    @SuppressLint("MissingPermission")
    private fun postSummary(snapshot: BatchSnapshot, shouldAlert: Boolean) {
        if (!canPostNotifications()) return
        val manager = NotificationManagerCompat.from(context)

        // If a burst transitions from 1-2 individual alerts into summary mode,
        // remove the two bounded individual slots so the phone presents one clear card.
        manager.cancel(INDIVIDUAL_NOTIFICATION_ID_1)
        manager.cancel(INDIVIDUAL_NOTIFICATION_ID_2)

        val title = if (snapshot.count >= SUMMARY_THRESHOLD) {
            "Multiple emergencies detected"
        } else {
            "Emergency activity continues"
        }

        val content = when {
            snapshot.pending != null && snapshot.pending > 0 ->
                if (snapshot.pending == 1) {
                    "1 SOS incident pending review"
                } else {
                    "${snapshot.pending} SOS incidents pending review"
                }

            snapshot.count >= SUMMARY_THRESHOLD ->
                "${snapshot.count} new SOS alerts need attention"

            else ->
                "New emergency activity is still arriving"
        }

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(
                snapshot.pending?.takeIf { it > 0 }?.let { "$it pending" }
                    ?: "${snapshot.count} new SOS alerts"
            )
        snapshot.lines.forEach(style::addLine)

        val builder = baseBuilder()
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(style)
            .setNumber((snapshot.pending ?: snapshot.count).coerceAtLeast(1))
            .setGroup("agap_sos_alerts")

        applyAlertPolicy(builder, shouldAlert)

        runCatching {
            manager.notify(SUMMARY_NOTIFICATION_ID, builder.build())
        }
    }

    /**
     * A small event count remains specific and readable. Only two fixed slots
     * exist, so even repeated low-volume batches cannot accumulate unlimited cards.
     */
    @SuppressLint("MissingPermission")
    private fun postIndividual(snapshot: BatchSnapshot, shouldAlert: Boolean) {
        if (!canPostNotifications()) return
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(SUMMARY_NOTIFICATION_ID)

        val lines = snapshot.lines.takeLast(SUMMARY_THRESHOLD - 1)
        val ids = intArrayOf(
            INDIVIDUAL_NOTIFICATION_ID_1,
            INDIVIDUAL_NOTIFICATION_ID_2
        )

        // Clear the second slot when this batch contains only one alert, avoiding
        // stale text from an earlier two-alert batch.
        if (lines.size < 2) {
            manager.cancel(INDIVIDUAL_NOTIFICATION_ID_2)
        }

        lines.forEachIndexed { index, line ->
            val builder = baseBuilder()
                .setContentTitle("New SOS emergency")
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                .setNumber(1)
                .setGroup("agap_sos_alerts")

            // Only the first card in the small batch may alert audibly. The second
            // card is visible but silent, preventing two back-to-back vibrations.
            applyAlertPolicy(builder, shouldAlert && index == 0)

            runCatching {
                manager.notify(ids[index], builder.build())
            }
        }
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
    }

    private fun applyAlertPolicy(
        builder: NotificationCompat.Builder,
        shouldAlert: Boolean
    ) {
        if (shouldAlert) {
            builder
                .setOnlyAlertOnce(false)
                .setVibrate(longArrayOf(0, 500, 200, 500))
        } else {
            builder
                .setOnlyAlertOnce(true)
                .setSilent(true)
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pruneRecentLocked(now: Long) {
        val iterator = recentKeys.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > RECENT_KEY_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun trimRecentKeysLocked() {
        while (recentKeys.size > MAX_RECENT_KEYS) {
            val firstKey = recentKeys.entries.firstOrNull()?.key ?: break
            recentKeys.remove(firstKey)
        }
    }
}
