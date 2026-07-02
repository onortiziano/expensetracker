package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class TransactionTagPrimaryKey(
    val transactionId: Int,
    val tagId: Int
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"]
)
data class TransactionTag(
    val transactionId: Int,
    val tagId: Int
)
