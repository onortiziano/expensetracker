package it.ciano.expensetracker.data.model

import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Junction

data class TransactionWithTags(
    @Embedded val transaction: Transaction
) {
    @Relation(
        parentColumn = "id",
        entityColumn = "tagId",
        associateBy = Junction(TransactionTag::class)
    )
    var tags: List<Tag> = emptyList()
}
