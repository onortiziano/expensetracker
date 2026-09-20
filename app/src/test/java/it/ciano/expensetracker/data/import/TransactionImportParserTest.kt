package it.ciano.expensetracker.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Calendar

class TransactionImportParserTest {

    private val testParser: () -> XmlPullParser = { XmlPullParserFactory.newInstance().newPullParser() }

    @Test
    fun `csv end to end`() {
        val content = "data;descrizione;importo\n03/09/2026;Bar;-4,50\n".toByteArray()
        val result = TransactionImportParser.parseFile("export.csv", content, ",")
        assertEquals(1, result.transactions.size)
        assertEquals("Bar", result.transactions[0].title)
        assertEquals(4.5, result.transactions[0].amount, 0.001)
    }

    @Test
    fun `ofx xml end to end`() {
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OFX><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260903</DTPOSTED>
            <TRNAMT>-10.00</TRNAMT><NAME>FARMACIA</NAME></STMTTRN></OFX>
        """.trimIndent()
        val result = TransactionImportParser.parseFile("estratto.ofx", content, ".", testParser)
        assertEquals(1, result.transactions.size)
        assertEquals("FARMACIA", result.transactions[0].title)
        val cal = Calendar.getInstance().apply { clear(); set(2026, Calendar.SEPTEMBER, 3, 0, 0, 0) }
        assertEquals(cal.timeInMillis, result.transactions[0].date)
    }

    @Test
    fun `ofx sgml end to end`() {
        val content = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102

            <OFX>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260904
            <TRNAMT>900.00
            <NAME>RIMBORSO
            </STMTTRN>
            </OFX>
        """.trimIndent()
        val result = TransactionImportParser.parseFile("legacy.ofx", content, ".", testParser)
        assertEquals(1, result.transactions.size)
        assertEquals("INCOME", result.transactions[0].type)
        assertEquals(900.0, result.transactions[0].amount, 0.001)
    }

    @Test
    fun `estensione sconosciuta produce errore di formato`() {
        val result = TransactionImportParser.parseFile("report.txt", "hello".toByteArray(), ".")
        assertTrue(result.transactions.isEmpty())
        assertTrue(result.errors.any { it.line == 0 })
    }
}