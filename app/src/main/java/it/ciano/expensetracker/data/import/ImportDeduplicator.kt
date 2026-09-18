package it.ciano.expensetracker.data.import

/**
 * Separa le righe da inserire da quelle duplicate. La decisione di duplicato
 * è delegata a [isDuplicate] (implementata dal repository con countExactMatch).
 */
object ImportDeduplicator {

    suspend fun split(
        rows: List<ImportRow>,
        isDuplicate: suspend (ImportRow) -> Boolean
    ): Pair<List<ImportRow>, Int> {
        val toInsert = mutableListOf<ImportRow>()
        var skipped = 0
        for (row in rows) {
            if (isDuplicate(row)) skipped++ else toInsert.add(row)
        }
        return toInsert to skipped
    }
}