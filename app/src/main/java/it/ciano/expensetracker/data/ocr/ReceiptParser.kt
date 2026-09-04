package it.ciano.expensetracker.data.ocr

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object ReceiptParser {

    private val AMOUNT_KEYWORDS = listOf("TOTALE", "TOT", "IMPORTO", "DA PAGARE", "TOTAL")
    private val AMOUNT_REGEX = Regex("(\\d{1,7}[.,]\\d{2})")
    private val DATE_PATTERNS = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN),
        SimpleDateFormat("dd-MM-yyyy", Locale.ITALIAN),
        SimpleDateFormat("dd.MM.yyyy", Locale.ITALIAN),
        SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN),
        SimpleDateFormat("yyyy/MM/dd", Locale.ITALIAN)
    )

    private val CATEGORY_KEYWORDS = mapOf(
        "Alimentari" to listOf("supermercato", "alimentari", "coop", "conad", "lidl", "eurospin", "esselunga", "guid", "penny"),
        "Ristorazione" to listOf("ristorante", "pizzeria", "bar", "gelateria", "caffè", "caffe", "mcdonald", "kebab", "trattoria"),
        "Trasporti" to listOf("trenitalia", "bus", "taxi", "autostrade", "parking", "parcheggio", "benzina", "carburante", "esso"),
        "Salute" to listOf("farmacia", "ospedale", "clinica", "medico", "farmacie")
    )

    private val TITLE_NOISE_PATTERNS = listOf(
        Regex("^\\d+$"),                          // pure numeri
        Regex("^\\d{3}$"),                        // numeri di riga (001, 002...)
        Regex("^\\d+[\\s/.-]"),                   // date all'inizio riga
        Regex("ricevuta\\s+fiscale", RegexOption.IGNORE_CASE),
        Regex("scontrino\\s+parlante", RegexOption.IGNORE_CASE),
        Regex("fattura", RegexOption.IGNORE_CASE),
        Regex("p\\.?\\s?i\\.?v\\.?a", RegexOption.IGNORE_CASE),
        Regex("partita\\s+iva", RegexOption.IGNORE_CASE),
        Regex("cod\\.?\\s*fisc", RegexOption.IGNORE_CASE),
        Regex("^\\d{11}$"),                       // partita IVA pura
        Regex("^\\d{16}$"),                       // codice fiscale puro
    )

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amount = extractAmount(lines)
        val date = extractDate(rawText)
        val title = extractTitle(lines)
        val category = suggestCategory(rawText)
        return ParsedReceipt(amount, date, title, category)
    }

    private fun extractTitle(lines: List<String>): String? {
        for (line in lines) {
            if (line.length < 2) continue
            if (TITLE_NOISE_PATTERNS.any { it.containsMatchIn(line) }) continue
            return line.take(50)
        }
        return lines.firstOrNull()?.take(50)
    }

    private fun extractAmount(lines: List<String>): Double? {
        for (keyword in AMOUNT_KEYWORDS) {
            val line = lines.firstOrNull { it.contains(keyword, ignoreCase = true) }
            if (line != null) {
                AMOUNT_REGEX.find(line)?.let { match ->
                    return parseNumber(match.groupValues[1])
                }
            }
        }
        return lines
            .flatMap { AMOUNT_REGEX.findAll(it).toList() }
            .mapNotNull { parseNumber(it.groupValues[1]) }
            .maxOrNull()
    }

    private fun parseNumber(raw: String): Double? {
        return raw.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun extractDate(text: String): Long? {
        for (formatter in DATE_PATTERNS) {
            try {
                formatter.isLenient = false
                val matcher = Pattern.compile("\\b\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b").matcher(text)
                while (matcher.find()) {
                    val candidate = matcher.group()
                    val parsed = formatter.parse(candidate) ?: continue
                    if (formatter.format(parsed) == candidate) {
                        return Calendar.getInstance().apply {
                            time = parsed
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
            } catch (_: Exception) {
                // pattern successivo
            }
        }
        return null
    }

    private fun suggestCategory(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return null
    }
}
