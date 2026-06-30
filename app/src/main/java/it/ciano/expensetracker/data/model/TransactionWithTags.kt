package it.ciano.expensetracker.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithTags(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagId",
        associateBy = androidx.room.Junction(TransactionTag::class)
    )
    val tags: List<Tag>
)
