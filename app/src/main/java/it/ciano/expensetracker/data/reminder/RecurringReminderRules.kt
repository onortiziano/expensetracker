package it.ciano.expensetracker.data.reminder

import it.ciano.expensetracker.data.model.RecurringTransaction
import java.util.Calendar

object RecurringReminderRules {

    /** Un promemoria viene pianificato solo per template attivi con prossima scadenza futura e dentro la finestra endDate. */
    fun shouldSchedule(r: RecurringTransaction, today: Long): Boolean {
        if (!r.isActive) return false
        if (r.nextDueDate <= today) return false
        if (r.endDate != null && r.nextDueDate > r.endDate) return false
        return true
    }

    /** Il promemoria scatta alle 09:00 del giorno della scadenza. */
    fun nextFireTime(nextDueDate: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nextDueDate
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}