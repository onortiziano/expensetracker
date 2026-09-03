package it.ciano.expensetracker.data.ocr

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider

class ReceiptCaptureManager(private val activity: ComponentActivity) {

    companion object {
        const val AUTHORITY = "it.ciano.expensetracker.fileprovider"
    }

    private var pendingPath: String? = null

    private val takePicture = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingPath
        if (success && path != null) {
            onPhotoCaptured?.invoke(path)
        } else {
            onPhotoCaptured?.invoke(null)
        }
    }

    var onPhotoCaptured: ((String?) -> Unit)? = null

    fun createFileUri(): Uri {
        val file = ReceiptStorage.createReceiptFile(activity, System.currentTimeMillis())
        pendingPath = file.absolutePath
        return FileProvider.getUriForFile(activity, AUTHORITY, file)
    }

    fun launch() {
        takePicture.launch(createFileUri())
    }
}
