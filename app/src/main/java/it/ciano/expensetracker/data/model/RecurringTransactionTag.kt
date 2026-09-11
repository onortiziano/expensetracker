package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recurring_transaction_tags",
    primaryKeys = ["recurringTransactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransaction::class,
            parentColumns = ["id"],
            childColumns = ["recurringTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["tagId"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class RecurringTransactionTag(
    val recurringTransactionId: Int,
    val tagId: Int
)
