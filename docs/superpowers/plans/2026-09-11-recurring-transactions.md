# Recurring Transactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add recurring transaction templates that auto-generate real transactions on app launch, with AlarmManager due-date notifications and a calendar preview of upcoming occurrences.

**Architecture:** New `recurring_transactions` + `recurring_transaction_tags` Room tables (DB v4→v5). A pure `RecurringDateCalculator` (shared date-advance logic) feeds a pure `RecurringTransactionGenerator` that produces occurrence data; a repository persists it atomically via `withTransaction`. `RecurringReminderScheduler` + `RecurringReminderReceiver` handle one-shot AlarmManager reminders. A new `RecurringTransactionsScreen` (list + add/edit dialog + `RecurringCalendar` month grid) is reachable from the drawer.

**Tech Stack:** Kotlin 1.9.24, Jetpack Compose (M3, BOM 2024.04.01), Room 2.6.1 (KSP), Flow/MVVM with manual `ViewModelFactory`, AlarmManager + NotificationManager, JUnit4 for JVM tests.

**Spec:** `docs/superpowers/specs/2026-09-11-recurring-transactions-design.md`

## Global Constraints

- Package root: `it.ciano.expensetracker`
- minSdk 23, targetSdk 35 → guard `AlarmManager.canScheduleExactAlarms()` (API 31+) and `NotificationChannel` (API 26+, `Build.VERSION_CODES.O`) with SDK checks.
- All UI strings must be added to both `res/values/strings.xml` (Italian, default) and `res/values-en/strings.xml` (English).
- Database version bump 4 → 5 with an explicit `Migration(4, 5)`; keep `fallbackToDestructiveMigration()` as-is.
- No third-party UI libraries — the calendar must be a hand-written Composables grid.
- Follow existing repo patterns: `StateFlow` UI state + `viewModel(factory = ViewModelFactory(app))`, `AlertDialog` for forms, `Channel`-based background deletes where relevant.
- Tests: JUnit4 JVM tests in `app/src/test/...` (no Robolectric available). Date logic must be pure (no Android deps) to be testable.
- Icon style via `mainViewModel.getIcon(filled, outlined, rounded, sharp, twoTone)` — all five variants required.
- Commit messages follow repo style (`feat:`, `test:`, `docs:`); commits must be small and per-task. **Note:** this filesystem rejects loose-object writes into `.git/objects`. To commit, use the workaround documented at the end of the plan.
- Style: Italian inline comments, English identifiers (matches existing code).

---

### Task 1: Entities + DB Migration v4→v5

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransaction.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransactionTag.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransactionWithTags.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt`

**Interfaces:**
- Produces: `RecurringTransaction` (table `recurring_transactions`), `RecurringTransactionTag` (table `recurring_transaction_tags`), `RecurringTransactionWithTags(recurring, tags)`. These types are used by every later task.

- [ ] **Step 1: Create `RecurringTransaction.kt`**

`data/model/RecurringTransaction.kt`:
```kotlin
package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val amount: Double,
    val type: String,          // "EXPENSE" o "INCOME"
    val categoryId: Int,
    val frequency: String,     // DAILY | WEEKLY | BIWEEKLY | MONTHLY | ANNUAL
    val startDate: Long,       // epoch millis
    val endDate: Long? = null, // null = indefinita
    val nextDueDate: Long,     // quando generare la prossima transazione
    val note: String = "",
    val isActive: Boolean = true,
    val lastGeneratedDate: Long? = null
)
```

- [ ] **Step 2: Create `RecurringTransactionTag.kt`**

`data/model/RecurringTransactionTag.kt`:
```kotlin
package it.ciano.expensetracker.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recurring_transaction_tags",
    primaryKeys = ["recurringTransactionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransaction::class,
            parentColumns = ["id"],
            childColumns = ["recurringTransactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["tagId"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tagId"])]
)
data class RecurringTransactionTag(
    val recurringTransactionId: Int,
    val tagId: Int
)
```

- [ ] **Step 3: Create `RecurringTransactionWithTags.kt`**

`data/model/RecurringTransactionWithTags.kt`:
```kotlin
package it.ciano.expensetracker.data.model

data class RecurringTransactionWithTags(
    val recurring: RecurringTransaction,
    val tags: List<Tag>
)
```

- [ ] **Step 4: Register entities + migration in `AppDatabase.kt`**

Edit `AppDatabase.kt`:
- Add `RecurringTransaction::class, RecurringTransactionTag::class` to the `entities` array.
- Bump `version = 4` to `version = 5`.
- Add the migration after `MIGRATION_3_4`:

```kotlin
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
```
- Add `MIGRATION_4_5` to the `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)` call.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransaction.kt app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransactionTag.kt app/src/main/java/it/ciano/expensetracker/data/model/RecurringTransactionWithTags.kt app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt
git commit -m "feat: recurring transactions entities e migration v4->v5"
```
(Use the commit workaround documented at the end of the plan.)

---

### Task 2: DAOs

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/dao/RecurringTransactionDao.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/dao/RecurringTransactionTagDao.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt`

**Interfaces:**
- Consumes: entities from Task 1.
- Produces:
  - `RecurringTransactionDao.getAllRecurring(): Flow<List<RecurringTransaction>>`
  - `RecurringTransactionDao.getActiveDue(today: Long): List<RecurringTransaction>`
  - `RecurringTransactionDao.insertRecurring(r): Long`, `updateRecurring(r)`, `deleteRecurring(r)`
  - `RecurringTransactionTagDao.getAllRecurringTags(): Flow<List<RecurringTransactionTag>>`
  - `RecurringTransactionTagDao.getTagIdsForRecurring(id: Int): List<Int>`
  - `RecurringTransactionTagDao.insertRecurringTag(link)`, `deleteTagsForRecurring(id)`

- [ ] **Step 1: Create `RecurringTransactionDao.kt`**

`data/dao/RecurringTransactionDao.kt`:
```kotlin
package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.RecurringTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransaction): Long

    @Update
    suspend fun updateRecurring(recurring: RecurringTransaction)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringTransaction)

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueDate ASC")
    fun getAllRecurring(): Flow<List<RecurringTransaction>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextDueDate <= :today")
    suspend fun getActiveDue(today: Long): List<RecurringTransaction>
}
```

- [ ] **Step 2: Create `RecurringTransactionTagDao.kt`**

`data/dao/RecurringTransactionTagDao.kt`:
```kotlin
package it.ciano.expensetracker.data.dao

import androidx.room.*
import it.ciano.expensetracker.data.model.RecurringTransactionTag
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringTag(link: RecurringTransactionTag)

    @Query("DELETE FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringId")
    suspend fun deleteTagsForRecurring(recurringId: Int)

    @Query("SELECT * FROM recurring_transaction_tags")
    fun getAllRecurringTags(): Flow<List<RecurringTransactionTag>>

    @Query("SELECT tagId FROM recurring_transaction_tags WHERE recurringTransactionId = :recurringId")
    suspend fun getTagIdsForRecurring(recurringId: Int): List<Int>
}
```

- [ ] **Step 3: Register DAOs in `AppDatabase.kt`**

Add to `AppDatabase`:
```kotlin
abstract fun recurringTransactionDao(): RecurringTransactionDao
abstract fun recurringTransactionTagDao(): RecurringTransactionTagDao
```
(The `import it.ciano.expensetracker.data.dao.*` already covers the new DAOs.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/dao/RecurringTransactionDao.kt app/src/main/java/it/ciano/expensetracker/data/dao/RecurringTransactionTagDao.kt app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt
git commit -m "feat: DAO per transazioni ricorrenti"
```
(Use the commit workaround.)

---

### Task 3: `RecurringDateCalculator` (pure date logic) — TDD

**Files:**
- Test: `app/src/test/java/it/ciano/expensetracker/data/generation/RecurringDateCalculatorTest.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/generation/RecurringDateCalculator.kt`

