package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true) 
    val tagId: Int = 0,
    
    val name: String,
    val color: Int = 0xFF6200EE.toInt()
)
