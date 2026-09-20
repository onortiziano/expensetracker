# CSV/OFX Transaction Import — Design Spec

**Date**: 2026-09-18
**Status**: Approved
**Scope**: New feature — import financial transactions from CSV and OFX (SGML + XML) files via Android Storage Access Framework (SAF), with a dedicated preview UI, optional deduplication, per-row category selection, and atomic bulk insertion into Room.

---

## Overview

Allow users to import transactions exported from their bank or a spreadsheet into Expense Tracker. The flow is entirely manual (no background sync) and respects the app's "local-first, no-cloud" privacy model.

Supported formats:

| Format | Variants | Notes |
|--------|----------|-------|
| CSV | Any separator/dialect; columns auto-detected by header names | Supports standard personal-finance exports (date, description, amount, optional category). Separator decimal from app preferences. |
| OFX (Open Financial Exchange) | Both SGML (legacy, pre-2000) and XML (v1.x / v2.x) | `<STMTTRN>` elements parsed via `XmlPullParser` after SGML→XML normalisation for legacy files. |

No Google Wallet API, Plaid, or any cloud-based bank API is used — only manual file selection.

---

## Data Model

No new Room entities or tables. Imported transactions become regular `Transaction` rows in the existing `transactions` table.

New pure-logic data classes (no Room annotations):

### `ParsedImportTransaction`

| Field | Type | Notes |
|-------|------|-------|
| `title` | String | Merchant/description. From OFX `<NAME>` / `<MEMO>`, or CSV description column. |
| `amount` | Double | Positive value. Sign is already resolved: expense-type rows have `type = EXPENSE`. |
| `type` | String | `"EXPENSE"` or `"INCOME"` — determined by sign or OFX `<TRNTYPE>`. |
| `date` | Long | Epoch millis of the transaction date. |
| `categoryName` | String? | Raw category name from the file; `null` if absent. The user maps this in the preview UI. |
| `note` | String | Additional note/memo; default `""`. |
| `sourceLine` | Int | 1-based line number in the file; used in error messages. |

### `ImportOutcome`

| Field | Type |
|-------|------|
| `imported` | Int |
| `skippedDuplicate` | Int |
| `errors` | List<ImportError> |

### `ImportError`

| Field | Type |
|-------|------|
| `line` | Int |
| `reason` | String |

---

## Parsing Logic

All parsers are **pure JVM functions** — no `android.*` imports. This makes them unit-testable with JUnit4 on the JVM.

### CSV (`CsvParser`)

Hand-written RFC 4180-compliant parser (handles quoted fields, embedded newlines, escaped quotes). Zero external dependencies.

**Header detection:**
- Read first non-empty line; split by detected separator (comma, semicolon, or tab — first separator that gives ≥3 columns wins).
- Normalize column names to lowercase (`trim()` + strip accents).
- Recognize these canonical names for each column role:
  - **date**: `data`, `date`, `fecha`, `transact.*date`, `booking.*date`, `value.*date`
  - **description**: `descrizione`, `description`, `memo`, `note`, `payee`, `merchant`, `name`, `concepto`, `detail`
  - **amount**: `importo`, `amount`, `debit`, `credit`, `trnamt`, `value`
  - **type** (optional): `type`, `tipo`, `trntype` — contains `debit`/`credit` or `expense`/`income`
  - **category** (optional): `categoria`, `category`, `cat`

If a required column (date, description, amount) is not found → `ImportError(line = 0, reason = "Colonne obbligatorie mancanti")`.

**Date parsing** (auto-detect, checked in order):
1. ISO-8601: `yyyy-MM-dd`
2. European: `dd/MM/yyyy` or `dd-MM-yyyy`
3. American: `MM/dd/yyyy`
4. Plain: `yyyyMMdd`
5. If none match → `ImportError` for that line.

**Amount parsing:**
- If the field contains a currency symbol, strip it.
- If the detected decimal separator (from app preferences) is `,`, replace `.` (thousands) then `,` → `.`. Vice-versa.
- Parse as `Double`. If invalid → `ImportError` for that line.

**Type inference:**
- If `type` column present: map `debit`/`expense`/`uscita` → `EXPENSE`; `credit`/`income`/`entrata` → `INCOME`.
- Otherwise: negative amount → `EXPENSE` (amount stored as positive); positive → `INCOME`.

### OFX (`OfxParser`)

**SGML normalisation** (`OfxSgmlNormalizer`, pure function):
- Reads the raw OFX byte stream.
- Detects OFX version header (`OFXHEADER:100` vs `OFX` vs no header → SGML).
- If SGML-style (no `<?xml` declaration, attributes in `TAG="value"` form):
  - Wraps content in `<OFX>` root if missing.
  - Converts SGML attributes `TAG="VALUE"` to `<TAG>VALUE</TAG>` child elements.
  - Self-closes empty tags `<TAG/>`.
- Output: a byte stream that is valid XML parseable by `XmlPullParser`.

