# Receipt OCR Feature Design

## Overview

Add the ability to scan a receipt photo via the device camera, extract structured data (amount, date, store name, category) using on-device OCR, and pre-fill a new expense entry. The receipt image is stored and viewable in the transaction detail screen.

## Requirements

- **Two entry points:** (A) FAB / button on `HomeScreen` for quick scan, (B) camera icon on `AddTransactionScreen` to scan while editing
- **OCR engine:** ML Kit Text Recognition v1 (on-device, no network)
- **Extracted fields:** amount, date, title (store name), suggested category (keyword matching)
- **Receipt storage:** image saved to app-internal `filesDir/receipts/`, URI stored in `Transaction.receiptUri`
- **Receipt viewing:** thumbnail in `TransactionDetailScreen` (not inline in history), tap to view full-size
- **Localization:** Italian (default) + English

## Architecture

### Data Flow

```
[FAB on Home]  or  [Button on AddTransactionScreen]
        │                        │
        ▼                        ▼
  System Camera (TakePicture contract)
        │
        ▼
  Receipt saved to filesDir/receipts/
        │
        ▼
  ML Kit Text Recognition → raw text
        │
        ▼
  ReceiptParser → ParsedReceipt(amount, date, title, suggestedCategory)
        │
        ▼
  Navigate to AddTransactionScreen (pre-filled)  ←──  OR  fill current form
        │
        ▼
  User reviews/edits → saves Transaction (with receiptUri)
```

### Approach

**System Camera Intent + ML Kit** — use Android's `TakePicture` ActivityResultContract to launch the device camera app. No CameraX dependency. The OCR/parsing layer is decoupled from capture, making it easy to swap to CameraX later.

## Data Model Changes

### Transaction Entity

```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val type: String,
    val categoryId: Int,
    val date: Long,
    val note: String = "",
    val receiptUri: String = ""   // NEW: path to receipt image, empty = no receipt
)
```

### Room Migration v3 → v4

```sql
ALTER TABLE transactions ADD COLUMN receiptUri TEXT NOT NULL DEFAULT ''
```

Registered in `AppDatabase` alongside existing migrations. `fallbackToDestructiveMigration()` retained as safety net.

### Image Storage

- Location: `filesDir/receipts/{timestamp}.jpg`
- Naming: timestamp-based to avoid collisions
- No storage permissions needed (app-internal)
- Auto-cleaned on uninstall

## Receipt Parsing

### ParsedReceipt Data Class

```kotlin
data class ParsedReceipt(
    val amount: Double?,
    val date: Long?,
    val title: String?,
    val suggestedCategoryName: String?
)
```

### Parsing Strategy

Applied to ML Kit raw text output, line by line:

1. **Amount** — regex for Italian receipt keywords (`TOTALE`, `TOT`, `IMPORTO`, `DA PAGARE`) followed by a number with `,` or `.` decimal. Fallback: largest number in text.
2. **Date** — regex for `DD/MM/YYYY`, `DD-MM-YYYY`, `DD.MM.YYYY`. Fallback: none.
3. **Title** — first non-empty line of receipt (usually store name), truncated to 50 chars.
4. **Category** — keyword matching against a hardcoded map of common Italian store types to existing category names.

### Category Keyword Map

```kotlin
private val categoryKeywords = mapOf(
    "Alimentari" to listOf("supermercato", "alimentari", "coop", "conad", "lidl", "eurospin", "esselunga"),
    "Ristorazione" to listOf("ristorante", "bar", "pizzeria", "gelateria", "caffè", "mcdonald"),
    "Trasporti" to listOf("trenitalia", "italia", "bus", "taxi", "autostrade", "parking"),
    "Salute" to listOf("farmacia", "ospedale", "clinica", "medico"),
)
```

Map lives in a companion object for easy extension. No user-configurable mapping for now (YAGNI).

## Camera & Permission Handling

### ReceiptCaptureManager

Wraps Android's `TakePicture` ActivityResultContract:

```kotlin
class ReceiptCaptureManager(
    private val activity: ComponentActivity,
    private val onResult: (Boolean, Uri?) -> Unit
) {
    private var photoUri: Uri? = null

    private val takePicture = activity.registerForActivityResult(
        TakePicture()
    ) { success -> onResult(success, photoUri) }

    fun capture() {
        photoUri = createImageUri()
        takePicture.launch(photoUri!!)
    }

    private fun createImageUri(): Uri { /* FileProvider URI */ }
}
```

- No explicit CAMERA permission request needed — `TakePicture` contract handles it
- `FileProvider` declared in manifest, paths configured in `res/xml/file_paths.xml`

## UI Changes

### HomeScreen

- Add a `SmallFloatingActionButton` with camera icon for quick receipt scan
- Existing FAB remains for manual "Add transaction"

### AddTransactionScreen

- Add a camera icon button in the top app bar
- Tapping launches camera → OCR → fills title/amount/date/category fields
- Receipt URI held in `TransactionViewModel`, saved with the transaction

### TransactionDetail (HomeScreen AlertDialog)

- The transaction detail is an `AlertDialog` inside `HomeScreen.kt` (there is no separate `TransactionDetailScreen.kt` file)
- If `receiptUri` is not empty, show a thumbnail at the bottom of this detail dialog
- Also shown (read-only confirmation) in `AddTransactionScreen` and `ModifyTransactionScreen`

### String Resources

| Key | Italian (default) | English |
|---|---|---|
| `scan_receipt` | Scatta ricevuta | Scan receipt |
| `receipt` | Ricevuta | Receipt |
| `no_receipt_found` | Nessuna ricevuta trovata | No receipt found |
| `ocr_processing` | Elaborazione ricevuta... | Processing receipt... |
| `ocr_failed` | Errore lettura ricevuta | Receipt reading error |
| `view_receipt` | Visualizza ricevuta | View receipt |

## Dependencies

Single new dependency in `app/build.gradle.kts`:

```kotlin
implementation("com.google.mlkit:text-recognition:16.0.0")
```

No CameraX, no Coil/Glide — `BitmapFactory` used for thumbnail rendering.

## Files Touched

| File | Change |
|---|---|
| `app/build.gradle.kts` | Add ML Kit dependency |
| `AndroidManifest.xml` | Add FileProvider |
| `res/xml/file_paths.xml` | NEW — FileProvider paths |
| `Transaction.kt` | Add `receiptUri` field |
| `AppDatabase.kt` | Migration v3→v4, bump version |
| `ReceiptParser.kt` | NEW — text parsing logic |
| `ReceiptCaptureManager.kt` | NEW — camera intent wrapper |
| `TransactionViewModel.kt` | Add OCR state + receipt URI handling |
| `AddTransactionScreen.kt` | Add scan button, pre-fill from OCR |
| `HomeScreen.kt` | Add scan receipt entry point, show receipt in detail dialog |
| `ModifyTransactionScreen.kt` | Preserve and show receipt on edit |
| `strings.xml` (it, en) | New string resources |

## Testing

- **Unit tests:** `ReceiptParserTest.kt` — test parsing logic with sample receipt texts (no Android dependencies, pure Kotlin)
- **Manual testing:** camera capture flow, OCR accuracy on real Italian receipts, category matching, image storage/retrieval
