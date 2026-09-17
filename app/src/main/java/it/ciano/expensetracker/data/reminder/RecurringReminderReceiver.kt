package it.ciano.expensetracker.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import it.ciano.expensetracker.MainActivity
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.preferences.UserPreferences
import it.ciano.expensetracker.data.repository.RecurringTransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecurringReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMIND = "it.ciano.expensetracker.REMIND_RECURRING"
        const val EXTRA_RECURRING_ID = "recurring_id"
        const val EXTRA_TITLE = "recurring_title"
        const val EXTRA_AMOUNT = "recurring_amount"
        const val CHANNEL_ID = "RECURRING_REMINDERS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> postReminder(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val all = RecurringTransactionRepository(
                            database = db,
                            recurringDao = db.recurringTransactionDao(),
                            recurringTagDao = db.recurringTransactionTagDao(),
                            transactionDao = db.transactionDao(),
                            transactionTagDao = db.transactionTagDao(),
                            tagDao = db.tagDao()
                        ).getAllRecurringWithTags().first().map { it.recurring }
                        RecurringReminderScheduler(context).rescheduleAll(all)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val recurringId = intent.getIntExtra(EXTRA_RECURRING_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

        val prefs = UserPreferences(context)
        val currency = prefs.getCurrency()
        val separator = prefs.getDecimalSeparator()
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale.getDefault())
        symbols.decimalSeparator = separator.firstOrNull() ?: ','
        val formatted = java.text.DecimalFormat("0.00", symbols).format(amount)

        val tapIntent = PendingIntent.getActivity(
            context,
            recurringId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.str_notifica_in_scadenza, "$formatted $currency"))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(recurringId, notification)
    }
}