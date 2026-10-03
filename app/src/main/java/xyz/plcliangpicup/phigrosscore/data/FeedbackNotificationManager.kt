package xyz.plcliangpicup.phigrosscore.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.MainActivity
import xyz.plcliangpicup.phigrosscore.R
import java.util.concurrent.TimeUnit

object FeedbackNotificationManager {
    const val EXTRA_FEEDBACK = "open_my_feedback"
    const val CHANNEL = "feedback_updates"
    private const val IN_APP_PREFERENCES = "feedback_in_app_notifications"
    private const val IN_APP_UNREAD_COUNT = "unread_count"
    private val inAppRevisions = mutableMapOf<String, Long>()
    private val inAppUnreadState = MutableStateFlow(0)
    private val inAppUnreadFlow = inAppUnreadState.asStateFlow()
    private var inAppStateLoaded = false

    // WorkManager is only a recovery path when Android has suspended the live connection.
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork("feedback-updates", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FeedbackNotificationWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build())
        manager.enqueueUniqueWork("feedback-updates-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FeedbackNotificationWorker>().setConstraints(constraints).build())
    }

    internal fun observeInAppUnread(context: Context): StateFlow<Int> {
        ensureInAppStateLoaded(context)
        return inAppUnreadFlow
    }

    @Synchronized
    internal fun updateInAppUnread(context: Context, count: Int) {
        val appContext = context.applicationContext
        ensureInAppStateLoaded(appContext)
        val unread = count.coerceAtLeast(0)
        inAppPreferences(appContext).edit().putInt(IN_APP_UNREAD_COUNT, unread).apply()
        inAppUnreadState.value = unread
    }

    @Synchronized
    internal fun clearInAppUnread(context: Context) {
        updateInAppUnread(context, 0)
    }

    @Synchronized
    fun resetInApp() { inAppRevisions.clear() }

    /** Both live updates and background recovery use this revision-based deduplication. */
    @Synchronized
    fun publish(context: Context, items: List<Feedback>): List<Feedback> {
        updateInAppUnread(context, items.size)
        val preferences = context.getSharedPreferences("feedback_notification_revisions", Context.MODE_PRIVATE)
        val manager = NotificationManagerCompat.from(context)
        val canNotify = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "反馈处理通知", NotificationManager.IMPORTANCE_DEFAULT))
        val newItems = items.filter { it.revision > (inAppRevisions[it.id] ?: preferences.getLong(it.id, 0)) }
        items.forEach { item ->
            inAppRevisions[item.id] = item.revision
            if (canNotify && preferences.getLong(item.id, 0) < item.revision) {
                val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_FEEDBACK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending = PendingIntent.getActivity(context, item.id.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val delivered = runCatching {
                    manager.notify("feedback", item.id.hashCode(), NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("反馈有新进展")
                        .setContentText("${item.title} · ${item.statusLabel}").setContentIntent(pending)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
                }.isSuccess
                if (delivered) preferences.edit().putLong(item.id, item.revision).apply()
            }
        }
        return newItems
    }

    private fun inAppPreferences(context: Context) =
        context.getSharedPreferences(IN_APP_PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensureInAppStateLoaded(context: Context) {
        if (!inAppStateLoaded) {
            inAppUnreadState.value = inAppPreferences(context.applicationContext)
                .getInt(IN_APP_UNREAD_COUNT, 0)
                .coerceAtLeast(0)
            inAppStateLoaded = true
        }
    }
}

class FeedbackNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = AppRepository(applicationContext, BuildConfig.API_BASE_URL)
        if (!repository.hasSession) return Result.success()
        return try {
            FeedbackNotificationManager.publish(applicationContext, repository.feedbackList(notifications = true))
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
