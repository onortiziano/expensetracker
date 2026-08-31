package it.ciano.expensetracker.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReceiptParserTest {

    private val cal = Calendar.getInstance().apply { clear() }

    @Test
    fun `estrae importo da riga TOTALE con virgola`() {
        val parsed = ReceiptParser.parse(
            "SUPERMERCATO\n" +
            "2026-08-13\n" +
            "TOTALE EU 12,50\n" +
            "GRAZIE"
        )
        assertEquals(12.50, parsed.amount!!, 0.001)
    }

    @Test
    fun `estrae importo da riga TOTALE con punto`() {
        val parsed = ReceiptParser.parse("TOTALE 7.90")
        assertEquals(7.90, parsed.amount!!, 0.001)
    }

    @Test
    fun `estrae importo con fallback al numero piu grande`() {
        val parsed = ReceiptParser.parse("Pane 1.20\nLatte 2.50\nTOT 45.00")
        assertEquals(45.00, parsed.amount!!, 0.001)
    }

    @Test
    fun `restituisce null come importo se nessun numero trovato`() {
        assertNull(ReceiptParser.parse("NESSUNA CIFRA QUI").amount)
    }

    @Test
    fun `estrae la data in formato italia slash`() {
        val parsed = ReceiptParser.parse("13/08/2026\nTOTALE 5,00")
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `estrae la data in formato ISO`() {
        val parsed = ReceiptParser.parse("2026-08-13\nTOTALE 5,00")
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `restituisce null come data se non presente`() {
        assertNull(ReceiptParser.parse("TOTALE 5,00").date)
    }

    @Test
    fun `puo essere vuoto il titolo se il testo non ha righe`() {
        assertNull(ReceiptParser.parse("").title)
    }

    @Test
    fun `suggerisce categoria ristorazione per pizzeria`() {
        assertEquals("Ristorazione", ReceiptParser.parse("PIZZERIA DA MARIO").suggestedCategoryName)
    }

    @Test
    fun `suggerisce categoria alimentari per supermercato`() {
        assertEquals("Alimentari", ReceiptParser.parse("SUPERMERCATO CONAD").suggestedCategoryName)
    }

    @Test
    fun `nessuna categoria suggerita se non combacia`() {
        assertNull(ReceiptParser.parse("LAVANDERIA").suggestedCategoryName)
    }

    @Test
    fun `importo rispetta il limite superiore della riga`() {
        val parsed = ReceiptParser.parse("TOTALE 0,15\nALTRO 1,00")
        assertEquals(0.15, parsed.amount!!, 0.001)
    }
}
