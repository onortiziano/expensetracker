# Native Intent Ingestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aggiungere a ExpenseTracker una porta d'ingresso Android via Intent (BroadcastReceiver + Activity trasparente con deep link) che inserisce spese in Room con parsing/validazione testata e feedback localizzato.

**Architecture:** Parser puro (`IntentExpenseParser`, zero dipendenze Android) che converte una `Map<String,String?>` in `IngestResult`; un `ExpenseIngestRepository` che risolve/crea la categoria per nome e inserisce via `TransactionRepository`; un glue `ExpenseIngest` condiviso da Receiver e Activity (shell sottili) che mostra Toast localizzati. La UI si aggiorna da sola via Room Flow → StateFlow → `collectAsState`.

**Tech Stack:** Kotlin 1.9.24, AGP 8.12.0, Room 2.6.1, JUnit4, minSdk 23, targetSdk 35, JVM target 17. Nessuna nuova dipendenza.

## Global Constraints

- Package reale app: `it.ciano.expensetracker` (NON `com.onortiziano...`).
- Stringhe mostrate all'utente SOLO via string resources in `res/values/strings.xml` (IT) + `res/values-en/strings.xml` (EN); risolte con il context dell'Application (avvolto da `LocaleHelper.wrap`).
- Date visualizzate: pattern esistente `android.text.format.DateFormat.getDateFormat(context)` (non toccare la UI lista).
- `Transaction.type` sempre `"EXPENSE"`, `note` sempre `""`.
- Categoria default `"Varie"`; se non esiste viene auto-creata (categoria principale, `parentCategoryId = null`).
- Nessun cambio schema DB (version 3 invariata, nessuna migration).
- Test: JUnit4 + `runBlocking`, nomi backtick in italiano (stile `ModifyTransactionHelpersTest`).
- Verifica sempre: `./gradlew testDebugUnitTest` e `./gradlew assembleDebug`.
- Deviations dallo spec: `IngestResult.Success` trasporta `ParsedExpense` (non `Transaction`, che richiede `categoryId` noto solo dopo lookup DB). `IngestResult.Error` trasporta un enum `IngestError` (i messaggi localizzati li decide la UI, non il parser puro).

---

### Task 0: Branch dedicato

**Files:** nessuno (solo git)

- [ ] **Step 1: Crea il branch dal main**

```bash
cd /root/projects/ExpenseTracker
git checkout main
git pull --ff-only origin main 2>/dev/null || true
git checkout -b feature/native-intent-ingestion
```

- [ ] **Step 2: Verifica il branch**

```bash
git branch --show-current
```
Expected: `feature/native-intent-ingestion`

---

### Task 1: `IngestResult` + `IntentExpenseParser` (parser puro) con test

