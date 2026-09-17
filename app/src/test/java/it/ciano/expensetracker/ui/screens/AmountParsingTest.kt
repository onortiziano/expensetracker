package it.ciano.expensetracker.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParsingTest {

    // --- parseAmountText: interpreta l'importo rispettando il separatore scelto ---

    @Test
    fun `parseAmountText con separatore virgola`() {
        assertEquals(12.5, parseAmountText("12,50", ","))
        assertEquals(800.0, parseAmountText("800,00", ","))
    }

    @Test
    fun `parseAmountText con separatore punto`() {
        assertEquals(12.5, parseAmountText("12.50", "."))
        assertEquals(800.0, parseAmountText("800.00", "."))
    }

    @Test
    fun `parseAmountText accetta interi senza separatore`() {
        assertEquals(12.0, parseAmountText("12", ","))
        assertEquals(12.0, parseAmountText("12", "."))
    }

    @Test
    fun `parseAmountText rifiuta separatore sbagliato`() {
        assertNull(parseAmountText("12.50", ","))
        assertNull(parseAmountText("12,50", "."))
    }

    @Test
    fun `parseAmountText rifiuta piu di un separatore`() {
        assertNull(parseAmountText("12,50,00", ","))
        assertNull(parseAmountText("12.50.00", "."))
    }

    @Test
    fun `parseAmountText rifiuta testo non numerico`() {
        assertNull(parseAmountText("abc", ","))
        assertNull(parseAmountText("", ","))
        assertNull(parseAmountText("12a", ","))
    }

    // --- formatAmountForEdit: precompila il campo importo con il separatore scelto ---

    @Test
    fun `formatAmountForEdit usa il separatore virgola`() {
        assertEquals("12,5", formatAmountForEdit(12.5, ","))
        assertEquals("800,0", formatAmountForEdit(800.0, ","))
    }

    @Test
    fun `formatAmountForEdit usa il separatore punto`() {
        assertEquals("12.5", formatAmountForEdit(12.5, "."))
        assertEquals("800.0", formatAmountForEdit(800.0, "."))
    }
}