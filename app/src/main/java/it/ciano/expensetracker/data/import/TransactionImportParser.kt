package it.ciano.expensetracker.data.import

import org.xmlpull.v1.XmlPullParser

/**
 * Facade: dato il nome del file e il suo contenuto, instrada verso il parser
 * corretto (CSV o OFX). Legge dal ContentResolver nel chiamante; qui resta puro.
 */
object TransactionImportParser {

    private val CSV_EXTENSIONS = setOf("csv", "tsv")
    private val OFX_EXTENSIONS = setOf("ofx", "ofxq", "qfx")

    fun parseFile(fileName: String, content: ByteArray, decimalSeparator: String): ImportParseResult =
        parseFile(fileName, content.toString(Charsets.UTF_8), decimalSeparator)

    /** Versione device: parser XML di Android. */
    fun parseFile(fileName: String, content: String, decimalSeparator: String): ImportParseResult =
        parseFile(fileName, content, decimalSeparator, ::newAndroidPullParser)

    /** @param parserFactory iniettabile per i test JVM (kxml2); default = Android. */
    fun parseFile(
        fileName: String,
        content: String,
        decimalSeparator: String,
        parserFactory: () -> XmlPullParser
    ): ImportParseResult {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in CSV_EXTENSIONS -> CsvParser.parse(content, decimalSeparator)
            ext in OFX_EXTENSIONS -> {
                val xml = OfxSgmlNormalizer.normalizeToXml(content)
                OfxParser.parse(xml, parserFactory())
            }
            else -> ImportParseResult(
                emptyList(),
                listOf(ImportError(0, "Formato file non riconosciuto"))
            )
        }
    }

    /** Su device usa l'XmlPullParser di Android; su JVM è una stub e i test passano kxml2. */
    private fun newAndroidPullParser(): XmlPullParser = android.util.Xml.newPullParser()
}