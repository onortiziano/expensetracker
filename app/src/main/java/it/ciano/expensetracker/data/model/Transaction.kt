package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) 
    val id: Int = 0,
    
    val amount: Double, // L'importo della spesa o dell'entrata
    
    // Qui usiamo una stringa per decidere se è un'entrata o un'uscita.
    // Useremo i valori "INCOME" per le entrate e "EXPENSE" per le uscite.
    val type: String, 
    
    // Questo è l'ID della categoria a cui appartiene questa transazione.
    // Collega questa tabella alla tabella 'categories' che abbiamo creato prima.
    val categoryId: Int,
    
    val date: Long, // La data salvata come timestamp (millisecondi)
    
    val note: String = "" // Una nota opzionale, di default è una stringa vuota
)