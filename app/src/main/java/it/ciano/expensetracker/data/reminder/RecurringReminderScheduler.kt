package it.ciano.expensetracker.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import it.ciano.expensetracker.data.model.RecurringTransaction

class RecurringReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(recurringId: Int, nextDueDate: Long, title: String, amount: Double) {
        val fireAt = RecurringReminderRules.nextFireTime(nextDueDate)
        if (fireAt <= System.currentTimeMillis()) return

        val pending = reminderPendingIntent(recurringId, title, amount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    fun cancelReminder(recurringId: Int) {
        alarmManager.cancel(reminderPendingIntent(recurringId, title = "", amount = 0.0))
    }

    fun rescheduleAll(recurrings: List<RecurringTransaction>) {
        recurrings.forEach { cancelReminder(it.id) }
        val today = System.currentTimeMillis()
        recurrings
            .filter { RecurringReminderRules.shouldSchedule(it, today) }
            .forEach { scheduleReminder(it.id, it.nextDueDate, it.title, it.amount) }
    }

    private fun reminderPendingIntent(recurringId: Int, title: String, amount: Double): PendingIntent {
        val intent = Intent(context, RecurringReminderReceiver::class.java).apply {
            action = RecurringReminderReceiver.ACTION_REMIND
            putExtra(RecurringReminderReceiver.EXTRA_RECURRING_ID, recurringId)
            putExtra(RecurringReminderReceiver.EXTRA_TITLE, title)
            putExtra(RecurringReminderReceiver.EXTRA_AMOUNT, amount)
        }
        return PendingIntent.getBroadcast(
            context,
            recurringId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}