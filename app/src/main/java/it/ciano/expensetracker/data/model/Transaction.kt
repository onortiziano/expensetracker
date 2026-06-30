package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) 
    val transactionId: Int = 0,
    
    val title: String = "",
    val amount: Double,
    val type: String,
    val categoryId: Int,
    val note: String = "",
    val date: Long = System.currentTimeMillis()
)
