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
