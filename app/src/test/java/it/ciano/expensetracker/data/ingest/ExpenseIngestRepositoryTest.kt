package it.ciano.expensetracker.data.ingest

import it.ciano.expensetracker.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseIngestRepositoryTest {

    @Test
    fun `findCategoryId trova la categoria ignorando maiuscole`() {
        val categories = listOf(
            Category(id = 1, name = "Pranzo"),
            Category(id = 2, name = "Trasporti")
        )
        assertEquals(1, findCategoryId(categories, "pranzo"))
    }

    @Test
    fun `findCategoryId restituisce null se la categoria non esiste`() {
        assertNull(findCategoryId(listOf(Category(id = 1, name = "Pranzo")), "Cena"))
    }

    @Test
    fun `toTransaction mappa i campi della spesa parsata`() {
        val parsed = ParsedExpense(amount = 12.5, categoryName = "Pranzo", title = "Ristorante", date = 123456L)
        val tx = toTransaction(parsed, categoryId = 7)
        assertEquals("EXPENSE", tx.type)
        assertEquals(12.5, tx.amount, 0.001)
        assertEquals(7, tx.categoryId)
        assertEquals(123456L, tx.date)
        assertEquals("Ristorante", tx.title)
        assertEquals("", tx.note)
    }
}
