package app.mementomori.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mementomori.MainActivity
import app.mementomori.MementoApp
import app.mementomori.R
import app.mementomori.core.AppData
import app.mementomori.core.ReminderComposer
import app.mementomori.core.ReminderPlanner
import app.mementomori.data.Prefs
import java.time.Instant

/**
 * Локальные напоминания. Расписание строит ReminderPlanner (общая логика), здесь — будильники Android.
 * Будильники неточные: система может сдвинуть их на несколько минут, зато не нужно особых разрешений.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "reminders"
    private const val EXTRA_ID = "id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_reminders_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Снимает старые будильники и ставит новые по текущим данным. */
    fun reschedule(context: Context, data: AppData) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = Prefs(context)
        for (index in 0 until prefs.scheduledReminders) {
            alarms.cancel(pendingIntent(context, index, null))
        }
        val plan = ReminderPlanner.plan(data, Instant.now())
        plan.forEachIndexed { index, item ->
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, item.id)
                .putExtra(EXTRA_TITLE, item.title)
                .putExtra(EXTRA_BODY, item.body)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.date.toEpochMilli(), pendingIntent(context, index, intent))
        }
        prefs.scheduledReminders = plan.size
    }

    private fun pendingIntent(context: Context, index: Int, intent: Intent?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            index,
            intent ?: Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun show(context: Context, id: String, title: String, body: String) {
        if (!canNotify(context)) return
        // Письмо открывается сразу, остальное — главный экран.
        val link = when {
            id.startsWith("memento.letter.") -> "mementomori://letter/" + id.removePrefix("memento.letter.")
            id.startsWith("memento.counter.") -> "mementomori://counter/" + id.removePrefix("memento.counter.")
            else -> null
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            link?.let { data = Uri.parse(it) }
        }
        val content = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFF2A541.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(content)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом.
        }
    }

    /** Пробное уведомление — сразу. */
    fun sendTest(context: Context, data: AppData) {
        val message = data.profile?.let { ReminderComposer.daily(Instant.now(), it, data.goals) } ?: ReminderComposer.quote(Instant.now())
        show(context, "memento.test", message.title, message.body)
    }

    internal fun fire(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        show(context, id, intent.getStringExtra(EXTRA_TITLE) ?: "Memento mori", intent.getStringExtra(EXTRA_BODY) ?: "")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.fire(context, intent)
    }
}

/** После перезагрузки, обновления или смены часового пояса будильники нужно поставить заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val model = MementoApp.model(context)
        if (!model.isDemo) ReminderScheduler.reschedule(context, model.data.value)
    }
}
