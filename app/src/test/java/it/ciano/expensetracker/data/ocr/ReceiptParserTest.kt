package it.ciano.expensetracker.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class ReceiptParserTest {

    private val cal = Calendar.getInstance().apply { clear() }

    // ---------- IMPORTI: keyword ----------

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
    fun `se la keyword ha piu importi sceglie l ultimo`() {
        val parsed = ReceiptParser.parse("TOT 5,00 12,50")
        assertEquals(12.50, parsed.amount!!, 0.001)
    }

    @Test
    fun `se la riga keyword non ha importi prende la riga successiva`() {
        val parsed = ReceiptParser.parse("TOTALE DA PAGARE\n12,50\nGRAZIE")
        assertEquals(12.50, parsed.amount!!, 0.001)
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
    fun `importo rispetta il limite superiore della riga`() {
        val parsed = ReceiptParser.parse("TOTALE 0,15\nALTRO 1,00")
        assertEquals(0.15, parsed.amount!!, 0.001)
    }

    // ---------- SANITIZZAZIONE CIFRE ----------

    @Test
    fun `O e o diventano zero`() {
        assertEquals(0.95, ReceiptParser.parse("TOTALE O,95").amount!!, 0.001)
        assertEquals(3.05, ReceiptParser.parse("TOTALE 3,o5").amount!!, 0.001)
    }

    @Test
    fun `I e l minuscola diventano uno`() {
        assertEquals(11.50, ReceiptParser.parse("TOTALE 1I,50").amount!!, 0.001)
        assertEquals(11.50, ReceiptParser.parse("TOTALE 1l,50").amount!!, 0.001)
        assertEquals(0.15, ReceiptParser.parse("TOTALE 0,l5").amount!!, 0.001)
    }

    @Test
    fun `S diventa cinque in contesto numerico`() {
        assertEquals(5.25, ReceiptParser.parse("TOTALE S,25").amount!!, 0.001)
    }

    @Test
    fun `B diventa otto in contesto numerico`() {
        assertEquals(8.50, ReceiptParser.parse("TOTALE B,50").amount!!, 0.001)
    }

    @Test
    fun `rimuove spazi spuri tra le cifre`() {
        val parsed = ReceiptParser.parse("TOTALE 1 2, 5 0")
        assertEquals(12.50, parsed.amount!!, 0.001)
    }

    @Test
    fun `piu importi sulla stessa riga restano separati`() {
        // "5,00 6,00" non deve diventare "5,006,00"
        val parsed = ReceiptParser.parse("TOTALE 5,00 6,00")
        assertEquals(6.00, parsed.amount!!, 0.001)
    }

    // ---------- SEPARATORE DECIMALE ----------

    @Test
    fun `preferenza virgola tratta i punti come migliaia`() {
        val parsed = ReceiptParser.parse("TOTALE 1.234,56", decimalSeparator = ",")
        assertEquals(1234.56, parsed.amount!!, 0.001)
    }

    @Test
    fun `preferenza punto tratta le virgole come migliaia`() {
        val parsed = ReceiptParser.parse("TOTALE 1,234.56", decimalSeparator = ".")
        assertEquals(1234.56, parsed.amount!!, 0.001)
    }

    @Test
    fun `auto rileva il punto decimale a fine numero`() {
        assertEquals(12.50, ReceiptParser.parse("TOTALE 12.50").amount!!, 0.001)
    }

    @Test
    fun `auto rileva la virgola decimale a fine numero`() {
        assertEquals(12.50, ReceiptParser.parse("TOTALE 12,50").amount!!, 0.001)
    }

    @Test
    fun `auto gestisce le migliaia europee`() {
        assertEquals(1234.56, ReceiptParser.parse("TOTALE 1.234,56").amount!!, 0.001)
    }

    @Test
    fun `auto gestisce le migliaia americane`() {
        assertEquals(1234.56, ReceiptParser.parse("TOTALE 1,234.56").amount!!, 0.001)
    }

    // ---------- DATA ----------

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
    fun `estrae la data con anno a due cifre`() {
        val parsed = ReceiptParser.parse("13-08-26\nTOTALE 5,00")
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `data americana Mese Giorno in locale inglese`() {
        val parsed = ReceiptParser.parse(
            "08/13/2026\nTOTALE 5,00",
            locale = Locale.US
        )
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `data ambigua con locale italiano e giorno mese`() {
        val parsed = ReceiptParser.parse(
            "05/06/2026\nTOTALE 5,00",
            locale = Locale.ITALIAN
        )
        cal.set(2026, Calendar.JUNE, 5, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `data ambigua con locale inglese e mese giorno`() {
        val parsed = ReceiptParser.parse(
            "05/06/2026\nTOTALE 5,00",
            locale = Locale.US
        )
        cal.set(2026, Calendar.MAY, 6, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `restituisce null come data se non presente`() {
        assertNull(ReceiptParser.parse("TOTALE 5,00").date)
    }

    // ---------- ESERCENTE ----------

    @Test
    fun `esercente e la prima riga valida in cima`() {
        val parsed = ReceiptParser.parse("SUPERMERCATO ESSELUNGA\nPIAZZA 5\nTOTALE 12,50")
        assertEquals("SUPERMERCATO ESSELUNGA", parsed.merchant)
    }

    @Test
    fun `esercente salta il rumore di intestazione`() {
        val parsed = ReceiptParser.parse("RICEVUTA FISCALE\nSUPERMERCATO COOP\nTOTALE 12,50")
        assertEquals("SUPERMERCATO COOP", parsed.merchant)
    }

    @Test
    fun `puo essere vuoto il merchant se il testo non ha righe`() {
        assertNull(ReceiptParser.parse("").merchant)
    }

    // ---------- CATEGORIA ----------

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

    // ---------- RIGHE SPAZIALI (bounding box) ----------

    @Test
    fun `righe spaziali separano keyword e importo sulla riga successiva`() {
        val lines = listOf(
            OcrLine("SUPERMERCATO", 10f),
            OcrLine("PANE 1,50", 40f),
            OcrLine("TOTALE DA PAGARE", 70f),
            OcrLine("12,50", 80f)
        )
        val parsed = ReceiptParser.parse(
            lines = lines,
            rawText = "SUPERMERCATO\nPANE 1,50\nTOTALE DA PAGARE\n12,50"
        )
        assertEquals(12.50, parsed.amount!!, 0.001)
        assertEquals("SUPERMERCATO", parsed.merchant)
    }

    @Test
    fun `fallback spaziale usa il massimo nella meta inferiore`() {
        val lines = listOf(
            OcrLine("PANE 1,20", 100f),
            OcrLine("LATTE 2,50", 120f),
            OcrLine("BRILLO 99,00", 190f),
            OcrLine("GRAZIE", 200f)
        )
        val parsed = ReceiptParser.parse(
            lines = lines,
            rawText = "PANE 1,20\nLATTE 2,50\nBRILLO 99,00\nGRAZIE",
            decimalSeparator = ","
        )
        assertEquals(99.00, parsed.amount!!, 0.001)
    }

    @Test
    fun `fallback ignora partita iva e numeri lunghi`() {
        val lines = listOf(
            OcrLine("SUPERMERCATO", 10f),
            OcrLine("P.IVA 12345678901", 50f),
            OcrLine("TEL 3331234567", 60f),
            OcrLine("14,90", 70f)
        )
        val parsed = ReceiptParser.parse(
            lines = lines,
            rawText = "SUPERMERCATO\nP.IVA 12345678901\nTEL 3331234567\n14,90",
            decimalSeparator = ","
        )
        assertEquals(14.90, parsed.amount!!, 0.001)
    }

    // ---------- CASI LIMITE ----------

    @Test
    fun `testo vuoto produce tutti campi null`() {
        val parsed = ReceiptParser.parse("")
        assertNull(parsed.amount)
        assertNull(parsed.date)
        assertNull(parsed.merchant)
        assertNull(parsed.suggestedCategoryName)
    }

    @Test
    fun `testo solo spazi produce tutti campi null`() {
        val parsed = ReceiptParser.parse("   \n  \n  ")
        assertNull(parsed.amount)
        assertNull(parsed.date)
        assertNull(parsed.merchant)
        assertNull(parsed.suggestedCategoryName)
    }

    @Test
    fun `testo illeggibile senza numeri non da importo`() {
        val parsed = ReceiptParser.parse("###???\naaaa bbbb\nxxxx")
        assertNull(parsed.amount)
    }

    @Test
    fun `esercente non vuoto viene sempre popolato per il flusso camera`() {
        val parsed = ReceiptParser.parse("CONAD\n13/08/2026\nTOTALE 5,00")
        assertNotNull(parsed.merchant)
    }

    // ---------- ESERCENTE: SIGLE DI SOCIETÀ ----------

    @Test
    fun `esercente e la riga con sigla srl anche se non e la prima`() {
        val parsed = ReceiptParser.parse(
            "RICEVUTA FISCALE\nPIAZZA 10\nCONAD SUPERMERCATO S.R.L.\nTOTALE 12,50"
        )
        assertEquals("CONAD SUPERMERCATO S.R.L.", parsed.merchant)
    }

    @Test
    fun `esercente srl con partita iva resta solo la ragione sociale`() {
        val parsed = ReceiptParser.parse(
            "GESTIONI ALIMENTARI S.R.L. P.IVA 03456789012\nVIA ROMA 7\nTOTALE 5,00"
        )
        assertEquals("GESTIONI ALIMENTARI S.R.L.", parsed.merchant)
    }

    @Test
    fun `esercente spa senza punti`() {
        val parsed = ReceiptParser.parse(
            "EUROSPIN SPA\nVIA MILANO 1\nTOTALE 7,90"
        )
        assertEquals("EUROSPIN SPA", parsed.merchant)
    }

    @Test
    fun `esercente spa minuscolo con punti`() {
        val parsed = ReceiptParser.parse(
            "30 PIAZZA\nLIDL S.P.A.\nTOTALE 3,50"
        )
        assertEquals("LIDL S.P.A.", parsed.merchant)
    }

    @Test
    fun `esercente snc in fondo alla ricevuta`() {
        val parsed = ReceiptParser.parse(
            "PANE 1,50\nLATTE 2,00\nGRAZIE\nFRATELLI BIANCHI S.N.C."
        )
        assertEquals("FRATELLI BIANCHI S.N.C.", parsed.merchant)
    }

    @Test
    fun `esercente sas`() {
        val parsed = ReceiptParser.parse(
            "PASTICCERIA ROSSI S.A.S.\nTOTALE 4,00"
        )
        assertEquals("PASTICCERIA ROSSI S.A.S.", parsed.merchant)
    }

    @Test
    fun `esercente cooperativa scarla con spazi`() {
        val parsed = ReceiptParser.parse(
            "IPERCOOP\nUNICOOP FIRENZE S.C. A R.L.\nTOTALE 23,40"
        )
        assertEquals("UNICOOP FIRENZE S.C. A R.L.", parsed.merchant)
    }

    @Test
    fun `senza sigle resta la prima riga valida`() {
        val parsed = ReceiptParser.parse(
            "RICEVUTA FISCALE\nSUPERMERCATO ESSELUNGA\nTOTALE 12,50"
        )
        assertEquals("SUPERMERCATO ESSELUNGA", parsed.merchant)
    }

    @Test
    fun `sigla societa vince anche se sopra c e una riga telefono`() {
        val parsed = ReceiptParser.parse(
            "RICEVUTA FISCALE\nTEL 3331234567\nCONAD S.R.L.\nTOTALE 5,00"
        )
        assertEquals("CONAD S.R.L.", parsed.merchant)
    }

    @Test
    fun `riga di telefono non corrode una sigla srl`() {
        val parsed = ReceiptParser.parse(
            "CONAD TELECOMUNICAZIONI S.R.L.\nTOTALE 5,00"
        )
        assertEquals("CONAD TELECOMUNICAZIONI S.R.L.", parsed.merchant)
    }
}