**Interfaces:**
- Consumes: `RecurringTransaction` (Task 1).
- Produces:
  - `RecurringDateCalculator.advance(dateMillis: Long, frequency: String): Long` — next occurrence strictly after `dateMillis`.
  - `RecurringDateCalculator.occurrencesInMonth(template: RecurringTransaction, year: Int, month: Int): List<Long>` — `month` is 0-based (`Calendar.MONTH` convention); returns epoch-millis dates of upcoming occurrences within that month, stopping at `endDate` (inclusive).
  - `RecurringDateCalculator.FREQUENCIES: List<String>` = `["DAILY","WEEKLY","BIWEEKLY","MONTHLY","ANNUAL"]` (used by the UI dropdown).
  - `RecurringDateCalculator.FREQUENCY_LABELS: Map<String, Int>` = frequency → string resource id (used by the UI list).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/it/ciano/expensetracker/data/generation/RecurringDateCalculatorTest.kt`:
```kotlin
package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringDateCalculatorTest {

    private fun millis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
        }.timeInMillis
    }

    private fun dayOfMonth(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.DAY_OF_MONTH)

    private fun monthOf(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.MONTH)

    private fun yearOf(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)

    @Test
    fun `advance daily aggiunge un giorno`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 2), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "DAILY"))
    }

    @Test
    fun `advance weekly aggiunge sette giorni`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 8), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "WEEKLY"))
    }

    @Test
    fun `advance biweekly aggiunge quattordici giorni`() {
        assertEquals(millis(2026, Calendar.SEPTEMBER, 15), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "BIWEEKLY"))
    }

    @Test
    fun `advance monthly mantiene lo stesso giorno del mese`() {
        assertEquals(millis(2026, Calendar.OCTOBER, 1), RecurringDateCalculator.advance(millis(2026, Calendar.SEPTEMBER, 1), "MONTHLY"))
    }

    @Test
    fun `advance monthly clamp a fine mese il 31 gennaio`() {
        val advanced = RecurringDateCalculator.advance(millis(2026, Calendar.JANUARY, 31), "MONTHLY")
        assertEquals(2026, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(28, dayOfMonth(advanced))
    }

    @Test
    fun `advance monthly dopo clamp mantiene il giorno clampato`() {
        // 31 gen -> 28 feb -> 28 mar (non scivola al 31)
        val advanced = RecurringDateCalculator.advance(millis(2026, Calendar.FEBRUARY, 28), "MONTHLY")
        assertEquals(28, dayOfMonth(advanced))
        assertEquals(Calendar.MARCH, monthOf(advanced))
    }

    @Test
    fun `advance monthly clamp anno bisestile a 29 febbraio`() {
        val advanced = RecurringDateCalculator.advance(millis(2024, Calendar.JANUARY, 31), "MONTHLY")
        assertEquals(2024, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(29, dayOfMonth(advanced))
    }

    @Test
    fun `advance annual mantiene giorno e mese`() {
        assertEquals(millis(2027, Calendar.JUNE, 15), RecurringDateCalculator.advance(millis(2026, Calendar.JUNE, 15), "ANNUAL"))
    }

    @Test
    fun `advance annual clamp 29 febbraio bisestile a 28 febbraio`() {
        val advanced = RecurringDateCalculator.advance(millis(2024, Calendar.FEBRUARY, 29), "ANNUAL")
        assertEquals(2025, yearOf(advanced))
        assertEquals(Calendar.FEBRUARY, monthOf(advanced))
        assertEquals(28, dayOfMonth(advanced))
    }

    @Test
    fun `occurrences mensili nel mese restituisce la data giusta`() {
        val template = RecurringTransaction(
            title = "Affitto", amount = 800.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), dates)
    }

    @Test
    fun `occurrences biweekly copre piu date nel mese`() {
        val template = RecurringTransaction(
            title = "Spesa", amount = 30.0, type = "EXPENSE", categoryId = 2,
            frequency = "BIWEEKLY", startDate = millis(2026, Calendar.SEPTEMBER, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 1)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(1L, 15L, 29L).map { millis(2026, Calendar.SEPTEMBER, it) }, dates)
    }

    @Test
    fun `occurrences di template inattivo e vuota`() {
        val template = RecurringTransaction(
            title = "Af", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15), isActive = false
        )
        assertTrue(RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER).isEmpty())
    }

    @Test
    fun `occurrences rispettano la endDate inclusiva`() {
        val template = RecurringTransaction(
            title = "Bo", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.SEPTEMBER, 15),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 15)
        )
        val dates = RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), dates)
    }

    @Test
    fun `occurrences dopo la endDate sono vuote`() {
        val template = RecurringTransaction(
            title = "Bo", amount = 1.0, type = "EXPENSE", categoryId = 1,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.AUGUST, 15),
            nextDueDate = millis(2026, Calendar.AUGUST, 15)
        )
        assertTrue(RecurringDateCalculator.occurrencesInMonth(template, 2026, Calendar.SEPTEMBER).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.generation.RecurringDateCalculatorTest"`
Expected: FAIL — `RecurringDateCalculator` unresolved.

- [ ] **Step 3: Write the implementation**

`data/generation/RecurringDateCalculator.kt`:
```kotlin
package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.model.RecurringTransaction
import java.util.Calendar

object RecurringDateCalculator {

    val FREQUENCIES = listOf("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUAL")

    val FREQUENCY_LABELS: Map<String, Int> = mapOf(
        "DAILY" to R.string.str_giornaliera,
        "WEEKLY" to R.string.str_settimanale,
        "BIWEEKLY" to R.string.str_bisettimanale,
        "MONTHLY" to R.string.str_mensile,
        "ANNUAL" to R.string.str_annuale
    )

    private fun calendarAt(dateMillis: Long): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = dateMillis
            clear(Calendar.HOUR_OF_DAY)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
        }

    /** Restituisce la ricorrenza successiva strettamente dopo dateMillis. */
    fun advance(dateMillis: Long, frequency: String): Long {
        val cal = calendarAt(dateMillis)
        when (frequency) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "BIWEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 14)
            "MONTHLY" -> {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.add(Calendar.MONTH, 1)
                val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, max))
            }
            "ANNUAL" -> {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                cal.add(Calendar.YEAR, 1)
                val max = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, minOf(day, max))
            }
            else -> error("Frequenza sconosciuta: $frequency")
        }
        return cal.timeInMillis
    }

    /** Date (epoch millis) delle prossime scadenze del template all'interno del mese indicato (month 0-based). */
    fun occurrencesInMonth(template: RecurringTransaction, year: Int, month: Int): List<Long> {
        if (!template.isActive) return emptyList()

        val firstOfMonth = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
        }.timeInMillis
        val lastOfMonth = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis

        // Partiamo da nextDueDate e avanziamo fino ad entrare nel mese visualizzato.
        var cursor = template.nextDueDate
        val endBoundary = template.endDate ?: Long.MAX_VALUE
        if (cursor > endBoundary) return emptyList()

        while (cursor < firstOfMonth) {
            cursor = advance(cursor, template.frequency)
            if (cursor > endBoundary) return emptyList()
        }

        val dates = mutableListOf<Long>()
        while (cursor <= lastOfMonth && cursor <= endBoundary) {
            dates.add(cursor)
            cursor = advance(cursor, template.frequency)
        }
        return dates
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.generation.RecurringDateCalculatorTest"`
Expected: PASS (the two `str_*` resources must exist — they are added in Task 8; if they are missing, add them now from the Task 8 string table to keep this compiling).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/generation/RecurringDateCalculator.kt app/src/test/java/it/ciano/expensetracker/data/generation/RecurringDateCalculatorTest.kt
git commit -m "test: copertura RecurringDateCalculator piu advance e occurrences"
```
(Use the commit workaround.)

---

### Task 4: `RecurringTransactionGenerator` (pure generation) — TDD

**Files:**
- Test: `app/src/test/java/it/ciano/expensetracker/data/generation/RecurringTransactionGeneratorTest.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/data/generation/RecurringTransactionGenerator.kt` (create)
- Create: `app/src/main/java/it/ciano/expensetracker/data/generation/GeneratedOccurrence.kt`

**Interfaces:**
- Consumes: `RecurringDateCalculator` (Task 3), `RecurringTransaction` (Task 1).
- Produces:
  - `GeneratedOccurrence(title: String, amount: Double, type: String, categoryId: Int, note: String, date: Long, tagIds: Set<Int>)`
  - `GenerationResult(occurrences: List<GeneratedOccurrence>, updated: List<RecurringTransaction>)`
  - `RecurringTransactionGenerator.generate(templates: List<RecurringTransaction>, tagIdsByRecurring: Map<Int, Set<Int>>, today: Long): GenerationResult`
  - Semantics: for each active template with `nextDueDate <= today`, generate occurrences at each due date from `nextDueDate` while `date <= today` AND `date <= endDate` (inclusive). After the loop set `nextDueDate = cursor` (first future occurrence), `lastGeneratedDate` = last generated date (or keep old value if none generated), and `isActive = false` when `cursor > endDate`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/it/ciano/expensetracker/data/generation/RecurringTransactionGeneratorTest.kt`:
```kotlin
package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringTransactionGeneratorTest {

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }.timeInMillis

    private fun template(
        id: Int, frequency: String, nextDueDate: Long, endDate: Long? = null,
        isActive: Boolean = true, startDate: Long = nextDueDate
    ) = RecurringTransaction(
        id = id, title = "Affitto $id", amount = 800.0, type = "EXPENSE", categoryId = 1,
        frequency = frequency, startDate = startDate, endDate = endDate,
        nextDueDate = nextDueDate, isActive = isActive
    )

    @Test
    fun `nessun template dovuto produce nessuna generazione`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val result = RecurringTransactionGenerator.generate(
            listOf(template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15))),
            emptyMap(), today
        )
        assertTrue(result.occurrences.isEmpty())
        assertTrue(result.updated.isEmpty())
    }

    @Test
    fun `template mensile in ritardo genera i periodi mancanti`() {
        val today = millis(2026, Calendar.FEBRUARY, 15)
        val t = template(1, "MONTHLY", millis(2026, Calendar.JANUARY, 1), startDate = millis(2026, Calendar.JANUARY, 1))
        val result = RecurringTransactionGenerator.generate(listOf(t), mapOf(1 to setOf(5)), today)

        val dates = result.occurrences.map { it.date }
        assertEquals(listOf(millis(2026, Calendar.JANUARY, 1), millis(2026, Calendar.FEBRUARY, 1)), dates)
        assertEquals(listOf(5), result.occurrences.first().tagIds.toList())

        val updated = result.updated.single()
        assertEquals(millis(2026, Calendar.MARCH, 1), updated.nextDueDate)
        assertEquals(millis(2026, Calendar.FEBRUARY, 1), updated.lastGeneratedDate)
        assertTrue(updated.isActive)
    }

    @Test
    fun `template inattivo non genera nulla`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 5), isActive = false)
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertTrue(result.occurrences.isEmpty())
    }

    @Test
    fun `endDate inclusiva genera l ultima occorrenza e poi disattiva`() {
        val today = millis(2026, Calendar.FEBRUARY, 15)
        val t = template(
            1, "MONTHLY", millis(2026, Calendar.JANUARY, 1),
            endDate = millis(2026, Calendar.FEBRUARY, 1),
            startDate = millis(2026, Calendar.JANUARY, 1)
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)

        assertEquals(listOf(millis(2026, Calendar.JANUARY, 1), millis(2026, Calendar.FEBRUARY, 1)), result.occurrences.map { it.date })
        val updated = result.updated.single()
        assertFalse(updated.isActive)
        assertEquals(millis(2026, Calendar.MARCH, 1), updated.nextDueDate)
    }

    @Test
    fun `endDate gia superata disattiva senza generare oltre`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(
            1, "MONTHLY", millis(2026, Calendar.AUGUST, 15),
            endDate = millis(2026, Calendar.AUGUST, 15),
            startDate = millis(2026, Calendar.AUGUST, 15)
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(listOf(millis(2026, Calendar.AUGUST, 15)), result.occurrences.map { it.date })
        val updated = result.updated.single()
        assertFalse(updated.isActive)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 15), updated.nextDueDate)
    }

    @Test
    fun `scadenza esattamente oggi viene generata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 15)
        val t = template(1, "MONTHLY", millis(2026, Calendar.SEPTEMBER, 15), startDate = millis(2026, Calendar.SEPTEMBER, 15))
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(listOf(millis(2026, Calendar.SEPTEMBER, 15)), result.occurrences.map { it.date })
    }

    @Test
    fun `daily genera ogni giorno fino a oggi`() {
        val today = millis(2026, Calendar.SEPTEMBER, 3)
        val t = template(1, "DAILY", millis(2026, Calendar.SEPTEMBER, 1), startDate = millis(2026, Calendar.SEPTEMBER, 1))
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        assertEquals(
            listOf(1, 2, 3).map { millis(2026, Calendar.SEPTEMBER, it) },
            result.occurrences.map { it.date }
        )
    }

    @Test
    fun `occorrenze copiano titolo importo tipo categoria e note`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = RecurringTransaction(
            id = 1, title = "Stipendio", amount = 1500.0, type = "INCOME", categoryId = 7,
            frequency = "MONTHLY", startDate = millis(2026, Calendar.SEPTEMBER, 1),
            nextDueDate = millis(2026, Calendar.SEPTEMBER, 1), note = "Netto"
        )
        val result = RecurringTransactionGenerator.generate(listOf(t), emptyMap(), today)
        val occ = result.occurrences.single()
        assertEquals("Stipendio", occ.title)
        assertEquals(1500.0, occ.amount, 0.001)
        assertEquals("INCOME", occ.type)
        assertEquals(7, occ.categoryId)
        assertEquals("Netto", occ.note)
        assertEquals(millis(2026, Calendar.SEPTEMBER, 1), occ.date)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.generation.RecurringTransactionGeneratorTest"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Write the implementation**

