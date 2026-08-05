package dev.memoji.flashcards.core.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.memoji.flashcards.MainActivity
import dev.memoji.flashcards.R
import javax.inject.Inject

internal class AndroidReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderNotifier {

    private val notificationManager = NotificationManagerCompat.from(context)

    override fun notifyReminder() {
        // Asked rather than assumed: the permission can be taken away at any time from system
        // settings, and posting into a channel the user has silenced is not worth an
        // exception in the log.
        if (!notificationsAllowed()) return

        createChannel()
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // The permission can go away between the question above and the answer here, and
            // a reminder that could not be delivered is not something to crash the app over.
            // Tomorrow's is already booked; if the user grants it again, it arrives again.
        }
    }

    /**
     * Two ways to be silenced, and both have to be asked about. `areNotificationsEnabled`
     * covers the app — the permission refused, or notifications turned off wholesale — but a
     * user can leave those alone and mute this one channel, and an app that only asked the
     * first question would show a switch reading on while every reminder went nowhere.
     */
    override fun notificationsAllowed(): Boolean = notificationManager.areNotificationsEnabled() &&
        notificationManager.getNotificationChannel(CHANNEL_ID)
            ?.let { it.importance != NotificationManager.IMPORTANCE_NONE }
        // No channel yet means no reminder has ever been posted, which is not the user having
        // silenced anything. It is created on the first post.
        ?: true

    /**
     * Created at post time rather than at startup, so an install that never turns reminders on
     * never grows a channel the user can find in system settings and wonder about.
     */
    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                // Default rather than high: this is a nudge, not something to interrupt for.
                // A heads-up card over whatever the user is doing is exactly the wrong tone.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.reminder_title))
        .setContentText(context.getString(R.string.reminder_body))
        .setContentIntent(openTheApp())
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Tapping it is the whole interaction; it should not still be there afterwards.
        .setAutoCancel(true)
        .build()

    /**
     * Opens the app where the user left it rather than starting a second copy — coming back
     * mid-Session to a fresh Decks screen would lose their place.
     */
    private fun openTheApp(): PendingIntent = PendingIntent.getActivity(
        context,
        /* requestCode = */ 0,
        // SINGLE_TOP alongside CLEAR_TOP is what makes this reuse the activity rather than
        // tear it down and build a new one — CLEAR_TOP on its own recreates a standard-launch
        // activity, which is the fresh start this is trying to avoid.
        Intent(context, MainActivity::class.java)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CHANNEL_ID = "daily_reminder"

        /** Fixed, so today's reminder replaces yesterday's rather than piling up beside it. */
        const val NOTIFICATION_ID = 1
    }
}
