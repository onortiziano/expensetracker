package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) 
    val categoryId: Int = 0,
    
    val name: String,
    
    // Se è null, la categoria è principale (es. "Cibo"). 
    // Se ha un valore, è una sottocategoria (es. "Supermercato" che punta a "Cibo").
    val parentCategoryId: Int? = null 
)
