package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0,
    
    val title: String, // Aggiunto: Titolo della transazione
    val amount: Double, 
    
    val type: String, 
    val categoryId: Int,
    val date: Long, 
    
    val note: String = "",
    val receiptUri: String = ""
)