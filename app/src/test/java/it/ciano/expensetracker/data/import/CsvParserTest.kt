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
    fun `file reale bancomat Google Play con date italiane e tipo scelto dall utente`() {
        // Dati reali dell'utente: delimitatore virgola, data "dd MMM yyyy, HH:mm",
        // importo con virgola decimale e suffisso valuta. Importi positivi = uscite
        // (app di pagamento): l'utente sceglie il tipo, qui EXPENSE.
        val content = "Data e ora,ID transazione,Descrizione,Prodotto,Metodo di pagamento,Stato,Importo\n" +
            "\"20 lug 2026, 07:00\",GPA.3365-9618-4252-88046,80 Robux (Roblox),App di Google Play,PayPal: tiziano.onor@gmail.com,Completo,\"1,19 EUR\"\n" +
            "\"5 lug 2026, 13:17\",GPA.3385-7857-3700-08429,40 Robux (Roblox),App di Google Play,Hype\u2008\u2008\u2008\u20081098,Completo,\"0,60 EUR\"\n"
        val mapping = CsvParser.ImportMapping(dateIdx = 0, descIdx = 2, amountIdx = 6, typeDefault = "EXPENSE")
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(2, result.transactions.size)
        assertEquals(0, result.errors.size)
        assertEquals("80 Robux (Roblox)", result.transactions[0].title)
        assertEquals(1.19, result.transactions[0].amount, 0.001)
        assertEquals("EXPENSE", result.transactions[0].type)
        assertEquals(millis(2026, Calendar.JULY, 20), result.transactions[0].date)
    }

    @Test
    fun `senza tipo scelto l importo positivo viene inferito come entrata`() {
        // File con separatore ";" e date-italiane senza ora: il tipo viene inferito dal segno.
        val content = "data;descrizione;importo\n" +
            "\"3 set 2026\";Supermercato;-25,50\n" +
            "\"4 set 2026\";Vendita;150,00\n"
        val result = CsvParser.parse(content, ",", CsvParser.ImportMapping(dateIdx = 0, descIdx = 1, amountIdx = 2))
        assertEquals(2, result.transactions.size)
        assertEquals(0, result.errors.size)
        assertEquals("EXPENSE", result.transactions[0].type)
        assertEquals("INCOME", result.transactions[1].type)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 3), result.transactions[0].date)
    }

    @Test
    fun `file reale dell utente con sole ritorni a capo CR`() {
        // Il file vero di Google Play usa solo \r come separatore di riga: una sola
        // "riga" per il parser. Se non venisse normalizzato darebbe 0 righe lette.
        val content = "Data e ora,ID transazione, Descrizione, Promemoria, Metodo di pagamento, Stato, Importo\r" +
            "\"20 lug 2026, 07:00\",GPA.3365-9618-4252-88046,80 Robux (Roblox),App di Google Play,PayPal: tiziano.onor@gmail.com,Completo,\"1,19 EUR\"\r" +
            "\"5 lug 2026, 13:17\",GPA.3385-7857-3700-08429,40 Robux (Roblox),App di Google Play,Hype\u2008\u2008\u2008\u20081098,Completo,\"0,60 EUR\""
        val mapping = CsvParser.ImportMapping(dateIdx = 0, descIdx = 2, amountIdx = 6, typeDefault = "EXPENSE")
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(2, result.transactions.size)
        assertEquals(0, result.errors.size)
        assertEquals("80 Robux (Roblox)", result.transactions[0].title)
        assertEquals(1.19, result.transactions[0].amount, 0.001)
        assertEquals("EXPENSE", result.transactions[0].type)
    }

    @Test
    fun `file ricorrente con CRLF non si rompe`() {
        val content = "date,description,amount\r\n2026-09-03,\"Negozio, S.R.L.\",-12.99\r\n"
        val result = CsvParser.parse(content, ".", CsvParser.ImportMapping(dateIdx = 0, descIdx = 1, amountIdx = 2), headerLines = 1)
        assertEquals(1, result.transactions.size)
        assertEquals(0, result.errors.size)
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

    @Test
    fun `header data e ora viene riconosciuto come colonna data`() {
        val content = "Data e ora;ID transazione;Descrizione;Prodotto;Metodo di pagamento;Stato;Importo\n" +
            "03/09/2026;TXN1;Supermercato;Pane;Carta;Completato;-25,50\n" +
            "04/09/2026;TXN2;Stipendio;;Bonifico;OK;1500,00\n"
        val result = CsvParser.parse(content, ",")
        assertEquals(0, result.errors.size)
        assertEquals(2, result.transactions.size)
        val spesa = result.transactions[0]
        assertEquals("Supermercato", spesa.title)
        assertEquals(25.5, spesa.amount, 0.001)
        assertEquals("EXPENSE", spesa.type)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 3), spesa.date)
    }

    @Test
    fun `detectFile rileva header e suggerisce ruoli su file non standard`() {
        val content = "Data e ora;ID transazione;Descrizione;Prodotto;Metodo di pagamento;Stato;Importo\n" +
            "03/09/2026;TXN1;Supermercato;Pane;Carta;Completato;-25,50\n"
        val info = CsvParser.detectFile(content)!!
        assertEquals(7, info.headers.size)
        assertEquals("Data e ora", info.headers[0])
        assertEquals(0, info.suggested.dateIdx)
        assertEquals(2, info.suggested.descIdx)
        assertEquals(6, info.suggested.amountIdx)
        assertEquals(-1, info.suggested.typeIdx)
    }

    @Test
    fun `parse con mapping esplicito mappa colonne non standard`() {
        val content = "Col1;Col2;Col3;Col4\n31/08/2026;Bolle;Gas;-42,10\n01/09/2026;Luce;Elettricita;-60,00\n"
        val mapping = CsvParser.ImportMapping(dateIdx = 0, descIdx = 2, amountIdx = 3)
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(0, result.errors.size)
        assertEquals(2, result.transactions.size)
        assertEquals("Gas", result.transactions[0].title)
        assertEquals(42.1, result.transactions[0].amount, 0.001)
        assertEquals(millis(2026, Calendar.AUGUST, 31), result.transactions[0].date)
    }

    @Test
    fun `parse senza colonna data usa la data odierna`() {
        val content = "Descrizione;Importo\nPane;-3,00\nLatte;-1,50\n"
        val mapping = CsvParser.ImportMapping(descIdx = 0, amountIdx = 1)
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(2, result.transactions.size)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(today, result.transactions[0].date)
    }

    @Test
    fun `parse senza colonna descrizione usa segnaposto`() {
        val content = "Importo;Data\n-5,00;03/09/2026\n"
        val mapping = CsvParser.ImportMapping(amountIdx = 0, dateIdx = 1)
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(1, result.transactions.size)
        assertEquals(CsvParser.PLACEHOLDER_TITLE, result.transactions[0].title)
    }

    @Test
    fun `riga senza importo viene saltata con errore di riga`() {
        val content = "Data;Descrizione;Importo\n03/09/2026;A;-10,00\n04/09/2026;B;\n"
        val mapping = CsvParser.ImportMapping(dateIdx = 0, descIdx = 1, amountIdx = 2)
        val result = CsvParser.parse(content, ",", mapping)
        assertEquals(1, result.transactions.size)
        assertEquals(1, result.errors.size)
        assertEquals(3, result.errors[0].line)
    }

    @Test
    fun `parse senza importo mappato non produce transazioni`() {
        val content = "Data;Descrizione\n03/09/2026;A\n"
        val mapping = CsvParser.ImportMapping(dateIdx = 0, descIdx = 1)
        val result = CsvParser.parse(content, ",", mapping)
        assertTrue(result.transactions.isEmpty())
        assertTrue(result.errors.isNotEmpty())
    }
}