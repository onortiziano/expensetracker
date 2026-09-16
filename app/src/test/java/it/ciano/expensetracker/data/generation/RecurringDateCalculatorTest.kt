package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringDateCalculatorTest {

    private fun millis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }

    private fun dayOfMonth(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.DAY_OF_MONTH)

    private fun monthOf(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.MONTH)

    private fun yearOf(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)

    @Test
    fun `advance daily aggiunge un giorno`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 2), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "DAILY"))
    }

    @Test
    fun `advance weekly aggiunge sette giorni`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 8), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "WEEKLY"))
    }

    @Test
    fun `advance biweekly aggiunge quattordici giorni`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 15), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "BIWEEKLY"))
    }

    @Test
    fun `advance monthly mantiene lo stesso giorno del mese`() {
        assertEquals(millis(2026, Calendar.OCTOBER, 1), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "MONTHLY"))
    }

    @Test
    fun `advance monthly clamp a fine mese il 31 gennaio`() {
        val advanced = RecurringDateCalculator.advance(millis(2026, Calendar.JANUARY, 31), "MONTHLY")
        assertEquals(2026, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(28, dayOfMonth(advanced))
    }

    @Test
    fun `advance monthly dopo clamp mantiene il giorno clampato`() {
        // 31 gen -> 28 feb -> 28 mar (non scivola al 31)
        val advanced = RecurringDateCalculator.advance(millis(2026, Calendar.FEBRUARY, 28), "MONTHLY")
        assertEquals(28, dayOfMonth(advanced))
        assertEquals(Calendar.MARCH, monthOf(advanced))
    }

    @Test
    fun `advance monthly clamp anno bisestile a 29 febbraio`() {
        val advanced = RecurringDateCalculator.advance(millis(2024, Calendar.JANUARY, 31), "MONTHLY")
        assertEquals(2024, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(29, dayOfMonth(advanced))
    }

    @Test
    fun `advance annual mantiene giorno e mese`() {
        assertEquals(millis(2027, Calendar.JUNE, 15), RecurringDateCalculator.advance(millis(2026, Calendar.JUNE, 15), "ANNUAL"))
    }

    @Test
    fun `advance annual clamp 29 febbraio bisestile a 28 febbraio`() {
        val advanced = RecurringDateCalculator.advance(millis(2024, Calendar.FEBRUARY, 29), "ANNUAL")
        assertEquals(2025, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(28, dayOfMonth(advanced))
    }

    @Test
    fun `occurrences mensili nel mese restituisce la data giusta`() {
        val template = RecurringTransaction(
            title = "Affitto", amount = 800.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), dates)
    }

    @Test
    fun `occurrences biweekly copre piu date nel mese`() {
        val template = RecurringTransaction(
            title = "Spesa", amount = 30.0, type = "EXPENSE", categoryId = 2,
            frequency = "BIWEEKLY", startDate = millis(2026, Calendar.SEPTEMBER, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 1)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(1L, 15L, 29L).map { millis(2026, Calendar.SEPTEMBER, it.toInt()) }, dates)
    }

    @Test
    fun `occurrences di template inattivo e vuota`() {
        val template = RecurringTransaction(
            title = "Af", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15), isActive = false
        )
        assertTrue(RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER).isEmpty())
    }

    @Test
    fun `occurrences rispettano la endDate inclusiva`() {
        val template = RecurringTransaction(
            title = "Bo", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.SEPTEMBER, 15),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), dates)
    }

    @Test
    fun `occurrences dopo la endDate sono vuote`() {
        val template = RecurringTransaction(
            title = "Bo", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.AUGUST, 15),
            nextDueDate = millis(2026, Calendar.AUGUST, 15)
        )
        assertTrue(RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER).isEmpty())
    }
}