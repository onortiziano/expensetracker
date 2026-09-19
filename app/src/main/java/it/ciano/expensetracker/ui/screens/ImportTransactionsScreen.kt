package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.import.CsvParser
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.ui.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTransactionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    val vm: ImportTransactionsViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val phase by vm.phase.collectAsState()
    val rows by vm.rows.collectAsState()
    val errors by vm.errors.collectAsState()
    val skipDuplicates by vm.skipDuplicates.collectAsState()
    val outcome by vm.outcome.collectAsState()
    val message by vm.message.collectAsState()
    val csvInfo by vm.csvInfo.collectAsState()
    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- SELEZIONE FILE ---
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.loadFile(it) }
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var addCategoryTarget by remember { mutableStateOf<Int?>(null) }
    var batchCategoryTarget by remember { mutableStateOf<String?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // --- BACK CON CONFERMA ---
    BackHandler(enabled = phase != ImportTransactionsViewModel.Phase.IDLE) {
        showExitConfirm = true
    }

    // --- ESECUZIONE USCITA ---
    fun exit() {
        vm.reset()
        navController.popBackStack()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_importa_transazioni), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (phase == ImportTransactionsViewModel.Phase.IDLE) exit() else showExitConfirm = true
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (phase == ImportTransactionsViewModel.Phase.PREVIEW) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExitConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.str_annulla)) }
                        Button(
                            onClick = { vm.importTransactions() },
                            enabled = rows.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.str_importa_n, rows.size)) }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (phase) {
                ImportTransactionsViewModel.Phase.IDLE -> {
                    Spacer(modifier = Modifier.height(48.dp))
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("text/*", "application/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.str_seleziona_file)) }
                    Text(
                        text = stringResource(R.string.str_nessun_file),
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                ImportTransactionsViewModel.Phase.PARSING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ImportTransactionsViewModel.Phase.MAPPING -> {
                    csvInfo?.let { info ->
                        val headers = info.headers
                        val sample = info.sampleRow
                        var amountIdx by remember { mutableStateOf(info.suggested.amountIdx) }
                        var dateIdx by remember { mutableStateOf(info.suggested.dateIdx) }
                        var descIdx by remember { mutableStateOf(info.suggested.descIdx) }
                        var catIdx by remember { mutableStateOf(info.suggested.catIdx) }
                        var typeChoice by remember {
                            mutableStateOf(
                                if (info.suggested.typeIdx >= 0) TypeChoice.Column(info.suggested.typeIdx)
                                else TypeChoice.AllExpense
                            )
                        }

                        Text(
                            text = stringResource(R.string.str_mapping_colonne),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.str_mapping_sugg_at),
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray
                        )

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                            item {
                                MappingField(
                                    label = stringResource(R.string.str_mapping_campo_importo),
                                    required = true,
                                    headers = headers,
                                    sample = sample,
                                    selected = amountIdx,
                                    onSelected = { amountIdx = it }
                                )
                            }
                            item {
                                MappingField(
                                    label = stringResource(R.string.str_mapping_campo_data),
                                    required = false,
                                    headers = headers,
                                    sample = sample,
                                    selected = dateIdx,
                                    onSelected = { dateIdx = it }
                                )
                            }
                            item {
                                MappingField(
                                    label = stringResource(R.string.str_mapping_campo_descrizione),
                                    required = false,
                                    headers = headers,
                                    sample = sample,
                                    selected = descIdx,
                                    onSelected = { descIdx = it }
                                )
                            }
                            item {
                                MappingField(
                                    label = stringResource(R.string.str_mapping_campo_categoria),
                                    required = false,
                                    headers = headers,
                                    sample = sample,
                                    selected = catIdx,
                                    onSelected = { catIdx = it }
                                )
                            }
                            item {
                                TypeChoiceField(
                                    headers = headers,
                                    sample = sample,
                                    selected = typeChoice,
                                    onSelected = { typeChoice = it }
                                )
                            }
                            if (amountIdx < 0) {
                                item {
                                    Text(
                                        text = stringResource(R.string.str_mapping_importo_obbligatorio),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val (typeIdx, typeDefault) = when (val tc = typeChoice) {
                                    is TypeChoice.Column -> tc.idx to null
                                    TypeChoice.AllExpense -> -1 to "EXPENSE"
                                    TypeChoice.AllIncome -> -1 to "INCOME"
                                    TypeChoice.FromSign -> -1 to null
                                }
                                vm.buildRows(
                                    CsvParser.ImportMapping(
                                        dateIdx = dateIdx, descIdx = descIdx,
                                        amountIdx = amountIdx, typeIdx = typeIdx, catIdx = catIdx,
                                        typeDefault = typeDefault
                                    )
                                )
                            },
                            enabled = amountIdx >= 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.str_mapping_conferma)) }
                    }
                }
                ImportTransactionsViewModel.Phase.PREVIEW -> {
                    Text(
                        text = stringResource(R.string.str_anteprima_import),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.str_salta_duplicati))
                        Switch(checked = skipDuplicates, onCheckedChange = vm::updateSkipDuplicates)
                    }

                    // --- CATEGORIE DI MASSA (da file) ---
                    val fileCategories = rows.mapNotNull { it.transaction.categoryName }.toSet()
                    if (fileCategories.isNotEmpty()) {
                        Text(stringResource(R.string.str_categoria_riga), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(fileCategories.toList()) { fc ->
                                FilterChip(
                                    selected = false,
                                    onClick = { batchCategoryTarget = fc },
                                    label = { Text(fc) }
                                )
                            }
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(rows) { index, row ->
                            ImportRowCard(
                                row = row,
                                categories = allCategories,
                                mainViewModel = mainViewModel,
                                onCategorySelected = { catId -> vm.assignCategory(index, catId) },
                                onAddCategory = { showAddCategoryDialog = true; addCategoryTarget = index }

                            )
                        }
                        if (errors.isNotEmpty()) {
                            item {
                                Text(stringResource(R.string.str_dettaglio_errori), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            items(errors.size) { i ->
                                val e = errors[i]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.LightGray, shape = MaterialTheme.shapes.small)
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red)
                                    Text(
                                        text = if (e.line == 0) localizedError(e.reason)
                                            else stringResource(R.string.str_errore_riga, e.line, localizedError(e.reason)),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- CREA CATEGORIA INLINE ---
    if (showAddCategoryDialog) {
        val targetIndex = addCategoryTarget
        AddImportCategoryDialog(
            categoryViewModel = categoryViewModel,
            allCategories = allCategories,
            onDismiss = { showAddCategoryDialog = false },
            onCategoryCreated = { newId ->
                showAddCategoryDialog = false
                if (targetIndex != null) vm.assignCategory(targetIndex, newId.toInt())
            }
        )
    }

    // --- APPLICA CATEGORIA A GRUPPO (chip categoria da file) ---
    batchCategoryTarget?.let { fileCategory ->
        AlertDialog(
            onDismissRequest = { batchCategoryTarget = null },
            title = { Text(fileCategory, fontWeight = FontWeight.Bold) },
            text = {
                if (allCategories.isEmpty()) {
                    Text(stringResource(R.string.str_nessuna_categoria))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allCategories.forEach { c ->
                            TextButton(
                                onClick = {
                                    vm.applyCategoryToRows(fileCategory, c.id)
                                    batchCategoryTarget = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(c.name, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { batchCategoryTarget = null }) { Text(stringResource(R.string.str_annulla)) }
            }
        )
    }

    // --- USCITA CON CONFERMA ---
    if (showExitConfirm && phase != ImportTransactionsViewModel.Phase.IDLE) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.str_conferma_esci_import), fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; exit() }) { Text(stringResource(R.string.str_chiudi)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(stringResource(R.string.str_annulla)) }
            }
        )
    }

    // --- RISULTATO ---
    if (outcome != null) {
        AlertDialog(
            onDismissRequest = { vm.reset() },
            title = { Text(stringResource(R.string.str_risultato_import, outcome!!.imported, outcome!!.skippedDuplicate, outcome!!.errors.size), fontWeight = FontWeight.Bold) },
            text = {
                if (outcome!!.errors.isNotEmpty()) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        outcome!!.errors.forEach { e ->
                            Text(
                                text = if (e.line == 0) localizedError(e.reason)
                                    else stringResource(R.string.str_errore_riga, e.line, localizedError(e.reason)),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.str_importazione_completata, outcome!!.imported))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = outcome!!.imported
                    vm.reset()
                    if (n > 0) scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.str_importazione_completata, n)) }
                }) { Text(stringResource(R.string.str_chiudi)) }
            }
        )
    }
}