**Files:**
- Test: `app/src/test/java/it/ciano/expensetracker/data/ingest/IntentExpenseParserTest.kt` (Create)
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/IngestResult.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/IntentExpenseParser.kt`

**Interfaces:**
- Produces:
  - `data class ParsedExpense(amount: Double, categoryName: String, title: String, date: Long)`
  - `enum class IngestError { MISSING_AMOUNT, INVALID_AMOUNT }`
  - `sealed class IngestResult { data class Success(val expense: ParsedExpense): IngestResult(); data class Error(val error: IngestError): IngestResult() }`
  - `object IntentExpenseParser { const val DEFAULT_CATEGORY = "Varie"; fun parse(params: Map<String, String?>): IngestResult }`

- [ ] **Step 1: Scrivi il test fallito**

```kotlin
package it.ciano.expensetracker.data.ingest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IntentExpenseParserTest {

    @Test
    fun `parse con dati completi produce una spesa valida`() {
        val result = IntentExpenseParser.parse(
            mapOf(
                "amount" to "12.50",
                "category" to "Pranzo",
                "note" to "Ristorante",
                "date" to "2026-08-13"
            )
        )

        assertTrue(result is IngestResult.Success)
        val expense = (result as IngestResult.Success).expense
        assertEquals(12.50, expense.amount, 0.001)
        assertEquals("Pranzo", expense.categoryName)
        assertEquals("Ristorante", expense.title)
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        }
        assertEquals(cal.timeInMillis, expense.date)
    }

    @Test
    fun `parse con solo amount usa default categoria data e titolo`() {
        val before = System.currentTimeMillis()
        val result = IntentExpenseParser.parse(mapOf("amount" to "12,50"))

        assertTrue(result is IngestResult.Success)
        val expense = (result as IngestResult.Success).expense
        assertEquals(12.50, expense.amount, 0.001)
        assertEquals("Varie", expense.categoryName)
        assertEquals("Varie", expense.title)
        assertTrue(expense.date in before - 1000..System.currentTimeMillis() + 1000)
    }

    @Test
    fun `parse con amount testo restituisce errore invalid`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "abc"))
        assertEquals(IngestResult.Error(IngestError.INVALID_AMOUNT), result)
    }

    @Test
    fun `parse con amount negativo restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "-5"))
        )
    }

    @Test
    fun `parse con amount zero restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "0"))
        )
    }

    @Test
    fun `parse senza amount restituisce errore missing`() {
        assertEquals(IngestResult.Error(IngestError.MISSING_AMOUNT), IntentExpenseParser.parse(emptyMap()))
    }

    @Test
    fun `parse con amount doppia virgola restituisce errore`() {
        assertEquals(
            IngestResult.Error(IngestError.INVALID_AMOUNT),
            IntentExpenseParser.parse(mapOf("amount" to "12,50,00"))
        )
    }

    @Test
    fun `parse con alias description valorizza il titolo`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "description" to "Caffe"))
        val expense = (result as IngestResult.Success).expense
        assertEquals("Caffe", expense.title)
    }

    @Test
    fun `parse con date epoch millis la converte`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "1000000"))
        val expense = (result as IngestResult.Success).expense
        assertEquals(1000000L, expense.date)
    }

    @Test
    fun `parse con date malformata usa la data corrente`() {
        val before = System.currentTimeMillis()
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "ieri"))
        val expense = (result as IngestResult.Success).expense
        assertTrue(expense.date in before - 1000..System.currentTimeMillis() + 1000)
    }

    @Test
    fun `parse con amount con spazi attorno funziona`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to " 12.50 "))
        assertTrue(result is IngestResult.Success)
        assertEquals(12.50, (result as IngestResult.Success).expense.amount, 0.001)
    }

    @Test
    fun `parse con data datetime ISO con orario funziona`() {
        val result = IntentExpenseParser.parse(mapOf("amount" to "5", "date" to "2026-08-13T10:30:00"))
        val expense = (result as IngestResult.Success).expense
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 13, 10, 30, 0)
        }
        assertEquals(cal.timeInMillis, expense.date)
    }
}
```

- [ ] **Step 2: Esegui il test per verificare che fallisca**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ingest.IntentExpenseParserTest"`
Expected: FAIL (classi non definite)

- [ ] **Step 3: Implementa i file principali**

`app/src/main/java/it/ciano/expensetracker/data/ingest/IngestResult.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

sealed class IngestResult {
    data class Success(val expense: ParsedExpense) : IngestResult()
    data class Error(val error: IngestError) : IngestResult()
}

enum class IngestError { MISSING_AMOUNT, INVALID_AMOUNT }

data class ParsedExpense(
    val amount: Double,
    val categoryName: String,
    val title: String,
    val date: Long
)
```

