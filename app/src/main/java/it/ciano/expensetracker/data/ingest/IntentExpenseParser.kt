package it.ciano.expensetracker.data.ingest

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

object IntentExpenseParser {

    const val DEFAULT_CATEGORY = "Varie"

    private const val KEY_AMOUNT = "amount"
    private const val KEY_CATEGORY = "category"
    private const val KEY_NOTE = "note"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_DATE = "date"

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd"
    )

    fun parse(params: Map<String, String?>): IngestResult {
        val amountRaw = params[KEY_AMOUNT]?.trim().orEmpty()
        if (amountRaw.isEmpty()) return IngestResult.Error(IngestError.MISSING_AMOUNT)

        val amount = parseAmount(amountRaw) ?: return IngestResult.Error(IngestError.INVALID_AMOUNT)
        if (amount <= 0.0) return IngestResult.Error(IngestError.INVALID_AMOUNT)

        val categoryName = params[KEY_CATEGORY]?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CATEGORY

        val title = (params[KEY_NOTE] ?: params[KEY_DESCRIPTION])?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: categoryName

        val date = parseDate(params[KEY_DATE]) ?: System.currentTimeMillis()

        return IngestResult.Success(ParsedExpense(amount, categoryName, title, date))
    }

    private fun parseAmount(raw: String): Double? {
        return raw.replace(',', '.').toDoubleOrNull()
    }

    private fun parseDate(raw: String?): Long? {
        if (raw == null) return null
        val value = raw.trim()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { return it }

        for (pattern in DATE_PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value)?.time
            } catch (_: ParseException) {
                // prova il pattern successivo
            }
        }
        return null
    }
}
