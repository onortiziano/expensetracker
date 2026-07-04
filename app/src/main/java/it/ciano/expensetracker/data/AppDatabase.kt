package it.ciano.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.ciano.expensetracker.data.dao.*
import it.ciano.expensetracker.data.model.*

// Definiamo le tabelle che compongono il database e la versione (4)
@Database(entities = [Category::class, Transaction::class, Budget::class, Tag::class, TransactionTag::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao

    companion object {
        // Variabile per tenere traccia dell'unica istanza del database (Singleton)
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Funzione per ottenere l'istanza del database in modo sicuro
        fun getDatabase(context: Context): AppDatabase {
            // Se l'istanza esiste già, la restituiamo. Se no, la creiamo.
            return INSTANCE ?: synchronized(this) {
                val migration_3_4 = object : Migration(3, 4) {
                    override fun migrate(database: SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE categories ADD COLUMN budget REAL")
                    }
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db" // Nome del file del database sul disco
                ).addMigrations(migration_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}