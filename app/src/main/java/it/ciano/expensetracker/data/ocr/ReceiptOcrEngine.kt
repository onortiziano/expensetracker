package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object ReceiptOcrEngine {

    suspend fun recognize(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        recognizer.close()
                        cont.resume(result.text)
                    }
                    .addOnFailureListener {
                        recognizer.close()
                        cont.resume(null)
                    }
            }
        } catch (e: Exception) {
            null
        }
    }
}
