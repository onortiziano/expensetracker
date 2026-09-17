# Stato sessione — ExpenseTracker (feature ricorrenze)

Data: 16/09/2026 — aggiornato il 17/09/2026
Branch: `feature/recurring-transactions`
Lingua conversazione: italiano

## Cosa è successo oggi (17/09)

Verifica manuale su device dell'utente: il fix del 16/09 (menu categoria/frequenza) FUNZIONA
("aggiungendo la categoria la transazione ricorrente si può salvare"). L'utente ha segnalato 2 nuovi bug:
1. La categoria deve poter essere AGGIUNTA durante la selezione (come in una normale transazione) —
   nel dialogo ricorrenza non c'era l'opzione "Aggiungi Nuova".
2. Il separatore decimale scelto non veniva rispettato nel dialogo ricorrenza.

Fix applicato e COMMITTATO come `9ee6201` (`fix: categoria inline e separatore decimale nel dialogo ricorrenze`):
- `RecurringTransactionsScreen.kt`: voce "+ Aggiungi Nuova" nel menu categoria + `AddCategoryDialog`
  inline (nome + budget opzionale), riusa `str_aggiungi_nuova`/`str_nuova_categoria`/ecc. già esistenti.
  Alla creazione la nuova categoria viene selezionata automaticamente (`vm.updateCategory(newId)`).
- `ModifyTransactionHelpers.kt`: nuovi helper puri `parseAmountText` e `formatAmountForEdit`
  (separator-aware) con test in `AmountParsingTest.kt` (TDD rosso→verde).