/** Traduce le ragioni canoniche dei parser nei messaggi localizzati. */
@Composable
private fun localizedError(reason: String): String = when (reason) {
    "Colonne obbligatorie mancanti" -> stringResource(R.string.str_colonne_mancanti)
    CsvParser.REASON_AMOUNT_NOT_MAPPED -> stringResource(R.string.str_importo_mancante)
    "Data non valida" -> stringResource(R.string.str_data_non_valida)
    "Importo non valido" -> stringResource(R.string.str_importo_non_valido)
    "Formato file non riconosciuto" -> stringResource(R.string.str_errore_formato)
    "Separatore non riconosciuto" -> stringResource(R.string.str_errore_formato)
    else -> reason
}

/** Come classificare il tipo quando il file può non avere una colonna tipo. */
private sealed interface TypeChoice {
    data object AllExpense : TypeChoice
    data object AllIncome : TypeChoice
    data object FromSign : TypeChoice
    data class Column(val idx: Int) : TypeChoice
}

/** Menu Tipo: forza tutte uscite/entrate, inferenza dal segno o colonna del file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeChoiceField(
    headers: List<String>,
    sample: List<String>,
    selected: TypeChoice,
    onSelected: (TypeChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.str_mapping_campo_tipo)
    val display = when (val s = selected) {
        TypeChoice.AllExpense -> stringResource(R.string.str_mapping_tipo_uscite)
        TypeChoice.AllIncome -> stringResource(R.string.str_mapping_tipo_entrate)
        TypeChoice.FromSign -> stringResource(R.string.str_mapping_tipo_segno)
        is TypeChoice.Column -> if (s.idx in headers.indices) headers[s.idx] else stringResource(R.string.str_mapping_non_usata)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = Color.Unspecified
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                readOnly = true,
                value = display,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.str_mapping_tipo_uscite)) },
                    onClick = { onSelected(TypeChoice.AllExpense); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.str_mapping_tipo_entrate)) },
                    onClick = { onSelected(TypeChoice.AllIncome); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.str_mapping_tipo_segno)) },
                    onClick = { onSelected(TypeChoice.FromSign); expanded = false }
                )
                HorizontalDivider()
                headers.forEachIndexed { index, h ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(h, fontWeight = FontWeight.Medium)
                                if (index < sample.size && sample[index].isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.str_mapping_esempio, sample[index]),
                                        fontSize = 11.sp, color = Color.Gray
                                    )
                                }
                            }
                        },
                        onClick = { onSelected(TypeChoice.Column(index)); expanded = false }
                    )
                }
            }
        }
    }
}

/** Dropdown per abbinare un campo dell'app a una colonna del file. -1 = non usata. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingField(
    label: String,
    required: Boolean,
    headers: List<String>,
    sample: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (selected in headers.indices) headers[selected]
        else stringResource(R.string.str_mapping_non_usata)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (required) "$label *" else label,
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (required && selected < 0) MaterialTheme.colorScheme.error else Color.Unspecified
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                readOnly = true,
                value = display,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.matchParentSize().clickable { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.str_mapping_non_usata)) },
                    onClick = { onSelected(-1); expanded = false }
                )
                headers.forEachIndexed { index, h ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(h, fontWeight = FontWeight.Medium)
                                if (index < sample.size && sample[index].isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.str_mapping_esempio, sample[index]),
                                        fontSize = 11.sp, color = Color.Gray
                                    )
                                }
                            }
                        },
                        onClick = { onSelected(index); expanded = false }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportRowCard(
    row: it.ciano.expensetracker.data.import.ImportRow,
    categories: List<Category>,
    mainViewModel: MainViewModel,
    onCategorySelected: (Int) -> Unit,
    onAddCategory: () -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    val t = row.transaction
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()) }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    text = (if (t.type == "EXPENSE") "-" else "+") + mainViewModel.formatCurrency(t.amount),
                    color = if (t.type == "EXPENSE") Color.Red else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = dateFormat.format(java.util.Date(t.date)),
                fontSize = 12.sp,
                color = Color.Gray
            )

            // DROPDOWN CATEGORIA (stesso pattern di RecurringTransactionsScreen)
            val currentCategory = categories.find { it.id == row.categoryId }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    readOnly = true,
                    value = currentCategory?.name ?: stringResource(R.string.str_nessuna_categoria),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.str_categoria_riga)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.matchParentSize().clickable { categoryExpanded = true })
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = { onCategorySelected(c.id); categoryExpanded = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.str_aggiungi_nuova), color = MaterialTheme.colorScheme.primary) },
                        onClick = { categoryExpanded = false; onAddCategory() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddImportCategoryDialog(
    categoryViewModel: CategoryViewModel,
    allCategories: List<Category>,
    onDismiss: () -> Unit,
    onCategoryCreated: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(stringResource(R.string.str_nuova_categoria), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.str_nome_categoria)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val isDuplicate = allCategories.any { it.name == name && it.parentCategoryId == null }
                        if (isDuplicate) return@launch
                        if (name.isNotBlank()) {
                            val newId = categoryViewModel.addCategory(Category(name = name))
                            onCategoryCreated(newId)
                        }
                    }
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.str_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.str_annulla)) }
        }
    )
}