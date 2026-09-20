package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.import.CsvParser
import it.ciano.expensetracker.data.import.ImportError
import it.ciano.expensetracker.data.import.ImportOutcome
import it.ciano.expensetracker.data.import.ImportRow
import it.ciano.expensetracker.data.import.TransactionImportParser
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.preferences.UserPreferences
import it.ciano.expensetracker.data.repository.CategoryRepository
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportTransactionsViewModel(
    application: Application,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : AndroidViewModel(application) {

    enum class Phase { IDLE, PARSING, MAPPING, PREVIEW }

    private val userPreferences = UserPreferences(application)

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _csvInfo = MutableStateFlow<CsvParser.CsvFileInfo?>(null)
    val csvInfo: StateFlow<CsvParser.CsvFileInfo?> = _csvInfo.asStateFlow()

    private val _rows = MutableStateFlow<List<ImportRow>>(emptyList())
    val rows: StateFlow<List<ImportRow>> = _rows.asStateFlow()

    private val _errors = MutableStateFlow<List<ImportError>>(emptyList())
    val errors: StateFlow<List<ImportError>> = _errors.asStateFlow()

    private val _skipDuplicates = MutableStateFlow(true)
    val skipDuplicates: StateFlow<Boolean> = _skipDuplicates.asStateFlow()

    private val _outcome = MutableStateFlow<ImportOutcome?>(null)
    val outcome: StateFlow<ImportOutcome?> = _outcome.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Contenuto del CSV letto: serve a ri-parsare dopo il mapping. */
    private var csvContent: String = ""

    fun clearMessage() { _message.value = null }

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            _phase.value = Phase.PARSING
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                try {
                    val name = queryDisplayName(uri) ?: app.getString(R.string.str_nessun_file)
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        _message.value = app.getString(R.string.str_errore_generico)
                        _phase.value = Phase.IDLE
                        return@withContext
                    }
                    val text = bytes.toString(Charsets.UTF_8)
                    val ext = name.substringAfterLast('.', "").lowercase()

                    // CSV/TSV/TXT: rileviamo le colonne e chiediamo il mapping manuale.
                    // Anche un CSV "pulito" passa dal mapping: ogni app ha il suo formato.
                    val csvFile = CsvParser.detectFile(text)
                    if ((ext == "csv" || ext == "tsv" || ext == "txt") && csvFile != null) {
                        csvContent = text
                        _csvInfo.value = csvFile
                        _rows.value = emptyList()
                        _errors.value = emptyList()
                        _fileName.value = name
                        _phase.value = Phase.MAPPING
                        return@withContext
                    }

                    // OFX / altri: parsing automatico come prima.
                    val parseResult = TransactionImportParser.parseFile(name, text, userPreferences.getDecimalSeparator())
                    val categories = categoryRepository.getAllCategories().first()
                    _rows.value = parseResult.transactions.map { t ->
                        ImportRow(t, matchCategoryId(t.categoryName, categories))
                    }
                    _errors.value = parseResult.errors
                    _csvInfo.value = null
                    _fileName.value = name
                    _phase.value = Phase.PREVIEW
                } catch (e: Exception) {
                    _message.value = app.getString(R.string.str_errore_generico)
                    _phase.value = Phase.IDLE
                }
            }
        }
    }

    /** Ri-parsa il contenuto CSV con il mapping scelto dall'utente e passa all'anteprima. */
    fun buildRows(mapping: CsvParser.ImportMapping) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                try {
                    if (csvContent.isBlank()) {
                        _message.value = app.getString(R.string.str_errore_generico)
                        _phase.value = Phase.IDLE
                        return@withContext
                    }
                    val parseResult = CsvParser.parse(csvContent, userPreferences.getDecimalSeparator(), mapping)
                    val categories = categoryRepository.getAllCategories().first()
                    _rows.value = parseResult.transactions.map { t ->
                        ImportRow(t, matchCategoryId(t.categoryName, categories))
                    }
                    _errors.value = parseResult.errors
                    _csvInfo.value = null
                    _fileName.value = app.getString(R.string.str_nessun_file)
                    _phase.value = Phase.PREVIEW
                } catch (e: Exception) {
                    _message.value = app.getString(R.string.str_errore_generico)
                    _phase.value = Phase.IDLE
                }
            }
        }
    }

    fun updateSkipDuplicates(v: Boolean) { _skipDuplicates.value = v }

    fun assignCategory(index: Int, categoryId: Int) {
        val current = _rows.value
        if (index in current.indices) {
            _rows.value = current.toMutableList().also { it[index] = it[index].copy(categoryId = categoryId) }
        }
    }

    /** Applica una categoria a tutte le righe con quel categoryName (o tutte se null). */
    fun applyCategoryToRows(filterCategoryName: String?, categoryId: Int) {
        val current = _rows.value
        _rows.value = current.map { row ->
            if (filterCategoryName == null || row.transaction.categoryName == filterCategoryName) {
                row.copy(categoryId = categoryId)
            } else row
        }
    }

    fun importTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = transactionRepository.bulkInsert(_rows.value, _skipDuplicates.value)
            _outcome.value = outcome
        }
    }

    fun reset() {
        _phase.value = Phase.IDLE
        _fileName.value = null
        _csvInfo.value = null
        _rows.value = emptyList()
        _errors.value = emptyList()
        _outcome.value = null
        csvContent = ""
    }

    private fun matchCategoryId(categoryName: String?, categories: List<Category>): Int {
        if (categoryName.isNullOrBlank()) return 0
        return categories.firstOrNull { it.name == categoryName }?.id ?: 0
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }
}