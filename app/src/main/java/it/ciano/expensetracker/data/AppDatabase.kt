package it.ciano.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.ciano.expensetracker.data.dao.*
import it.ciano.expensetracker.data.model.*

@Database(
    entities = [
        Category::class, 
        Transaction::class, 
        Tag::class, 
        TransactionTag::class, 
        GlobalBudget::class
    ], 
    version = 4, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun globalBudgetDao(): GlobalBudgetDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: indice UNIQUE su global_budgets (month, year).
        // Prima deduplica le righe duplicate esistenti per evitare il fallimento del CREATE INDEX.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM global_budgets WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM global_budgets GROUP BY month, year)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_global_budgets_month_year ON global_budgets (month, year)"
                )
            }
        }

        // v2 -> v3: rimozione tabelle legacy non più usate (Budget e CategoryBudget)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS budgets")
                db.execSQL("DROP TABLE IF EXISTS category_budgets")
            }
        }

        // v3 -> v4: nuova colonna receiptUri per la foto della ricevuta
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUri TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration() // Ultima risorsa in caso di schema non gestito
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}