package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object ReceiptStorage {

    const val AUTHORITY = "it.ciano.expensetracker.fileprovider"
    const val RECEIPT_DIR = "receipts"

    fun createReceiptFile(context: Context, timestamp: Long): File {
        val dir = File(context.filesDir, RECEIPT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$timestamp.jpg")
    }

    fun loadBitmap(context: Context, path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }
}
