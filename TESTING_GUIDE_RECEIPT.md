# TESTING_GUIDE — Scansione ricevute via OCR (ML Kit)

Questa guida spiega come testare la funzione di scatto/OCR della ricevuta
in ExpenseTracker.

## Funzionalità

1. **Quick-scan da Home:** il FAB con l'icona camera apre la schermata di
   cattura dedicata (`CameraCaptureScreen`, CameraX), esegue l'OCR al rientro
   e apre `AddTransactionScreen` precompilato.
2. **In Add Transaction:** l'icona camera nella barra in alto apre la stessa
   schermata di cattura e precompila i campi al rientro, senza perdere il
   form.
3. **Ricevuta nel dettaglio:** nel dialog del dettaglio transazione (Home)
   viene mostrata la miniatura della ricevuta se presente.

### Schermata di cattura (CameraX)

Al tocco della camera si apre una schermata dedicata (invece della camera di
sistema) perché su alcuni device (per esempio MIUI/Xiaomi) il contract
`TakePicture` salva file corrotti (immagine verde uniforme) senza contenuto.

- Alla prima apertura viene richiesto il permesso `CAMERA`; senza permesso è
  mostrato un messaggio con pulsante "Riprova".
- È presente un'**anteprima live** della fotocamera frontale/posteriore.
- Il pulsante di scatto cattura la foto; viene validata anti-immagine
  "uniforme/corrotta" prima di procedere.
- Dopo lo scatto appare l'**anteprima della foto** con i pulsanti
  "Riprova" (nuovo scatto) e "Usa foto" (conferma e rientro al form con OCR).

## Cosa viene estratto

- **Importo** — da righe `TOTALE`/`TOT`/`IMPORTO`/`DA PAGARE`, con virgola o
  punto. Fallback: numero più grande nel testo.
- **Data** — `DD/MM/YYYY`, `DD-MM-YYYY`, `DD.MM.YYYY`, `YYYY-MM-DD`.
- **Titolo** — prima riga del testo OCR (in genere il nome del negozio).
- **Categoria** — match per parole chiave italiane (es. "supermercato" →
  `Alimentari`), SOLO se esiste una categoria con quel nome.

## Test manuale

1. Installa l'APK (debug) con `./gradlew installDebug`.
2. Da Home tocca il FAB camera: concedi il permesso `CAMERA` se richiesto,
   inquadra una ricevuta reale o ben illuminata e scatta.
3. Verifica l'anteprima della foto; tocca "Usa foto".
4. Verifica che la nuova spesa appaia in AddTransaction con importo/data
   precompilati; correggi se necessario e salva.
5. Ripeti dall'icona camera dentro AddTransaction (deve precompilare senza
   cambiare schermata).
6. Apri il dettaglio della transazione in Home e verifica la ricevuta.
7. Modifica la transazione e verifica che la ricevuta venga conservata.

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
