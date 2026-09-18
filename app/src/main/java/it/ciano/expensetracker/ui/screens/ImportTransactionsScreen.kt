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
                    IconButton(onClick = { showExitConfirm = true }) {
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
    "Data non valida" -> stringResource(R.string.str_data_non_valida)
    "Importo non valido" -> stringResource(R.string.str_importo_non_valido)
    "Formato file non riconosciuto" -> stringResource(R.string.str_errore_formato)
    "Separatore non riconosciuto" -> stringResource(R.string.str_errore_formato)
    else -> reason
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