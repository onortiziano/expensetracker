package it.ciano.expensetracker.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithTags(
    val transaction: Transaction,
    val tags: List<Tag>
)