`data/generation/GeneratedOccurrence.kt`:
```kotlin
package it.ciano.expensetracker.data.generation

data class GeneratedOccurrence(
    val title: String,
    val amount: Double,
    val type: String,
    val categoryId: Int,
    val note: String,
    val date: Long,
    val tagIds: Set<Int>
)

data class GenerationResult(
    val occurrences: List<GeneratedOccurrence>,
    val updated: List<RecurringTransaction>
)
```

`data/generation/RecurringTransactionGenerator.kt`:
```kotlin
package it.ciano.expensetracker.data.generation

import it.ciano.expensetracker.data.model.RecurringTransaction

object RecurringTransactionGenerator {

    /**
     * Per ogni template attivo con nextDueDate <= today genera le occorrenze mancanti
     * (retrodatate alla scadenza) e restituisce i template aggiornati.
     */
    fun generate(
        templates: List<RecurringTransaction>,
        tagIdsByRecurring: Map<Int, Set<Int>>,
        today: Long
    ): GenerationResult {
        val occurrences = mutableListOf<GeneratedOccurrence>()
        val updated = mutableListOf<RecurringTransaction>()

        for (t in templates) {
            if (!t.isActive || t.nextDueDate > today) continue

            val endBoundary = t.endDate ?: Long.MAX_VALUE
            val tagIds = tagIdsByRecurring[t.id] ?: emptySet()

            var cursor = t.nextDueDate
            var lastGenerated = t.lastGeneratedDate
            var generated = false

            while (cursor <= today && cursor <= endBoundary) {
                occurrences.add(
                    GeneratedOccurrence(
                        title = t.title,
                        amount = t.amount,
                        type = t.type,
                        categoryId = t.categoryId,
                        note = t.note,
                        date = cursor,
                        tagIds = tagIds
                    )
                )
                lastGenerated = cursor
                generated = true
                cursor = RecurringDateCalculator.advance(cursor, t.frequency)
            }

            if (!generated) continue

            val stillActive = !(t.endDate != null && cursor > t.endDate)
            updated.add(
                t.copy(
                    nextDueDate = cursor,
                    lastGeneratedDate = lastGenerated,
                    isActive = stillActive
                )
            )
        }
        return GenerationResult(occurrences, updated)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.generation.RecurringTransactionGeneratorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/generation/GeneratedOccurrence.kt app/src/main/java/it/ciano/expensetracker/data/generation/RecurringTransactionGenerator.kt app/src/test/java/it/ciano/expensetracker/data/generation/RecurringTransactionGeneratorTest.kt
git commit -m "feat: generatore transazioni ricorrenti con test"
```
(Use the commit workaround.)

---

### Task 5: Repository with atomic persistence

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/repository/RecurringTransactionRepository.kt`

**Interfaces:**
- Consumes: DAOs (Task 2), `AppDatabase.withTransaction`, `GeneratedOccurrence` (Task 4), `Tag`, `TransactionTag` models.
- Produces:
  - `fun getAllRecurringWithTags(): Flow<List<RecurringTransactionWithTags>>`
  - `suspend fun getDueTemplates(today: Long): List<RecurringTransaction>`
  - `suspend fun getTagIds(recurringId: Int): Set<Int>`
  - `suspend fun insertRecurring(r: RecurringTransaction, tagIds: Set<Int>): Long`
  - `suspend fun updateRecurring(r: RecurringTransaction, tagIds: Set<Int>)`
  - `suspend fun deleteRecurring(r: RecurringTransaction)`
  - `suspend fun applyGeneration(occurrences: List<GeneratedOccurrence>, updated: List<RecurringTransaction>)` — atomic via `withTransaction`.

- [ ] **Step 1: Write the implementation**

`data/repository/RecurringTransactionRepository.kt`:
```kotlin
package it.ciano.expensetracker.data.repository

