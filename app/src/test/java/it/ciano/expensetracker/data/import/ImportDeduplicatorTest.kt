package it.ciano.expensetracker.data.import

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking

class ImportDeduplicatorTest {

    private fun row(title: String, amount: Double) = ImportRow(
        transaction = ParsedImportTransaction(
            title = title, amount = amount, type = "EXPENSE",
            date = 1L, sourceLine = 1
        ),
        categoryId = 0
    )

    @Test
    fun `split mantiene i non duplicati e conta i duplicati`() = runBlocking {
        val rows = listOf(row("A", 1.0), row("A", 1.0), row("B", 2.0))
        val (toInsert, skipped) = ImportDeduplicator.split(rows) { it.transaction.title == "A" }
        assertEquals(1, toInsert.size)
        assertEquals("B", toInsert.single().transaction.title)
        assertEquals(2, skipped)
    }

    @Test
    fun `senza duplicati tutto viene inserito`() = runBlocking {
        val rows = listOf(row("A", 1.0), row("B", 2.0))
        val (toInsert, skipped) = ImportDeduplicator.split(rows) { false }
        assertEquals(2, toInsert.size)
        assertEquals(0, skipped)
    }
}