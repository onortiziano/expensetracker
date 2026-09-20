package it.ciano.expensetracker.data.backup

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object BackupRestoreLogic {

    fun isSafeZipEntryName(name: String): Boolean {
        return name != ".." &&
            !name.startsWith("../") &&
            !name.startsWith("/") &&
            !name.contains("..")
    }

    fun extractToTempDir(zipTempFile: File, cacheDir: File): Map<String, File> {
        val tempFiles = mutableMapOf<String, File>()
        ZipInputStream(FileInputStream(zipTempFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isSafeZipEntryName(entry.name)) {
                    val safeName = entry.name.replace('/', '_')
                    val tempFile = File(cacheDir, "restore_${safeName}.tmp")
                    tempFile.parentFile?.mkdirs()
                    tempFile.outputStream().use { zipIn.copyTo(it) }
                    tempFiles[entry.name] = tempFile
                }
                entry = zipIn.nextEntry
            }
        }
        return tempFiles
    }

    fun applyToStorage(
        tempFiles: Map<String, File>,
        dbFile: File,
        prefsFile: File,
        receiptsDir: File
    ): Boolean {
        if (tempFiles.containsKey("expense_tracker_db")) {
            val dbDir = dbFile.parentFile
            dbDir?.listFiles { _, name -> name.startsWith("expense_tracker_db") }?.forEach { it.delete() }
            tempFiles.filter { it.key.startsWith("expense_tracker_db") }.forEach { (name, tempFile) ->
                tempFile.copyTo(File(dbDir, name), overwrite = true)
            }
        }

        tempFiles["user_prefs.xml"]?.let { temp ->
            prefsFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            temp.copyTo(prefsFile, overwrite = true)
        }

        tempFiles.filter { it.key.startsWith("receipts/") }.forEach { (name, tempFile) ->
            if (!receiptsDir.exists()) receiptsDir.mkdirs()
            tempFile.copyTo(File(receiptsDir, name.removePrefix("receipts/")), overwrite = true)
        }

        return tempFiles.isNotEmpty()
    }
}