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
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.MainActivity
import xyz.plcliangpicup.phigrosscore.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.TimeUnit

object SuggestionNotificationManager {
    const val EXTRA_POST_ID = "suggestion_post_id"
    const val CHANNEL_ID = "suggestion_comments"
    private const val PREFERENCES = "suggestion_notifications"
    private const val ENABLED = "enabled"
    private const val CURSOR = "cursor"
    private const val IN_APP_UNREAD = "in_app_unread"
    private const val PERIODIC_WORK = "suggestion-comment-notifications"
    private const val IMMEDIATE_WORK = "suggestion-comment-notifications-now"
    private val inAppUnreadState = MutableStateFlow(false)
    private val inAppUnreadFlow = inAppUnreadState.asStateFlow()
    private var inAppStateLoaded = false

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, false)

    internal fun observeInAppUnread(context: Context): StateFlow<Boolean> {
        ensureInAppStateLoaded(context)
        return inAppUnreadFlow
    }

    @Synchronized
    internal fun markInAppUnread(context: Context) {
        val appContext = context.applicationContext
        ensureInAppStateLoaded(appContext)
        preferences(appContext).edit { putBoolean(IN_APP_UNREAD, true) }
        inAppUnreadState.value = true
    }

    @Synchronized
    internal fun clearInAppUnread(context: Context) {
        val appContext = context.applicationContext
        ensureInAppStateLoaded(appContext)
        preferences(appContext).edit { putBoolean(IN_APP_UNREAD, false) }
        inAppUnreadState.value = false
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        preferences(appContext).edit {
            putBoolean(ENABLED, enabled)
            if (enabled) putString(CURSOR, Instant.now().toString()) else remove(CURSOR)
        }
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(IMMEDIATE_WORK)
            return
        }
        createChannel(appContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SuggestionNotificationWorker>()
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SuggestionNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build(),
        )
    }

    internal fun cursor(context: Context): String =
        preferences(context).getString(CURSOR, null) ?: Instant.now().toString()

    internal fun updateCursor(context: Context, cursor: String) {
        preferences(context).edit { putString(CURSOR, cursor) }
    }

    internal fun notify(context: Context, item: SuggestionNotificationItem) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(appContext)
        val intent = Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_POST_ID, item.postId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            item.postId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = item.commentCount.coerceAtLeast(1)
        val message = "您的求建议帖子“${item.postTitle}”获得了${count}条评论，点击查看"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("求建议有新评论")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(appContext).notify(item.postId.hashCode(), notification)
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "求建议评论",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "求建议帖子收到新评论时提醒" },
        )
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    private fun ensureInAppStateLoaded(context: Context) {
        if (!inAppStateLoaded) {
            inAppUnreadState.value = preferences(context.applicationContext).getBoolean(IN_APP_UNREAD, false)
            inAppStateLoaded = true
        }
    }
}

class SuggestionNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!SuggestionNotificationManager.isEnabled(applicationContext)) return Result.success()
        val repository = AppRepository(applicationContext, BuildConfig.API_BASE_URL)
        if (!repository.hasSession) return Result.success()
        return runCatching {
            val response = repository.fetchSuggestionNotifications(
                SuggestionNotificationManager.cursor(applicationContext),
            )
            if (response.items.isNotEmpty()) {
                SuggestionNotificationManager.markInAppUnread(applicationContext)
            }
            response.items.forEach { SuggestionNotificationManager.notify(applicationContext, it) }
            SuggestionNotificationManager.updateCursor(applicationContext, response.checkedAt)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
