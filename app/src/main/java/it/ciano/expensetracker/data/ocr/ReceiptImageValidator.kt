package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Helper per la validazione e il caricamento dei file immagine delle ricevute.
 * Su alcuni device (es. MIUI) il meccanismo di sistema può scrivere file corrotti
 * (immagini placeholder, es. rettangoli verde uniforme) che non contengono la scena
 * fotografata. Queste funzioni aiutano a rilevare tali casi prima di tentare l'OCR.
 */
object ReceiptImageValidator {

    /**
     * Decodifica il file immagine dal path dato.
     * Restituisce null se il file non esiste o non è un'immagine decodificabile.
     */
    fun decodeFile(path: String, maxPixels: Int = 4096 * 4096): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = false }
            BitmapFactory.decodeFile(path, opts)?.let { bmp ->
                if (bmp.width <= 0 || bmp.height <= 0) null else bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifica se il file è una immagine valida e non un blocco uniforme.
     * Un'immagine "verde uniforme" o comunque priva di contenuto non dà risultati OCR.
     *
     * @return true se l'immagine è decodificabile e contiene variazione di colore.
     */
    fun isValidReceiptImage(path: String): Boolean {
        val bmp = decodeFile(path) ?: return false
        if (bmp.width < 16 || bmp.height < 16) return false

        return try {
            // Campiona pochi pixel per capire se l'immagine è un blocco uniforme.
            val step = maxOf(1, minOf(bmp.width, bmp.height) / 10)
            var distinct = 0
            var prevRgb = Int.MIN_VALUE
            var y = 0
            while (y < bmp.height) {
                var x = 0
                while (x < bmp.width) {
                    val rgb = bmp.getPixel(x, y)
                    if (rgb != prevRgb) {
                        distinct++
                        prevRgb = rgb
                    }
                    x += step
                }
                y += step
            }
            // Se c'è più di un colore distinto, l'immagine ha contenuto.
            distinct > 1
        } catch (e: Exception) {
            // In caso di errore, preferiamo provare l'OCR (fallback ottimistico).
            true
        }
    }
}
