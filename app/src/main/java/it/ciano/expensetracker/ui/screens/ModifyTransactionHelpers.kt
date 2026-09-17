package it.ciano.expensetracker.ui.screens

import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.TransactionWithTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

/**
 * Cerca la transazione da modificare e le categorie, attendendo finché la transazione
 * non appare nei flussi. Al primo match il flusso termina: la transazione viene quindi
 * caricata UNA sola volta, evitando che emissioni successive dei flussi resettino i campi
 * mentre l'utente sta digitando.
 *
 * Restituisce null se il flusso completa senza mai trovare la transazione.
 */
suspend fun awaitTransactionToModify(
    transactions: Flow<List<TransactionWithTags>>,
    categories: Flow<List<Category>>,
    transactionId: Int
): Pair<TransactionWithTags, List<Category>>? {
    return combine(transactions, categories) { txList, catList ->
        txList.find { it.transaction.id == transactionId }?.let { it to catList }
    }.firstOrNull { it != null }
}

/**
 * Estrae anno, mese e giorno (per [android.app.DatePickerDialog], che usa
 * mese 0-based) da un timestamp, così il DatePicker parte dalla data della
 * transazione invece che da oggi.
 */
fun datePickerInitialValues(millis: Long): Triple<Int, Int, Int> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return Triple(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/**
 * Interpreta il testo dell'importo rispettando il separatore decimale scelto
 * dall'utente (`,` o `.`). Restituisce il valore numerico, oppure null se il
 * testo non è un importo valido (separatore sbagliato, più separatori o
 * caratteri non numerici).
 */
fun parseAmountText(text: String, separator: String): Double? {
    if (text.isBlank()) return null
    val sep = separator.firstOrNull() ?: ','
    if (text.any { !it.isDigit() && it != sep }) return null
    if (text.count { it == sep } > 1) return null
    return text.replace(separator, ".").toDoubleOrNull()
}

/**
 * Formatta l'importo per la precompilazione del campo modifica usando il
 * separatore decimale scelto dall'utente (`,` o `.`).
 */
fun formatAmountForEdit(amount: Double, separator: String): String {
    val sep = separator.firstOrNull() ?: ','
    return if (sep == ',') amount.toString().replace('.', ',') else amount.toString()
}
