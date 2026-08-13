# Design: Porta d'ingresso nativa Android via Intent (native-intent-ingestion)

Data: 2026-08-13
Stato: Approvato

## Obiettivo

Permettere a Kai9000 (o ad altre app esterne) di inserire nuove spese direttamente
in ExpenseTracker senza aprire l'app e con impatto batteria ~zero, tramite:

1. Un `BroadcastReceiver` dichiarato nel manifest (action `it.ciano.expensetracker.ADD_EXPENSE`).
2. Un'Activity trasparente con deep link URL scheme `expensetracker://add_expense`.

Package reale dell'app: **`it.ciano.expensetracker`** (non `com.onortiziano...`).

## Componenti

Nuovo package: `it.ciano.expensetracker.data.ingest`

### `IngestResult` (sealed class, pura)

```kotlin
sealed class IngestResult {
    data class Success(val transaction: Transaction, val categoryName: String) : IngestResult()
    data class Error(val message: String) : IngestResult()
}
```

### `IntentExpenseParser` (oggetto puro, zero dipendenze Android)

- `fun parse(params: Map<String, String?>): IngestResult`
- Parametri accettati:
  - `amount` — **obbligatorio**, `Double` o `String`. Accetta separatore decimale
    punto (`12.50`) o virgola (`12,50`). Deve essere `> 0`, altrimenti `Error`.
  - `category` — opzionale, default `"Varie"`.
  - `note` / `description` — alias, opzionale, default `""`.
  - `date` — opzionale, ISO-8601 (`yyyy-MM-dd`, `yyyy-MM-ddTHH:mm`, `yyyy-MM-ddTHH:mm:ss`)
    oppure epoch millis. Default: `System.currentTimeMillis()`.
- Mapping sul modello `Transaction`:
  - `title` = valore di `note`/`description`; se vuoto → nome categoria risolta.
  - `type` = `"EXPENSE"`.
  - `note` = `""`.
  - `categoryId` = risolto a runtime da `ExpenseIngestRepository` (nome → id).
  - `date` = epoch millis.
- Helper Android-dipendenti separati e minimi (da `Intent`/`Uri` a `Map<String,String?>`):
  - `extractExtras(intent: Intent): Map<String, String?>`
  - `extractQuery(uri: Uri): Map<String, String?>`

### `ExpenseIngestRepository` (suspend)

- `suspend fun insert(result: IngestResult.Success, dao/category access)`:
  1. Risolve o crea la categoria per nome (case-insensitive) come categoria
     principale (`parentCategoryId = null`). Se il nome non esiste → la crea.
     Se il parametro manca → default `"Varie"` (auto-creata al primo uso).
  2. Costruisce la `Transaction` con `categoryId` risolto.
  3. Inserisce via `TransactionRepository.insertTransaction(tx, emptySet())`.
- Logica di risoluzione del nome categoria come funzioni pure testabili.

### `ExpenseIngestReceiver` (BroadcastReceiver)

- Registrato nel manifest, `android:exported="true"`, intent-filter con action
  `it.ciano.expensetracker.ADD_EXPENSE`.
- `onReceive`: `goAsync()` + `CoroutineScope(Dispatchers.IO)` →
  `extractExtras(intent)` → `parse(...)` → in caso di `Success` inserisce via
  repository → `Toast.makeText(context, esito, LENGTH_SHORT).show()` →
  `pendingResult.finish()`.
- Errori: Toast `"Importo non valido"` / `"Parametri mancanti"`, nessun inserimento.

### `ExpenseIngestActivity` (trasparente)

- Registrata nel manifest, `android:exported="true"`, `launchMode="singleTop"`,
  `excludeFromRecents="true"`, tema trasparente/noActionBar (nessun flash visivo).
- Intent-filter: action `VIEW`/`SEND`, data scheme `expensetracker`, host `add_expense`.
- `onCreate`/`onNewIntent`: estrae params da extras **e/o** dalla query del deep link
  (`expensetracker://add_expense?amount=12.50&category=Pranzo`) →
  parse → insert → Toast → `finish()`.
- Al termine il task torna al chiamante / all'activity precedente.

## AndroidManifest.xml

- `<receiver android:name=".data.ingest.ExpenseIngestReceiver" android:exported="true">`
  con `<intent-filter>`: action `it.ciano.expensetracker.ADD_EXPENSE`.
- `<activity android:name=".data.ingest.ExpenseIngestActivity" ...>`
  con `<intent-filter>`: action `VIEW`/`SEND`, data scheme `expensetracker`,
  host `add_expense`. Tema trasparente dedicato in `res/values/themes.xml`.

## Data flow

```
altra app / am broadcast / am start
        │
        ├─ Receiver: extras → Map → parse → insert → Toast
        └─ Activity:  extras|query → Map → parse → insert → Toast → finish()
                        │
                        ▼
        ExpenseIngestRepository: resolveOrCreateCategory → Transaction → TransactionRepository
                        │
                        ▼
                   Room (transactions)
                        │
                        ▼
              Flow → StateFlow → Compose (aggiornamento automatico lista)
```

## Error handling

- `amount` mancante o non numerico o `<= 0` → `Error("Importo non valido")`, nessun inserimento.
- `date` malformata → fallback alla data corrente (nessun errore bloccante).
- Categoria mai bloccante: sempre risolta o auto-creata.
- Errori di inserimento DB → Toast di errore, nessun crash.

## Testing

- JUnit4 + `runBlocking`, stile esistente (nomi backtick in italiano).
- `IntentExpenseParserTest`:
  - dati completi (amount, category, note, date)
  - solo `amount` (default category/date/note)
  - amount malformati: testo, negativo, zero, doppia virgola, spazi
  - alias `description` → `note`
  - date ISO, epoch, malformate (fallback a data corrente)
  - default `"Varie"`
- `ExpenseIngestRepositoryTest`: logica pura di risoluzione nome categoria
  (case-insensitive, creazione se mancante).
- Verifica compilazione: `./gradlew compileReleaseKotlin` e `assembleDebug`.

## TESTING_GUIDE.md (root repository)

1. Esempi pratici:
   - `am broadcast -a it.ciano.expensetracker.ADD_EXPENSE -p it.ciano.expensetracker --es amount "12.50" --es category "Pranzo"`
   - `am start -a android.intent.action.VIEW -d "expensetracker://add_expense?amount=12.50&category=Pranzo"`
2. Tabella parametri (obbligatori/opzionali, alias, default).
3. Comportamento atteso in Room e UI Compose (auto-aggiornamento + Toast).
4. Note: Android 8+ richiede broadcast espliciti (`-p`/`-n`); Android 12+ il
   Toast in background può essere soppresso.

## Flusso Git

- Branch `feature/native-intent-ingestion` creato da `main`.
- TDD: test prima del codice di parsing.
- Commit incrementali, poi PR verso `main` (fa scattare la GitHub Action `assembleRelease`).
