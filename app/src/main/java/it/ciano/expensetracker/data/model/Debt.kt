package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId"), Index("isSettled")]
)
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionId: Int,
    val name: String,
    val amount: Double,
    val isSettled: Int = 0,
    val settledDate: Long = 0L
)