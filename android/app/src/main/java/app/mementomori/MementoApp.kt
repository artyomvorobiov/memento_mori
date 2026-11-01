package app.mementomori

import android.app.Application
import android.content.Context
import app.mementomori.data.AppModel
import app.mementomori.reminders.ReminderScheduler

class MementoApp : Application() {
    /** Единственный источник правды для приложения, виджетов и напоминаний. */
    val model: AppModel by lazy { AppModel(this) }

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
    }

    companion object {
        fun model(context: Context): AppModel = (context.applicationContext as MementoApp).model
    }
}