- `RecurringTransactionsScreen.kt` (Save button): `enabled` ora usa `parseAmountText(amount, separator)`
  → con separatore sbagliato/multiplo il bottone resta DISABILITATO (richiesta esplicita dell'utente).
- `RecurringTransactionViewModel.kt`: `save()` usa `parseAmountText` con `UserPreferences.getDecimalSeparator()`
  (non più `replaceFirst(',', '.')` hardcoded); `startEdit()` precompila l'importo con `formatAmountForEdit`.

Verifiche: `compileDebugKotlin`, `testDebugUnitTest` (tutti PASS, inclusi i 9 nuovi test), `assembleDebug`
→ APK `app/build/outputs/apk/debug/app-debug.apk` (17/09 03:00, ~60 MB). `git fsck --no-dangling` pulito.

## Cosa è successo oggi (17/09) — SECONDA PARTE: code review PR #15

L'utente in locale: "verifichiamola" → PR #15 (https://github.com/onortiziano/expensetracker/pull/15),
skill `requesting-code-review`, code reviewer subagent auto-invocato. Esito: 1 Critical, 3 Important, 1 Minor.

### Issue verificate (verificare sul codice prima di implementare — skill receiving-code-review)

- **C1 (CRITICAL — confermata vera)**: `rescheduleReminders()` leggeva `recurringWithTags.value`
  (StateFlow `WhileSubscribed(5000)` con initial `emptyList()`). All'avvio da `HomeScreen` nessuno
  colleziona il flow → a freddo `rescheduleAll([])` non programmava nessun reminder.
  FIX: legge il DB direttamente (`repository.getAllRecurringWithTags().first().map{it.recurring}`),
  stesso pattern già usato dal boot receiver.
- **I1**: il boot receiver usava `CoroutineScope(Dispatchers.IO).launch` senza `goAsync()`
  → rischio kill del processo. FIX: `goAsync()` + `pendingResult.finish()` in `finally`.
- **I2**: "Data Fine" attivata senza data scelta → `save()` salvava `endDate = 0` (guard `!= 0L`)
  → template zombie attivo per sempre. FIX: `save()` rifiuta `endDateEnabled && endDate == 0L`,
  e il bottone Salva viene disabilitato (`!endDateEnabled || endDate != 0L`).
- **I3 (TDD rosso→verde)**: il generatore non disattivava template con `endDate < nextDueDate`.
  FIX: branch esplicito in `RecurringTransactionGenerator.generate()` che lo disattiva + 2 test
  nuovi (scaduto viene disattivato; futura con endDate null non toccata).
- **M1**: modificare un template in pausa forzava `isActive = true`. FIX: preserva `existing.isActive`.

Verifiche POST-fix: `testDebugUnitTest` (28 PASS), `assembleDebug`, `lintDebug` (solo 3 errori
PRE-ESISTENTI in AddTransactionScreen/ModifyTransactionScreen/AndroidManifest, non toccati).

Commit `02094cf` ("fix: code review PR #15") + push. CI GitHub: re-run in corso, il precedente su
PR era SUCCESS (dopo fix tastiera). Per il merge servono i secrets keystore o lì CI fallisce.

## Cosa è successo oggi (17/09) — TERZA PARTE: test utente + merge PR #15

1. L'utente ha testato su device il nuovo APK (installato manualmente, NO adb): confermati M1
   (pausa → modifica → resta in pausa) e I2 (Data Fine attiva senza data → Salva disabilitato).
   Rinuncia alle verifiche con adb (non installato) e alla modifica dell'orologio.
2. Test notifica DELEGATO al giorno della scadenza: l'utente ha creato una DAILY con inizio domani
   → la notifica DEVE arrivare domani alle 09:00 (verifica su device del fix C1).
3. Merge PR #15 eseguito con `gh pr merge --merge --delete-branch` → commit `33a6fc3`
   ("Merge pull request #15"). NOTA: il primo push del merge ha dato errore objproxy locale
   ma il merge su GitHub era già riuscito; ri-sync di main con `export GIT_OBJECT_DIRECTORY=/tmp/gitobjtest && git fetch && git merge --ff-only + materialize`.
4. CI su main (`33a6fc3`): **completed/success**; la release automatica ha creato
   **Release v50** (17/09 05:08) con `app-release.apk` firmato (stesso codice della debug
   installata dall'utente; il debug APK locale è della release installata).
5. Branch `feature/recurring-transactions` rimosso (remote + tracking locale prune).

### Dopo questo round

- Unica attività aperta: **conferma notifica domani 09:00** su device (debug APK già installato
  = stesso codice di v50). Se arriva → feature ricorrenze CHIUSA al 100%.

## Cosa è successo ieri (16/09)

Implementazione ricorrenze COMPLETATA (11 task del piano), testata e committata nella sessione precedente.
Verifica manuale su device dell'utente ha evidenziato un BUG nella UI del dialogo "nuova ricorrenza":
- La selezione "Categoria principale" non si apriva (`ExposedDropdownMenuBox` non apriva il menu).
- Il tasto "Salva" restava disabilitato (conseguenza: `categoryId == 0`).
- Il comportamento era identico con data inizio prima/dopo oggi.
- L'utente ha fatto un'installazione pulita (disinstallato + reinstallato) → **DB senza categorie**.

## Diagnosi del bug (systematic-debugging)

- Cause investigate: (1) popup/meccanismo `menuAnchor()`; (2) mancanza categorie nel DB.
- Root cause UI: il dialogo era l'UNICO posto nell'app con `ExposedDropdownMenuBox`+`menuAnchor()` dentro un contenuto
  scrollabile (`verticalScroll`) dentro un `AlertDialog`. Il tap non apriva il menu. Non riproducibile senza device.
- Root cause dati: l'app NON fa seed di categorie nel DB (nessuna INSERT nelle migration); dopo installazione pulita
  le categorie sono 0 ovunque. Il dialogo ricorrenza (a differenza di AddTransaction/Modify) non offre creazione inline,
  e senza categorie non si può salvare (guard `categoryId != 0`).

## Fix applicato — NON COMMITTATO, NON VERIFICATO su device

File modificati (3, in working tree):
- `app/src/main/java/it/ciano/expensetracker/ui/screens/RecurringTransactionsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Contenuto del fix:
1. CATEGORIA e FREQUENZA: sostituito `ExposedDropdownMenuBox` + `menuAnchor()` con la struttura già PROVATA
   nel dialogo (stesso meccanismo dei campi data): `Box { OutlinedTextField; Box(matchParentSize().clickable { expanded = true }); DropdownMenu(...) }`.
   Rimossi quindi `ExposedDropdownMenuBox`/`ExposedDropdownMenu`/`menuAnchor()` dal dialogo.
2. Caso "nessuna categoria": se `mainCategories.isEmpty()` → nel menu un `DropdownMenuItem` disabilitato
   con testo `str_nessuna_categoria_disponibile` ("Nessuna categoria disponibile. Creane una in Gestione Categorie.").
   Aggiunta la stringa in `values/strings.xml` e `values-en/strings.xml`.
3. Salva resta disabilitato senza categoria (corretto per design).

Verifiche eseguite OGGI DOPO il fix:
- `compileDebugKotlin` → BUILD SUCCESSFUL (warning pre-esistenti: `separator` mai usato, `endDateToggle` mai usata — NON toccare ora).
- `assembleDebug` → BUILD SUCCESSFUL, APK: `app/build/outputs/apk/debug/app-debug.apk` (16/09 17:25, ~60 MB).
- Test unitari NON rieseguiti dopo il fix (non toccano la UI; rieseguirli comunque). Nessun TOCTA al DAO/generatore/reminder.

## PROSSIMO PASSO (prossima sessione)

1. **Conferma notifica** su device: domani alle 09:00 la DAILY creata oggi deve notificare
   (debug APK già installato = stesso codice di v50). È l'ultima voce non confermata della feature.
2. Se arriva: feature ricorrenze CHIUSA. La checklist manuale del piano
   (righe ~2533-2541 di `docs/superpowers/plans/2026-09-11-recurring-transactions.md`) è da
   segnare come completata nelle voci ora testate (creazione/modifica/pausa, swipe, tastiera, categoria).

## Ambiente / gotchas (IMPORTANTI)

- SDK: `/opt/android-sdk`; `local.properties` assente. Gradle: `ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk bash gradlew <task>`
  (gradlew non eseguibile direttamente: mount fuseblk, usare `bash gradlew`).
- Workaround COMMIT obbligatorio (altrimenti `git commit` impazzisce con gli oggetti):
  ```
  mkdir -p /tmp/gitobjtest/info
  printf '/root/projects/ExpenseTracker/.git/objects\n' > /tmp/gitobjtest/info/alternates
  export GIT_OBJECT_DIRECTORY=/tmp/gitobjtest
  export GIT_AUTHOR_NAME="Tiziano Onor" GIT_AUTHOR_EMAIL="tiziano@android-termux"
  export GIT_COMMITTER_NAME="Tiziano Onor" GIT_COMMITTER_EMAIL="tiziano@android-termux"
  git add <files> && git commit -m "..."
  python3 /tmp/materialize_objects.py
  find .git/objects -name ".l2s.tmp_*" -delete
  unset GIT_OBJECT_DIRECTORY
  git fsck --no-dangling
  ```
- Richieste permessi notifica (Android 13+) e SCHEDULE_EXACT_ALARM già gestite nel codice (Task 6/11, commit e99d487/377ddba).

## Stato git corrente

- Branch: `main` (aggiornato a `33a6fc3` = Merge pull request #15). Branch feature ELIMINATO.
- Working tree: pulito (a parte questo session-state doc).
- Ultimo commit: `33a6fc3 Merge pull request #15 from onortiziano/feature/recurring-transactions`
- Commit principali integrati: 33a6fc3 (merge), 90db848, 02094cf, fdaccdd, f116af8, 9ee6201.

## File chiave ricorrenze (referenza)

- UI: `app/src/main/java/it/ciano/expensetracker/ui/screens/RecurringTransactionsScreen.kt`
- Calendario: `app/src/main/java/it/ciano/expensetracker/ui/components/RecurringCalendar.kt`
- VM: `app/src/main/java/it/ciano/expensetracker/ui/viewmodel/RecurringTransactionViewModel.kt`
- Dati: `data/dao/*Dao.kt`, `data/generation/RecurringDateCalculator.kt` + `RecurringTransactionGenerator.kt`, `data/repository/RecurringTransactionRepository.kt`
- Reminder: `data/reminder/RecurringReminderRules.kt`, `RecurringReminderScheduler.kt`, `RecurringReminderReceiver.kt`
- Piano: `docs/superpowers/plans/2026-09-11-recurring-transactions.md` (57 checkbox [x], solo checklist manuale da fare)
- Spec: `docs/superpowers/specs/2026-09-11-recurring-transactions-design.md`