**XML parsing** (`XmlPullParser`):
- Walk through elements; when `<STMTTRN>` is entered, read child elements:
  - `<TRNTYPE>`: `DEBIT`/`CREDIT`/`POS`/`DEP`/`INT`/`FEE` → maps to `EXPENSE`/`INCOME` per standard mapping.
  - `<DTPOSTED>`: format `YYYYMMDD` (possibly with `HH:MM:SS.000` suffix) → epoch millis.
  - `<TRNAMT>`: amount string; negative = expense.
  - `<NAME>`: merchant/description (primary).
  - `<MEMO>`: note (secondary, appended to note field).
  - `<CATEGORY>`: category name if present.
- Multiple `<STMTTRN>` → multiple `ParsedImportTransaction`.

---

## Preview UI

### New Route & Screen

- Route: `IMPORT_TRANSACTIONS` in `Routes` object.
- Screen: `ImportTransactionsScreen` (same scaffold pattern as `RecurringTransactionsScreen`).
- Drawer entry: new `NavigationDrawerItem` between "Cronologia" and "Impostazioni" (icon: `Icons.Filled.FileUpload` via `mainViewModel.getIcon`).

### Screen Layout

**Phase 1 — File selection:**
- Central `OutlinedButton` "Seleziona file CSV/OFX" → `ActivityResultContracts.OpenDocument` with MIME types `text/*, application/*`.
- After selection: parse on `Dispatchers.IO`; show a `CircularProgressIndicator` during parsing.
- On parse error: snackbar with the error message; no crash.

**Phase 2 — Preview (after parse):**
- `LazyColumn` of `ParsedImportTransaction` cards showing: date, title, amount, inferred type (green/red), and current category assignment.
- **Toggle bar at top**: "Salta duplicati" (`Switch` composable) — ON by default.
- **Category per row**: each card has a category dropdown (same pattern as the recurring dialog: `Box { OutlinedTextField; Box(matchParentSize().clickable); DropdownMenu(...) }`). The dropdown lists existing categories plus "+ Aggiungi Nuova" (same inline creation as in the recurring dialog). If `categoryName` from the file matched an existing category by name, it is pre-selected.
- **Batch category action**: a chip row above the list showing all distinct categories from the file; tapping one applies that category to all rows with that same `categoryName` (or all unmapped rows).
- **Error rows**: rows that failed parsing appear at the bottom of the list with a warning icon and the error reason, visually distinct (grey background).

**Phase 3 — Import:**
- Bottom bar with two buttons:
  - "Annulla" → discard, return to home.
  - "Importa N transazioni" → disabled if no rows with valid data; calls `TransactionRepository.bulkInsert()`.

**Phase 4 — Result:**
- Dialog M3: "Importate: X · Saltate (duplicati): Y · Errori: Z" with a detail expandable list of errors.
- "Chiudi" → returns to home. A snackbar "N transazioni importate" confirms.

---

## Deduplication

When the "Salta duplicati" toggle is ON, before inserting each `Transaction` the repository checks:

```kotlin
// New query in TransactionDao
@Query(
    "SELECT COUNT(*) FROM transactions WHERE title = :title AND amount = :amount AND date = :date AND type = :type"
)
suspend fun countExactMatch(title: String, amount: Double, date: Long, type: String): Int
```

If `countExactMatch ≥ 1`, the row is skipped and counted in `skippedDuplicate`.

When the toggle is OFF, every row is inserted unconditionally (no query cost).

---

## Bulk Insertion

New method in `TransactionRepository`:

```kotlin
suspend fun bulkInsert(
    transactions: List<ParsedImportTransaction>,
    tagIds: Set<Int> = emptySet(),
    skipDuplicates: Boolean
): ImportOutcome
```

Implementation:
- Wrapped in `database.withTransaction { ... }` for atomicity (all-or-nothing).
- For each `ParsedImportTransaction`:
  1. If `skipDuplicates` and `countExactMatch(...) > 0` → increment `skippedDuplicate`, continue.
  2. Insert `Transaction` via `transactionDao.insertTransaction(...)`.
  3. If `tagIds` non-empty → insert `TransactionTag` rows.
  4. On `RoomException` (e.g. constraint violation) → add to `errors` list, continue.
- Return `ImportOutcome` with counts and error list.

---

## Navigation & Integration

### `AppNavigation.kt`

```kotlin
const val IMPORT_TRANSACTIONS = "import_transactions"

composable(Routes.IMPORT_TRANSACTIONS) {
    ImportTransactionsScreen(navController)
}
```

### `HomeScreen.kt` — Drawer

New `NavigationDrawerItem` after Cronologia:

```
Icon: mainViewModel.getIcon(Icons.Filled.FileUpload, ...)
Label: str_importa_transazioni
onClick: navigate(IMPORT_TRANSACTIONS)
```

### `ViewModelFactory.kt`

