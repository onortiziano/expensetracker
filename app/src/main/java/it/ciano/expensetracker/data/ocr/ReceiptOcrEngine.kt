package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object ReceiptOcrEngine {

    private const val TAG = "ReceiptOcrEngine"

    suspend fun recognize(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing URI: $uri")

            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                Log.w(TAG, "InputImage.fromFilePath failed, trying BitmapFactory fallback: ${e.message}")
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(TAG, "Cannot open InputStream for URI: $uri")
                    return@withContext null
                }
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) {
                    Log.e(TAG, "BitmapFactory.decodeStream returned null for URI: $uri")
                    return@withContext null
                }
                Log.d(TAG, "Bitmap fallback succeeded: ${bitmap.width}x${bitmap.height}")
                InputImage.fromBitmap(bitmap, 0)
            }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Log.d(TAG, "ML Kit recognizer created, processing image...")

            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        recognizer.close()
                        if (cont.isActive) cont.resume(text)
                    }
                    .addOnFailureListener { e ->
                        recognizer.close()
                        Log.e(TAG, "ML Kit processing failed: ${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }

                cont.invokeOnCancellation {
                    Log.w(TAG, "OCR coroutine cancelled, closing recognizer")
                    recognizer.close()
                }
            }

            if (result != null) {
                val text = result.text
                Log.d(TAG, "OCR succeeded. Text length: ${text.length}, preview: ${text.take(200)}")
                text.ifEmpty {
                    Log.w(TAG, "OCR returned empty string")
                    null
                }
            } else {
                Log.w(TAG, "OCR returned null result")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during OCR: ${e.message}", e)
            null
        }
    }
}
