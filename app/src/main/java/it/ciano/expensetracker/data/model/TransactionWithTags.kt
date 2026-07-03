package it.ciano.expensetracker.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithTags(
    @Embedded val transaction: Transaction,
    @Relation(
        junction = TransactionTag::class,
        parentColumn = "id",
        entityColumn = "tagId"
    )
    val tags: List<Tag>
)
