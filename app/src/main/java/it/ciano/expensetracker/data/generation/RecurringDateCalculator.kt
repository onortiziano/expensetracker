package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.model.RecurringTransaction
import java.util.Calendar

object RecurringDateCalculator {

    val FREQUENCIES = listOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUAL")

    val FREQUENCY_LABELS: Map<String, Int> = mapOf(
        "DAILY" to R.string.str_giornaliera,
        "WEEKLY" to R.string.str_settimanale,
        "BIWEEKLY" to R.string.str_bisettimanale,
        "MONTHLY" to R.string.str_mensile,
        "ANNUAL" to R.string.str_annuale
    )

    private fun calendarAt(dateMillis: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = dateMillis
            clear(Calendar.HOUR_OF_DAY)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
        }

    /** Restituisce la ricorrenza successiva strettamente dopo dateMillis. */
    fun advance(dateMillis: Long, frequency: String): Long {
        val cal = calendarAt(dateMillis)
        when (frequency) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "BIWEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 14)
            "MONTHLY" -> {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.add(Calendar.MONTH, 1)
                val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, max))
            }
            "ANNUAL" -> {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.add(Calendar.YEAR, 1)
                val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, max))
            }
            else -> error("Frequenza sconosciuta: $frequency")
        }
        return cal.timeInMillis
    }

    /** Date (epoch millis) delle prossime scadenze del template all'interno del mese indicato (month 0-based). */
    fun occurrencesInMonth(template: RecurringTransaction, year: Int, month: Int): List<Long> {
        if (!template.isActive) return emptyList()

        val firstOfMonth = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
        }.timeInMillis
        val lastOfMonth = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis

        // Partiamo da nextDueDate e avanziamo fino ad entrare nel mese visualizzato.
        var cursor = template.nextDueDate
        val endBoundary = template.endDate ?: Long.MAX_VALUE
        if (cursor > endBoundary) return emptyList()

        while (cursor < firstOfMonth) {
            cursor = advance(cursor, template.frequency)
            if (cursor > endBoundary) return emptyList()
        }

        val dates = mutableListOf<Long>()
        while (cursor <= lastOfMonth && cursor <= endBoundary) {
            dates.add(cursor)
            cursor = advance(cursor, template.frequency)
        }
        return dates
    }
}