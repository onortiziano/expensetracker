package it.ciano.expensetracker.data.import

import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parser per transazioni bancarie OFX (già normalizzato in XML). Il parser
 * XML è iniettato: sui test JVM si usa kxml2, su device quello di Android.
 */
object OfxParser {

    private const val TRNTYPE_DEBIT = "DEBIT"
    private const val TRNTYPE_CREDIT = "CREDIT"

    fun parse(content: String, parser: XmlPullParser): ImportParseResult {
        parser.setInput(StringReader(content))
        val transactions = mutableListOf<ParsedImportTransaction>()
        val errors = mutableListOf<ImportError>()

        var event = parser.eventType
        var inTrn = false
        var currentTag = ""
        val fields = HashMap<String, String>()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "STMTTRN" -> {
                            inTrn = true
                            fields.clear()
                        }
                        else -> if (inTrn) currentTag = parser.name
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTrn && currentTag.isNotEmpty()) {
                        fields[currentTag] = parser.text?.trim() ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "STMTTRN" && inTrn) {
                        buildTransaction(fields)?.let { transactions.add(it) }
                        inTrn = false
                        currentTag = ""
                    } else if (inTrn) {
                        currentTag = ""
                    }
                }
            }
            event = parser.next()
        }
        return ImportParseResult(transactions, errors)
    }

    private fun buildTransaction(fields: HashMap<String, String>): ParsedImportTransaction? {
        val amountRaw = fields["TRNAMT"] ?: return null
        val amount = amountRaw.toDoubleOrNull() ?: return null
        val date = parseOfxDate(fields["DTPOSTED"] ?: return null) ?: return null

        val trntype = fields["TRNTYPE"]?.uppercase(Locale.ROOT).orEmpty()
        val type = when {
            trntype == TRNTYPE_DEBIT || trntype == "POS" || trntype == "FEE" || trntype == "ATM" -> "EXPENSE"
            trntype == TRNTYPE_CREDIT || trntype == "DEP" || trntype == "INT" || trntype == "DIV" -> "INCOME"
            else -> if (amount < 0) "EXPENSE" else "INCOME"
        }

        val name = fields["NAME"].orEmpty().trim()
        val memo = fields["MEMO"].orEmpty().trim()
        val title = name.ifEmpty { memo.ifEmpty { "Transazione" } }
        val note = if (name.isNotEmpty() && memo.isNotEmpty()) memo else ""

        return ParsedImportTransaction(
            title = title,
            amount = kotlin.math.abs(amount),
            type = type,
            date = date,
            categoryName = fields["CATEGORY"]?.takeIf { it.isNotBlank() },
            note = note,
            sourceLine = 1 // OFX non ha concetto di riga; raggruppiamo il totale in caso di errori
        )
    }

    /** DTPOSTED: YYYYMMDD oppure YYYYMMDDHHMMSS.xxx (con eventuale timezone tra parentesi). */
    private fun parseOfxDate(raw: String): Long? {
        val base = raw.substringBefore('[').take(14)
        val fmt = if (base.length >= 14) {
            SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT)
        } else {
            SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        }
        fmt.isLenient = false
        val date = try { fmt.parse(base.take(fmt.toPattern().length)) } catch (e: Exception) { null } ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = date.time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}