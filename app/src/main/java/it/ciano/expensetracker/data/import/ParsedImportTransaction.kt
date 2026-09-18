package it.ciano.expensetracker.data.import

/** Transazione estratta da un file esterno (CSV/OFX), pronta per l'anteprima. */
data class ParsedImportTransaction(
    val title: String,
    val amount: Double,
    val type: String, // "EXPENSE" o "INCOME"
    val date: Long,   // epoch millis
    val categoryName: String? = null,
    val note: String = "",
    val sourceLine: Int // 1-based, per i messaggi di errore
)

/** Errore di parsing per una singola riga (line = 0 per errori a livello di file). */
data class ImportError(
    val line: Int,
    val reason: String
)

/** Esito complessivo dell'importazione. */
data class ImportOutcome(
    val imported: Int,
    val skippedDuplicate: Int,
    val errors: List<ImportError>
)

/** Coppia riga importabile + categoria assegnata in anteprima. */
data class ImportRow(
    val transaction: ParsedImportTransaction,
    val categoryId: Int
)

/** Output comune dei parser (CSV e OFX). */
data class ImportParseResult(
    val transactions: List<ParsedImportTransaction>,
    val errors: List<ImportError>
)