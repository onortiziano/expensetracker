package it.ciano.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import it.ciano.expensetracker.data.dao.*
import it.ciano.expensetracker.data.model.*

// Definiamo le tabelle che compongono il database e la versione (1)
@Database(entities = [Category::class, Transaction::class, Budget::class, Tag::class, TransactionTag::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Metodi astratti per ottenere i nostri "telecomandi" (DAO)
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao

    companion object {
        // Variabile per tenere traccia dell'unica istanza del database (Singleton)
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Funzione per ottenere l'istanza del database in modo sicuro
        fun getDatabase(context: Context): AppDatabase {
            // Se l'istanza esiste già, la restituiamo. Se no, la creiamo.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db" // Nome del file del database sul disco
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}