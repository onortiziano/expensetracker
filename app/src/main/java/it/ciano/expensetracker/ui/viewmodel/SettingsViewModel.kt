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

    private val _iconStyle = MutableStateFlow(userPreferences.getIconStyle())
    val iconStyle: StateFlow<String> = _iconStyle.asStateFlow()

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

    fun backupAll(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("expense_tracker_db")
                val walFile = File(dbFile.absolutePath + "-wal")
                val shmFile = File(dbFile.absolutePath + "-shm")
                val prefsFile = File(context.filesDir.parent, "shared_prefs/user_prefs.xml")
                
                var filesAdded = 0

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
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
                    }
                }
                
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

                if (tempFiles.containsKey("expense_tracker_db")) {
                    val dbDir = dbPath.parentFile
                    dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { it.delete() }
                    
                    tempFiles.filter { it.key.startsWith("expense_tracker_db") }.forEach { (name, tempFile) ->
                        val destFile = File(dbDir, name)
                        tempFile.copyTo(destFile, overwrite = true)
                    }
                }

                if (tempFiles.containsKey("user_prefs.xml")) {
                    if (!prefsDir.exists()) prefsDir.mkdirs()
                    tempFiles["user_prefs.xml"]?.copyTo(prefsPath, overwrite = true)
                }

                zipTempFile.delete()
                tempFiles.values.forEach { it.delete() }

                if (tempFiles.isEmpty()) {
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
