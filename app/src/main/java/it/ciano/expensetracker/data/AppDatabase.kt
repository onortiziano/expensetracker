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
        GlobalBudget::class, 
        RecurringTransaction::class, 
        RecurringTransactionTag::class
    ], 
    version = 5, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun globalBudgetDao(): GlobalBudgetDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun recurringTransactionTagDao(): RecurringTransactionTagDao

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

        // v4 -> v5: tabelle per le transazioni ricorrenti
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        frequency TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        nextDueDate INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        lastGeneratedDate INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_transaction_tags (
                        recurringTransactionId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(recurringTransactionId, tagId),
                        FOREIGN KEY(recurringTransactionId) REFERENCES recurring_transactions(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(tagId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recurring_transaction_tags_tagId ON recurring_transaction_tags (tagId)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration() // Ultima risorsa in caso di schema non gestito
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}