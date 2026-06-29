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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)
    private val context = application.applicationContext

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
                val dbFile = context.getDatabasePath("expense_tracker_db")
                val prefsFile = File(context.filesDir.parent, "shared_prefs/user_prefs.xml")
                var filesAdded = 0

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        if (dbFile.exists()) {
                            addFileToZip(dbFile, "expense_tracker_db", zipOut)
                            filesAdded++
                        }
                        if (prefsFile.exists()) {
                            addFileToZip(prefsFile, "user_prefs.xml", zipOut)
                            filesAdded++
                        }
                    }
                }
                
                // Se non è stato aggiunto alcun file, il backup è considerato fallito
                if (filesAdded == 0) {
                    onComplete(false)
                } else {
                    onComplete(true)
                }
            } catch (e: Exception) {
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
                val dbPath = context.getDatabasePath("expense_tracker_db")
                val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
                val prefsPath = File(prefsDir, "user_prefs.xml")
                
                val zipTempFile = File(context.cacheDir, "import_temp.zip")
                val tempDbFile = File(context.cacheDir, "restore_db.tmp")
                val tempPrefsFile = File(context.cacheDir, "restore_prefs.tmp")

                // 1. Copia l'archivio
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    zipTempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                var dbFound = false
                var prefsFound = false

                // 2. Decomprimi e verifica
                ZipInputStream(FileInputStream(zipTempFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when (entry.name) {
                                "expense_tracker_db" -> {
                                    tempDbFile.outputStream().use { zipIn.copyTo(it) }
                                    dbFound = true
                                }
                                "user_prefs.xml" -> {
                                    tempPrefsFile.outputStream().use { zipIn.copyTo(it) }
                                    prefsFound = true
                                }
                            }
                        }
                        entry = zipIn.nextEntry
                    }
                }

                // 3. Sostituzione solo se abbiamo trovato qualcosa
                if (dbFound) {
                    val dbDir = dbPath.parentFile
                    dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { it.delete() }
                    tempDbFile.copyTo(dbPath, overwrite = true)
                }

                if (prefsFound) {
                    if (!prefsDir.exists()) prefsDir.mkdirs()
                    tempPrefsFile.copyTo(prefsPath, overwrite = true)
                }

                // 4. Pulizia
                zipTempFile.delete()
                tempDbFile.delete()
                tempPrefsFile.delete()

                // Se non abbiamo trovato nulla di utile, il ripristino è fallito
                if (!dbFound && !prefsFound) {
                    onComplete(false)
                } else {
                    onComplete(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}