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

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOut ->
                        if (dbFile.exists()) addFileToZip(dbFile, "expense_tracker_db", zipOut)
                        if (prefsFile.exists()) addFileToZip(prefsFile, "user_prefs.xml", zipOut)
                    }
                }
                onComplete(true)
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
                val prefsPath = File(context.filesDir.parent, "shared_prefs/user_prefs.xml")
                
                // File temporanei per evitare di distruggere i dati se il restore fallisce
                val tempDbFile = File(context.cacheDir, "restore_db.tmp")
                val tempPrefsFile = File(context.cacheDir, "restore_prefs.tmp")

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                when (entry.name) {
                                    "expense_tracker_db" -> {
                                        tempDbFile.outputStream().use { zipIn.copyTo(it) }
                                    }
                                    "user_prefs.xml" -> {
                                        tempPrefsFile.outputStream().use { zipIn.copyTo(it) }
                                    }
                                }
                            }
                            entry = zipIn.nextEntry
                        }
                    }
                }

                // Se siamo arrivati qui, il ZIP è valido. Ora sostituiamo i file.
                if (tempDbFile.exists()) {
                    // Elimina TUTTI i file del database (incluso WAL e SHM) per evitare corruzioni
                    val dbDir = dbPath.parentFile
                    dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { it.delete() }
                    tempDbFile.copyTo(dbPath, overwrite = true)
                    tempDbFile.delete()
                }

                if (tempPrefsFile.exists()) {
                    tempPrefsFile.copyTo(prefsPath, overwrite = true)
                    tempPrefsFile.delete()
                }

                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}