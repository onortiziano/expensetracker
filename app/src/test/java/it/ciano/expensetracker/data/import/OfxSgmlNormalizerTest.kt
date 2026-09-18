package it.ciano.expensetracker.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfxSgmlNormalizerTest {

    @Test
    fun `rileva gia xml dalla dichiarazione`() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<OFX>\n</OFX>\n"
        assertTrue(OfxSgmlNormalizer.isXml(xml))
    }

    @Test
    fun `file sgml non viene rilevato come xml`() {
        val sgml = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\n\r\n<OFX>\n</OFX>\n"
        assertFalse(OfxSgmlNormalizer.isXml(sgml))
    }

    @Test
    fun `xml resta invariato`() {
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<OFX>\n</OFX>\n"
        assertEquals(xml, OfxSgmlNormalizer.normalizeToXml(xml))
    }

    @Test
    fun `header sgml viene rimosso e contenuto incapsulato`() {
        val sgml = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\n\r\n" +
            "SIGNONMSGSRSV1\r\nSONRS\r\nSTATUS\r\nCODE0\r\n</STATUS>\r\n</SONRS>\r\n</SIGNONMSGSRSV1>\r\n"
        val out = OfxSgmlNormalizer.normalizeToXml(sgml)
        assertFalse(out.contains("OFXHEADER"))
        assertTrue(out.trim().startsWith("<OFX>"))
        assertTrue(out.trim().endsWith("</OFX>"))
    }

    @Test
    fun `attributi sgml vengono espansi in figli`() {
        val sgml = "SIGNONMSGSRSV1\r\nSONRS\r\nSTATUS SEVERITY=\"INFO\"\r\nCODE0\r\n</STATUS>\r\n</SONRS>\r\n"
        val out = OfxSgmlNormalizer.normalizeToXml(sgml)
        assertTrue(out.contains("<SEVERITY>INFO</SEVERITY>"))
        assertFalse(out.contains("SEVERITY=\""))
    }

    @Test
    fun `tag gia chiusi vengono lasciati come sono`() {
        val sgml = "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE></STMTTRN>\n"
        val out = OfxSgmlNormalizer.normalizeToXml(sgml)
        assertTrue(out.contains("<TRNTYPE>DEBIT</TRNTYPE>"))
    }
}