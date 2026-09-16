package it.ciano.expensetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import it.ciano.expensetracker.data.preferences.UserPreferences
import it.ciano.expensetracker.data.reminder.RecurringReminderReceiver

class ExpenseTrackerApp : Application() {

    override fun attachBaseContext(base: Context) {
        val code = UserPreferences(base).getAppLanguage()
        super.attachBaseContext(LocaleHelper.wrap(base, code))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RecurringReminderReceiver.CHANNEL_ID,
                getString(R.string.str_promemoria_scadenza),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
