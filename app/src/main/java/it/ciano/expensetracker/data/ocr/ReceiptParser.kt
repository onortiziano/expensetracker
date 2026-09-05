package it.ciano.expensetracker.data.ocr

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object ReceiptParser {

    private val AMOUNT_KEYWORDS = listOf(
        "TOTALE", "TOT", "TOTAL", "DA PAGARE", "IMPORTO", "DOVUTO",
        "EURO", "EUR", "CONTANTE", "CARTE", "CARTA"
    )

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
        "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy",
        "MM/dd/yyyy", "MM-dd-yyyy", "MM.dd.yyyy",
        "dd/MM/yy", "dd-MM-yy", "MM/dd/yy"
    )

    /** Singole parole/token numerici presenti in una riga (senza spazi). */
    private val AMOUNT_CANDIDATE_REGEX = Regex("[0-9.,OoIl|SB]+")

    /** Sequenza numerica frammentata da spazi spuri (es. "1 2, 5 0"). */
    private val GLUE_REGEX = Regex("[0-9.,OoIl|SB]+(?:[ ]+[0-9.,OoIl|SB]+)+")

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

    fun parse(
        rawText: String,
        decimalSeparator: String = "auto",
        locale: Locale = Locale.getDefault()
    ): ParsedReceipt {
        val lines = rawText.lines().mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null else OcrLine(trimmed, (index + 1) * 10f)
        }
        return parse(lines, rawText, decimalSeparator, locale)
    }

    fun parse(
        lines: List<OcrLine>,
        rawText: String,
        decimalSeparator: String = "auto",
        locale: Locale = Locale.getDefault()
    ): ParsedReceipt {
        val amount = extractAmount(lines, decimalSeparator)
        val date = extractDate(rawText, locale)
        val merchant = extractMerchant(lines)
        val category = suggestCategory(rawText)
        return ParsedReceipt(amount, date, merchant, category)
    }

    fun parse(
        result: ReceiptOcrResult,
        decimalSeparator: String = "auto",
        locale: Locale = Locale.getDefault()
    ): ParsedReceipt = parse(result.lines, result.rawText, decimalSeparator, locale)

    // ---------- DURATA: TOTALE ----------

    private fun extractAmount(lines: List<OcrLine>, prefix: String): Double? {
        val keywordIndex = lines.indexOfFirst { line ->
            AMOUNT_KEYWORDS.any { line.text.contains(it, ignoreCase = true) }
        }
        if (keywordIndex >= 0) {
            val candidates = amountCandidates(lines[keywordIndex].text, prefix)
            if (candidates.isNotEmpty()) return candidates.last()
            if (keywordIndex + 1 < lines.size) {
                val next = amountCandidates(lines[keywordIndex + 1].text, prefix)
                if (next.isNotEmpty()) return next.last()
            }
        }

        // Fallback: numeri validi nella metà inferiore (poi ovunque), il più alto
        val lower = if (lines.isEmpty()) emptyList() else {
            val mid = (lines.minOf { it.top } + lines.maxOf { it.top }) / 2f
            lines.filter { it.top >= mid }
        }
        val candidates = (if (lower.isNotEmpty()) lower else lines)
            .flatMap { amountCandidates(it.text, prefix) }
        return candidates.maxOrNull()
    }

    private fun amountCandidates(line: String, prefix: String): List<Double> {
        val direct = AMOUNT_CANDIDATE_REGEX.findAll(line)
            .mapNotNull { parseAmountToken(it.value, prefix) }
        val glued = GLUE_REGEX.find(line)
            ?.let { parseAmountToken(it.value, prefix) }
        return buildList {
            addAll(direct)
            if (glued != null) add(glued)
        }.distinct()
    }

    /** Corregge gli scambi tipici dell'OCR su carta termica. */
    private fun sanitizeLetters(raw: String): String = raw.map { ch ->
        when (ch) {
            'O', 'o' -> '0'
            'I', 'l', '|' -> '1'
            'S', 's' -> '5'
            'B', 'b' -> '8'
            else -> ch
        }
    }.joinToString("")

    private fun parseAmountToken(raw: String, prefix: String): Double? {
        val collapsed = raw.replace(" ", "")
        val s = sanitizeLetters(collapsed).trim('.', ',')
        if (s.isEmpty()) return null

        var intPart: String?
        var fracPart: String?

        when (prefix) {
            "," -> {
                val cleaned = s.filter { it != '.' }
                if (cleaned.contains(',')) {
                    val parts = cleaned.split(',')
                    if (parts.size != 2) return null
                    intPart = parts[0]; fracPart = parts[1]
                } else {
                    // Nessuna virgola: se l'unico punto era seguito da 1-2 cifre, era il decimale
                    val dot = s.lastIndexOf('.')
                    if (dot >= 0 && s.count { it == '.' } == 1 && s.length - dot - 1 in 1..2) {
                        intPart = s.substring(0, dot); fracPart = s.substring(dot + 1)
                    } else return null
                }
            }

            "." -> {
                val cleaned = s.filter { it != ',' }
                if (cleaned.contains('.')) {
                    val parts = cleaned.split('.')
                    if (parts.size != 2) return null
                    intPart = parts[0]; fracPart = parts[1]
                } else {
                    val comma = s.lastIndexOf(',')
                    if (comma >= 0 && s.count { it == ',' } == 1 && s.length - comma - 1 in 1..2) {
                        intPart = s.substring(0, comma); fracPart = s.substring(comma + 1)
                    } else return null
                }
            }

            else -> { // auto: il separatore seguito da esattamente 1-2 cifre a fine numero è quello decimale
                val lastSep = s.indexOfLast { it == '.' || it == ',' }
                if (lastSep < 0) return null
                val digitsAfter = s.length - lastSep - 1
                if (digitsAfter !in 1..2) return null
                val sep = s[lastSep]
                val other = if (sep == '.') ',' else '.'
                intPart = s.take(lastSep).filter { it != other }
                fracPart = s.drop(lastSep + 1)
            }
        }

        if (intPart.isNullOrEmpty() || intPart.length > 7) return null
        if (fracPart.isNullOrEmpty() || fracPart.length !in 1..2) return null
        if (!intPart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return null

        val value = intPart.toDouble() + ("0.$fracPart").toDouble()
        return value.takeIf { it in 0.01..9_999_999.0 }
    }

    // ---------- DATA ----------

    private fun extractDate(text: String, locale: Locale): Long? {
        val isEnglish = locale.language.equals("en", ignoreCase = true)
        val ordered = if (isEnglish) {
            listOf("MM/dd/yyyy", "MM-dd-yyyy", "MM.dd.yyyy") + listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy")
        } else {
            listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy") + listOf("MM/dd/yyyy", "MM-dd-yyyy", "MM.dd.yyyy")
        }
        val patterns = ordered + DATE_PATTERNS.filter { it.startsWith("yyyy") || it.endsWith("yy") }
        val formatters = patterns
            .distinct()
            .map { SimpleDateFormat(it, locale).apply { isLenient = false } }

        val matcher = Pattern.compile("\\b\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b").matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group()
            for (formatter in formatters) {
                try {
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
                } catch (_: Exception) {
                    // provo il pattern successivo
                }
            }
        }
        return null
    }

    // ---------- ESERCENTE ----------

    private fun extractMerchant(lines: List<OcrLine>): String? {
        for (line in lines) {
            val text = line.text
            if (text.length < 2) continue
            if (!text.any { it.isLetter() }) continue
            if (TITLE_NOISE_PATTERNS.any { it.containsMatchIn(text) }) continue
            return text.take(50)
        }
        return lines.firstOrNull()?.text?.take(50)
    }

    // ---------- CATEGORIA ----------

    private fun suggestCategory(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return null
    }
}