`app/src/main/java/it/ciano/expensetracker/data/ingest/IntentExpenseParser.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

object IntentExpenseParser {

    const val DEFAULT_CATEGORY = "Varie"

    private const val KEY_AMOUNT = "amount"
    private const val KEY_CATEGORY = "category"
    private const val KEY_NOTE = "note"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_DATE = "date"

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd"
    )

    fun parse(params: Map<String, String?>): IngestResult {
        val amountRaw = params[KEY_AMOUNT]?.trim().orEmpty()
        if (amountRaw.isEmpty()) return IngestResult.Error(IngestError.MISSING_AMOUNT)

        val amount = parseAmount(amountRaw) ?: return IngestResult.Error(IngestError.INVALID_AMOUNT)
        if (amount <= 0.0) return IngestResult.Error(IngestError.INVALID_AMOUNT)

        val categoryName = params[KEY_CATEGORY]?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CATEGORY

        val title = (params[KEY_NOTE] ?: params[KEY_DESCRIPTION])?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: categoryName

        val date = parseDate(params[KEY_DATE]) ?: System.currentTimeMillis()

        return IngestResult.Success(ParsedExpense(amount, categoryName, title, date))
    }

    private fun parseAmount(raw: String): Double? {
        return raw.replace(',', '.').toDoubleOrNull()
    }

    private fun parseDate(raw: String?): Long? {
        if (raw == null) return null
        val value = raw.trim()
        if (value.isEmpty()) return null

        value.toLongOrNull()?.let { return it }

        for (pattern in DATE_PATTERNS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value)?.time
            } catch (_: ParseException) {
                // prova il pattern successivo
            }
        }
        return null
    }
}
```

