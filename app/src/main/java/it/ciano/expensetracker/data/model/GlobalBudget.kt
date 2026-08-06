package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "global_budgets",
    indices = [Index(value = ["month", "year"], unique = true)]
)
data class GlobalBudget(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val month: Int, // 1-12
    val year: Int
)