package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryManagementScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val settingsViewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(app))
    val categories by categoryViewModel.allCategories.collectAsState()
    val separator by settingsViewModel.decimalSeparator.collectAsState()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var showModifyConfirmDialog by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Categorie", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                categoryToEdit = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi Categoria")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Elenco Categorie",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories) { category ->
                    val displayName = if (category.parentCategoryId != null) {
                        val parent = categories.find { it.id == category.parentCategoryId }
                        "${parent?.name ?: "Sconosciuto"} > ${category.name}"
                    } else {
                        category.name
                    }
                    CategorySwipeItem(
                        category = category,
                        displayName = displayName,
                        separator = separator,
                        onEditRequest = {
                            showModifyConfirmDialog = category
                        },
                        onDeleteRequest = { cat ->
                            categoryViewModel.deleteCategory(cat)
                        }
                    )
                }
            }
        }

        if (showDialog) {
            CategoryDialog(
                category = categoryToEdit,
                availableParents = categories.filter { it.id != categoryToEdit?.id },
                separator = separator,
                onDismiss = { 
                    showDialog = false 
                    categoryToEdit = null
                },
                onConfirm = { name, budget, parentId ->
                    scope.launch {
                        if (categoryToEdit == null) {
                            categoryViewModel.addCategory(Category(name = name, budget = budget, parentCategoryId = parentId))
                        } else {
                            val updated = categoryToEdit!!.copy(name = name, budget = budget, parentCategoryId = parentId)
                            categoryViewModel.updateCategory(updated)
                        }
                    }
                    showDialog = false
                    categoryToEdit = null
                }
            )
        }

        if (showModifyConfirmDialog != null) {
            AlertDialog(
                onDismissRequest = { showModifyConfirmDialog = null },
                title = { Text("Modifica Categoria", fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.str_vuoi_modificare_i_dettagli_di_questa_categoria)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            categoryToEdit = showModifyConfirmDialog
                            showModifyConfirmDialog = null
                            showDialog = true
                        }
                    ) { Text(stringResource(R.string.str_s_modifica)) }
                },
                dismissButton = {
                    TextButton(onClick = { showModifyConfirmDialog = null }) { Text(stringResource(R.string.str_annulla)) }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategorySwipeItem(
    category: Category,
    displayName: String,
    separator: String,
    onEditRequest: () -> Unit,
    onDeleteRequest: (Category) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteDialog = true
                    false 
                }
                else -> false
            }
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.str_elimina_categoria)) },
            text = { Text("Sei sicuro di voler eliminare '${category.name}'? L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRequest(category)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text(stringResource(R.string.str_elimina)) }
            },
            dismissButton = { 
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.str_annulla))
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isSwipingLeft = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = if (isSwipingLeft || dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 
                        Color(0xFFD32F2F) else Color.Transparent
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Elimina", tint = Color.White)
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { /* No action */ },
                    onLongClick = onEditRequest
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (category.budget != null) {
                            "Budget: ${String.format("%.2f", category.budget).replace(".", separator)}€"
                        } else {
                            "Senza budget"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDialog(
    category: Category?,
    availableParents: List<Category>,
    separator: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Double?, Int?) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var budgetText by remember { mutableStateOf(category?.budget?.toString() ?: "") }
    var selectedParentId by remember { mutableStateOf(category?.parentCategoryId) }
    var categoryType by remember { mutableStateOf(if (category?.parentCategoryId == null) "MAIN" else "SUB") }
    var expanded by remember { mutableStateOf(false) }

    // Normalizza il testo del budget sostituendo il separatore scelto dall'utente con il punto
    val normalizedBudget = budgetText.replace(separator, ".")
    val containsWrongSeparator = (separator == "," && budgetText.contains(".")) || (separator == "." && budgetText.contains(","))
    val isBudgetValid = budgetText.isEmpty() || (!containsWrongSeparator && normalizedBudget.toDoubleOrNull() != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Nuova Categoria" else "Modifica Categoria", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.str_nome_categoria)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text(stringResource(R.string.str_budget_opzionale)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = !isBudgetValid
                )
                if (!isBudgetValid) {
                    Text(
                        text = "Inserire un numero valido",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Tipo di categoria", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = categoryType == "MAIN",
                            onClick = { 
                                categoryType = "MAIN"
                                selectedParentId = null 
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
                    val parentName = selectedParentId?.let { id -> availableParents.find { it.id == id }?.name } ?: "Seleziona Padre"
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Sottocategoria di...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        ExposedDropdownMenuBox(
                            expanded = parentExpanded,
                            onExpandedChange = { parentExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = parentName,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.str_scegli_il_padre)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = parentExpanded,
                                onDismissRequest = { parentExpanded = false }
                            ) {
                                availableParents.forEach { parent ->
                                    DropdownMenuItem(
                                        text = { Text(parent.name) },
                                        onClick = {
                                            selectedParentId = parent.id
                                            parentExpanded = false
                                        }
                                    )
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
                    val budget = normalizedBudget.toDoubleOrNull()
                    onConfirm(name, budget, selectedParentId)
                },
                enabled = isBudgetValid && name.isNotBlank()
            ) { Text(stringResource(R.string.str_salva)) }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.str_annulla)) }
        }
    )
}