- [ ] **Step 4: Esegui il test per verificare che passi**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ingest.IntentExpenseParserTest"`
Expected: PASS (12 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/it/ciano/expensetracker/data/ingest/IntentExpenseParserTest.kt \
        app/src/main/java/it/ciano/expensetracker/data/ingest/IngestResult.kt \
        app/src/main/java/it/ciano/expensetracker/data/ingest/IntentExpenseParser.kt
git commit -m "feat: parser puro per ingestione spesa via Intent con test"
```

---

### Task 2: `ExpenseIngestRepository` (risoluzione categoria + mapping Transaction) con test

**Files:**
- Test: `app/src/test/java/it/ciano/expensetracker/data/ingest/ExpenseIngestRepositoryTest.kt` (Create)
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestRepository.kt`

**Interfaces:**
- Consumes (da Task 1): `ParsedExpense`, `IngestResult`, `IntentExpenseParser`
- Consumes (esistenti): `CategoryRepository.getAllCategories(): Flow<List<Category>>`, `CategoryRepository.insertCategory(category: Category): Long`, `TransactionRepository.insertTransaction(transaction: Transaction, tagIds: Set<Int>)` (suspend), `Category(id: Int = 0, name: String, parentCategoryId: Int? = null, budget: Double? = null)`
- Produces:
  - `fun findCategoryId(categories: List<Category>, name: String): Int?`
  - `fun toTransaction(parsed: ParsedExpense, categoryId: Int): Transaction`
  - `class ExpenseIngestRepository(categoryRepository: CategoryRepository, transactionRepository: TransactionRepository)` con `suspend fun insertExpense(parsed: ParsedExpense)`

- [ ] **Step 1: Scrivi il test fallito**

```kotlin
package it.ciano.expensetracker.data.ingest

import it.ciano.expensetracker.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseIngestRepositoryTest {

    @Test
    fun `findCategoryId trova la categoria ignorando maiuscole`() {
        val categories = listOf(
            Category(id = 1, name = "Pranzo"),
            Category(id = 2, name = "Trasporti")
        )
        assertEquals(1, findCategoryId(categories, "pranzo"))
    }

    @Test
    fun `findCategoryId restituisce null se la categoria non esiste`() {
        assertNull(findCategoryId(listOf(Category(id = 1, name = "Pranzo")), "Cena"))
    }

    @Test
    fun `toTransaction mappa i campi della spesa parsata`() {
        val parsed = ParsedExpense(amount = 12.5, categoryName = "Pranzo", title = "Ristorante", date = 123456L)
        val tx = toTransaction(parsed, categoryId = 7)
        assertEquals("EXPENSE", tx.type)
        assertEquals(12.5, tx.amount, 0.001)
        assertEquals(7, tx.categoryId)
        assertEquals(123456L, tx.date)
        assertEquals("Ristorante", tx.title)
        assertEquals("", tx.note)
    }
}
```

- [ ] **Step 2: Esegui il test per verificare che fallisca**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ingest.ExpenseIngestRepositoryTest"`
Expected: FAIL (funzioni non definite)

- [ ] **Step 3: Implementa il file**

`app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestRepository.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.repository.CategoryRepository
import it.ciano.expensetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first

class ExpenseIngestRepository(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {

    suspend fun insertExpense(parsed: ParsedExpense) {
        val categories = categoryRepository.getAllCategories().first()
        val categoryId = findCategoryId(categories, parsed.categoryName)
            ?: categoryRepository.insertCategory(
                Category(name = parsed.categoryName, parentCategoryId = null)
            ).toInt()
        transactionRepository.insertTransaction(toTransaction(parsed, categoryId), emptySet())
    }
}

fun findCategoryId(categories: List<Category>, name: String): Int? {
    return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
}

fun toTransaction(parsed: ParsedExpense, categoryId: Int): Transaction {
    return Transaction(
        title = parsed.title,
        amount = parsed.amount,
        type = "EXPENSE",
        categoryId = categoryId,
        date = parsed.date,
        note = ""
    )
}
```

- [ ] **Step 4: Esegui il test per verificare che passi**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ingest.ExpenseIngestRepositoryTest"`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/it/ciano/expensetracker/data/ingest/ExpenseIngestRepositoryTest.kt \
        app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestRepository.kt
git commit -m "feat: repository di ingestione spese con risoluzione categoria e test"
```

---

### Task 3: `IntentParamsExtractor` (extras Intent e query Uri → Map)

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/IntentParamsExtractor.kt`

**Interfaces:**
- Consumes: `android.content.Intent`, `android.net.Uri`
- Produces:
  - `object IntentParamsExtractor`
  - `fun extractExtras(intent: Intent?): Map<String, String?>`
  - `fun extractQuery(uri: Uri): Map<String, String?>`

Nota: dipendono dal framework Android, quindi solo verifica di compilazione (nessun unit test locale, come i file `MainActivity`/`ModifyTransactionHelpers` non-helper).

- [ ] **Step 1: Implementa il file**

`app/src/main/java/it/ciano/expensetracker/data/ingest/IntentParamsExtractor.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import android.content.Intent
import android.net.Uri

object IntentParamsExtractor {

    fun extractExtras(intent: Intent?): Map<String, String?> {
        val extras = intent?.extras ?: return emptyMap()
        val map = mutableMapOf<String, String?>()
        for (key in extras.keySet()) {
            map[key] = extras.get(key)?.toString()
        }
        return map
    }

    fun extractQuery(uri: Uri): Map<String, String?> {
        val names = uri.queryParameterNames ?: return emptyMap()
        val map = mutableMapOf<String, String?>()
        for (name in names) {
            map[name] = uri.getQueryParameter(name)
        }
        return map
    }
}
```

- [ ] **Step 2: Verifica la compilazione**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/ingest/IntentParamsExtractor.kt
git commit -m "feat: estrazione parametri da extras Intent e query deep link"
```

---

### Task 4: Stringhe localizzate + glue `ExpenseIngest` + `ExpenseIngestReceiver` + manifest

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (aggiungi chiavi in coda)
- Modify: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngest.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `IntentExpenseParser.parse`, `ExpenseIngestRepository.insertExpense` (suspend), `IntentParamsExtractor.extractExtras`, `AppDatabase.getDatabase(context)`, `R.string.ingest_*`
- Produces:
  - `object ExpenseIngest` con `suspend fun process(context: Context, params: Map<String, String?>): Boolean`
  - `class ExpenseIngestReceiver : BroadcastReceiver()`
  - Manifest: `<receiver android:name=".data.ingest.ExpenseIngestReceiver" android:exported="true">` con intent-filter action `it.ciano.expensetracker.ADD_EXPENSE`

- [ ] **Step 1: Aggiungi le stringhe IT**

In coda a `app/src/main/res/values/strings.xml` (prima di `</resources>`):

```xml
    <!-- Ingestione via Intent -->
    <string name="ingest_success">Spesa inserita: %1$s</string>
    <string name="ingest_error_missing_amount">Importo mancante</string>
    <string name="ingest_error_invalid_amount">Importo non valido</string>
    <string name="ingest_error_db">Errore durante l\'inserimento della spesa</string>
```

- [ ] **Step 2: Aggiungi le stringhe EN**

In coda a `app/src/main/res/values-en/strings.xml` (prima di `</resources>`):

```xml
    <!-- Intent ingestion -->
    <string name="ingest_success">Expense added: %1$s</string>
    <string name="ingest_error_missing_amount">Missing amount</string>
    <string name="ingest_error_invalid_amount">Invalid amount</string>
    <string name="ingest_error_db">Error while adding the expense</string>
```

- [ ] **Step 3: Implementa il glue `ExpenseIngest`**

`app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngest.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.AppDatabase
import it.ciano.expensetracker.data.repository.CategoryRepository
import it.ciano.expensetracker.data.repository.TransactionRepository

object ExpenseIngest {

    suspend fun process(context: Context, params: Map<String, String?>): Boolean {
        val appContext = context.applicationContext
        return when (val result = IntentExpenseParser.parse(params)) {
            is IngestResult.Success -> {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val repository = ExpenseIngestRepository(
                        CategoryRepository(db.categoryDao()),
                        TransactionRepository(db.transactionDao(), db.transactionTagDao(), db.tagDao())
                    )
                    repository.insertExpense(result.expense)
                    showToast(appContext, appContext.getString(R.string.ingest_success, result.expense.amount.toString()))
                    true
                } catch (e: Exception) {
                    showToast(appContext, appContext.getString(R.string.ingest_error_db))
                    false
                }
            }
            is IngestResult.Error -> {
                val messageRes = when (result.error) {
                    IngestError.MISSING_AMOUNT -> R.string.ingest_error_missing_amount
                    IngestError.INVALID_AMOUNT -> R.string.ingest_error_invalid_amount
                }
                showToast(appContext, appContext.getString(messageRes))
                false
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
```

- [ ] **Step 4: Implementa il Receiver**

`app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestReceiver.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseIngestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpenseIngest.process(context.applicationContext, IntentParamsExtractor.extractExtras(intent))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 5: Registra il Receiver nel manifest**

In `app/src/main/AndroidManifest.xml`, dentro `<application>`, dopo `<activity ...>.MainActivity</activity>`:

```xml
        <receiver
            android:name=".data.ingest.ExpenseIngestReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="it.ciano.expensetracker.ADD_EXPENSE" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 6: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-en/strings.xml \
        app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngest.kt \
        app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestReceiver.kt \
        app/src/main/AndroidManifest.xml
git commit -m "feat: BroadcastReceiver per ingestione spese con stringhe localizzate"
```

---

### Task 5: `ExpenseIngestActivity` trasparente con deep link + tema + manifest

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ExpenseIngest.process`, `IntentParamsExtractor.extractExtras/extractQuery`, tema `@style/Theme.ExpenseTracker.Transparent`
- Produces:
  - `class ExpenseIngestActivity : Activity()` con `handleIntent(intent: Intent?)`
  - Stile `Theme.ExpenseTracker.Transparent`
  - Manifest: `<activity android:name=".data.ingest.ExpenseIngestActivity" ...>` con intent-filter VIEW/SEND + data scheme `expensetracker` host `add_expense`

- [ ] **Step 1: Aggiungi il tema trasparente**

In `app/src/main/res/values/themes.xml`, dentro `<resources>` dopo lo stile esistente:

```xml
    <style name="Theme.ExpenseTracker.Transparent" parent="android:Theme.Translucent.NoTitleBar">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:backgroundDimEnabled">false</item>
    </style>
```

- [ ] **Step 2: Implementa l'Activity**

`app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestActivity.kt`:

```kotlin
package it.ciano.expensetracker.data.ingest

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseIngestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val params = mutableMapOf<String, String?>()
        params.putAll(IntentParamsExtractor.extractExtras(intent))
        intent?.data?.let { params.putAll(IntentParamsExtractor.extractQuery(it)) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpenseIngest.process(applicationContext, params)
            } finally {
                runOnUiThread { finish() }
            }
        }
    }
}
```

- [ ] **Step 3: Registra l'Activity nel manifest**

In `app/src/main/AndroidManifest.xml`, dentro `<application>` (accanto al receiver):

```xml
        <activity
            android:name=".data.ingest.ExpenseIngestActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.ExpenseTracker.Transparent">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="expensetracker"
                    android:host="add_expense" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/ingest/ExpenseIngestActivity.kt \
        app/src/main/res/values/themes.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: Activity trasparente con deep link expensetracker per ingestione spese"
```

---

### Task 6: `TESTING_GUIDE.md` + verifica completa + Pull Request

**Files:**
- Create: `TESTING_GUIDE.md` (root repository)

- [ ] **Step 1: Scrivi la guida di testing**

`TESTING_GUIDE.md`:

```markdown
# TESTING_GUIDE — Ingestione spese via Intent

Questa guida spiega come testare la porta d'ingresso nativa Android di
ExpenseTracker, che permette a Kai9000 (o ad altre app) di inserire spese
direttamente nel database senza aprire l'app, con consumo batteria ~zero.

Ci sono due porte d'ingresso equivalenti:

1. **BroadcastReceiver** con action `it.ciano.expensetracker.ADD_EXPENSE`.
2. **Activity trasparente** con deep link `expensetracker://add_expense`.

## 1. Comandi da terminale (adb)

### Broadcast (BroadcastReceiver)

Su Android 8+ (API 26+) i broadcast impliciti NON raggiungono i receiver
dichiarati nel manifest: il comando deve essere **esplicito**, specificando il
package (`-p`) o il componente (`-n`).

```bash
# Spesa minima (categoria "Varie", data odierna)
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE -p it.ciano.expensetracker --es amount "12.50"

# Spesa completa
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE -p it.ciano.expensetracker \
  --es amount "12.50" --es category "Pranzo" --es note "Ristorante" --es date "2026-08-13"

# Importo con virgola (locale IT)
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE -p it.ciano.expensetracker --es amount "12,50"

# Errore: importo non valido (non viene inserito nulla)
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE -p it.ciano.expensetracker --es amount "abc"

# Forma alternativa con componente esplicito
adb shell am broadcast -a it.ciano.expensetracker.ADD_EXPENSE \
  -n it.ciano.expensetracker/.data.ingest.ExpenseIngestReceiver --es amount "7.00"
```

### Deep link (Activity trasparente)

```bash
# Spesa minima
adb shell am start -a android.intent.action.VIEW -d "expensetracker://add_expense?amount=12.50"

# Spesa completa
adb shell am start -a android.intent.action.VIEW \
  -d "expensetracker://add_expense?amount=12.50&category=Pranzo&note=Ristorante&date=2026-08-13"
```

L'activity trasparente si chiude da sola subito dopo l'inserimento (nessun
flash visibile).

## 2. Parametri accettati

| Parametro | Obbligatorio | Alias | Default | Descrizione |
|---|---|---|---|---|
| `amount` | Si | — | — | Importo > 0. Accetta separatore punto (`12.50`) o virgola (`12,50`). |
| `category` | No | — | `Varie` | Nome categoria. Se non esiste viene auto-creata (categoria principale). |
| `note` | No | `description` | `""` | Usato come titolo della spesa. Se assente, il titolo è il nome della categoria. |
| `date` | No | — | data corrente | Data in ISO-8601 (`2026-08-13`, `2026-08-13T10:30`, `2026-08-13T10:30:00`) o epoch millis. |

La categoria di default `Varie` viene auto-creata al primo utilizzo.

## 3. Comportamento atteso

### Database (Room)

- Viene inserita una riga in `transactions` con `type = 'EXPENSE'`, `note = ''`
  e `categoryId` risolto (o creata) dalla categoria indicata.
- La lista non è filtrata: la nuova spesa appare in Home e Cronologia.

### UI (Jetpack Compose)

- Se l'app è aperta, la lista si **aggiorna automaticamente** (Room Flow →
  StateFlow → `collectAsState`).
