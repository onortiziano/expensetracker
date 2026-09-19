package it.ciano.expensetracker.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.generation.RecurringDateCalculator
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.RecurringTransactionWithTags
import it.ciano.expensetracker.ui.components.RecurringCalendar
import it.ciano.expensetracker.ui.viewmodel.*
import java.util.Calendar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringTransactionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application

    val vm: RecurringTransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))
    val settingsViewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val recurrings by vm.recurringWithTags.collectAsState()
    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val mainCategories by categoryViewModel.mainCategories.collectAsState(initial = emptyList())
    val allTags by tagViewModel.allTags.collectAsState(initial = emptyList())
    val separator = settingsViewModel.decimalSeparator.collectAsState().value
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringTransactionWithTags?>(null) }
    var deleteCandidate by remember { mutableStateOf<RecurringTransactionWithTags?>(null) }

    // --- CREAZIONE CATEGORIA INLINE (come AddTransactionScreen) ---
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // --- PERMESSO NOTIFICHE ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var notificationsDenied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_transazioni_ricorrenti), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingItem = null
                vm.startNew()
                showAddDialog = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
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
            Spacer(modifier = Modifier.height(8.dp))

            RecurringCalendar(
                transactions = recurrings.map { it.recurring },
                mainViewModel = mainViewModel
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Banner(
                    text = stringResource(R.string.str_notifiche_negate),
                    onAction = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            if (recurrings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.str_nessuna_ricorrente), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recurrings, key = { it.recurring.id }) { item ->
                        RecurringCard(
                            item = item,
                            mainViewModel = mainViewModel,
                            allCategories = allCategories,
                            dateFormat = dateFormat,
                            onToggleActive = { active -> vm.setActive(item, active) },
                            onDelete = { deleteCandidate = item },
                            onSingleClick = { editingItem = item; vm.startEdit(item); showAddDialog = true },
                            onEditClick = { editingItem = item; vm.startEdit(item); showAddDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(
            vm = vm,
            mainCategories = mainCategories,
            allCategories = allCategories,
            allTags = allTags,
            separator = separator,
            dateFormat = dateFormat,
            isEdit = editingItem != null,
            onAddCategoryClick = { showAddCategoryDialog = true },
            onDismiss = { showAddDialog = false },
            onSave = {
                showAddDialog = false
                editingItem = null
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            categoryViewModel = categoryViewModel,
            allCategories = allCategories,
            separator = separator,
            onDismiss = { showAddCategoryDialog = false },
            onCategoryCreated = { newId ->
                vm.updateCategory(newId.toInt())
                showAddCategoryDialog = false
            }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.str_transazioni_ricorrenti), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.str_conferma_elimina_ricorrente)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(candidate)
                        deleteCandidate = null
                    }
                ) { Text(stringResource(R.string.str_si_elimina), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.str_annulla))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun RecurringCard(
    item: RecurringTransactionWithTags,
    mainViewModel: MainViewModel,
    allCategories: List<Category>,
    dateFormat: java.text.DateFormat,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSingleClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val r = item.recurring
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
            }
            false
        }
    )

    val cardShape = MaterialTheme.shapes.medium

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isSwipingLeft = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val isSwipingRight = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (isSwipingLeft || isSwipingRight) Color(0xFFD32F2F) else Color.Transparent

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.str_elimina),
                    tint = Color.White
                )
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onSingleClick, onLongClick = onEditClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val category = allCategories.find { it.id == r.categoryId }
                    Text(
                        text = category?.name ?: stringResource(R.string.str_senza_categoria),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = stringResource(
                            RecurringDateCalculator.FREQUENCY_LABELS[r.frequency] ?: R.string.str_frequenza_obbligatoria
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = stringResource(
                            R.string.str_prossima_generazione,
                            dateFormat.format(java.util.Date(r.nextDueDate))
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (r.type == "INCOME")
                            "+" + mainViewModel.formatCurrency(r.amount).removePrefix("+")
                            else "-" + mainViewModel.formatCurrency(r.amount).removePrefix("-"),
                        color = if (r.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Switch(
                        checked = r.isActive,
                        onCheckedChange = onToggleActive
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text, modifier = Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = onAction) {
                Text(stringResource(R.string.str_attiva_notifiche))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRecurringDialog(
    vm: RecurringTransactionViewModel,
    allCategories: List<Category>,
    mainCategories: List<Category>,
    allTags: List<it.ciano.expensetracker.data.model.Tag>,
    separator: String,
    dateFormat: java.text.DateFormat,
    isEdit: Boolean,
    onAddCategoryClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val title by vm.title.collectAsState()
    val amount by vm.amount.collectAsState()
    val type by vm.type.collectAsState()
    val categoryId by vm.categoryId.collectAsState()
    val frequency by vm.frequency.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val endDateEnabled by vm.endDateEnabled.collectAsState()
    val endDate by vm.endDate.collectAsState()
    val note by vm.note.collectAsState()
    val selectedTags by vm.selectedTags.collectAsState()

    val categoryMap = remember(allCategories) { allCategories.associateBy { it.id } }
    val endDateToggle = remember { mutableStateOf(endDateEnabled) }

    val startDateDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                vm.updateStartDate(Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }
    val endDateDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                vm.updateEndDate(Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = {
            Text(
                stringResource(if (isEdit) R.string.str_modifica_ricorrente else R.string.str_nuova_ricorrente),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = vm::updateTitle,
                    label = { Text(stringResource(R.string.str_titolo)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = vm::updateAmount,
                    label = { Text(stringResource(R.string.str_importo)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { vm.updateType("EXPENSE") },
                        label = { Text(stringResource(R.string.str_uscita)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { vm.updateType("INCOME") },
                        label = { Text(stringResource(R.string.str_entrata)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                var categoryExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        value = categoryMap[categoryId]?.name ?: stringResource(R.string.str_scegli_categoria),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_categoria_principale)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { categoryExpanded = true })
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        if (mainCategories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.str_nessuna_categoria_disponibile)) },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            mainCategories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name) },
                                    onClick = { vm.updateCategory(c.id); categoryExpanded = false }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.str_aggiungi_nuova), color = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                categoryExpanded = false
                                onAddCategoryClick()
                            }
                        )
                    }
                }

                var frequencyExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        readOnly = true,
                        value = stringResource(RecurringDateCalculator.FREQUENCY_LABELS[frequency] ?: R.string.str_frequenza_obbligatoria),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_frequenza)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { frequencyExpanded = true })
                    DropdownMenu(
                        expanded = frequencyExpanded,
                        onDismissRequest = { frequencyExpanded = false }
                    ) {
                        RecurringDateCalculator.FREQUENCIES.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(stringResource(RecurringDateCalculator.FREQUENCY_LABELS[f] ?: R.string.str_frequenza_obbligatoria)) },
                                onClick = { vm.updateFrequency(f); frequencyExpanded = false }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (startDate != 0L) dateFormat.format(java.util.Date(startDate)) else "",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.str_data_inizio)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { startDateDialog.show() })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.str_data_fine), fontSize = 14.sp)
                    Checkbox(
                        checked = endDateEnabled,
                        onCheckedChange = { vm.setEndDateEnabled(it) }
                    )
                }
                if (endDateEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (endDate != 0L) dateFormat.format(java.util.Date(endDate)) else "",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.str_data_fine)) },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { endDateDialog.show() })
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = vm::updateNote,
                    label = { Text(stringResource(R.string.str_note)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (allTags.isNotEmpty()) {
                    Text(stringResource(R.string.str_tag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTags.contains(tag.tagId),
                                onClick = { vm.toggleTag(tag.tagId) },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.save(onSave) },
                enabled = title.isNotBlank() && (parseAmountText(amount, separator) ?: 0.0) > 0.0 && categoryId != 0 && startDate != 0L && (!endDateEnabled || endDate != 0L)
            ) { Text(stringResource(R.string.str_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.str_annulla)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryDialog(
    categoryViewModel: CategoryViewModel,
    allCategories: List<Category>,
    separator: String,
    onDismiss: () -> Unit,
    onCategoryCreated: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var categoryType by remember { mutableStateOf("MAIN") }
    var parentId by remember { mutableStateOf<Int?>(null) }

    val normalizedBudget = budget.replace(separator, ".")
    val containsWrongSeparator =
        (separator == "," && budget.contains(".")) || (separator == "." && budget.contains(","))
    val isBudgetValid =
        budget.isEmpty() || (!containsWrongSeparator && normalizedBudget.toDoubleOrNull() != null)

    val parentCandidates = allCategories.filter { it.parentCategoryId == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = {
            Text(stringResource(R.string.str_nuova_categoria), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.str_nome_categoria)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text(stringResource(R.string.str_budget_opzionale)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !isBudgetValid,
                    singleLine = true
                )
                if (!isBudgetValid) {
                    Text(
                        text = stringResource(R.string.str_numero_non_valido),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.str_tipo_categoria), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = categoryType == "MAIN",
                            onClick = {
                                categoryType = "MAIN"
                                parentId = null
                            },
                            label = { Text(stringResource(R.string.str_principale)) }
                        )
                        FilterChip(
                            selected = categoryType == "SUB",
                            onClick = { categoryType = "SUB" },
                            label = { Text(stringResource(R.string.str_sottocategoria)) }
                        )
                    }
                }

                if (categoryType == "SUB") {
                    var parentExpanded by remember { mutableStateOf(false) }
                    val parentName = parentId?.let { pid -> parentCandidates.find { it.id == pid }?.name }
                        ?: stringResource(R.string.str_scegli_padre)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.str_sottocategoria_di), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                readOnly = true,
                                value = parentName,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.str_seleziona_padre)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { parentExpanded = true })
                            DropdownMenu(
                                expanded = parentExpanded,
                                onDismissRequest = { parentExpanded = false }
                            ) {
                                if (parentCandidates.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.str_nessuna_categoria_disponibile)) },
                                        enabled = false,
                                        onClick = {}
                                    )
                                } else {
                                    parentCandidates.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.name) },
                                            onClick = {
                                                parentId = p.id
                                                parentExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val isDuplicate = allCategories.any {
                            it.name == name && it.parentCategoryId == parentId
                        }
                        if (isDuplicate) return@launch

                        if (name.isNotBlank() && (categoryType == "MAIN" || parentId != null)) {
                            val budgetValue = budget.replace(separator, ".").toDoubleOrNull()
                            val newId = categoryViewModel.addCategory(
                                Category(name = name, budget = budgetValue, parentCategoryId = parentId)
                            )
                            onCategoryCreated(newId)
                        }
                    }
                },
                enabled = name.isNotBlank() && isBudgetValid && (categoryType == "MAIN" || parentId != null)
            ) { Text(stringResource(R.string.str_salva)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.str_annulla))
            }
        }
    )
}