No new ViewModel class — the import logic is orchestrated by `ImportTransactionsViewModel` (new file, registered in `ViewModelFactory`).

---

## Architecture — New Files

| File | Layer |
|------|-------|
| `data/import/CsvParser.kt` | Pure CSV parser |
| `data/import/OfxSgmlNormalizer.kt` | SGML→XML normaliser (pure) |
| `data/import/OfxParser.kt` | OFX XML parser (uses `XmlPullParser`) |
| `data/import/TransactionImportParser.kt` | Facade: Uri → `List<ParsedImportTransaction>` |
| `data/import/ParsedImportTransaction.kt` | Data classes (`ParsedImportTransaction`, `ImportOutcome`, `ImportError`) |
| `ui/viewmodel/ImportTransactionsViewModel.kt` | ViewModel (StateFlow preview, orchestration) |
| `ui/screens/ImportTransactionsScreen.kt` | Screen composable |

## Modified Files

| File | Change |
|------|--------|
| `data/dao/TransactionDao.kt` | Add `countExactMatch` query |
| `data/repository/TransactionRepository.kt` | Add `bulkInsert` method |
| `ui/viewmodel/ViewModelFactory.kt` | Register `ImportTransactionsViewModel` |
| `ui/screens/AppNavigation.kt` | Add route `IMPORT_TRANSACTIONS` + composable |
| `ui/screens/HomeScreen.kt` | Add drawer item |
| `res/values/strings.xml` | New Italian strings |
| `res/values-en/strings.xml` | New English strings |

No `AppDatabase` changes (no new entities, no migration).

---

## i18n

New string keys:

| Key | Italian | English |
|-----|---------|---------|
| `str_importa_transazioni` | Importa Transazioni | Import Transactions |
| `str_seleziona_file` | Seleziona file CSV/OFX | Select CSV/OFX file |
| `str_nessun_file` | Nessun file selezionato | No file selected |
| `str_anteprima_import` | Anteprima Import | Import Preview |
| `str_salta_duplicati` | Salta duplicati | Skip duplicates |
| `str_categoria_riga` | Categoria | Category |
| `str_importa_n` | Importa %1$d transazioni | Import %1$d transactions |
| `str_errore_formato` | Formato file non riconosciuto | Unrecognized file format |
| `str_colonne_mancanti` | Colonne obbligatorie mancanti | Required columns missing |
| `str_data_non_valida` | Data non valida | Invalid date |
| `str_importo_non_valido` | Importo non valido | Invalid amount |
| `str_risultato_import` | Importate: %1$d · Saltate: %2$d · Errori: %3$d | Imported: %1$d · Skipped: %2$d · Errors: %3$d |
| `str_dettaglio_errori` | Dettaglio errori | Error details |
| `str_importazione_completata` | %1$d transazioni importate | %1$d transactions imported |
| `str_errore_generico` | Errore durante l'importazione | Error during import |
| `str_nessuna_categoria` | Nessuna categoria | No category |
| `str_errore_riga` | Riga %1$d: %2$s | Row %1$d: %2$s |
| `str_conferma_esci_import` | Uscire senza importare? Le modifiche andranno perse. | Exit without importing? Changes will be lost. |

---

## Testing (JUnit4 JVM — no Android deps in parsers)

- **CsvParserTest**:
  - Parse semicolon-separated European CSV.
  - Parse comma-separated with quoted fields containing commas.
  - Parse tab-separated with multiline quoted fields.
  - Header detection with various column name variants.
  - Amount parsing with `,` and `.` separators, currency symbols.
  - Date parsing: ISO-8601, European, American, yyyyMMdd.
  - Missing required column → error.
  - Empty file → empty list.

- **OfxSgmlNormalizerTest**:
  - SGML with `TAG="value"` attributes → valid XML output.
  - Already-XML OFX → pass-through unchanged.
  - Missing `<OFX>` root → wrapped correctly.

- **OfxParserTest**:
  - Parse `<STMTTRN>` with DEBIT type → `EXPENSE`.
  - Parse `<STMTTRN>` with CREDIT type → `INCOME`.
  - `<DTPOSTED>` with and without time suffix.
  - `<CATEGORY>` present and absent.
  - Multiple `<STMTTRN>` elements.

- **TransactionImportParserTest** (integration of CsvParser + OfxParser):
  - End-to-end: file bytes → `List<ParsedImportTransaction>` with correct types, dates, amounts.

- **Dedup test** (in ViewModel / repository test):
  - `bulkInsert` with `skipDuplicates = true` and an exact match already in DB → row skipped, `skippedDuplicate` incremented.

---

## Out of Scope

- Google Wallet / bank API integration (out of policy).
- Multi-currency conversion (single currency, same as the rest of the app).
- Automatic scheduled import (no WorkManager; import is always manual).
- Column mapping UI (auto-detection is sufficient; manual mapping is future work).
- PDF or image file import (OCR path already exists for receipts).
