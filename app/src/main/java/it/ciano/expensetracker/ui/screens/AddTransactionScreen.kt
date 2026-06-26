package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = ViewModelFactory(app)
    )

    // STATO PER IL DIALOG DI AGGIUNTA CATEGORIA
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<Int?>(null) }
    var categoryType by remember { mutableStateOf("MAIN") } // "MAIN" o "SUB"

    // MAPPA PER I NOMI
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categoryMap = remember(categories) {
        categories.associate { it.id to it.name }
    }

    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedCategoryId by remember { mutableIntStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuova Transazione", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            val screenHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = screenHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Importo") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opzionale)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(text = "Tipo di operazione", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = type == "EXPENSE",
                            onClick = { type = "EXPENSE" },
                            label = { Text("Uscita") }
                        )
                        FilterChip(
                            selected = type == "INCOME",
                            onClick = { type = "INCOME" },
                            label = { Text("Entrata") }
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    val selectedCategoryName = categoryMap[selectedCategoryId] ?: "Seleziona categoria"

                    Text(text = "Categoria", fontWeight = FontWeight.Bold)
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedCategoryName,
                            onValueChange = {},
                            label = { Text("Categoria") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        expanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Aggiungi Categoria", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    expanded = false
                                    showAddCategoryDialog = true
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val amountValue = amount.toDoubleOrNull() ?: 0.0
                        if (amountValue > 0) {
                            val transaction = Transaction(
                                amount = amountValue,
                                type = type,
                                categoryId = selectedCategoryId,
                                note = note,
                                date = System.currentTimeMillis()
                            )
                            viewModel.addTransaction(transaction)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Salva Transazione", fontSize = 18.sp)
                }
            }
        }

        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddCategoryDialog = false
                    newCategoryName = ""
                    selectedParentId = null
                    categoryType = "MAIN"
                },
                title = { Text("Nuova Categoria", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 1. NOME CATEGORIA
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Nome Categoria") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 2. TIPO DI CATEGORIA
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "Tipo di categoria", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = categoryType == "MAIN",
                                    onClick = { 
                                        categoryType = "MAIN"
                                        selectedParentId = null 
                                    },
                                    label = { Text("Principale") }
                                )
                                FilterChip(
                                    selected = categoryType == "SUB",
                                    onClick = { categoryType = "SUB" },
                                    label = { Text("Sottocategoria") }
                                )
                            }
                        }

                        // 3. SELEZIONE PADRE (solo se SUB)
                        if (categoryType == "SUB") {
                            var parentExpanded by remember { mutableStateOf(false) }
                            val parentName = selectedParentId?.let { categoryMap[it] } ?: "Seleziona Padre"
                            
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
                                        label = { Text("Scegli il Padre") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                                        modifier = Modifier.menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = parentExpanded,
                                        onDismissRequest = { parentExpanded = false }
                                    ) {
                                        categories.filter { it.parentCategoryId == null }.forEach { parent ->
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
                            val isDuplicate = categories.any { 
                                it.name == newCategoryName && it.parentCategoryId == selectedParentId 
                            }
                            
                            if (isDuplicate) {
                                // Qui potremmo aggiungere un toast, per ora blocchiamo il salvataggio
                                return@Button 
                            }

                            if (newCategoryName.isNotBlank() && (categoryType == "MAIN" || selectedParentId != null)) {
                                categoryViewModel.addCategory(
                                    Category(name = newCategoryName, parentCategoryId = selectedParentId)
                                )
                                showAddCategoryDialog = false
                                newCategoryName = ""
                                selectedParentId = null
                                categoryType = "MAIN"
                            }
                        },
                        enabled = newCategoryName.isNotBlank() && (categoryType == "MAIN" || selectedParentId != null)
                    ) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}
