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
