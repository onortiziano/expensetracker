package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringTransactionGeneratorTest {

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }.timeInMillis

    private fun template(
        id: Int, frequency: String, nextDueDate: Long, endDate: Long? = null,
        isActive: Boolean = true, startDate: Long = nextDueDate
    ) = RecurringTransaction(
        id = id, title = "Affitto $id", amount = 800.0, type = "EXPENSE", categoryId = 1,
        frequency = frequency, startDate = startDate, endDate = endDate,
        nextDueDate = nextDueDate, isActive = isActive
    )

    @Test
    fun `nessun template dovuto produce nessuna generazione`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val result = RecurringTransactionGenerator.generate(
            listOf(template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15))),
            emptyMap(), today
        )
        assertTrue(result.occurrences.isEmpty())
        assertTrue(result.updated.isEmpty())
    }

    @Test
    fun `template mensile in ritardo genera i periodi mancanti`() {
        val today = millis(2026, Calendar.FEBRUARY, 15)
        val t = template(1, "MONTHLY", millis(2026, Calendar.JANUARY, 1), startDate = millis(2026, Calendar.JANUARY, 1))
        val result = RecurringTransactionGenerator.generate(listOf(t), mapOf(1 to setOf(5)), today)

        val dates = result.occurrences.map { it.date }
        assertEquals(listOf(millis(2026, Calendar.JANUARY, 1), millis(2026, Calendar.FEBRUARY, 1)), dates)
        assertEquals(listOf(5), result.occurrences.first().tagIds.toList())

        val updated = result.updated.single()
        assertEquals(millis(2026, Calendar.MARCH, 1), updated.nextDueDate)
        assertEquals(millis(2026, Calendar.FEBRUARY, 1), updated.lastGeneratedDate)
        assertTrue(updated.isActive)
    }

    @Test
    fun `template inattivo non genera nulla`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 5), isActive = false)
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertTrue(result.occurrences.isEmpty())
    }

    @Test
    fun `template con endDate precedente a nextDueDate viene disattivato senza generare`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(
            1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15),
            endDate = millis(2026, Calendar.SEPTEMBER, 1)
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertTrue(result.occurrences.isEmpty())
        val updated = result.updated.single()
        assertFalse(updated.isActive)
    }

    @Test
    fun `template attivo con nextDueDate futura e endDate nulla non viene toccato`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15))
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertTrue(result.occurrences.isEmpty())
        assertTrue(result.updated.isEmpty())
    }

    @Test
    fun `endDate inclusiva genera l ultima occorrenza e poi disattiva`() {
        val today = millis(2026, Calendar.FEBRUARY, 15)
        val t = template(
            1, "MONTHLY", millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.FEBRUARY, 1),
            startDate = millis(2026, Calendar.JANUARY, 1)
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)

        assertEquals(listOf(millis(2026, Calendar.JANUARY, 1), millis(2026, Calendar.FEBRUARY, 1)), result.occurrences.map { it.date })
        val updated = result.updated.single()
        assertFalse(updated.isActive)
        assertEquals(millis(2026, Calendar.MARCH, 1), updated.nextDueDate)
    }

    @Test
    fun `endDate gia superata disattiva senza generare oltre`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(
            1, "MONTHLY", millis(2026, Calendar.AUGUST, 15),
            endDate = millis(2026, Calendar.AUGUST, 15),
            startDate = millis(2026, Calendar.AUGUST, 15)
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(listOf(millis(2026, Calendar.AUGUST, 15)), result.occurrences.map { it.date })
        val updated = result.updated.single()
        assertFalse(updated.isActive)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 15), updated.nextDueDate)
    }

    @Test
    fun `scadenza esattamente oggi viene generata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 15)
        val t = template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15), startDate = millis(2026, Calendar.SEPTEMBER, 15))
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), result.occurrences.map { it.date })
    }

    @Test
    fun `daily genera ogni giorno fino a oggi`() {
        val today = millis(2026, Calendar.SEPTEMBER, 3)
        val t = template(1, "DAILY", millis(2026, Calendar.SEPTEMBER, 1), startDate = millis(2026, Calendar.SEPTEMBER, 1))
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(
            listOf(1, 2, 3).map { millis(2026, Calendar.SEPTEMBER, it) },
            result.occurrences.map { it.date }
        )
    }

    @Test
    fun `occorrenze copiano titolo importo tipo categoria e note`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = RecurringTransaction(
            id = 1, title = "Stipendio", amount = 1500.0, type = "INCOME", categoryId = 7,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.SEPTEMBER, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 1), note = "Netto"
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        val occ = result.occurrences.single()
        assertEquals("Stipendio", occ.title)
        assertEquals(1500.0, occ.amount, 0.001)
        assertEquals("INCOME", occ.type)
        assertEquals(7, occ.categoryId)
        assertEquals("Netto", occ.note)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 1), occ.date)
    }
}