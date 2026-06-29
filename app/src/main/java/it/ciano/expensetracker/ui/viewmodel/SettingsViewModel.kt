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
                android.util.Log.d("ExpenseTracker", "Restore started for URI: $uri")
                val dbPath = context.getDatabasePath("expense_tracker_db")
                val prefsDir = File(context.filesDir.parentFile, "shared_prefs")
                val prefsPath = File(prefsDir, "user_prefs.xml")
                
                val zipTempFile = File(context.cacheDir, "import_temp.zip")
                val tempDbFile = File(context.cacheDir, "restore_db.tmp")
                val tempPrefsFile = File(context.cacheDir, "restore_prefs.tmp")

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    zipTempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                android.util.Log.d("ExpenseTracker", "ZIP copied to cache: ${zipTempFile.length()} bytes")

                var dbFound = false
                var prefsFound = false

                ZipInputStream(FileInputStream(zipTempFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            android.util.Log.d("ExpenseTracker", "Found entry: ${entry.name}")
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

                if (dbFound) {
                    android.util.Log.d("ExpenseTracker", "Restoring DB to: ${dbPath.absolutePath}")
                    val dbDir = dbPath.parentFile
                    dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { 
                        android.util.Log.d("ExpenseTracker", "Deleting old DB file: ${it.name}")
                        it.delete() 
                    }
                    tempDbFile.copyTo(dbPath, overwrite = true)
                    android.util.Log.d("ExpenseTracker", "DB restored. New size: ${dbPath.length()} bytes")
                }

                if (prefsFound) {
                    android.util.Log.d("ExpenseTracker", "Restoring Prefs to: ${prefsPath.absolutePath}")
                    if (!prefsDir.exists()) prefsDir.mkdirs()
                    tempPrefsFile.copyTo(prefsPath, overwrite = true)
                    android.util.Log.d("ExpenseTracker", "Prefs restored. Size: ${prefsPath.length()} bytes")
                }

                zipTempFile.delete()
                tempDbFile.delete()
                tempPrefsFile.delete()

                if (!dbFound && !prefsFound) {
                    android.util.Log.d("ExpenseTracker", "Restore failed: no valid files found in ZIP")
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