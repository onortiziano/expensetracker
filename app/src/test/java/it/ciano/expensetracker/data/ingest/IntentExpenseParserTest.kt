package it.ciano.expensetracker.data.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IntentExpenseParserTest {

    @Test
    fun `parse con dati completi produce una spesa valida`() {
        val result = IntentExpenseParser.parse(
            mapOf(
                "amount" to "12.50",
                "category" to "Pranzo",
                "note" to "Ristorante",
                "date" to "2026-08-13"
            )
        )

        assertTrue(result is IngestResult.Success)
        val expense = (result as IngestResult.Success).expense
        assertEquals(12.50, expense.amount, 0.001)
        assertEquals("Pranzo", expense.categoryName)
        assertEquals("Ristorante", expense.title)
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        }
        assertEquals(cal.timeInMillis, expense.date)
    }

    @Test
    fun `parse con solo amount usa default categoria data e titolo`() {
        val before = System.currentTimeMillis()
        val result = IntentExpenseParser.parse(mapOf("amount" to "12,50"))

        assertTrue(result is IngestResult.Success)
        val expense = (result as IngestResult.Success).expense
        assertEquals(12.50, expense.amount, 0.001)
        assertEquals("Varie", expense.categoryName)
        assertEquals("Varie", expense.title)
        assertTrue(expense.date in before - 1000..System.currentTimeMillis() + 1000)
    }

    @Test
    fun `parse con amount testo restituisce errore invalid`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "abc"))
        assertEquals(IngestResult.Error(IngestError.INVALID_AMOUNT), result)
    }

    @Test
    fun `parse con amount negativo restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "-5"))
        )
    }

    @Test
    fun `parse con amount zero restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "0"))
        )
    }

    @Test
    fun `parse senza amount restituisce errore missing`() {
        assertEquals(IngestResult.Error(IngestError.MISSING_AMOUNT), IntentExpenseParser.parse(emptyMap()))
    }

    @Test
    fun `parse con amount doppia virgola restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "12,50,00"))
        )
    }

    @Test
    fun `parse con alias description valorizza il titolo`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "description" to "Caffe"))
        val expense = (result as IngestResult.Success).expense
        assertEquals("Caffe", expense.title)
    }

    @Test
    fun `parse con date epoch millis la converte`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "1000000"))
        val expense = (result as IngestResult.Success).expense
        assertEquals(1000000L, expense.date)
    }

    @Test
    fun `parse con date malformata usa la data corrente`() {
        val before = System.currentTimeMillis()
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "ieri"))
        val expense = (result as IngestResult.Success).expense
        assertTrue(expense.date in before - 1000..System.currentTimeMillis() + 1000)
    }

    @Test
    fun `parse con amount con spazi attorno funziona`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to " 12.50 "))
        assertTrue(result is IngestResult.Success)
        assertEquals(12.50, (result as IngestResult.Success).expense.amount, 0.001)
    }

    @Test
    fun `parse con data datetime ISO con orario funziona`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "2026-08-13T10:30:00"))
        val expense = (result as IngestResult.Success).expense
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 13, 10, 30, 0)
        }
        assertEquals(cal.timeInMillis, expense.date)
    }
}
