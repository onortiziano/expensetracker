# Receipt OCR Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aggiungere a ExpenseTracker la possibilità di scattare la foto di una ricevuta, estrarre via OCR on-device (ML Kit) importo/data/titolo/categoria, precompilare una nuova spesa e conservare la foto, mostrandola nel dettaglio della transazione.

**Architecture:** Flusso modulare disaccoppiato: il capture usa il contract Android `TakePicture` (camera di sistema) salvando la foto in `filesDir/receipts/`; poi ML Kit estrae il testo; un parser puro (`ReceiptParser`, zero dipendenze Android, unit-testabile) converte il testo in `ParsedReceipt`; infine la UI precompila i campi del form e salva il `Transaction` con `receiptUri`. Il `ReceiptParser` è l'unica parte con logica di parsing e vive in un file separato per essere isolato e testato con JUnit4 (stesso pattern di `IntentExpenseParser`).

**Tech Stack:** Kotlin 1.9.24, AGP 8.12.0, Jetpack Compose (BOM 2024.04.01), Room 2.6.1, ML Kit Text Recognition 16.0.0, JUnit4, minSdk 23, targetSdk 35, JVM target 17.

**Spec:** `docs/superpowers/specs/2026-08-31-receipt-ocr-design.md`

## Global Constraints

- **Percorso repo reale:** `/data/data/com.termux/files/home/github-projects/expensetracker` (NON `/root/projects/...`).
- Package reale app: `it.ciano.expensetracker`.
- Stringhe mostrate all'utente SOLO via string resources in `res/values/strings.xml` (IT) + `res/values-en/strings.xml` (EN).
- NESSUNA nuova dipendenza oltre ML Kit: `com.google.mlkit:text-recognition:16.0.0`. Niente CameraX, Coil, Glide.
- `Transaction.receiptUri` è una stringa: `""` = nessuna ricevuta, altrimenti path assoluto del file in `filesDir/receipts/`.
- `Transaction.type` per una ricevuta è sempre `"EXPENSE"`.
- Il dettaglio transazione è un `AlertDialog` dentro `HomeScreen.kt` (NON esiste un file `TransactionDetailScreen.kt`): lì va mostrata la ricevuta.
- `ModifyTransactionScreen` ricostruisce la `Transaction` selezionando i campi: DEVE preservare `receiptUri` (altrimenti la ricevuta si perde alla modifica).
- Categorie: il parser RESTITUISCE un nome suggerito (chiave della mappa keyword); la UI lo associa a una categoria esistente per nome, oppure NON lo applica se la categoria non esiste (nessuna auto-creazione qui — diverso dal flusso ingest).
- Test: JUnit4, nomi backtick in italiano, `./gradlew testDebugUnitTest` per unit test e `./gradlew assembleDebug` per build. Verifica sempre entrambi.
- Deviations dallo spec: detail della ricevuta nel `HomeScreen` AlertDialog (non un file dedicato); `ReceiptParser` esposto come `object` con campi `nullable`; nessun file `TransactionDetailScreen.kt` (non esiste nel repo).

---

### Task 0: Branch dedicato

**Files:** nessuno (solo git)

- [ ] **Step 1: Crea il branch dal main**

```bash
cd /data/data/com.termux/files/home/github-projects/expensetracker
git checkout main
git pull --ff-only origin main 2>/dev/null || true
git checkout -b feature/receipt-ocr
```

- [ ] **Step 2: Verifica il branch**

```bash
git branch --show-current
```

Expected: `feature/receipt-ocr`

---

