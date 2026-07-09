package it.ciano.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import it.ciano.expensetracker.data.dao.*
import it.ciano.expensetracker.data.model.*

@Database(
    entities = [
        Category::class, 
        Transaction::class, 
        Budget::class, 
        Tag::class, 
        TransactionTag::class, 
        GlobalBudget::class, 
        CategoryBudget::class
    ], 
    version = 1, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun globalBudgetDao(): GlobalBudgetDao
    abstract fun categoryBudgetDao(): CategoryBudgetDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .fallbackToDestructiveMigration() // Reset totale in caso di cambio versione
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}