import androidx.room.withTransaction
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.dao.RecurringTransactionDao
import it.ciano.expensetracker.data.dao.RecurringTransactionTagDao
import it.ciano.expensetracker.data.dao.TagDao
import it.ciano.expensetracker.data.dao.TransactionDao
import it.ciano.expensetracker.data.dao.TransactionTagDao
import it.ciano.expensetracker.data.generation.GeneratedOccurrence
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.data.model.RecurringTransactionTag
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RecurringTransactionRepository(
    private val database: AppDatabase,
    private val recurringDao: RecurringTransactionDao,
    private val recurringTagDao: RecurringTransactionTagDao,
    private val transactionDao: TransactionDao,
    private val transactionTagDao: TransactionTagDao,
    private val tagDao: TagDao
) {
    fun getAllRecurringWithTags(): Flow<List<RecurringTransactionWithTags>> =
        combine(
            recurringDao.getAllRecurring(),
            recurringTagDao.getAllRecurringTags(),
            tagDao.getAllTags()
        ) { recurs, links, tags ->
            recurs.map { r ->
                val itemTags = links
                    .filter { it.recurringTransactionId == r.id }
                    .mapNotNull { l -> tags.find { it.tagId == l.tagId } }
                RecurringTransactionWithTags(r, itemTags)
            }
        }

    suspend fun getDueTemplates(today: Long): List<RecurringTransaction> =
        recurringDao.getActiveDue(today)

    suspend fun getTagIds(recurringId: Int): Set<Int> =
        recurringTagDao.getTagIdsForRecurring(recurringId).toSet()

    suspend fun insertRecurring(recurring: RecurringTransaction, tagIds: Set<Int>): Long {
        val id = recurringDao.insertRecurring(recurring)
        saveTags(id.toInt(), tagIds)
        return id
    }

    suspend fun updateRecurring(recurring: RecurringTransaction, tagIds: Set<Int>) {
        recurringDao.updateRecurring(recurring)
        saveTags(recurring.id, tagIds)
    }

    suspend fun deleteRecurring(recurring: RecurringTransaction) {
        recurringTagDao.deleteTagsForRecurring(recurring.id)
        recurringDao.deleteRecurring(recurring)
    }

    private suspend fun saveTags(recurringId: Int, tagIds: Set<Int>) {
        recurringTagDao.deleteTagsForRecurring(recurringId)
        tagIds.forEach { tagId ->
            recurringTagDao.insertRecurringTag(RecurringTransactionTag(recurringId, tagId))
        }
    }

    /**
     * Applica in modo atomico le transazioni generate e gli aggiornamenti dei template.
     * Un crash a metà non lascia il DB in uno stato di duplicazione.
     */
    suspend fun applyGeneration(
        occurrences: List<GeneratedOccurrence>,
        updated: List<RecurringTransaction>
    ) {
        database.withTransaction {
            occurrences.forEach { occ ->
                val id = transactionDao.insertTransaction(
                    Transaction(
                        title = occ.title,
                        amount = occ.amount,
                        type = occ.type,
                        categoryId = occ.categoryId,
                        date = occ.date,
                        note = occ.note
                    )
                ).toInt()
                occ.tagIds.forEach { tagId ->
                    transactionTagDao.insertTransactionTag(TransactionTag(id, tagId))
                }
            }
            updated.forEach { recurringDao.updateRecurring(it) }
        }
    }
}
```

- [ ] **Step 2: Register the repository in `ViewModelFactory.kt`**

Edit `data/../ui/viewmodel/ViewModelFactory.kt` (AndroidViewModel flow is added in Task 6; here just wire the repository):

```kotlin
private val recurringTransactionRepository = RecurringTransactionRepository(
    database = database,
    recurringDao = database.recurringTransactionDao(),
    recurringTagDao = database.recurringTransactionTagDao(),
    transactionDao = database.transactionDao(),
    transactionTagDao = database.transactionTagDao(),
    tagDao = database.tagDao()
)
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/repository/RecurringTransactionRepository.kt
git commit -m "feat: repository transazioni ricorrenti con persistenza atomica"
```
(Use the commit workaround.)

---

### Task 6: Reminder rules (pure) + scheduler + receiver + manifest + channel

**Files:**
- Test: `app/src/test/java/it/ciano/expensetracker/data/reminder/RecurringReminderRulesTest.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderRules.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderScheduler.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/it/ciano/expensetracker/ExpenseTrackerApp.kt`

**Interfaces:**
- Consumes: `RecurringTransaction` (Task 1).
- Produces:
  - `RecurringReminderRules.shouldSchedule(r: RecurringTransaction, today: Long): Boolean`
  - `RecurringReminderRules.nextFireTime(nextDueDate: Long): Long` — day of due date at 09:00 local.
  - `RecurringReminderScheduler(context)` with `scheduleReminder(recurringId, nextDueDate, title, amount)`, `cancelReminder(recurringId)`, `rescheduleAll(list)`.
  - `RecurringReminderReceiver` handling actions `it.ciano.expensetracker.REMIND_RECURRING` (post notification) and `android.intent.action.BOOT_COMPLETED` (reschedule all).
  - `ACTION_REMIND: String` constant.

- [ ] **Step 1: Write the failing test for the pure rules**

`app/src/test/java/it/ciano/expensetracker/data/reminder/RecurringReminderRulesTest.kt`:
```kotlin
package it.ciano.expensetracker.data.reminder

import it.ciano.expensetracker.data.model.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringReminderRulesTest {

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0, 0) }.timeInMillis

    private fun template(
        nextDueDate: Long, isActive: Boolean = true, endDate: Long? = null
    ) = RecurringTransaction(
        title = "Affitto", amount = 800.0, type = "EXPENSE", categoryId = 1,
        frequency = "MONTHLY", startDate = nextDueDate, endDate = endDate,
        nextDueDate = nextDueDate, isActive = isActive
    )

    @Test
    fun `template attivo e futuro viene schedulato`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertTrue(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 15)), today))
    }

    @Test
    fun `template inattivo non viene schedulato`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 15), isActive = false), today))
    }

    @Test
    fun `scadenza odierna o passata non viene schedulata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 10)), today))
        assertFalse(RecurringReminderRules.shouldSchedule(template(millis(2026, Calendar.SEPTEMBER, 5)), today))
    }

    @Test
    fun `scadenza oltre la endDate non viene schedulata`() {
        val today = millis(2026, Calendar.SEPTEMBER, 10)
        val t = template(millis(2026, Calendar.OCTOBER, 15), endDate = millis(2026, Calendar.SEPTEMBER, 30))
        assertFalse(RecurringReminderRules.shouldSchedule(t, today))
    }

    @Test
    fun `nextFireTime e alle 9 del giorno della scadenza`() {
        assertEquals(
            millis(2026, Calendar.SEPTEMBER, 15, 9),
            RecurringReminderRules.nextFireTime(millis(2026, Calendar.SEPTEMBER, 15))
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.reminder.RecurringReminderRulesTest"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Create the pure rules**

`data/reminder/RecurringReminderRules.kt`:
```kotlin
package it.ciano.expensetracker.data.reminder

import it.ciano.expensetracker.data.model.RecurringTransaction
import java.util.Calendar

object RecurringReminderRules {

    /** Un promemoria viene pianificato solo per template attivi con prossima scadenza futura e dentro la finestra endDate. */
    fun shouldSchedule(r: RecurringTransaction, today: Long): Boolean {
        if (!r.isActive) return false
        if (r.nextDueDate <= today) return false
        if (r.endDate != null && r.nextDueDate > r.endDate) return false
        return true
    }

