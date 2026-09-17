package it.ciano.expensetracker.data.reminder

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringReminderRulesTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    private fun template(
        nextDueDate: Long, isActive: Boolean = true, endDate: Long? = null
    ) = RecurringTransaction(
        title = "Affitto", amount = 800.0, type = "EXPENSE", categoryId = 1,
        frequency = "MONTHLY", startDate = nextDueDate, endDate = endDate,
        nextDueDate = nextDueDate, isActive = isActive
    )

    @Test
    fun `template attivo e futuro viene schedulato`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertTrue(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 15)), today))
    }

    @Test
    fun `template inattivo non viene schedulato`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 15), isActive = false), today))
    }

    @Test
    fun `scadenza odierna o passata non viene schedulata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 10)), today))
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 5)), today))
    }

    @Test
    fun `scadenza oltre la endDate non viene schedulata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(millis(2026, Calendar.OCTOBER, 15), endDate = millis(2026, Calendar.SEPTEMBER, 30))
        assertFalse(RecurringReminderRules.shouldSchedule(t, today))
    }

    @Test
    fun `nextFireTime e alle 9 del giorno della scadenza`() {
        assertEquals(
            millis(2026, Calendar.SEPTEMBER, 15, 9),
            RecurringReminderRules.nextFireTime(millis(2026, Calendar.SEPTEMBER, 15))
        )
    }
}