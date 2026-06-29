package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.time.LocalDateTime

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)
    private val context = application.applicationContext

    private fun writeDebugLog(message: String) {
        try {
            val logFile = File(context.filesDir, "backup_debug.txt")
            logFile.appendText("${LocalDateTime.now()}: $message\\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDebugLog(): String {
        return try {
            val logFile = File(context.filesDir, "backup_debug.txt")
            if (logFile.exists()) logFile.readText() else "Log non ancora creato. Effettua un backup."
        } catch (e: Exception) {
            "Errore nella lettura del log: ${e.message}"
        }
    }

    private val _currency = MutableStateFlow(userPreferences.getCurrency())
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _decimalSeparator = MutableStateFlow(userPreferences.getDecimalSeparator())
    val decimalSeparator: StateFlow<String> = _decimalSeparator.asStateFlow()

    fun updateCurrency(newSymbol: String) {
        userPreferences.saveCurrency(newSymbol)
        _currency.value = newSymbol
    }

    fun updateDecimalSeparator(newSeparator: String) {
        userPreferences.saveDecimalSeparator(newSeparator)
        _decimalSeparator.value = newSeparator
    }

    fun backupAll(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeDebugLog("Backup started (Multi-file mode)")
                
                val dbFile = context.getDatabasePath("expense_tracker_db")
                val walFile = File(dbFile.absolutePath + "-wal")
                val shmFile = File(dbFile.absolutePath + "-shm")
                val prefsFile = File(context.filesDir.parent, "shared_prefs/user_prefs.xml")
                
                var filesAdded = 0

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        // 1. Database principale
                        if (dbFile.exists()) {
                            writeDebugLog("Adding DB: ${dbFile.length()} bytes")
                            addFileToZip(dbFile, "expense_tracker_db", zipOut)
                            filesAdded++
                        }
                        // 2. WAL File (fondamentale per i dati recenti)
                        if (walFile.exists()) {
                            writeDebugLog("Adding WAL: ${walFile.length()} bytes")
                            addFileToZip(walFile, "expense_tracker_db-wal", zipOut)
                            filesAdded++
                        }
                        // 3. SHM File
                        if (shmFile.exists()) {
                            writeDebugLog("Adding SHM: ${shmFile.length()} bytes")
                            addFileToZip(shmFile, "expense_tracker_db-shm", zipOut)
                            filesAdded++
                        }
                        // 4. Preferenze
                        if (prefsFile.exists()) {
                            writeDebugLog("Adding Prefs: ${prefsFile.length()} bytes")
                            addFileToZip(prefsFile, "user_prefs.xml", zipOut)
                            filesAdded++
                        }
                    }
                }
                
                if (filesAdded == 0) {
                    writeDebugLog("Backup failed: no files found")
                    onComplete(false)
                } else {
                    writeDebugLog("Backup successful. Total files added: $filesAdded")
                    onComplete(true)
                }
            } catch (e: Exception) {
                writeDebugLog("Backup critical error: ${e.message}")
                e.printStackTrace()
                onComplete(false)
            }
        }
    }

    private fun addFileToZip(file: File, fileName: String, zipOut: ZipOutputStream) {
        val entry = ZipEntry(fileName)
        zipOut.putNextEntry(entry)
        FileInputStream(file).use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    fun restoreAll(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ExpenseTracker", "Restore started for URI: $uri")
                val dbPath = context.getDatabasePath("expense_tracker_db")
                val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
                val prefsPath = File(prefsDir, "user_prefs.xml")
                
                val zipTempFile = File(context.cacheDir, "import_temp.zip")
                
                // Mappa per i file temporanei estratti
                val tempFiles = mutableMapOf<String, File>()
                
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    zipTempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                ZipInputStream(FileInputStream(zipTempFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val tempFile = File(context.cacheDir, "restore_${entry.name}.tmp")
                            tempFile.outputStream().use { zipIn.copyTo(it) }
                            tempFiles[entry.name] = tempFile
                        }
                        entry = zipIn.nextEntry
                    }
                }

                // Ripristino Database e file correlati (WAL/SHM)
                if (tempFiles.containsKey("expense_tracker_db")) {
                    val dbDir = dbPath.parentFile
                    // Pulizia totale di ogni file che inizia con expense_tracker_db
                    dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { it.delete() }
                    
                    // Copia tutti i file DB estratti (db, wal, shm)
                    tempFiles.filter { it.key.startsWith("expense_tracker_db") }.forEach { (name, tempFile) ->
                        val destFile = File(dbDir, name)
                        tempFile.copyTo(destFile, overwrite = true)
                        android.util.Log.d("ExpenseTracker", "Restored $name: ${destFile.length()} bytes")
                    }
                }

                // Ripristino Preferenze
                if (tempFiles.containsKey("user_prefs.xml")) {
                    if (!prefsDir.exists()) prefsDir.mkdirs()
                    tempFiles["user_prefs.xml"]?.copyTo(prefsPath, overwrite = true)
                    android.util.Log.d("ExpenseTracker", "Prefs restored")
                }

                // Pulizia cache
                zipTempFile.delete()
                tempFiles.values.forEach { it.delete() }

                if (tempFiles.isEmpty()) {
                    android.util.Log.d("ExpenseTracker", "Restore failed: ZIP empty")
                    onComplete(false)
                } else {
                    android.util.Log.d("ExpenseTracker", "Restore successful")
                    onComplete(true)
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpenseTracker", "Restore error: ${e.message}", e)
                onComplete(false)
            }
        }
    }
}