    /** Il promemoria scatta alle 09:00 del giorno della scadenza. */
    fun nextFireTime(nextDueDate: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nextDueDate
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "it.ciano.expensetracker.data.reminder.RecurringReminderRulesTest"`
Expected: PASS.

- [ ] **Step 5: Create the scheduler**

`data/reminder/RecurringReminderScheduler.kt`:
```kotlin
package it.ciano.expensetracker.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import it.ciano.expensetracker.data.model.RecurringTransaction

class RecurringReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(recurringId: Int, nextDueDate: Long, title: String, amount: Double) {
        val fireAt = RecurringReminderRules.nextFireTime(nextDueDate)
        if (fireAt <= System.currentTimeMillis()) return

        val pending = reminderPendingIntent(recurringId, title, amount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
    }

    fun cancelReminder(recurringId: Int) {
        alarmManager.cancel(reminderPendingIntent(recurringId, title = "", amount = 0.0))
    }

    fun rescheduleAll(recurrings: List<RecurringTransaction>) {
        recurrings.forEach { cancelReminder(it.id) }
        val today = System.currentTimeMillis()
        recurrings
            .filter { RecurringReminderRules.shouldSchedule(it, today) }
            .forEach { scheduleReminder(it.id, it.nextDueDate, it.title, it.amount) }
    }

    private fun reminderPendingIntent(recurringId: Int, title: String, amount: Double): PendingIntent {
        val intent = Intent(context, RecurringReminderReceiver::class.java).apply {
            action = RecurringReminderReceiver.ACTION_REMIND
            putExtra(RecurringReminderReceiver.EXTRA_RECURRING_ID, recurringId)
            putExtra(RecurringReminderReceiver.EXTRA_TITLE, title)
            putExtra(RecurringReminderReceiver.EXTRA_AMOUNT, amount)
        }
        return PendingIntent.getBroadcast(
            context,
            recurringId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

- [ ] **Step 6: Create the receiver**

`data/reminder/RecurringReminderReceiver.kt`:
```kotlin
package it.ciano.expensetracker.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import it.ciano.expensetracker.MainActivity
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecurringReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMIND = "it.ciano.expensetracker.REMIND_RECURRING"
        const val EXTRA_RECURRING_ID = "recurring_id"
        const val EXTRA_TITLE = "recurring_title"
        const val EXTRA_AMOUNT = "recurring_amount"
        const val CHANNEL_ID = "RECURRING_REMINDERS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> postReminder(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val scheduler = RecurringReminderScheduler(context)
                    val all = db.recurringTransactionDao().getAllRecurring().take(1) as? List<it.ciano.expensetracker.data.model.RecurringTransaction> ?: emptyList()
                    scheduler.rescheduleAll(all)
                }
            }
        }
    }

    private fun postReminder(context: Context, intent: Intent) {
        val recurringId = intent.getIntExtra(EXTRA_RECURRING_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

        val prefs = UserPreferences(context)
        val currency = prefs.getCurrency()
        val separator = prefs.getDecimalSeparator()
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale.getDefault())
        symbols.decimalSeparator = separator.firstOrNull() ?: ','
        val formatted = java.text.DecimalFormat("0.00", symbols).format(amount)

        val tapIntent = PendingIntent.getActivity(
            context,
            recurringId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.str_notifica_in_scadenza, "$formatted $currency"))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(recurringId, notification)
    }
}
```
Note: the boot-reschedule implementation above reads the first emission of the Flow; a cleaner equivalent is used in the ViewModel (Task 7). If `take(1)` doesn't compile to a list in your setup, replace that block with:

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    val db = AppDatabase.getDatabase(context)
    val all = it.ciano.expensetracker.data.repository.RecurringTransactionRepository(
        database = db,
        recurringDao = db.recurringTransactionDao(),
        recurringTagDao = db.recurringTransactionTagDao(),
        transactionDao = db.transactionDao(),
        transactionTagDao = db.transactionTagDao(),
        tagDao = db.tagDao()
    ).getAllRecurringWithTags().first().map { it.recurring }
    RecurringReminderScheduler(context).rescheduleAll(all)
}
```
(Add `import kotlinx.coroutines.flow.first`.)

- [ ] **Step 7: Add permissions + receiver to `AndroidManifest.xml`**

Insert after the existing `<uses-permission android:name="android.permission.CAMERA" />`:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```
Add inside `<application>` (after the ingest receiver):
```xml
<receiver
    android:name=".data.reminder.RecurringReminderReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="it.ciano.expensetracker.REMIND_RECURRING" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

- [ ] **Step 8: Create the notification channel in `ExpenseTrackerApp.kt`**

```kotlin
package it.ciano.expensetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import it.ciano.expensetracker.data.preferences.UserPreferences
import it.ciano.expensetracker.data.reminder.RecurringReminderReceiver

class ExpenseTrackerApp : Application() {

    override fun attachBaseContext(base: Context) {
        val code = UserPreferences(base).getAppLanguage()
        super.attachBaseContext(LocaleHelper.wrap(base, code))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RecurringReminderReceiver.CHANNEL_ID,
                getString(R.string.str_promemoria_scadenza),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 9: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderRules.kt app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderScheduler.kt app/src/main/java/it/ciano/expensetracker/data/reminder/RecurringReminderReceiver.kt app/src/test/java/it/ciano/expensetracker/data/reminder/RecurringReminderRulesTest.kt app/src/main/AndroidManifest.xml app/src/main/java/it/ciano/expensetracker/ExpenseTrackerApp.kt
git commit -m "feat: promemoria scadenza ricorrenti con AlarmManager"
```
(Use the commit workaround.)

---

### Task 7: `RecurringTransactionViewModel` + factory wiring

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/ui/viewmodel/RecurringTransactionViewModel.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/viewmodel/ViewModelFactory.kt`

**Interfaces:**
- Consumes: `RecurringTransactionRepository` (Task 5), `RecurringReminderScheduler` (Task 6), `RecurringTransactionGenerator` (Task 4), `RecurringTransaction`, `RecurringTransactionWithTags`.
- Produces:
  - `val recurringWithTags: StateFlow<List<RecurringTransactionWithTags>>`
  - Form state: `title`, `amount`, `type`, `categoryId`, `frequency`, `startDate`, `endDateEnabled`, `endDate`, `note`, `selectedTags` — all `StateFlow`; plus `editId: StateFlow<Int?>`.
  - Updaters: `updateTitle`, `updateAmount`, `updateType`, `updateCategory`, `updateFrequency`, `updateStartDate`, `setEndDateEnabled`, `updateEndDate`, `updateNote`, `toggleTag`.
  - `startNew()`, `startEdit(item)`, `save(onSaved: () -> Unit)`, `setActive(item, active)`, `delete(item)`, `runPendingGeneration()`, `rescheduleReminders()`.

- [ ] **Step 1: Write the implementation**

`ui/viewmodel/RecurringTransactionViewModel.kt`:
```kotlin
package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.ciano.expensetracker.data.generation.RecurringTransactionGenerator
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.data.reminder.RecurringReminderScheduler
import it.ciano.expensetracker.data.repository.RecurringTransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class RecurringTransactionViewModel(
    application: Application,
    private val repository: RecurringTransactionRepository
) : AndroidViewModel(application) {

    private val reminderScheduler = RecurringReminderScheduler(application)

    val recurringWithTags: StateFlow<List<RecurringTransactionWithTags>> =
        repository.getAllRecurringWithTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- STATO FORM ---
    private val _editId = MutableStateFlow<Int?>(null)
    val editId: StateFlow<Int?> = _editId.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _type = MutableStateFlow("EXPENSE")
    val type: StateFlow<String> = _type.asStateFlow()

    private val _categoryId = MutableStateFlow(0)
    val categoryId: StateFlow<Int> = _categoryId.asStateFlow()

    private val _frequency = MutableStateFlow("MONTHLY")
    val frequency: StateFlow<String> = _frequency.asStateFlow()

    private val _startDate = MutableStateFlow(0L)
    val startDate: StateFlow<Long> = _startDate.asStateFlow()

    private val _endDateEnabled = MutableStateFlow(false)
    val endDateEnabled: StateFlow<Boolean> = _endDateEnabled.asStateFlow()

    private val _endDate = MutableStateFlow(0L)
    val endDate: StateFlow<Long> = _endDate.asStateFlow()

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _selectedTags = MutableStateFlow(setOf<Int>())
    val selectedTags: StateFlow<Set<Int>> = _selectedTags.asStateFlow()

    // --- UPDATER ---
    fun updateTitle(v: String) { _title.value = v }
    fun updateAmount(v: String) { _amount.value = v }
    fun updateType(v: String) { _type.value = v }
    fun updateCategory(id: Int) { _categoryId.value = id }
    fun updateFrequency(v: String) { _frequency.value = v }
    fun updateStartDate(v: Long) { _startDate.value = v }
    fun setEndDateEnabled(enabled: Boolean) {
        _endDateEnabled.value = enabled
        if (!enabled) _endDate.value = 0L
    }
    fun updateEndDate(v: Long) { _endDate.value = v }
    fun updateNote(v: String) { _note.value = v }
    fun toggleTag(tagId: Int) {
        val current = _selectedTags.value
        _selectedTags.value = if (tagId in current) current - tagId else current + tagId
    }

    fun startNew() {
        _editId.value = null
        resetForm()
    }

    fun startEdit(item: RecurringTransactionWithTags) {
        val r = item.recurring
        _editId.value = r.id
        _title.value = r.title
        _amount.value = r.amount.toString()
        _type.value = r.type
        _categoryId.value = r.categoryId
        _frequency.value = r.frequency
        _startDate.value = r.startDate
        _endDateEnabled.value = r.endDate != null
        _endDate.value = r.endDate ?: 0L
        _note.value = r.note
        _selectedTags.value = item.tags.map { it.tagId }.toSet()
    }

    fun save(onSaved: () -> Unit) {
        val normalizedAmount = _amount.value.replaceFirst(',', '.')
        val amountValue = normalizedAmount.toDoubleOrNull() ?: 0.0
        if (_title.value.isBlank() || amountValue <= 0.0 || _categoryId.value == 0) return
        val start = _startDate.value
        if (start == 0L) return
        if (_endDateEnabled.value && _endDate.value != 0L && _endDate.value < start) return

        val existingId = _editId.value
        val recurring = RecurringTransaction(
            id = existingId ?: 0,
            title = _title.value,
            amount = amountValue,
            type = _type.value,
            categoryId = _categoryId.value,
            frequency = _frequency.value,
            startDate = start,
            endDate = if (_endDateEnabled.value) _endDate.value else null,
            nextDueDate = if (existingId != null) selectNextDueDate(existingId) else start,
            note = _note.value,
            isActive = true,
            lastGeneratedDate = null
        )
        val tagIds = _selectedTags.value

        viewModelScope.launch(Dispatchers.IO) {
            if (existingId != null) {
                repository.updateRecurring(recurring, tagIds)
            } else {
                repository.insertRecurring(recurring, tagIds)
                rescheduleReminders()
            }
            withContextList()
        }
        onSaved()
    }

    /** Recupera il prossimo nextDueDate (se esiste già un template) o usa startDate. */
    private fun selectNextDueDate(existingId: Int): Long {
        val snapshot = recurringWithTags.value.firstOrNull { it.recurring.id == existingId }?.recurring
        return snapshot?.nextDueDate ?: _startDate.value
    }

    private fun withContextList() { /* placeholder ritorna il flusso aggiornato via Room */ }

    fun setActive(item: RecurringTransactionWithTags, active: Boolean) {
        val updated = item.recurring.copy(isActive = active)
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateRecurring(updated, item.tags.map { it.tagId }.toSet())
            if (active) rescheduleReminders() else reminderScheduler.cancelReminder(updated.id)
        }
    }

    fun delete(item: RecurringTransactionWithTags) {
        val id = item.recurring.id
        viewModelScope.launch(Dispatchers.IO) {
            reminderScheduler.cancelReminder(id)
            repository.deleteRecurring(item.recurring)
        }
    }

    fun runPendingGeneration() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val due = repository.getDueTemplates(today)
            if (due.isEmpty()) return@launch
            val tagMap = due.associate { it.id to repository.getTagIds(it.id) }
            val result = RecurringTransactionGenerator.generate(due, tagMap, today)
            repository.applyGeneration(result.occurrences, result.updated)
            rescheduleReminders()
        }
    }

    fun rescheduleReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = recurringWithTags.value.map { it.recurring }
            reminderScheduler.rescheduleAll(all)
        }
    }

    private fun resetForm() {
        _title.value = ""
        _amount.value = ""
        _type.value = "EXPENSE"
        _categoryId.value = 0
        _frequency.value = "MONTHLY"
        _startDate.value = 0L
        _endDateEnabled.value = false
        _endDate.value = 0L
        _note.value = ""
        _selectedTags.value = emptySet()
    }
}
```
Note: remove the two helper stubs (`withContextList`, `selectNextDueDate` misuse) by inlining cleanly: in `save()`, for updates use the snapshot `nextDueDate` via `recurringWithTags.value` directly and drop the two helpers entirely. Final `save()` body:

```kotlin
    fun save(onSaved: () -> Unit) {
        val normalizedAmount = _amount.value.replaceFirst(',', '.')
        val amountValue = normalizedAmount.toDoubleOrNull() ?: 0.0
        if (_title.value.isBlank() || amountValue <= 0.0 || _categoryId.value == 0) return
        val start = _startDate.value
        if (start == 0L) return
        if (_endDateEnabled.value && _endDate.value != 0L && _endDate.value < start) return

        val existingId = _editId.value
        val snapshotNextDue = existingId?.let { id ->
            recurringWithTags.value.firstOrNull { it.recurring.id == id }?.recurring?.nextDueDate
        }

        val recurring = RecurringTransaction(
            id = existingId ?: 0,
            title = _title.value,
            amount = amountValue,
            type = _type.value,
            categoryId = _categoryId.value,
            frequency = _frequency.value,
            startDate = start,
            endDate = if (_endDateEnabled.value) _endDate.value else null,
            nextDueDate = snapshotNextDue ?: start,
            note = _note.value,
            isActive = true,
            lastGeneratedDate = existingId?.let { id ->
                recurringWithTags.value.firstOrNull { it.recurring.id == id }?.recurring?.lastGeneratedDate
            }
        )

        viewModelScope.launch(Dispatchers.IO) {
            if (existingId != null) {
                repository.updateRecurring(recurring, _selectedTags.value)
            } else {
                repository.insertRecurring(recurring, _selectedTags.value)
            }
            rescheduleReminders()
        }
        onSaved()
    }
```

- [ ] **Step 2: Register in `ViewModelFactory.kt`**

Add to the `when` block:
```kotlin
modelClass.isAssignableFrom(RecurringTransactionViewModel::class.java) ->
    RecurringTransactionViewModel(application, recurringTransactionRepository) as T
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/viewmodel/RecurringTransactionViewModel.kt app/src/main/java/it/ciano/expensetracker/ui/viewmodel/ViewModelFactory.kt
git commit -m "feat: viewmodel delle transazioni ricorrenti"
```
(Use the commit workaround.)

---

### Task 8: i18n strings + navigation + drawer item

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/AppNavigation.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt`

**Interfaces:**
- Produces: `Routes.RECURRING_TRANSACTIONS = "recurring_transactions"`. Drawer item in HomeScreen navigates to it.

- [ ] **Step 1: Add Italian strings to `values/strings.xml`**

Append before `</resources>`:
```xml
    <!-- Transazioni ricorrenti -->
    <string name="str_transazioni_ricorrenti">Transazioni Ricorrenti</string>
    <string name="str_nessuna_ricorrente">Nessuna transazione ricorrente</string>
    <string name="str_nuova_ricorrente">Nuova Transazione Ricorrente</string>
    <string name="str_modifica_ricorrente">Modifica Transazione Ricorrente</string>
    <string name="str_frequenza">Frequenza</string>
    <string name="str_giornaliera">Giornaliera</string>
    <string name="str_settimanale">Settimanale</string>
    <string name="str_bisettimanale">Bi-settimanale</string>
    <string name="str_mensile">Mensile</string>
    <string name="str_annuale">Annuale</string>
    <string name="str_data_inizio">Data Inizio</string>
    <string name="str_data_fine">Data Fine (opzionale)</string>
    <string name="str_attiva">Attiva</string>
    <string name="str_in_pausa">In Pausa</string>
    <string name="str_prossima_generazione">Prossima generazione: %1$s</string>
    <string name="str_conferma_elimina_ricorrente">Eliminare questa transazione ricorrente?</string>
    <string name="str_ricorrente_salvata">Transazione ricorrente salvata</string>
    <string name="str_frequenza_obbligatoria">Selezionare una frequenza</string>
    <string name="str_scadenze_mese">Scadenze</string>
    <string name="str_nessuna_scadenza">Nessuna scadenza questo mese</string>
    <string name="str_ricorrenze_il_giorno">Ricorrenze del %1$s</string>
    <string name="str_promemoria_scadenza">Promemoria scadenza</string>
    <string name="str_in_scadenza_oggi">In scadenza oggi</string>
    <string name="str_notifica_in_scadenza">In scadenza oggi: %1$s</string>
    <string name="str_attiva_notifiche">Notifiche scadenza</string>
    <string name="str_notifiche_concesse">Le notifiche sono state concesse</string>
    <string name="str_notifiche_negate">Notifiche disattivate. Concedile dalle impostazioni di sistema</string>
```

- [ ] **Step 2: Add English strings to `values-en/strings.xml`**

Append before `</resources>`:
```xml
    <!-- Recurring transactions -->
    <string name="str_transazioni_ricorrenti">Recurring Transactions</string>
    <string name="str_nessuna_ricorrente">No recurring transactions</string>
    <string name="str_nuova_ricorrente">New Recurring Transaction</string>
    <string name="str_modifica_ricorrente">Edit Recurring Transaction</string>
    <string name="str_frequenza">Frequency</string>
    <string name="str_giornaliera">Daily</string>
    <string name="str_settimanale">Weekly</string>
    <string name="str_bisettimanale">Bi-weekly</string>
    <string name="str_mensile">Monthly</string>
    <string name="str_annuale">Annual</string>
    <string name="str_data_inizio">Start Date</string>
    <string name="str_data_fine">End Date (optional)</string>
    <string name="str_attiva">Active</string>
    <string name="str_in_pausa">Paused</string>
    <string name="str_prossima_generazione">Next generation: %1$s</string>
    <string name="str_conferma_elimina_ricorrente">Delete this recurring transaction?</string>
    <string name="str_ricorrente_salvata">Recurring transaction saved</string>
    <string name="str_frequenza_obbligatoria">Select a frequency</string>
    <string name="str_scadenze_mese">Due dates</string>
    <string name="str_nessuna_scadenza">No due dates this month</string>
    <string name="str_ricorrenze_il_giorno">Occurrences on %1$s</string>
    <string name="str_promemoria_scadenza">Due-date reminder</string>
    <string name="str_in_scadenza_oggi">Due today</string>
    <string name="str_notifica_in_scadenza">Due today: %1$s</string>
    <string name="str_attiva_notifiche">Due-date notifications</string>
    <string name="str_notifiche_concesse">Notifications granted</string>
    <string name="str_notifiche_negate">Notifications disabled. Grant them from system settings</string>
```

- [ ] **Step 3: Add the route to `AppNavigation.kt`**

In `Routes`:
```kotlin
const val RECURRING_TRANSACTIONS = "recurring_transactions"
```
In the `NavHost`:
```kotlin
composable(Routes.RECURRING_TRANSACTIONS) {
    RecurringTransactionsScreen(navController)
}
```

- [ ] **Step 4: Add the drawer item to `HomeScreen.kt`**

Import `androidx.compose.material.icons.filled.Repeat`, `androidx.compose.material.icons.outlined.Repeat`, `androidx.compose.material.icons.rounded.Repeat`, `androidx.compose.material.icons.sharp.Repeat`, `androidx.compose.material.icons.twotone.Repeat`.

Insert a `NavigationDrawerItem` between the Home and Cronologia items:
```kotlin
NavigationDrawerItem(
    label = { Text(stringResource(R.string.str_transazioni_ricorrenti)) },
    selected = false,
    onClick = {
        scope.launch { drawerState.close() }
        navController.navigate(Routes.RECURRING_TRANSACTIONS)
    },
    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Repeat, Icons.Outlined.Repeat, Icons.Rounded.Repeat, Icons.Sharp.Repeat, Icons.TwoTone.Repeat), contentDescription = null) },
    modifier = Modifier.padding(horizontal = 12.dp)
)
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (will fail until `RecurringTransactionsScreen` exists from Task 9 — acceptable if Task 9 is done before this check; otherwise sequence Task 9 first).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/java/it/ciano/expensetracker/ui/screens/AppNavigation.kt app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt
git commit -m "feat: route, stringhe e voce drawer per transazioni ricorrenti"
```
(Use the commit workaround.)

---

### Task 9: `RecurringCalendar` composable

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/ui/components/RecurringCalendar.kt`

**Interfaces:**
- Consumes: `RecurringDateCalculator.occurrencesInMonth` (Task 3), `RecurringTransaction`, `GeneratedOccurrence`-independent (works on `List<RecurringTransaction>`), `MainViewModel.formatCurrency`, `Calendar`.
- Produces: `@Composable fun RecurringCalendar(transactions: List<RecurringTransaction>, mainViewModel: MainViewModel)` — a tappable month grid.

- [ ] **Step 1: Write the implementation**

`ui/components/RecurringCalendar.kt`:
```kotlin
package it.ciano.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.generation.RecurringDateCalculator
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringCalendar(
    transactions: List<RecurringTransaction>,
    mainViewModel: MainViewModel
) {
    var displayedMonth by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedDay by remember { mutableStateOf<Calendar?>(null) }

    val activeTransactions = transactions.filter { it.isActive }

    val year = displayedMonth.get(Calendar.YEAR)
    val month = displayedMonth.get(Calendar.MONTH)

    val occurrencesByDay = remember(year, month, activeTransactions) {
        val map = mutableMapOf<Int, List<RecurringTransaction>>()
        activeTransactions.forEach { t ->
            RecurringDateCalculator.occurrencesInMonth(t, year, month).forEach { epoch ->
                val day = Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.DAY_OF_MONTH)
                map[day] = (map[day] ?: emptyList()) + t
            }
        }
        map
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shiftMonth(displayedMonth, -1) { displayedMonth = it } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                Text(
                    text = "${monthsName(month)} $year",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { shiftMonth(displayedMonth, 1) { displayedMonth = it } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            val firstDay = displayedMonth.clone() as Calendar
            firstDay.set(Calendar.DAY_OF_MONTH, 1)
            val startColumn = (firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
            val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 6).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (0 until 7).forEach { col ->
                            val dayNumber = week * 7 + col - startColumn + 1
                            if (dayNumber in 1..daysInMonth) {
                                val due: List<RecurringTransaction>? = occurrencesByDay[dayNumber]
                                DayCell(
                                    day = dayNumber,
                                    hasDue = !due.isNullOrEmpty(),
                                    onClick = {
                                        if (!due.isNullOrEmpty()) {
                                            selectedDay = Calendar.getInstance().apply {
                                                clear()
                                                set(year, month, dayNumber, 0, 0, 0)
                                            }
                                        }
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f)) { Spacer(modifier = Modifier.height(36.dp)) }
                            }
                        }
                    }
                }
            }

            if (occurrencesByDay.isEmpty()) {
                Text(
                    text = stringResource(R.string.str_nessuna_scadenza),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }

    val dayDialog = selectedDay
    if (dayDialog != null) {
        val dayKey = dayDialog.get(Calendar.DAY_OF_MONTH)
        val occurrences = occurrencesByDay[dayKey] ?: emptyList()
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = {
                Text(stringResource(R.string.str_ricorrenze_il_giorno,
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(dayDialog.time)))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    occurrences.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            Text(mainViewModel.formatCurrency(t.amount))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDay = null }) {
                    Text(stringResource(R.string.str_chiudi))
                }
            }
        )
    }
}

@Composable
private fun DayCell(day: Int, hasDue: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clickable(enabled = hasDue, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = day.toString(), fontSize = 13.sp)
        if (hasDue) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun shiftMonth(current: Calendar, delta: Int, onChanged: (Calendar) -> Unit) {
    val next = current.clone() as Calendar
    next.add(Calendar.MONTH, delta)
    next.set(Calendar.DAY_OF_MONTH, 1)
    onChanged(next)
}

@Composable
private fun monthsName(month: Int): String {
    val names = arrayOf(
        stringResource(R.string.str_mese_gennaio), stringResource(R.string.str_mese_febbraio),
        stringResource(R.string.str_mese_marzo), stringResource(R.string.str_mese_aprile),
        stringResource(R.string.str_mese_maggio), stringResource(R.string.str_mese_giugno),
        stringResource(R.string.str_mese_luglio), stringResource(R.string.str_mese_agosto),
        stringResource(R.string.str_mese_settembre), stringResource(R.string.str_mese_ottobre),
        stringResource(R.string.str_mese_novembre), stringResource(R.string.str_mese_dicembre)
    )
    return names[month]
}
```

- [ ] **Step 2: Add the month-name strings (both locales)**

In `values/strings.xml` append:
```xml
    <string name="str_mese_gennaio">Gennaio</string>
    <string name="str_mese_febbraio">Febbraio</string>
    <string name="str_mese_marzo">Marzo</string>
    <string name="str_mese_aprile">Aprile</string>
    <string name="str_mese_maggio">Maggio</string>
    <string name="str_mese_giugno">Giugno</string>
    <string name="str_mese_luglio">Luglio</string>
    <string name="str_mese_agosto">Agosto</string>
    <string name="str_mese_settembre">Settembre</string>
    <string name="str_mese_ottobre">Ottobre</string>
    <string name="str_mese_novembre">Novembre</string>
    <string name="str_mese_dicembre">Dicembre</string>
```
In `values-en/strings.xml` append the English equivalents (January … December).

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/components/RecurringCalendar.kt app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat: calendario scadenze ricorrenti"
```
(Use the commit workaround.)

---

### Task 10: `RecurringTransactionsScreen` (list, dialogs, calendar, notification banner)

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/ui/screens/RecurringTransactionsScreen.kt`

**Interfaces:**
- Consumes: `RecurringTransactionViewModel` (Task 7), `CategoryViewModel`, `TagViewModel`, `SettingsViewModel`, `MainViewModel`, `RecurringCalendar` (Task 9), `RecurringDateCalculator.FREQUENCIES`/`FREQUENCY_LABELS` (Task 3), `RecurringTransactionWithTags`.
- Produces: `@Composable fun RecurringTransactionsScreen(navController: NavHostController)`.

- [ ] **Step 1: Write the implementation**

`ui/screens/RecurringTransactionsScreen.kt`:
```kotlin
package it.ciano.expensetracker.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.generation.RecurringDateCalculator
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.ui.components.RecurringCalendar
import it.ciano.expensetracker.ui.viewmodel.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTransactionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application

    val vm: RecurringTransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))
    val settingsViewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val recurrings by vm.recurringWithTags.collectAsState()
    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val mainCategories by categoryViewModel.mainCategories.collectAsState(initial = emptyList())
    val allTags by tagViewModel.allTags.collectAsState(initial = emptyList())
    val separator = settingsViewModel.decimalSeparator.collectAsState().value
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringTransactionWithTags?>(null) }
    var deleteCandidate by remember { mutableStateOf<RecurringTransactionWithTags?>(null) }

    // --- PERMESSO NOTIFICHE ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var notificationsDenied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_transazioni_ricorrenti), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingItem = null
                vm.startNew()
                showAddDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            RecurringCalendar(
                transactions = recurrings.map { it.recurring },
                mainViewModel = mainViewModel
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Banner(
                    text = stringResource(R.string.str_notifiche_negate),
                    onAction = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            if (recurrings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.str_nessuna_ricorrente), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recurrings, key = { it.recurring.id }) { item ->
                        RecurringCard(
                            item = item,
                            mainViewModel = mainViewModel,
                            allCategories = allCategories,
                            dateFormat = dateFormat,
                            onToggleActive = { active -> vm.setActive(item, active) },
                            onDelete = { deleteCandidate = item },
                            onSingleClick = { editingItem = item; vm.startEdit(item); showAddDialog = true },
                            onEditClick = { editingItem = item; vm.startEdit(item); showAddDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(
            vm = vm,
            allCategories = allCategories,
            mainCategories = mainCategories,
            allTags = allTags,
            separator = separator,
            dateFormat = dateFormat,
            isEdit = editingItem != null,
            onDismiss = { showAddDialog = false },
            onSave = {
                showAddDialog = false
                editingItem = null
            }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.str_transazioni_ricorrenti), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.str_conferma_elimina_ricorrente)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(candidate)
                        deleteCandidate = null
                    }
                ) { Text(stringResource(R.string.str_si_elimina), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.str_annulla))
                }
            }
        )
    }
}

@Composable
private fun RecurringCard(
    item: RecurringTransactionWithTags,
    mainViewModel: MainViewModel,
    allCategories: List<Category>,
    dateFormat: java.text.DateFormat,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSingleClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val r = item.recurring
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFD32F2F))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.str_elimina), tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onSingleClick, onLongClick = onEditClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val category = allCategories.find { it.id == r.categoryId }
                    Text(
                        text = category?.name ?: stringResource(R.string.str_senza_categoria),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = stringResource(
                            RecurringDateCalculator.FREQUENCY_LABELS[r.frequency] ?: R.string.str_frequenza_obbligatoria
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = stringResource(
                            R.string.str_prossima_generazione,
                            dateFormat.format(java.util.Date(r.nextDueDate))
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (r.type == "INCOME")
                            "+" + mainViewModel.formatCurrency(r.amount).removePrefix("+")
                            else "-" + mainViewModel.formatCurrency(r.amount).removePrefix("-"),
                        color = if (r.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = r.isActive,
                        onCheckedChange = onToggleActive
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onAction) {
                Text(stringResource(R.string.str_attiva_notifiche))
            }
        }
    }
}

@Composable
private fun AddRecurringDialog(
    vm: RecurringTransactionViewModel,
    allCategories: List<Category>,
    mainCategories: List<Category>,
    allTags: List<it.ciano.expensetracker.data.model.Tag>,
    separator: String,
    dateFormat: java.text.DateFormat,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val title by vm.title.collectAsState()
    val amount by vm.amount.collectAsState()
    val type by vm.type.collectAsState()
    val categoryId by vm.categoryId.collectAsState()
    val frequency by vm.frequency.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val endDateEnabled by vm.endDateEnabled.collectAsState()
    val endDate by vm.endDate.collectAsState()
    val note by vm.note.collectAsState()
    val selectedTags by vm.selectedTags.collectAsState()

    val categoryMap = remember(allCategories) { allCategories.associateBy { it.id } }
    val endDateToggle = remember { mutableStateOf(endDateEnabled) }

    val startDateDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                vm.updateStartDate(Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }
    val endDateDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                vm.updateEndDate(Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (isEdit) R.string.str_modifica_ricorrente else R.string.str_nuova_ricorrente),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = vm::updateTitle,
                    label = { Text(stringResource(R.string.str_titolo)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = vm::updateAmount,
                    label = { Text(stringResource(R.string.str_importo)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { vm.updateType("EXPENSE") },
                        label = { Text(stringResource(R.string.str_uscita)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { vm.updateType("INCOME") },
                        label = { Text(stringResource(R.string.str_entrata)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                var categoryExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = categoryMap[categoryId]?.name ?: stringResource(R.string.str_scegli_categoria),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_categoria_principale)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        mainCategories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = { vm.updateCategory(c.id); categoryExpanded = false }
                            )
                        }
                    }
                }

                var frequencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = it }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = stringResource(RecurringDateCalculator.FREQUENCY_LABELS[frequency] ?: R.string.str_frequenza_obbligatoria),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_frequenza)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                        RecurringDateCalculator.FREQUENCIES.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(stringResource(RecurringDateCalculator.FREQUENCY_LABELS[f] ?: R.string.str_frequenza_obbligatoria)) },
                                onClick = { vm.updateFrequency(f); frequencyExpanded = false }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (startDate != 0L) dateFormat.format(java.util.Date(startDate)) else "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_data_inizio)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { startDateDialog.show() })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.str_data_fine), fontSize = 14.sp)
                    Checkbox(
                        checked = endDateEnabled,
                        onCheckedChange = { vm.setEndDateEnabled(it) }
                    )
                }
                if (endDateEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (endDate != 0L) dateFormat.format(java.util.Date(endDate)) else "",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.str_data_fine)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { endDateDialog.show() })
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = vm::updateNote,
                    label = { Text(stringResource(R.string.str_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (allTags.isNotEmpty()) {
                    Text(stringResource(R.string.str_tag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTags.contains(tag.tagId),
                                onClick = { vm.toggleTag(tag.tagId) },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.save(onSave) },
                enabled = title.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && categoryId != 0 && startDate != 0L
            ) { Text(stringResource(R.string.str_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.str_annulla)) }
        }
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tasks' tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/screens/RecurringTransactionsScreen.kt
git commit -m "feat: schermata gestione transazioni ricorrenti"
```
(Use the commit workaround.)

---

### Task 11: Wire generation + rescheduling on app launch

**Files:**
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt`

**Interfaces:**
- Consumes: `RecurringTransactionViewModel.runPendingGeneration()` and `.rescheduleReminders()` (Task 7).

- [ ] **Step 1: Add the launch hook to `HomeScreen.kt`**

Get the VM at the top of the composable (after the existing ViewModels):
```kotlin
val recurringViewModel: RecurringTransactionViewModel = viewModel(factory = ViewModelFactory(app))
```
Add a `LaunchedEffect`:
```kotlin
LaunchedEffect(Unit) {
    recurringViewModel.runPendingGeneration()
    recurringViewModel.rescheduleReminders()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt
git commit -m "feat: generazione ricorrenti e schedulazione promemoria all avvio"
```
(Use the commit workaround.)

---

## Manual verification checklist (final task)

- [ ] Build a release/debug APK: `./gradlew :app:assembleDebug`
- [ ] Install on emulator/device: `adb install app/build/outputs/apk/debug/app-debug.apk`
- [ ] Open the app → DATABASE v4→v5 migration applies without error.
- [ ] Drawer shows "Transazioni Ricorrenti"; tap → screen opens with empty calendar + empty state.
- [ ] FAB → create a monthly EXPENSE "Affitto" €800, category Casa, start today, tag "Bollette".
- [ ] Card shows title, amount, frequency, next generation date; calendar marks today's date with a dot.
- [ ] Pause via switch → card shows "In Pausa", calendar dot disappears.
- [ ] Re-activate; long-press → edit; change amount → save → card updates.
- [ ] Swipe-to-delete → confirmation → removed; if notifications were granted, no alarm scheduled (verify via  `adb shell dumpsys alarm | grep expensetracker`).
- [ ] Set start date 2 days ago for a DAILY €5 "Caffè" → force-stop and reopen the app → two "Caffè" transactions appear in Home History dated yesterday and today.
- [ ] Android 13+: first visit to the screen requests POST_NOTIFICATIONS; grant → alarm scheduled for next due (dumpsys shows it); deny → banner shows with "Notifiche scadenza" button.
- [ ] Boot reschedule: `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` (on device, requires it to be simulated) → alarms present again.
- [ ] Calendar month navigation: next month shows due occurrences for active monthly template.

---

## Git commit workaround (mandatory in this workspace)

The workspace filesystem (fuseblk) rejects loose-object writes into `.git/objects`, so a normal `git add`/`git commit` fails with: `unable to write file .git/objects/XX/...: No such file or directory`. Workaround (already exercised, repo is consistent):

```bash
mkdir -p /tmp/gitobjtest/info
printf '%s\n' "/root/projects/ExpenseTracker/.git/objects" > /tmp/gitobjtest/info/alternates
export GIT_OBJECT_DIRECTORY=/tmp/gitobjtest
export GIT_AUTHOR_NAME="Tiziano Onor" GIT_AUTHOR_EMAIL="tiziano@android-termux" GIT_COMMITTER_NAME="Tiziano Onor" GIT_COMMITTER_EMAIL="tiziano@android-termux"
git add <files>
git commit -m "<message>"
# then materialize loose objects from /tmp into the real store:
python3 /tmp/materialize_objects.py
find .git/objects -name ".l2s.tmp_*" -delete
unset GIT_OBJECT_DIRECTORY
git fsck --no-dangling   # must be clean
```

If `/tmp/materialize_objects.py` does not exist, create it (it walks `/tmp/gitobjtest`, zlib-decompresses each `.l2s.tmp_obj_*.<n>` file, computes its SHA-1, and writes it to `objects/<sha[:2]>/<sha[2:]>`).