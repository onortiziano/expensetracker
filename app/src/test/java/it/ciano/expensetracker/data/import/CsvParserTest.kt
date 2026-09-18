package it.ciano.expensetracker.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CsvParserTest {

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }.timeInMillis

    @Test
    fun `csv punto e virgola con header italiano`() {
        val content = "data;descrizione;importo\n" +
            "03/09/2026;Supermercato;-25,50\n" +
            "04/09/2026;Stipendio;1500,00\n"
        val result = CsvParser.parse(content, ",")
        assertEquals(2, result.transactions.size)
        assertEquals(0, result.errors.size)
        val spesa = result.transactions[0]
        assertEquals("Supermercato", spesa.title)
        assertEquals(25.5, spesa.amount, 0.001)
        assertEquals("EXPENSE", spesa.type)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 3), spesa.date)
        val entrata = result.transactions[1]
        assertEquals("INCOME", entrata.type)
        assertEquals(1500.0, entrata.amount, 0.001)
    }

    @Test
    fun `csv virgola con campi quotati contenenti virgole`() {
        val content = "date,description,amount\n" +
            "2026-09-03,\"Negozio, S.R.L.\",-12.99\n"
        val result = CsvParser.parse(content, ".")
        assertEquals(1, result.transactions.size)
        assertEquals("Negozio, S.R.L.", result.transactions[0].title)
        assertEquals(12.99, result.transactions[0].amount, 0.001)
    }

    @Test
    fun `csv tab separato senza header`() {
        val content = "2026-09-01\tBenzina\t-40,00\n2026-09-02\tPranzo\t-8,50\n"
        // Nessun header riconosciuto -> data+desc+importo per posizione
        val result = CsvParser.parse(content, ",")
        assertEquals(2, result.transactions.size)
        assertEquals("Benzina", result.transactions[0].title)
    }

    @Test
    fun `colonna tipo esplicita vince sul segno`() {
        val content = "Date,Description,Amount,Type\n" +
            "2026-09-03,Canone,-10.0,DEBIT\n" +
            "2026-09-04,Interessi,3.0,CREDIT\n"
        val result = CsvParser.parse(content, ".")
        assertEquals("EXPENSE", result.transactions[0].type)
        assertEquals("INCOME", result.transactions[1].type)
    }

    @Test
    fun `categoria opzionale viene letta`() {
        val content = "Data;Descrizione;Importo;Categoria\n" +
            "03/09/2026;Ristorante;-30,00;\n" +
            "04/09/2026;Affitto;-600,00;Casa\n"
        val result = CsvParser.parse(content, ",")
        assertEquals("Casa", result.transactions[1].categoryName)
        assertTrue(result.transactions[0].categoryName.isNullOrEmpty())
    }

    @Test
    fun `date in formato americano giorno maggiore di 12`() {
        val content = "date,description,amount\n2026-12-25,Regalo,-20.00\n12/25/2026,Regalo2,-5.00\n"
        val result = CsvParser.parse(content, ".")
        assertEquals(millis(2026, Calendar.DECEMBER, 25), result.transactions[0].date)
        assertEquals(millis(2026, Calendar.DECEMBER, 25), result.transactions[1].date)
    }

    @Test
    fun `data non valida produce un errore di riga`() {
        val content = "date,description,amount\n2026-99-99,Errato,-1.0\n"
        val result = CsvParser.parse(content, ".")
        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.errors.size)
        assertEquals(2, result.errors[0].line)
    }

    @Test
    fun `importo non valido produce un errore di riga`() {
        val content = "date,description,amount\n2026-09-03,Errato,abc\n"
        val result = CsvParser.parse(content, ".")
        assertTrue(result.transactions.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `campi obbligatori mancanti producono errore di file`() {
        val content = "note,importo\n2026-09-03,-10\n"
        val result = CsvParser.parse(content, ".")
        assertTrue(result.transactions.isEmpty())
        assertTrue(result.errors.isNotEmpty())
        assertEquals(0, result.errors[0].line)
    }

    @Test
    fun `file vuoto produce lista vuota`() {
        val result = CsvParser.parse("", ".")
        assertTrue(result.transactions.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `campi quotati multilinea vengono uniti`() {
        val content = "date,description,amount\n2026-09-03,\"Riga uno\nRiga due\",-4.50\n"
        val result = CsvParser.parse(content, ".")
        assertEquals(1, result.transactions.size)
        assertEquals("Riga uno\nRiga due", result.transactions[0].title)
    }

    @Test
    fun `importo con simbolo valuta viene pulito`() {
        val content = "data;descrizione;importo\n2026-09-03;Spesa;-€ 12,50\n"
        val result = CsvParser.parse(content, ",")
        assertEquals(12.5, result.transactions[0].amount, 0.001)
    }
}