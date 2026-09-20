package it.ciano.expensetracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupRestoreLogicTest {

    private fun makeZip(zipFile: File, entries: Map<String, String>) {
        ZipOutputStream(zipFile.outputStream()).use { zipOut ->
            entries.forEach { (name, content) ->
                zipOut.putNextEntry(ZipEntry(name))
                zipOut.write(content.toByteArray())
                zipOut.closeEntry()
            }
        }
    }

    @Test
    fun `estrae le voci del backup appiattendo i percorsi delle ricevute`() {
        val dir = Files.createTempDirectory("backup-test").toFile()
        try {
            val zipFile = File(dir, "backup.zip")
            makeZip(
                zipFile,
                mapOf(
                    "expense_tracker_db" to "DBDATA",
                    "expense_tracker_db-wal" to "WAL",
                    "expense_tracker_db-shm" to "SHM",
                    "user_prefs.xml" to "<prefs/>",
                    "receipts/IMG_0001.jpg" to "JPEGDATA"
                )
            )

            val cacheDir = File(dir, "cache").apply { mkdirs() }
            val tempFiles = BackupRestoreLogic.extractToTempDir(zipFile, cacheDir)

            assertEquals(5, tempFiles.size)
            assertTrue(tempFiles.containsKey("receipts/IMG_0001.jpg"))
            assertTrue(tempFiles.values.all { it.exists() })
            assertEquals("JPEGDATA", String(tempFiles["receipts/IMG_0001.jpg"]!!.readBytes()))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `applica il ripristino con db prefs e ricevute`() {
        val dir = Files.createTempDirectory("backup-test").toFile()
        try {
            val zipFile = File(dir, "backup.zip")
            makeZip(
                zipFile,
                mapOf(
                    "expense_tracker_db" to "DBDATA",
                    "expense_tracker_db-wal" to "WAL",
                    "user_prefs.xml" to "<prefs/>",
                    "receipts/IMG_0001.jpg" to "JPEGDATA"
                )
            )

            val cacheDir = File(dir, "cache").apply { mkdirs() }
            val dbDir = File(dir, "databases").apply { mkdirs() }
            val dbFile = File(dbDir, "expense_tracker_db").apply { writeText("OLD") }
            val prefsFile = File(dir, "shared_prefs/user_prefs.xml").apply {
                parentFile?.mkdirs()
                writeText("OLD")
            }
            val receiptsDir = File(dir, "receipts")

            val tempFiles = BackupRestoreLogic.extractToTempDir(zipFile, cacheDir)
            val success = BackupRestoreLogic.applyToStorage(tempFiles, dbFile, prefsFile, receiptsDir)

            assertTrue(success)
            assertEquals("DBDATA", dbFile.readText())
            assertEquals("<prefs/>", prefsFile.readText())
            assertEquals("JPEGDATA", File(receiptsDir, "IMG_0001.jpg").readText())
            assertTrue(File(dbDir, "expense_tracker_db-wal").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `applica il ripristino anche se la cartella shared_prefs manca`() {
        val dir = Files.createTempDirectory("backup-test").toFile()
        try {
            val zipFile = File(dir, "backup.zip")
            makeZip(zipFile, mapOf("user_prefs.xml" to "<prefs/>"))

            val cacheDir = File(dir, "cache").apply { mkdirs() }
            val prefsFile = File(dir, "shared_prefs/user_prefs.xml")

            val tempFiles = BackupRestoreLogic.extractToTempDir(zipFile, cacheDir)
            val success = BackupRestoreLogic.applyToStorage(tempFiles, File(dir, "db"), prefsFile, File(dir, "receipts"))

            assertTrue(success)
            assertEquals("<prefs/>", prefsFile.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `rifiuta nomi zip insicuri`() {
        assertFalse(BackupRestoreLogic.isSafeZipEntryName("../evil.txt"))
        assertFalse(BackupRestoreLogic.isSafeZipEntryName("a/../../evil.txt"))
        assertTrue(BackupRestoreLogic.isSafeZipEntryName("receipts/IMG.jpg"))
    }
}