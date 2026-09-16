package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction

object RecurringTransactionGenerator {

    /**
     * Per ogni template attivo con nextDueDate <= today genera le occorrenze mancanti
     * (retrodatate alla scadenza) e restituisce i template aggiornati.
     */
    fun generate(
        templates: List<RecurringTransaction>,
        tagIdsByRecurring: Map<Int, Set<Int>>,
        today: Long
    ): GenerationResult {
        val occurrences = mutableListOf<GeneratedOccurrence>()
        val updated = mutableListOf<RecurringTransaction>()

        for (t in templates) {
            if (!t.isActive || t.nextDueDate > today) continue

            val endBoundary = t.endDate ?: Long.MAX_VALUE
            val tagIds = tagIdsByRecurring[t.id] ?: emptySet()

            var cursor = t.nextDueDate
            var lastGenerated = t.lastGeneratedDate
            var generated = false

            while (cursor <= today && cursor <= endBoundary) {
                occurrences.add(
                    GeneratedOccurrence(
                        title = t.title,
                        amount = t.amount,
                        type = t.type,
                        categoryId = t.categoryId,
                        note = t.note,
                        date = cursor,
                        tagIds = tagIds
                    )
                )
                lastGenerated = cursor
                generated = true
                cursor = RecurringDateCalculator.advance(cursor, t.frequency)
            }

            if (!generated) continue

            val stillActive = !(t.endDate != null && cursor > t.endDate)
            updated.add(
                t.copy(
                    nextDueDate = cursor,
                    lastGeneratedDate = lastGenerated,
                    isActive = stillActive
                )
            )
        }
        return GenerationResult(occurrences, updated)
    }
}