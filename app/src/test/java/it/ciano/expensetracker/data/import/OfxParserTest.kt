package it.ciano.expensetracker.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory

class OfxParserTest {

    private fun parser() = XmlPullParserFactory.newInstance().newPullParser()

    private val ofxXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <OFX>
          <BANKMSGSRSV1>
            <STMTTRNRS>
              <STMTRS>
                <STMTTRN>
                  <TRNTYPE>DEBIT</TRNTYPE>
                  <DTPOSTED>20260903000000.000</DTPOSTED>
                  <TRNAMT>-25.50</TRNAMT>
                  <NAME>SUPERMERCATO</NAME>
                  <MEMO>Carta di debito</MEMO>
                </STMTTRN>
                <STMTTRN>
                  <TRNTYPE>CREDIT</TRNTYPE>
                  <DTPOSTED>20260904</DTPOSTED>
                  <TRNAMT>1500.00</TRNAMT>
                  <NAME>STIPENDIO</NAME>
                </STMTTRN>
                <STMTTRN>
                  <TRNTYPE>POS</TRNTYPE>
                  <DTPOSTED>20260905120000.000[+1:GMT]</DTPOSTED>
                  <TRNAMT>-8.90</TRNAMT>
                  <NAME>CAFFE</NAME>
                  <CATEGORY>Casa</CATEGORY>
                </STMTTRN>
              </STMTRS>
            </STMTTRNRS>
          </BANKMSGSRSV1>
        </OFX>
    """.trimIndent()

    @Test
    fun `parsa le transazioni e mappa i tipi`() {
        val result = OfxParser.parse(ofxXml, parser())
        assertEquals(3, result.transactions.size)
        assertEquals("EXPENSE", result.transactions[0].type)
        assertEquals("INCOME", result.transactions[1].type)
        assertEquals("EXPENSE", result.transactions[2].type)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `mappa importo data e titolo correttamente`() {
        val result = OfxParser.parse(ofxXml, parser())
        val spesa = result.transactions[0]
        assertEquals("SUPERMERCATO", spesa.title)
        assertEquals(25.5, spesa.amount, 0.001)
        assertEquals("Carta di debito", spesa.note)
        val cal = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.SEPTEMBER, 3, 0, 0, 0)
        }
        assertEquals(cal.timeInMillis, spesa.date)
    }

    @Test
    fun `categoria viene letta se presente`() {
        val result = OfxParser.parse(ofxXml, parser())
        assertEquals("Casa", result.transactions[2].categoryName)
        assertTrue(result.transactions[0].categoryName.isNullOrEmpty())
    }

    @Test
    fun `xml vuoto produce lista vuota`() {
        val result = OfxParser.parse("<?xml version=\"1.0\"?>\n<OFX></OFX>", parser())
        assertTrue(result.transactions.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `dlposted compatto senza orario viene accettato`() {
        val result = OfxParser.parse(ofxXml, parser())
        val entrata = result.transactions[1]
        val cal = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.SEPTEMBER, 4, 0, 0, 0)
        }
        assertEquals(cal.timeInMillis, entrata.date)
    }
}