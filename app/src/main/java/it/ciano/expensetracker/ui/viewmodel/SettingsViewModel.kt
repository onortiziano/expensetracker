package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.backup.BackupRestoreLogic
import it.ciano.expensetracker.data.preferences.UserPreferences
import it.ciano.expensetracker.data.repository.GlobalBudgetRepository
import it.ciano.expensetracker.data.model.GlobalBudget
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)
    private val globalBudgetRepository = GlobalBudgetRepository(application)
    private val context = application.applicationContext
    
    private val _currency = MutableStateFlow(userPreferences.getCurrency())
    val currency: StateFlow<String> = _currency.asStateFlow()
    
    private val _decimalSeparator = MutableStateFlow(userPreferences.getDecimalSeparator())
    val decimalSeparator: StateFlow<String> = _decimalSeparator.asStateFlow()
    
    private val _iconStyle = MutableStateFlow(userPreferences.getIconStyle())
    val iconStyle: StateFlow<String> = _iconStyle.asStateFlow()

    private val _appLanguage = MutableStateFlow(userPreferences.getAppLanguage())
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    // Callback per ricreare l'Activity dopo il cambio lingua (necessario su HyperOS)
    var onLanguageChanged: (() -> Unit)? = null

    fun updateAppLanguage(code: String) {
        if (code == _appLanguage.value) return
        userPreferences.saveAppLanguage(code)
        _appLanguage.value = code
        onLanguageChanged?.invoke()
    }

    fun updateCurrency(newSymbol: String) {
        userPreferences.saveCurrency(newSymbol)
        _currency.value = newSymbol
    }

    fun updateDecimalSeparator(newSeparator: String) {
        userPreferences.saveDecimalSeparator(newSeparator)
        _decimalSeparator.value = newSeparator
    }

    fun updateIconStyle(newStyle: String) {
        userPreferences.saveIconStyle(newStyle)
        _iconStyle.value = newStyle
    }

    fun getBudgetForMonth(month: Int, year: Int): Flow<GlobalBudget?> {
        return globalBudgetRepository.getBudgetForMonth(month, year)
    }

    fun saveGlobalBudget(amount: Double, month: Int, year: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = globalBudgetRepository.getBudgetForMonth(month, year).first()
            val id = existing?.id ?: 0
            globalBudgetRepository.saveBudget(GlobalBudget(id = id, amount = amount, month = month, year = year))
        }
    }

    fun backupAll(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "backup_${System.currentTimeMillis()}.zip")
            try {
                val dbFile = context.getDatabasePath("expense_tracker_db")
                val walFile = File(dbFile.absolutePath + "-wal")
                val shmFile = File(dbFile.absolutePath + "-shm")
                val prefsFile = File(context.filesDir.parent, "shared_prefs/user_prefs.xml")

                // 1. Costruiamo lo zip completo in cache locale (filesystem affidabile).
                var filesAdded = 0
                tempZip.outputStream().use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        if (dbFile.exists()) {
                            addFileToZip(dbFile, "expense_tracker_db", zipOut)
                            filesAdded++
                        }
                        if (walFile.exists()) {
                            addFileToZip(walFile, "expense_tracker_db-wal", zipOut)
                            filesAdded++
                        }
                        if (shmFile.exists()) {
                            addFileToZip(shmFile, "expense_tracker_db-shm", zipOut)
                            filesAdded++
                        }
                        if (prefsFile.exists()) {
                            addFileToZip(prefsFile, "user_prefs.xml", zipOut)
                            filesAdded++
                        }
                        if (addReceiptsToZip(zipOut) > 0) {
                            filesAdded++
                        }
                    }
                }

                if (filesAdded == 0 || !tempZip.exists()) {
                    withContext(Dispatchers.Main) { runCatching { onComplete(false) } }
                    return@launch
                }

                // 2. Estendiamo la permission sulla Uri: su alcuni OEM il write via
                //    ContentResolver dopo CreateDocument fallisce senza persistable grant.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }

                // 3. Copiamo il file locale verso la destinazione SAF.
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream == null) {
                    withContext(Dispatchers.Main) { runCatching { onComplete(false) } }
                    return@launch
                }
                outputStream.use { out ->
                    tempZip.inputStream().use { it.copyTo(out) }
                    out.flush()
                }
                withContext(Dispatchers.Main) { runCatching { onComplete(true) } }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { runCatching { onComplete(false) } }
            } finally {
                tempZip.delete()
            }
        }
    }

    private fun addFileToZip(file: File, fileName: String, zipOut: ZipOutputStream) {
        val entry = ZipEntry(fileName)
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    private fun addReceiptsToZip(zipOut: ZipOutputStream): Int {
        val receiptsDir = File(context.filesDir, ReceiptStorage.RECEIPT_DIR)
        if (!receiptsDir.exists()) return 0
        var count = 0
        receiptsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                addFileToZip(file, "receipts/${file.name}", zipOut)
                count++
            }
        }
        return count
    }

    fun restoreAll(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbPath = context.getDatabasePath("expense_tracker_db")
                val prefsPath = File(context.filesDir.parentFile, "shared_prefs/user_prefs.xml")
                val receiptsDir = File(context.filesDir, ReceiptStorage.RECEIPT_DIR)

                val zipTempFile = File(context.cacheDir, "import_temp.zip")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    zipTempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val tempFiles = BackupRestoreLogic.extractToTempDir(zipTempFile, context.cacheDir)
                val success = BackupRestoreLogic.applyToStorage(tempFiles, dbPath, prefsPath, receiptsDir)

                zipTempFile.delete()
                tempFiles.values.forEach { it.delete() }

                withContext(Dispatchers.Main) { runCatching { onComplete(success) } }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { runCatching { onComplete(false) } }
            }
        }
    }
}
