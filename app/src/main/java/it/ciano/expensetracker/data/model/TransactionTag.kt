package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"]
)
data class TransactionTag(
    val transactionId: Int,
    val tagId: Int
)
