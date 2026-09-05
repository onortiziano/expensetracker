package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Canvas
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Wrapper ML Kit per il riconoscimento del testo nelle ricevute.
 *
 * Per migliorare l'affidabilità rispetto al semplice `InputImage.fromFilePath`,
 * questa implementazione applica un preprocessing manuale:
 *  - downscale dell'immagine a un lato lungo massimo (ML Kit lavora meglio sotto ~2000px)
 *  - applicazione esplicita dell'orientamento EXIF (indispensabile con `fromBitmap`)
 *  - generazione di più varianti (originale, scala di grigi, contrasto aumentato)
 *  - scelta automatica della variante che produce il testo più lungo/ricco
 */
object ReceiptOcrEngine {

    private const val TAG = "ReceiptOcrEngine"
    private const val MAX_DIMENSION = 2048

    suspend fun recognize(uri: Uri, context: Context): ReceiptOcrResult? = withContext(Dispatchers.Default) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: run { Log.e(TAG, "Cannot open InputStream for URI: $uri"); return@withContext null }
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (original == null) {
                Log.e(TAG, "BitmapFactory.decodeStream returned null for URI: $uri")
                return@withContext null
            }

            val oriented = applyOrientation(original, uri)
            if (oriented !== original) original.recycle()

            val base = downscale(oriented)

            val variants = buildVariants(base)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            var bestGraph: Text? = null
            var bestSize = 0
            for (variant in variants) {
                val graph = recognizeBitmap(recognizer, variant)
                val size = graph?.text?.length ?: 0
                if (size > bestSize) {
                    bestSize = size
                    bestGraph = graph
                    Log.d(TAG, "Better OCR variant (len=$bestSize)")
                }
                if (variant !== base) variant.recycle()
            }
            recognizer.close()

            if (base !== oriented) base.recycle()
            oriented.recycle()

            val result = bestGraph?.let(::toReceiptResult)
            result?.takeIf { it.rawText.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during OCR: ${e.message}", e)
            null
        }
    }

    private fun toReceiptResult(graph: Text): ReceiptOcrResult {
        val words = mutableListOf<OcrWord>()
        for (block in graph.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    words += OcrWord(
                        text = element.text,
                        left = box.left.toFloat(),
                        top = box.top.toFloat(),
                        right = box.right.toFloat(),
                        bottom = box.bottom.toFloat()
                    )
                }
            }
        }
        val lines = OcrLayout.groupWordsIntoLines(words)
        val rawText = lines.joinToString("\n") { it.text }
        return ReceiptOcrResult(lines, rawText)
    }

    private suspend fun recognizeBitmap(recognizer: TextRecognizer, bitmap: Bitmap): Text? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text: Text ->
                        if (cont.isActive) cont.resume(text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit processing failed: ${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation {
                    Log.w(TAG, "OCR coroutine cancelled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "recognizeBitmap failed: ${e.message}", e)
            null
        }
    }

    /**
     * Applica l'orientamento EXIF della foto (deprecato su API 23 ma con fallback)
     * per evitare testo ruotato che degrada fortemente l'OCR.
     */
    private fun applyOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val path = uri.path
            val rotation = if (path != null) {
                try {
                    when (ExifInterface(path).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } catch (_: Exception) {
                    0f
                }
            } else {
                0f
            }
            if (rotation == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyOrientation failed: ${e.message}")
            bitmap
        }
    }

    /**
     * Riduce la risoluzione a un lato lungo massimo configurato.
     */
    private fun downscale(bitmap: Bitmap): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height).toFloat()
        if (maxSide <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION / maxSide
        val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /**
     * Costruisce le varianti di immagine su cui provare il riconoscimento:
     *  - originale
     *  - scala di grigi (migliora il contrasto testo/sfondo su ricevute a colori)
     *  - contrasto aumentato
     */
    private fun buildVariants(bitmap: Bitmap): List<Bitmap> {
        val variants = mutableListOf<Bitmap>()
        variants.add(bitmap)
        variants.add(toGrayscale(bitmap))
        variants.add(increaseContrast(bitmap))
        return variants
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0.299f, 0.587f, 0.114f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    private fun increaseContrast(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // Matrice di contrasto: scala i canali e riporta al centro (128)
        val scale = 1.6f
        val translate = 128f * (1f - scale)
        val matrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }
}