- Viene mostrato un **Toast** localizzato (lingua salvata dall'utente):
  - successo: "Spesa inserita: <importo>"
  - errore: "Importo mancante" / "Importo non valido" / "Errore durante l'inserimento della spesa"

## 4. Limiti noti

- **Android 8+**: per il BroadcastReceiver serve un broadcast esplicito
  (`-p`/`-n`); un `am broadcast` implicito senza package viene ignorato.
- **Android 12+**: un Toast mostrato quando l'app è in background può essere
  soppresso dal sistema; il deep link (Activity in foreground) non ha questo limite.
- **Force-stop**: se l'utente ha forzato la chiusura dell'app, né broadcast né
  deep link la riattivano finché l'app non viene riaperta manualmente.
```

- [ ] **Step 2: Esegui la suite completa di test e build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, tutti i test PASS (esistenti + nuovi).

- [ ] **Step 3: Commit della guida**

```bash
git add TESTING_GUIDE.md
git commit -m "docs: guida di testing per ingestione spese via Intent"
```

- [ ] **Step 4: Push e crea la Pull Request verso main**

```bash
git push -u origin feature/native-intent-ingestion
gh pr create \
  --base main \
  --head feature/native-intent-ingestion \
  --title "feat: porta di ingresso nativa via Intent (BroadcastReceiver + deep link)" \
  --body "## Riepilogo

Aggiunge una porta di ingresso Android nativa per inserire spese da app esterne (es. Kai9000) senza aprire l'app.

- **BroadcastReceiver** action \`it.ciano.expensetracker.ADD_EXPENSE\`
- **Activity trasparente** con deep link \`expensetracker://add_expense\`
- Parser puro (\`IntentExpenseParser\`) con validazione e test unitari (JUnit4)
- Risoluzione/auto-creazione categoria, inserimento via Room
- Toast localizzati (IT/EN, lingua salvata dall'utente)
- \`TESTING_GUIDE.md\` con esempi \`am broadcast\` / \`am start\`

## Test

\`./gradlew testDebugUnitTest assembleDebug\` — verde.

Guida: vedi \`TESTING_GUIDE.md\`."
```

Expected: PR creata verso `main` (fa scattare la GitHub Action `android.yml` con `assembleRelease`).

---

## Self-Review

- **Spec coverage:** parser+validazione (Task 1), repository/categoria (Task 2), extractor (Task 3), receiver+strings i18n (Task 4), activity+deep link+tema (Task 5), TESTING_GUIDE (Task 6), branch+PR (Task 0/6). Coperto tutto lo spec.
- **Placeholder scan:** nessun TBD/TODO; ogni step ha codice o comando concreto.
- **Type consistency:** `ParsedExpense(amount, categoryName, title, date)`, `IngestResult.Success/Error`, `IngestError.MISSING_AMOUNT/INVALID_AMOUNT`, `findCategoryId(categories, name): Int?`, `toTransaction(parsed, categoryId): Transaction`, `ExpenseIngest.process(context, params): Boolean`, `IntentParamsExtractor.extractExtras(Intent?)/extractQuery(Uri)` usati in modo identico in tutti i task.
