package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class TransactionTag(
    val transactionId: Int,
    val tagId: Int
)
