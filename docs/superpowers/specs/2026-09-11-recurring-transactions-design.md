# Recurring Transactions - Design Spec

**Date**: 2026-09-11
**Status**: Approved
**Scope**: Feature addition — recurring transaction templates with automatic generation on app launch

---

## Overview

Add support for recurring transaction templates. Users define a template (title, amount, category, frequency, date range) and the app automatically generates real transactions when due, on each app launch.

Generated transactions are **independent** from the template — they are normal `Transaction` rows that can be edited or deleted without affecting the recurring definition.

---

## Data Model

### New Entity: `RecurringTransaction`

Table: `recurring_transactions`

| Column | Type | Notes |
|---|---|---|
| `id` | Int (PK, autoGenerate) | |
| `title` | String | Template name, e.g. "Affitto", "Netflix" |
| `amount` | Double | |
| `type` | String | `"EXPENSE"` or `"INCOME"` |
| `categoryId` | Int | FK → categories |
| `frequency` | String | `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `ANNUAL` |
| `startDate` | Long | Epoch millis — from when the recurrence is active |
| `endDate` | Long? | null = indefinite |
| `nextDueDate` | Long | When the next transaction should be generated |
| `note` | String | Default `""` |
| `isActive` | Boolean | true = active, false = paused |
| `lastGeneratedDate` | Long? | Last date a transaction was generated |

### New Join Table: `recurring_transaction_tags`

Same structure as `transaction_tags`:
- `recurringTransactionId` Int (FK → recurring_transactions, CASCADE)
- `tagId` Int (FK → tags, CASCADE)

### Unchanged

The `transactions` table is **not modified**. Generated transactions are regular `Transaction` rows.

---

## Generation Logic

**Trigger**: on app launch, in `HomeScreen` via `LaunchedEffect(Unit)`.

**Algorithm** (`RecurringTransactionGenerator`):

For each `RecurringTransaction` where `isActive = true` AND `nextDueDate <= today`:

1. Set `cursor = nextDueDate`
2. While `cursor <= today`:
   - Create a `Transaction` with the template's fields and `date = cursor` (backdated to the due date, handling missed periods)
   - Copy associated tags from `recurring_transaction_tags` to `transaction_tags`
   - Advance `cursor` by the frequency interval
3. If `endDate != null` and `cursor > endDate`: set `isActive = false`
4. Set `nextDueDate = cursor` (the first future occurrence)
5. Set `lastGeneratedDate` to the date of the last transaction generated in step 2

The `cursor` loop guarantees no duplicates: `nextDueDate` only ever moves forward, and each generated transaction is dated at its due date.

**Date calculation per frequency**:
- `DAILY`: nextDueDate + 1 day
- `WEEKLY`: nextDueDate + 7 days
- `BIWEEKLY`: nextDueDate + 14 days
- `MONTHLY`: same day next month (handles 28/29/30/31 edge cases)
- `ANNUAL`: same day next year

**Thread**: `Dispatchers.IO`, does not block UI.

---

## UI

### Navigation

- New route `RECURRING_TRANSACTIONS` in `Routes` object
- New `NavigationDrawerItem` in HomeScreen drawer, between Home and Cronologia
- Icon: `Icons.Filled.Repeat` (respecting `mainViewModel.getIcon()` pattern)

### Screen: `RecurringTransactionsScreen`

**TopAppBar**: "Transazioni Ricorrenti" with back button

**Body**: `LazyColumn` with cards for each recurring transaction:
- Title, formatted amount, type (green/red color)
- Frequency label (translated)
- Next due date
- Active/Paused switch (`Switch` composable)
- Swipe-to-delete with confirmation
- Single click → detail dialog
- Long click → edit dialog

**Empty state**: "Nessuna transazione ricorrente" centered text

**FAB**: "+" to add new recurring transaction → opens Add dialog

### Dialog: Add/Edit Recurring Transaction

`AddRecurringTransactionDialog` (AlertDialog, same pattern as category dialogs):

Fields:
- Title (`OutlinedTextField`)
- Amount (`OutlinedTextField`, keyboard type Decimal)
- Type: Uscita/Entrata (`FilterChip` pair)
- Category: main + subcategory dropdowns (same as AddTransactionScreen)
- Frequency: dropdown (`ExposedDropdownMenuBox`) — Giornaliera/Settimanale/Bi-settimanale/Mensile/Annuale
- Start date: DatePicker
- End date: optional toggle + DatePicker
- Note: optional `OutlinedTextField`
- Tags: `FlowRow` with `FilterChip` (same as AddTransactionScreen)

Validation: title required, amount > 0, category selected, start date set.

---

## Architecture

### New Files

| File | Layer |
|---|---|
| `data/model/RecurringTransaction.kt` | Entity |
| `data/model/RecurringTransactionWithTags.kt` | UI data class |
| `data/dao/RecurringTransactionDao.kt` | DAO |
| `data/dao/RecurringTransactionTagDao.kt` | DAO (join) |
| `data/repository/RecurringTransactionRepository.kt` | Repository |
| `data/generation/RecurringTransactionGenerator.kt` | Generation logic (pure, testable) |
| `ui/viewmodel/RecurringTransactionViewModel.kt` | ViewModel |
| `ui/screens/RecurringTransactionsScreen.kt` | Screen |

### Modified Files

| File | Change |
|---|---|
| `data/AppDatabase.kt` | Add entities, DAOs, abstract methods, migration v4→v5 |
| `ui/viewmodel/ViewModelFactory.kt` | Register RecurringTransactionViewModel |
| `ui/screens/AppNavigation.kt` | Add route + composable |
| `ui/screens/HomeScreen.kt` | Call generator on launch, add drawer item |
| `res/values/strings.xml` | New Italian strings |
| `res/values-en/strings.xml` | New English strings |

### DB Migration v4 → v5

```sql
CREATE TABLE recurring_transactions (
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
);

