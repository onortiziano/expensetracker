package it.ciano.expensetracker.ui.screens

import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionWithTags
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ModifyTransactionHelpersTest {

    private fun tx(id: Int) = TransactionWithTags(
        transaction = Transaction(
            id = id,
            title = "transazione $id",
            amount = 10.0,
            type = "EXPENSE",
            categoryId = 1,
            date = 1000L
        ),
        tags = emptyList()
    )

    private fun category(id: Int = 1, name: String = "Categoria") = Category(id = id, name = name)

    // --- Bug 2: la transazione va caricata UNA sola volta, non a ogni emissione ---

    @Test
    fun `awaitTransactionToModify restituisce la transazione cercata e le categorie`() = runBlocking {
        val transactions = flowOf(listOf(tx(1), tx(2)), listOf(tx(3)))
        val categories = flowOf(listOf(category()))

        val result = awaitTransactionToModify(transactions, categories, transactionId = 2)

        assertNotNull(result)
        assertEquals(2, result?.first?.transaction?.id)
        assertEquals(listOf(category()), result?.second)
    }

    @Test
    fun `awaitTransactionToModify si blocca finche la transazione non appare`() = runBlocking {
        val transactions = flowOf(emptyList(), listOf(tx(5)))
        val categories = flowOf(emptyList(), listOf(category()))

        val result = awaitTransactionToModify(transactions, categories, transactionId = 5)

        assertNotNull(result)
        assertEquals(5, result?.first?.transaction?.id)
        assertEquals(listOf(category()), result?.second)
    }

    @Test
    fun `awaitTransactionToModify non restituisce nulla se la transazione non esiste`() = runBlocking {
        val transactions = flowOf(listOf(tx(1)))
        val categories = flowOf(listOf(category()))

        val result = awaitTransactionToModify(transactions, categories, transactionId = 999)

        assertNull(result)
    }

    // --- Bug 1/3: il DatePicker deve partire dalla data della transazione ---

    @Test
    fun `datePickerInitialValues estrae anno mese giorno dalla data`() {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(2024, Calendar.MARCH, 15, 10, 30, 0)

        val (year, month, day) = datePickerInitialValues(cal.timeInMillis)

        assertEquals(2024, year)
        assertEquals(Calendar.MARCH, month)
        assertEquals(15, day)
    }

    @Test
    fun `datePickerInitialValues su data zero (non ancora caricata) usa epoca`() {
        val (year, month, day) = datePickerInitialValues(0L)

        assertEquals(1970, year)
        assertEquals(Calendar.JANUARY, month)
        assertEquals(1, day)
    }
}