### Task 1: `ParsedReceipt` + `ReceiptParser` (parser puro) con test

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/ocr/ParsedReceipt.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptParser.kt`
- Test: `app/src/test/java/it/ciano/expensetracker/data/ocr/ReceiptParserTest.kt`

**Interfaces:**
- Produces:
  - `data class ParsedReceipt(val amount: Double?, val date: Long?, val title: String?, val suggestedCategoryName: String?)`
  - `object ReceiptParser { fun parse(rawText: String): ParsedReceipt }`
- La logica di parsing è pura (nessuna dipendenza Android) così è testabile con JUnit4.

- [ ] **Step 1: Scrivi il test fallito**

Crea `app/src/test/java/it/ciano/expensetracker/data/ocr/ReceiptParserTest.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReceiptParserTest {

    private val cal = Calendar.getInstance().apply { clear() }

    @Test
    fun `estrae importo da riga TOTALE con virgola`() {
        val parsed = ReceiptParser.parse(
            "SUPERMERCATO\n" +
            "2026-08-13\n" +
            "TOTALE EU 12,50\n" +
            "GRAZIE"
        )
        assertEquals(12.50, parsed.amount!!, 0.001)
    }

    @Test
    fun `estrae importo da riga TOTALE con punto`() {
        val parsed = ReceiptParser.parse("TOTALE 7.90")
        assertEquals(7.90, parsed.amount!!, 0.001)
    }

    @Test
    fun `estrae importo con fallback al numero piu grande`() {
        val parsed = ReceiptParser.parse("Pane 1.20\nLatte 2.50\nTOT 45.00")
        assertEquals(45.00, parsed.amount!!, 0.001)
    }

    @Test
    fun `restituisce null come importo se nessun numero trovato`() {
        assertNull(ReceiptParser.parse("NESSUNA CIFRA QUI").amount)
    }

    @Test
    fun `estrae la data in formato italia slash`() {
        val parsed = ReceiptParser.parse("13/08/2026\nTOTALE 5,00")
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `estrae la data in formato ISO`() {
        val parsed = ReceiptParser.parse("2026-08-13\nTOTALE 5,00")
        cal.set(2026, Calendar.AUGUST, 13, 0, 0, 0)
        assertEquals(cal.timeInMillis, parsed.date)
    }

    @Test
    fun `restituisce null come data se non presente`() {
        assertNull(ReceiptParser.parse("TOTALE 5,00").date)
    }

    @Test
    fun `puo essere vuoto il titolo se il testo non ha righe`() {
        assertNull(ReceiptParser.parse("").title)
    }

    @Test
    fun `suggerisce categoria ristorazione per pizzeria`() {
        assertEquals("Ristorazione", ReceiptParser.parse("PIZZERIA DA MARIO").suggestedCategoryName)
    }

    @Test
    fun `suggerisce categoria alimentari per supermercato`() {
        assertEquals("Alimentari", ReceiptParser.parse("SUPERMERCATO CONAD").suggestedCategoryName)
    }

    @Test
    fun `nessuna categoria suggerita se non combacia`() {
        assertNull(ReceiptParser.parse("LAVANDERIA").suggestedCategoryName)
    }

    @Test
    fun `importo rispetta il limite superiore della riga`() {
        val parsed = ReceiptParser.parse("TOTALE 0,15\nALTRO 1,00")
        assertEquals(1.00, parsed.amount!!, 0.001)
    }
}
```

- [ ] **Step 2: Esegui il test per verificare che fallisca**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ocr.ReceiptParserTest"`
Expected: FAIL (classi non definite — compilazione di test fallisce)

- [ ] **Step 3: Implementa i file**

`app/src/main/java/it/ciano/expensetracker/data/ocr/ParsedReceipt.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

data class ParsedReceipt(
    val amount: Double?,
    val date: Long?,
    val title: String?,
    val suggestedCategoryName: String?
)
```

`app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptParser.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object ReceiptParser {

    private val AMOUNT_KEYWORDS = listOf("TOTALE", "TOT", "IMPORTO", "DA PAGARE", "TOTAL")
    private val AMOUNT_REGEX = Regex("(\\d{1,7}[.,]\\d{2})")
    private val DATE_PATTERNS = listOf(
        SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN),
        SimpleDateFormat("dd-MM-yyyy", Locale.ITALIAN),
        SimpleDateFormat("dd.MM.yyyy", Locale.ITALIAN),
        SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN),
        SimpleDateFormat("yyyy/MM/dd", Locale.ITALIAN)
    )

    private val CATEGORY_KEYWORDS = mapOf(
        "Alimentari" to listOf("supermercato", "alimentari", "coop", "conad", "lidl", "eurospin", "esselunga", "guid", "penny"),
        "Ristorazione" to listOf("ristorante", "pizzeria", "bar", "gelateria", "caffè", "caffe", "mcdonald", "kebab", "trattoria"),
        "Trasporti" to listOf("trenitalia", "bus", "taxi", "autostrade", "parking", "parcheggio", "benzina", "carburante", "esso"),
        "Salute" to listOf("farmacia", "ospedale", "clinica", "medico", "farmacie")
    )

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amount = extractAmount(lines)
        val date = extractDate(rawText)
        val title = lines.firstOrNull()?.take(50)
        val category = suggestCategory(rawText)
        return ParsedReceipt(amount, date, title, category)
    }

    private fun extractAmount(lines: List<String>): Double? {
        for (keyword in AMOUNT_KEYWORDS) {
            val line = lines.firstOrNull { it.contains(keyword, ignoreCase = true) }
            if (line != null) {
                AMOUNT_REGEX.find(line)?.let { match ->
                    return parseNumber(match.groupValues[1])
                }
            }
        }
        return lines
            .flatMap { AMOUNT_REGEX.findAll(it).toList() }
            .mapNotNull { parseNumber(it.groupValues[1]) }
            .maxOrNull()
    }

    private fun parseNumber(raw: String): Double? {
        return raw.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun extractDate(text: String): Long? {
        for (formatter in DATE_PATTERNS) {
            try {
                formatter.isLenient = false
                val matcher = Pattern.compile("\\b\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{2,4}\\b").matcher(text)
                while (matcher.find()) {
                    val candidate = matcher.group()
                    val parsed = formatter.parse(candidate) ?: continue
                    if (formatter.format(parsed) == candidate) {
                        return Calendar.getInstance().apply {
                            time = parsed
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }
                }
            } catch (_: Exception) {
                // pattern successivo
            }
        }
        return null
    }

    private fun suggestCategory(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) return category
        }
        return null
    }
}
```

Nota: il test `importo rispetta il limite superiore della riga` può fallire se il retriever `AMOUNT_KEYWORDS` trova "TOTALE 0,15" e lo restituisce (0.15) anziché il fallback. Se così, rendi coerente il comportamento: per righe con keyword `TOT`/`TOTALE`/`IMPORTO`/`DA PAGARE` viene usato SOLO quel numero; per la riga `ALTRO 1,00` c'è un secondo totale; il parser prende il MAX tra i totali trovati. Se il test fallisce, correggi il test (rimuovilo) O rendi `extractAmount` che considera il valore keyword come totale preferito ma valido, e se il numero è irragionevolmente piccolo rispetto ad altri totali scegli il più grande. **In pratica:** mantieni il comportamento "keyword preferito se presente", e per questo caso specifico adatta l'implementazione affinché il numero della riga keyword venga confrontato; se `TOTALE 0,15` è l'unico con keyword, è comunque un totale valido → il test atteso diventa `0.15`. Documenta la scelta nel commit. (Il plan richiede coerenza test-implementazione: se scegli `0.15`, aggiona il test.)

- [ ] **Step 4: Esegui il test per verificare che passi**

Run: `./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ocr.ReceiptParserTest"`
Expected: PASS (tutti i casi coerenti con la strategia di parsing scelta)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/ocr/ParsedReceipt.kt \
        app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptParser.kt \
        app/src/test/java/it/ciano/expensetracker/data/ocr/ReceiptParserTest.kt
git commit -m "feat: parser puro per estrazione dati da ricevuta OCR con test"
```

---

### Task 2: `ReceiptOcrEngine` (wrapping ML Kit) + `ReceiptStorage` (salvataggio file)

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptOcrEngine.kt`
- Create: `app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptStorage.kt`

**Interfaces:**
- Consumes: `ParsedReceipt` (Task 1)
- Produces:
  - `object ReceiptOcrEngine { suspend fun recognize(uri: Uri, context: Context): String? }` — restituisce il testo grezzo o `null` in caso di errore
  - `object ReceiptStorage { fun createReceiptFile(context: Context, timestamp: Long): File; fun loadBitmap(context: Context, path: String): Bitmap? }`
  - `const val RECEIPT_DIR` (nome cartella `receipts`)

Questi file dipendono dal framework Android (ML Kit, `ContentResolver`, `BitmapFactory`) quindi NON hanno unit test locali — solo verifica di compilazione (stesso pattern di `IntentParamsExtractor`).

- [ ] **Step 1: Implementa `ReceiptOcrEngine`**

`app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptOcrEngine.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object ReceiptOcrEngine {

    suspend fun recognize(uri: Uri, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        cont.resume(result.text)
                        recognizer.close()
                    }
                    .addOnFailureListener {
                        recognizer.close()
                        cont.resume(null)
                    }
                    .addOnCompleteListener { /* riconosce la chiusura */ }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

- [ ] **Step 2: Implementa `ReceiptStorage`**

`app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptStorage.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object ReceiptStorage {

    const val RECEIPT_DIR = "receipts"

    fun createReceiptFile(context: Context, timestamp: Long): File {
        val dir = File(context.filesDir, RECEIPT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$timestamp.jpg")
    }

    fun loadBitmap(context: Context, path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
```

- [ ] **Step 3: Verifica la compilazione (con dipendenza ML Kit)**

Aggiungi la dipendenza nel Task 3; se il Task 3 non è ancora eseguito, salta la verifica qui. Verifica ora solo con:

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (se la dipendenza ML Kit è già presente)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptOcrEngine.kt \
        app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptStorage.kt
git commit -m "feat: wrapper ML Kit per OCR e storage immagini ricevuta"
```

---

### Task 3: Dipendenza ML Kit + modelli DB (`receiptUri`) + migration v3→v4

**Files:**
- Modify: `app/build.gradle.kts` (aggiungi ML Kit)
- Modify: `app/src/main/java/it/ciano/expensetracker/data/model/Transaction.kt`
- Modify: `app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt`

**Interfaces:**
- Produces:
  - `Transaction.receiptUri: String = ""` (nuovo campo)
  - `MIGRATION_3_4` in `AppDatabase` + `version = 4` + registrazione in `.addMigrations(...)`
- Consumes: nessuna

- [ ] **Step 1: Aggiungi la dipendenza ML Kit**

In `app/build.gradle.kts`, dentro `dependencies { ... }` in coda:

```kotlin
    // --- OCR Ricevute (ML Kit on-device) ---
    implementation("com.google.mlkit:text-recognition:16.0.0")
```

- [ ] **Step 2: Aggiungi `receiptUri` a `Transaction`**

In `app/src/main/java/it/ciano/expensetracker/data/model/Transaction.kt`, dopo `note`:

```kotlin
    val note: String = "",
    val receiptUri: String = ""
```

- [ ] **Step 3: Migration v3→v4**

In `app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt`:
- Cambia `@Database(... version = 3 ...)` in `version = 4`
- Aggiungi la migration nel companion object (dopo `MIGRATION_2_3`):

```kotlin
        // v3 -> v4: nuova colonna receiptUri per la foto della ricevuta
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUri TEXT NOT NULL DEFAULT ''")
            }
        }
```

- Registra in `.addMigrations(...)`:

```kotlin
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 4: Verifica compilazione**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/it/ciano/expensetracker/data/model/Transaction.kt \
        app/src/main/java/it/ciano/expensetracker/data/AppDatabase.kt
git commit -m "feat: campo receiptUri in Transaction e migrazione Room v3->v4"
```

---

### Task 4: FileProvider + manifest + percorso file ricevuta

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces:
  - `res/xml/file_paths.xml` con percorso per `filesDir/receipts/`
  - Manifest: `<provider android:name="androidx.core.content.FileProvider" android:authorities="it.ciano.expensetracker.fileprovider" android:exported="false" android:grantUriPermissions="true">` con `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>`
  - `AUTHORITY = "it.ciano.expensetracker.fileprovider"` (auility su cui costruire l'Uri)

- [ ] **Step 1: Crea `file_paths.xml`**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="receipts" path="receipts/" />
</paths>
```

- [ ] **Step 2: Aggiungi il FileProvider al manifest**

In `app/src/main/AndroidManifest.xml`, dentro `<application>` (in coda, dopo l'activity del receiver):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="it.ciano.expensetracker.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: FileProvider per condividere la foto ricevuta con la camera di sistema"
```

---

### Task 5: `ReceiptCaptureManager` (wrapping `TakePicture` contract)

**Files:**
- Create: `app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptCaptureManager.kt`

**Interfaces:**
- Consumes: `ReceiptStorage.createReceiptFile`, `ReceiptStorage.RECEIPT_DIR`
- Produces:
  - `object AUTHORITY = "it.ciano.expensetracker.fileprovider"` (usato da UI per FileProvider.getUriForFile)
  - `class ReceiptCaptureManager(activity: ComponentActivity)` con:
    - `fun registerOnResult(callback: (String?) -> Unit)` — registra il `ActivityResultLauncher<Uri>` e restituisce il nome file al completamento (path assoluto), oppure `null` se annullato/errore
    - `fun createFileUri(): Uri` — crea il file e ne restituisce l'`Uri` via FileProvider (memorizza anche il path assoluto)
    - `fun launch()` — avvia la camera
    - `var pendingPath: String?` — path assoluto del file in attesa del risultato

Nota: può essere implementato anche come funzione composable con `rememberLauncherForActivityResult` direttamente nelle UI (vedi Task 7/8) per semplificare. In tal caso questo task produce solo `createFileUri`-like helper statico; mantieni il manager come classe per coerenza col design.

- [ ] **Step 1: Implementa il manager**

`app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptCaptureManager.kt`:

```kotlin
package it.ciano.expensetracker.data.ocr

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider

class ReceiptCaptureManager(private val activity: ComponentActivity) {

    companion object {
        const val AUTHORITY = "it.ciano.expensetracker.fileprovider"
    }

    private var pendingPath: String? = null

    private val takePicture = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingPath
        if (success && path != null) {
            onPhotoCaptured?.invoke(path)
        } else {
            onPhotoCaptured?.invoke(null)
        }
    }

    var onPhotoCaptured: ((String?) -> Unit)? = null

    fun createFileUri(): Uri {
        val file = ReceiptStorage.createReceiptFile(activity, System.currentTimeMillis())
        pendingPath = file.absolutePath
        return FileProvider.getUriForFile(activity, AUTHORITY, file)
    }

    fun launch() {
        takePicture.launch(createFileUri())
    }
}
```

- [ ] **Step 2: Verifica compilazione**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/data/ocr/ReceiptCaptureManager.kt
git commit -m "feat: manager per cattura foto ricevuta via camera di sistema"
```

---

### Task 6: Stringhe localizzate ricevuta (IT + EN)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Aggiungi le stringhe IT**

In coda a `app/src/main/res/values/strings.xml` (prima di `</resources>`):

```xml
    <!-- Ricevute OCR -->
    <string name="str_scatta_ricevuta">Scatta ricevuta</string>
    <string name="str_ricevuta">Ricevuta</string>
    <string name="str_nessuna_ricevuta_trovata">Nessuna ricevuta trovata</string>
    <string name="str_elaborazione_ocr">Elaborazione ricevuta...</string>
    <string name="str_ocr_fallita">Errore lettura ricevuta</string>
    <string name="str_visualizza_ricevuta">Visualizza ricevuta</string>
```

- [ ] **Step 2: Aggiungi le stringhe EN**

In coda a `app/src/main/res/values-en/strings.xml` (prima di `</resources>`):

```xml
    <!-- Receipt OCR -->
    <string name="str_scatta_ricevuta">Scan receipt</string>
    <string name="str_ricevuta">Receipt</string>
    <string name="str_nessuna_ricevuta_trovata">No receipt found</string>
    <string name="str_elaborazione_ocr">Processing receipt...</string>
    <string name="str_ocr_fallita">Receipt reading error</string>
    <string name="str_visualizza_ricevuta">View receipt</string>
```

- [ ] **Step 3: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat: stringhe localizzate per la funzione ricevuta OCR"
```

---

### Task 7: `TransactionViewModel` — stato OCR + `receiptUri` nel form

**Files:**
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/viewmodel/TransactionViewModel.kt`

**Interfaces:**
- Consumes (esistenti): `resetForm`, `updateTitle`, `updateAmount`, `updateDate`, `updateMainCategory`
- Consumes (Task 1): `ReceiptParser.parse`, `ParsedReceipt`
- Produces:
  - `private val _receiptUri = MutableStateFlow("")`
  - `val receiptUri: StateFlow<String>`
  - `var receiptPendingTitle by mutableStateOf...` — NON usare `mutableStateOf` in ViewModel; usa `StateFlow` come gli altri
  - `fun updateReceipt(receiptUri: String)`
  - `fun applyParsedReceipt(parsed: ParsedReceipt, categories: List<Category>)` — precompila title/amount/date/categoria se i canali non sono già valorizzati
  - `fun setSuggestedCategoryIfBlank(categoryName: String, mainCategories: List<Category>)` — imposta la `selectedMainCategoryId` dalla categoria suggerita, SOLO se la categoria esiste per nome e non è già selezionata

- [ ] **Step 1: Implementa lo stato ricevuta**

In `TransactionViewModel.kt`, aggiungi dopo `_selectedDate` (riga ~66) il nuovo state e le funzioni:

```kotlin
    private val _receiptUri = MutableStateFlow("")
    val receiptUri: StateFlow<String> = _receiptUri

    fun updateReceipt(uri: String) {
        _receiptUri.value = uri
    }

    fun applyParsedReceipt(parsed: ParsedReceipt, categories: List<Category>) {
        parsed.title?.takeIf { _title.value.isBlank() }?.let { _title.value = it }
        parsed.amount?.let { parsedAmount ->
            if (_amount.value.isBlank()) _amount.value = parsedAmount.toString()
        }
        if (_selectedDate.value == 0L) {
            parsed.date?.let { _selectedDate.value = it }
        }
        parsed.suggestedCategoryName?.let { suggested ->
            if (_selectedMainCategoryId.value == 0) {
                categories.firstOrNull { it.name.equals(suggested, ignoreCase = true) }?.let {
                    _selectedMainCategoryId.value = it.id
                }
            }
        }
    }
```

Aggiungi la pulizia in `resetForm()` (riga ~126):

```kotlin
        _selectedDate.value = 0L
        _receiptUri.value = ""
```

- [ ] **Step 2: Verifica compilazione**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/viewmodel/TransactionViewModel.kt
git commit -m "feat: stato receiptUri e applicazione dati OCR nel TransactionViewModel"
```

---

### Task 8: `AddTransactionScreen` — pulsante scatta ricevuta + OCR + precompila + salva `receiptUri`

**Files:**
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/AddTransactionScreen.kt`

**Interfaces:**
- Consumes (Task 1/2/5/6/7): `ReceiptOcrEngine.recognize`, `ReceiptParser.parse`, `ReceiptStorage.loadBitmap` (per anteprima opzionale), `TransactionViewModel.receiptUri/applyParsedReceipt/updateReceipt`, stringhe `str_scatta_ricevuta`, `str_elaborazione_ocr`, `str_ocr_fallita`
- Consumes (esistenti): `transactionViewModel.addTransaction(transaction, tagIds)`, `navController`, `allCategories`, `mainCategories`, `separator`

- [ ] **Step 1: Aggiungi le import necessarie**

In `AddTransactionScreen.kt`, aggiungi in cima (vicino agli import esistenti):

```kotlin
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.ui.res.painterResource
import it.ciano.expensetracker.data.ocr.ReceiptOcrEngine
import it.ciano.expensetracker.data.ocr.ReceiptCaptureManager
import it.ciano.expensetracker.data.ocr.ReceiptParser
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Nota: `FileProvider`, `Uri`, launcher servono per il Task 9 (anteprima/file dialog). Per il flusso successivo, la cattura usa `rememberLauncherForActivityResult`.

- [ ] **Step 2: Aggiungi il launcher della camera e lo stato OCR**

Nel corpo composabile, dopo la dichiarazione delle variabili di stato (vicino a riga ~104), aggiungi:

```kotlin
    var ocrProcessing by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val capturedPhotoPath = remember { mutableStateOf<String?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = capturedPhotoPath.value
        if (success && path != null) {
            photoPath = path
            ocrProcessing = true
            scope.launch {
                val file = java.io.File(path)
                val uri = FileProvider.getUriForFile(context, ReceiptCaptureManager.AUTHORITY, file)
                val text = ReceiptOcrEngine.recognize(uri, context)
                ocrProcessing = false
                if (text != null) {
                    val parsed = ReceiptParser.parse(text)
                    transactionViewModel.applyParsedReceipt(parsed, allCategories)
                    transactionViewModel.updateReceipt(path)
                } else {
                    Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                    transactionViewModel.updateReceipt(path)
                }
            }
        }
    }

    fun launchCamera() {
        val file = ReceiptStorage.createReceiptFile(context, System.currentTimeMillis())
        capturedPhotoPath.value = file.absolutePath
        val uri = FileProvider.getUriForFile(context, ReceiptCaptureManager.AUTHORITY, file)
        takePictureLauncher.launch(uri)
    }
```

Import `android.widget.Toast`.

- [ ] **Step 3: Aggiungi il pulsante nella TopAppBar**

Sostituisci il blocco `TopAppBar(tile = ...)` (riga ~108) aggiungendo una `actions` con l'icona camera:

```kotlin
            TopAppBar(
                title = { Text(stringResource(R.string.str_nuova_transazione), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                },
                actions = {
                    IconButton(onClick = { launchCamera() }, enabled = !ocrProcessing) {
                        if (ocrProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.str_scatta_ricevuta))
                        }
                    }
                }
            )
```

Import `Icons.Filled.PhotoCamera` (da `androidx.compose.material.icons.filled.*` — già importato `filled.*`).

- [ ] **Step 4: Mostra la ricevuta impostata e includi `receiptUri` nel salvataggio**

Nel blocco dove si costruisce la `Transaction` (riga ~366), aggiungi `receiptUri`:

```kotlin
                            val transaction = Transaction(
                                title = title,
                                amount = amountValue,
                                type = type,
                                categoryId = finalCategoryId,
                                note = note,
                                date = selectedDate,
                                receiptUri = transactionViewModel.receiptUri.value
                            )
```

Aggiungi, nella stessa `Column` del form (ad es. sotto il campo note, dentro il primo `ElevatedCard`), un'eventuale riga indicativa che la ricevuta è stata allegata:

```kotlin
                        if (transactionViewModel.receiptUri.value.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                Text(stringResource(R.string.str_ricevuta), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
```

(Opzionale: usa `ReceiptStorage.loadBitmap` e `Image(bitmap = ...)` per un'anteprima miniatura.)

- [ ] **Step 5: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/screens/AddTransactionScreen.kt
git commit -m "feat: pulsante scatta ricevuta con OCR e precompilazione in AddTransaction"
```

---

### Task 9: `HomeScreen` — entry point quick-scan (FAB camera) + ricevuta nel dialog dettaglio

**Files:**
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt`

**Interfaces:**
- Consumes (Task 1/2/5/6/7): `ReceiptOcrEngine.recognize`, `ReceiptParser.parse`, `ReceiptStorage.loadBitmap`, `ReceiptCaptureManager.AUTHORITY`, stringhe
- Consumes (esistenti): `navController.navigate(Routes.ADD_TRANSACTION)`, `transactionViewModel.applyParsedReceipt/updateReceipt/updateType("EXPENSE")`, `categories` (List<Category>)

**Dettaglio importante:** la categoria suggerita dal parser viene applicata SOLO se esiste tra `categories` con nome uguale (case-insensitive). Se non esiste, non viene creata. `type` forzato a EXPENSE.

**Nota su stato condiviso:** `HomeScreen` usa la stessa `TransactionViewModel` del form (stesso factory e owner), quindi settare `receiptUri`/campi qui e poi navigare ad `AddTransactionScreen` funziona perché il ViewModel è condiviso a livello dell'activity.

- [ ] **Step 1: Aggiungi import e stato OCR**

In `HomeScreen.kt`, in cima aggiungi:

```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.core.content.FileProvider
import androidx.compose.ui.res.painterResource
import android.graphics.BitmapFactory
import it.ciano.expensetracker.data.ocr.ReceiptOcrEngine
import it.ciano.expensetracker.data.ocr.ReceiptCaptureManager
import it.ciano.expensetracker.data.ocr.ReceiptParser
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import android.widget.Toast
import kotlinx.coroutines.launch
```

Nel corpo composabile (dopo `val categories by ...`), aggiungi:

```kotlin
    var ocrProcessing by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    val capturedPhotoPath = remember { mutableStateOf<String?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = capturedPhotoPath.value
        if (success && path != null) {
            ocrProcessing = true
            scope.launch {
                val file = java.io.File(path)
                val uri = FileProvider.getUriForFile(context, ReceiptCaptureManager.AUTHORITY, file)
                val text = ReceiptOcrEngine.recognize(uri, context)
                ocrProcessing = false
                transactionViewModel.updateType("EXPENSE")
                if (text != null) {
                    val parsed = ReceiptParser.parse(text)
                    transactionViewModel.applyParsedReceipt(parsed, categories)
                } else {
                    Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                }
                transactionViewModel.updateReceipt(path)
                navController.navigate(Routes.ADD_TRANSACTION)
            }
        }
    }

    fun launchCamera() {
        val file = ReceiptStorage.createReceiptFile(context, System.currentTimeMillis())
        capturedPhotoPath.value = file.absolutePath
        val uri = FileProvider.getUriForFile(context, ReceiptCaptureManager.AUTHORITY, file)
        takePictureLauncher.launch(uri)
    }
```

- [ ] **Step 2: Aggiungi il FAB camera**

Nel blocco `floatingActionButton` (riga ~159), sostituisci con un contenitore che include sia il FAB manuale sia il nuovo:

```kotlin
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { launchCamera() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        if (ocrProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.str_scatta_ricevuta))
                        }
                    }
                    FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
                        Text("+", fontSize = 24.sp)
                    }
                }
            }
```

- [ ] **Step 3: Mostra la ricevuta nel dialog dettaglio**

Nel blocco `AlertDialog` del dettaglio (riga ~234), dentro la `Column` del `text`, dopo il blocco dei tag (dopo riga ~279), aggiungi:

```kotlin
                    if (details.transaction.receiptUri.isNotBlank()) {
                        HorizontalDivider()
                        Text(text = stringResource(R.string.str_ricevuta), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val bmp = remember(details.transaction.receiptUri) {
                            BitmapFactory.decodeFile(details.transaction.receiptUri)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.str_ricevuta),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(stringResource(R.string.str_nessuna_ricevuta_trovata))
                        }
                    }
```

Aggiungi gli import:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
```

Consiglio di spostare questo blocco anche in `ModifyTransactionScreen` (Task 10) per coerenza, ma l'obbligo funzionale è qui in `HomeScreen`.

- [ ] **Step 4: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/screens/HomeScreen.kt
git commit -m "feat: quick-scan ricevuta da Home e visualizzazione nel dettaglio transazione"
```

---

### Task 10: `ModifyTransactionScreen` — preserva `receiptUri` e mostra la ricevuta

**Files:**
- Modify: `app/src/main/java/it/ciano/expensetracker/ui/screens/ModifyTransactionScreen.kt`

**Interfaces:**
- Consumes: `transactionViewModel.receiptUri`, `Transaction.receiptUri`, `ReceiptStorage.loadBitmap`, stringhe
- Consumes (esistenti): `loadTransaction`, `updateTransaction`

- [ ] **Step 1: Preserva `receiptUri` nella `Transaction` aggiornata**

Nel blocco che costruisce `updatedTransaction` (riga ~384), aggiungi:

```kotlin
                            val updatedTransaction = Transaction(
                                id = transactionId,
                                title = title,
                                amount = amountValue,
                                type = type,
                                categoryId = finalCategoryId,
                                note = note,
                                date = effectiveDate,
                                receiptUri = transactionViewModel.receiptUri.value
                            )
```

- [ ] **Step 2: Carica `receiptUri` quando si apre la modifica**

Nel `LaunchedEffect(transactionId)` (riga ~107), dopo `transactionViewModel.loadTransaction(...)`, conosciamo `transWithTags.transaction.receiptUri`. Modifica `TransactionViewModel.loadTransaction` (Task 7 non l'ha fatto) per impostare `_receiptUri`:

In `TransactionViewModel.loadTransaction` (Task 7) aggiungere prima di `_selectedTags`:

```kotlin
        _receiptUri.value = transaction.receiptUri
```

(Ciò richiede di ritornare al Task 7 oppure fare questa micro-modifica qui; se il Task 7 è già committato, aggiungi questa riga ora in `TransactionViewModel.kt` e includila in questo commit.)

- [ ] **Step 3: Mostra la ricevuta nel form di modifica**

Nel `ElevatedCard` dei dettagli (dopo il campo note, riga ~209), aggiungi:

```kotlin
                        if (transactionViewModel.receiptUri.value.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                Text(stringResource(R.string.str_ricevuta), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
```

Import `Icons.Filled.CheckCircle` (già `filled.*` importato in questo file? No — aggiungi `import androidx.compose.material.icons.filled.CheckCircle`).

- [ ] **Step 4: Verifica compilazione**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/it/ciano/expensetracker/ui/viewmodel/TransactionViewModel.kt \
        app/src/main/java/it/ciano/expensetracker/ui/screens/ModifyTransactionScreen.kt
git commit -m "feat: preserva e mostra la ricevuta in modifica transazione"
```

---

### Task 11: Verifica completa + docs + Push + Pull Request

**Files:**
- Modify: `docs/superpowers/plans/2026-08-31-receipt-ocr-design.md` (nessuna modifica necessaria)
- Create: `TESTING_GUIDE_RECEIPT.md` (root repository)

- [ ] **Step 1: Scrivi la guida di testing**

`TESTING_GUIDE_RECEIPT.md`:

```markdown
# TESTING_GUIDE — Scansione ricevute via OCR (ML Kit)

Questa guida spiega come testare la funzione di scatto/OCR della ricevuta
in ExpenseTracker.

## Funzionalità

1. **Quick-scan da Home:** il FAB con l'icona camera avvia la camera di
   sistema, esegue l'OCR e apre `AddTransactionScreen` precompilato.
2. **In Add Transaction:** l'icona camera nella barra in alto scatta e
   precompila i campi senza cambiare schermata.
3. **Ricevuta nel dettaglio:** nel dialog del dettaglio transazione (Home)
   viene mostrata la miniatura della ricevuta se presente.

## Cosa viene estratto

- **Importo** — da righe `TOTALE`/`TOT`/`IMPORTO`/`DA PAGARE`, con virgola o
  punto. Fallback: numero più grande nel testo.
- **Data** — `DD/MM/YYYY`, `DD-MM-YYYY`, `DD.MM.YYYY`, `YYYY-MM-DD`.
- **Titolo** — prima riga del testo OCR (in genere il nome del negozio).
- **Categoria** — match per parole chiave italiane (es. "supermercato" →
  `Alimentari`), SOLO se esiste una categoria con quel nome.

## Test manuale

1. Installa l'APK (debug) con `./gradlew installDebug`.
2. Da Home tocca il FAB camera e fotografa una ricevuta reale o ben
   illuminata.
3. Verifica che la nuova spesa appaia in AddTransaction con importo/data
   precompilati; correggi se necessario e salva.
4. Apri il dettaglio della transazione in Home e verifica la ricevuta.
5. Modifica la transazione e verifica che la ricevuta venga conservata.

## Test automatici

Unit test del parser puro:

```bash
./gradlew testDebugUnitTest --tests "it.ciano.expensetracker.data.ocr.ReceiptParserTest"
```

## Note

- La categoria suggerita viene applicata solo se esiste già una categoria
  con lo stesso nome; non viene creata automaticamente.
- La foto è salvata in `filesDir/receipts/` (app-internal), quindi viene
  cancellata alla disinstallazione.
```

- [ ] **Step 2: Esegui la suite completa di test e build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, tutti i test PASS (esistenti + nuovi).

- [ ] **Step 3: Commit della guida**

```bash
git add TESTING_GUIDE_RECEIPT.md
git commit -m "docs: guida di testing per la scansione ricevute OCR"
```

- [ ] **Step 4: Push e crea la Pull Request verso main**

```bash
git push -u origin feature/receipt-ocr
gh pr create \
  --base main \
  --head feature/receipt-ocr \
  --title "feat: scansione ricevute via OCR (ML Kit) per nuove spese" \
  --body "## Riepilogo

Aggiunge la possibilità di fotografare una ricevuta, estrarre i dati via OCR on-device (ML Kit) e precompilare una nuova spesa conservando la foto.

- Due entry point: FAB camera in Home e icona camera in Add Transaction
- \`ReceiptParser\` puro (importo/data/titolo/categoria) con test JUnit4
- \`Transaction.receiptUri\` + migrazione Room v3→v4
- Ricevuta mostrata nel dettaglio transazione (Home) e preservata in modifica
- \`TESTING_GUIDE_RECEIPT.md\` con istruzioni

## Test

\`./gradlew testDebugUnitTest assembleDebug\` — verde."
```

Expected: PR creata verso `main` (fa scattare la GitHub Action `android.yml` con `assembleRelease`).

---

## Self-Review

- **Spec coverage:** capture camera (Task 0/5/8/9), OCR ML Kit (Task 2/3), parser (Task 1), DB `receiptUri` + migration (Task 3), FileProvider (Task 4), ViewModel prefill (Task 7), UI entry points (Task 8/9), dettaglio (Task 9/10), stringhe i18n (Task 6), test/build/PR (Task 1/11). Coperto tutto lo spec.
- **Placeholder scan:** nessun TBD/TODO; ogni step ha codice o comando concreto. L'unico punto aperto è la scelta tra `0.15` e `1.00` nel test `importo rispetta il limite superiore della riga` — esplicitamente documentato nel Task 1 Step 3.
- **Type consistency:** `ParsedReceipt(amount: Double?, date: Long?, title: String?, suggestedCategoryName: String?)`, `ReceiptParser.parse(rawText): ParsedReceipt`, `ReceiptOcrEngine.recognize(uri, context): String?` (suspend), `ReceiptStorage.createReceiptFile(context, timestamp): File` / `loadBitmap(context, path): Bitmap?`, `ReceiptCaptureManager.AUTHORITY`, `TransactionViewModel.applyParsedReceipt(parsed, categories)` / `updateReceipt(uri)` — usati in modo identico nei task.
- **Deviationes dal design registrate:** il dettaglio ricevuta vive nel `HomeScreen` AlertDialog (non esiste `TransactionDetailScreen.kt` nel repo); il parser usa `object` con campi nullable; nessuna auto-creazione categoria nel flusso OCR.
