package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0,
    
    val month: Int, // Il mese (da 1 a 12)
    
    val year: Int, // L'anno (es. 2026)
    
    val limitAmount: Double? = null // La cifra massima impostata per quel mese
)