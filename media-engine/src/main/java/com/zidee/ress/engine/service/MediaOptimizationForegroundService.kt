package com.zidee.ress.engine.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps heavy offline photo/video optimization alive on low-spec devices when the user
 * switches apps, and exposes a notification Cancel action.
 */
class MediaOptimizationForegroundService : Service() {
    private var isForegroundStarted: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // Ensure startForeground was called if an earlier startForegroundService is still pending.
                ensureForegroundStarted(DEFAULT_LABEL, 0)
                activeSessions.values.forEach { handle ->
                    handle.job?.cancel(CancellationException("Cancelled from notification"))
                }
                activeSessions.clear()
                if (pendingForegroundStarts.get() <= 0) {
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }
            else -> {
                val progressPercent = intent?.getIntExtra(EXTRA_PROGRESS_PERCENT, 0)?.coerceIn(0, 100) ?: 0
                val label = intent?.getStringExtra(EXTRA_LABEL) ?: DEFAULT_LABEL
                ensureForegroundStarted(label, progressPercent)
                decrementPendingStarts()
                // If the job already finished or failed before onStartCommand ran, stop cleanly now
                // after startForeground() satisfied Android's ForegroundServiceDidNotStartInTime contract.
                if (activeSessions.isEmpty() && pendingForegroundStarts.get() <= 0) {
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        if (isForegroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        super.onDestroy()
    }

    private fun ensureForegroundStarted(
        label: String,
        progressPercent: Int,
    ) {
        val notification = buildNotification(this, label, progressPercent)
        val serviceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        isForegroundStarted = true
    }

    private fun stopSelfSafely() {
        if (isForegroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID: String = "zidee_media_optimization"
        const val NOTIFICATION_ID: Int = 1001

        private const val ACTION_START_OR_UPDATE = "com.zidee.ress.action.OPTIMIZATION_UPDATE"
        private const val ACTION_CANCEL = "com.zidee.ress.action.OPTIMIZATION_CANCEL"
        private const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        private const val EXTRA_LABEL = "extra_label"
        private const val DEFAULT_LABEL = "Mengoptimalkan media (Zidee Ress)..."

        private class SessionHandle(val job: Job?)

        private val nextSessionId = AtomicInteger(1)
        private val pendingForegroundStarts = AtomicInteger(0)
        private val activeSessions = ConcurrentHashMap<Int, SessionHandle>()

        private fun decrementPendingStarts() {
            pendingForegroundStarts.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        }

        fun ensureNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Optimasi Media Offline",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Menjaga proses optimasi foto dan video tetap berjalan di latar belakang"
                    setShowBadge(false)
                }
            manager.createNotificationChannel(channel)
        }

        internal fun registerJob(job: Job?): Int {
            val id = nextSessionId.getAndIncrement()
            activeSessions[id] = SessionHandle(job)
            return id
        }

        internal fun unregisterJob(id: Int) {
            activeSessions.remove(id)
        }

        internal fun activeJobCount(): Int = activeSessions.size

        fun startOrUpdate(
            context: Context,
            label: String,
            progressPercent: Int,
        ) {
            val intent =
                Intent(context, MediaOptimizationForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_PROGRESS_PERCENT, progressPercent.coerceIn(0, 100))
                }
            pendingForegroundStarts.incrementAndGet()
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                decrementPendingStarts()
                throw t
            }
        }

        fun updateProgressNotification(
            context: Context,
            label: String,
            progressPercent: Int,
        ) {
            if (activeSessions.isEmpty()) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureNotificationChannel(context)
            manager.notify(NOTIFICATION_ID, buildNotification(context, label, progressPercent.coerceIn(0, 100)))
        }

        fun stopIfIdle(context: Context) {
            if (activeSessions.isNotEmpty()) return
            // Never call stopService while a startForegroundService() call has not yet delivered
            // onStartCommand -> startForeground(); onStartCommand will stop itself cleanly instead.
            if (pendingForegroundStarts.get() > 0) return
            context.stopService(Intent(context, MediaOptimizationForegroundService::class.java))
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }

        private fun buildNotification(
            context: Context,
            label: String,
            progressPercent: Int,
        ): Notification {
            val cancelIntent =
                Intent(context, MediaOptimizationForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                }
            val cancelPendingIntent =
                PendingIntent.getService(
                    context,
                    0,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Zidee Ress")
                .setContentText("$label ($progressPercent%)")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progressPercent, progressPercent == 0)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPendingIntent)
                .build()
        }
    }
}
