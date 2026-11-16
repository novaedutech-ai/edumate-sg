package sg.edumate.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import sg.edumate.app.BuildConfig
import sg.edumate.app.data.remote.EduMateApi
import sg.edumate.app.data.repository.RoomLessonPlanRepository
import sg.edumate.domain.model.Assignment
import sg.edumate.domain.model.SyncState
import sg.edumate.domain.usecase.ApplyRetentionPolicyUseCase
import java.util.concurrent.TimeUnit

/** Uploads locally edited plans when online. Conflicts are resolved server-side by version. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val plans: RoomLessonPlanRepository, private val api: EduMateApi,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.USE_MOCK_BACKEND) return Result.success()
        return try {
            plans.pendingUpload().forEach { p -> api.putLessonPlan(p.id, p); plans.upsert(p.copy(syncState = SyncState.SYNCED)) }
            Result.success()
        } catch (e: java.io.IOException) { Result.retry() }
    }
}

@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters, private val retention: ApplyRetentionPolicyUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { retention(); return Result.success() }
}

/** Assignment due reminder. Notification text contains no student data. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(applicationContext).notify(title.hashCode(),
                NotificationCompat.Builder(applicationContext, CHANNEL_REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Assignment due soon")
                    .setContentText(title)
                    .setAutoCancel(true)
                    .build())
        }
        return Result.success()
    }
    companion object { const val KEY_TITLE = "title" }
}

const val CHANNEL_REMINDERS = "reminders"

object WorkScheduler {
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Assignment reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork("retention", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())
        wm.enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    fun syncNow(context: Context) = WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())

    fun scheduleReminder(context: Context, a: Assignment, nowMs: Long) {
        val delay = a.dueEpochMs - a.remindBeforeMinutes * 60_000L - nowMs
        if (delay <= 0) return
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-${a.id}", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.KEY_TITLE to a.title)).build())
    }
}
