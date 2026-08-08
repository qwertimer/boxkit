package com.qwertimer.forge.enforce

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qwertimer.forge.MainActivity
import com.qwertimer.forge.R
import java.time.LocalDateTime

object NotificationChannels {
    const val REMINDER = "workout_reminder"
    const val NAG = "workout_nag"
    const val ENFORCE = "workout_enforce"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_reminder_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                NAG,
                context.getString(R.string.channel_nag_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_nag_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            },
        )

        // The enforcement channel deliberately behaves like an alarm clock: it bypasses Do Not
        // Disturb only if the user grants that, but it always sounds and always vibrates.
        manager.createNotificationChannel(
            NotificationChannel(
                ENFORCE,
                context.getString(R.string.channel_enforce_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_enforce_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }
}

/** Builds and posts the escalating reminders. */
class Notifier(private val context: Context) {

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun postNag(
        stage: Int,
        firstReminder: LocalDateTime,
        title: String,
        body: String,
        escalating: Boolean,
    ) {
        if (!canPost()) return

        val builder = NotificationCompat.Builder(
            context,
            if (escalating) NotificationChannels.NAG else NotificationChannels.REMINDER,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openWorkoutIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (escalating) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_notification,
                "Start now",
                openWorkoutIntent(),
            )
            .addAction(
                R.drawable.ic_notification,
                "Snooze ${NagActionReceiver.SNOOZE_MINUTES}m",
                actionIntent(NagActionReceiver.ACTION_SNOOZE, stage, firstReminder),
            )

        post(builder.build())
    }

    /**
     * The last resort: a full-screen intent. On a locked or idle device this launches
     * [EnforcementActivity] outright; otherwise it lands as a heads-up notification that opens it.
     */
    fun postEnforcement(title: String, body: String) {
        if (!canPost()) return

        val fullScreen = PendingIntent.getActivity(
            context,
            REQUEST_ENFORCE,
            Intent(context, EnforcementActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.ENFORCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()

        post(notification)
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * [canPost] already gates every call, but the permission can be revoked between that check and
     * this one, and a few OEM builds throw regardless. A reminder that fails to post is not worth
     * taking the process down for.
     */
    private fun post(notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w("Forge", "Notification rejected", e)
        }
    }

    private fun openWorkoutIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(
        action: String,
        stage: Int,
        firstReminder: LocalDateTime,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, NagActionReceiver::class.java)
            .setAction(action)
            .putExtra(NagAlarmReceiver.EXTRA_STAGE, stage)
            .putExtra(NagAlarmReceiver.EXTRA_FIRST_REMINDER, firstReminder.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        /** One id, reused, so escalating reminders replace each other instead of stacking up. */
        const val NOTIFICATION_ID = 1001
        const val REQUEST_OPEN = 2001
        const val REQUEST_ENFORCE = 2002
    }
}