CREATE TABLE recurring_transaction_tags (
    recurringTransactionId INTEGER NOT NULL,
    tagId INTEGER NOT NULL,
    PRIMARY KEY(recurringTransactionId, tagId),
    FOREIGN KEY(recurringTransactionId) REFERENCES recurring_transactions(id) ON DELETE CASCADE,
    FOREIGN KEY(tagId) REFERENCES tags(tagId) ON DELETE CASCADE
);
```

---

## i18n

New string keys (Italian / English):

| Key | Italian | English |
|---|---|---|
| `str_transazioni_ricorrenti` | Transazioni Ricorrenti | Recurring Transactions |
| `str_nessuna_ricorrente` | Nessuna transazione ricorrente | No recurring transactions |
| `str_nuova_ricorrente` | Nuova Transazione Ricorrente | New Recurring Transaction |
| `str_modifica_ricorrente` | Modifica Transazione Ricorrente | Edit Recurring Transaction |
| `str_frequenza` | Frequenza | Frequency |
| `str_giornaliera` | Giornaliera | Daily |
| `str_settimanale` | Settimanale | Weekly |
| `str_bisettimanale` | Bi-settimanale | Bi-weekly |
| `str_mensile` | Mensile | Monthly |
| `str_annuale` | Annuale | Annual |
| `str_data_inizio` | Data Inizio | Start Date |
| `str_data_fine` | Data Fine (opzionale) | End Date (optional) |
| `str_attiva` | Attiva | Active |
| `str_in_pausa` | In Pausa | Paused |
| `str_prossima_generazione` | Prossima generazione: %1$s | Next generation: %1$s |
| `str_conferma_elimina_ricorrente` | Eliminare questa transazione ricorrente? | Delete this recurring transaction? |
| `str_ricorrente_salvata` | Transazione ricorrente salvata | Recurring transaction saved |
| `str_frequenza_obbligatoria` | Selezionare una frequenza | Select a frequency |

---

## Testing

- Unit tests for `RecurringTransactionGenerator` (date calculation, missed period handling, endDate boundary)
- Unit tests for `RecurringTransactionDao` queries
- UI manual test: create recurring, relaunch app, verify transaction generated
- Edge cases: today == startDate, app unused for multiple periods, endDate exactly on a due date

---

## Out of Scope

- Background generation (WorkManager) — future enhancement
- Notification reminders for upcoming due dates
- Visual calendar preview
- Multi-currency support for recurring templates
