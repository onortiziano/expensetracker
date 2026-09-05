package it.ciano.expensetracker.data.ocr

/**
 * Modelli puri (nessuna dipendenza Android) per l'output strutturato dell'OCR.
 */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrLine(
    val text: String,
    val top: Float
)

data class ReceiptOcrResult(
    val lines: List<OcrLine>,
    val rawText: String
) {
    companion object {
        val EMPTY = ReceiptOcrResult(emptyList(), "")
    }
}

object OcrLayout {

    /**
     * Ricompone le parole riconosciute (o singole box) in righe orizzontali:
     * parole il cui centro verticale cade entro una tolleranza appartengono
     * alla stessa riga, ordinata da sinistra a destra.
     */
    fun groupWordsIntoLines(words: List<OcrWord>): List<OcrLine> {
        if (words.isEmpty()) return emptyList()

        val avgHeight = words.map { it.bottom - it.top }.average().let { if (it.isNaN()) 0f else it.toFloat() }
        val tolerance = (avgHeight * 0.4f).coerceAtLeast(2f)

        val sorted = words
            .map { it to (it.top + it.bottom) / 2f }
            .sortedBy { it.second }

        val grouped = mutableListOf<MutableList<OcrWord>>()
        var currentY: Float? = null
        for ((word, centerY) in sorted) {
            if (currentY == null || centerY - currentY > tolerance) {
                currentY = centerY
                grouped.add(mutableListOf(word))
            } else {
                grouped.last().add(word)
            }
        }

        return grouped.map { group ->
            val ordered = group.sortedBy { it.left }
            val text = ordered.joinToString(" ") { it.text }
            OcrLine(
                text = text,
                top = group.minOf { it.top }
            )
        }.sortedBy { it.top }
    }
}