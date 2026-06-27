package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
fun ModifyTransactionScreen(
    navController: NavHostController,
    transactionId: Int
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))

    val amount by transactionViewModel.amount.collectAsState()
    val note by transactionViewModel.note.collectAsState()
    val type by transactionViewModel.type.collectAsState()
    val selectedMainCategoryId by transactionViewModel.selectedMainCategoryId.collectAsState()
    val selectedSubCategoryId by transactionViewModel.selectedSubCategoryId.collectAsState()

    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val mainCategories by categoryViewModel.mainCategories.collectAsState(initial = emptyList())
    val categoryMap by categoryViewModel.categoryMap.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<Int?>(null) }
    var categoryType by remember { mutableStateOf("MAIN") }

    LaunchedEffect(transactionId) {
        transactionViewModel.allTransactions.collect { transactions ->
            val transaction = transactions.find { it.id == transactionId }
            transaction?.let { trans ->
                transactionViewModel.loadTransaction(trans, allCategories)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifica Transazione", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp)
        ) {
            val screenHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = screenHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "Dettagli Transazione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = note,
                            onValueChange = { transactionViewModel.updateNote(it) },
                            label = { Text("Titolo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { transactionViewModel.updateAmount(it) },
                            label = { Text("Importo") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "Classificazione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        Text(text = "Tipo di operazione", fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = type == "EXPENSE",
                                onClick = { transactionViewModel.updateType("EXPENSE") },
                                label = { Text("Uscita") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = type == "INCOME",
                                onClick = { transactionViewModel.updateType("INCOME") },
                                label = { Text("Entrata") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(text = "Categoria Principale", fontSize = 13.sp)
                        var mainExpanded by remember { mutableStateOf(false) }
                        val mainCategoryName = categoryMap[selectedMainCategoryId] ?: "Senza Categoria"

                        ExposedDropdownMenuBox(
                            expanded = mainExpanded,
                            onExpandedChange = { mainExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = mainCategoryName,
                                onValueChange = {},
                                label = { Text("Categoria Principale") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = mainExpanded,
                                onDismissRequest = { mainExpanded = false }
                            ) {
                                mainCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            transactionViewModel.updateMainCategory(category.id)
                                            mainExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("+ Aggiungi Nuova", color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        mainExpanded = false
                                        showAddCategoryDialog = true
                                    }
                                )
                            }
                        }

                        val subCategories = allCategories.filter { it.parentCategoryId == selectedMainCategoryId }
                        if (selectedMainCategoryId != 0 && subCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Sottocategoria", fontSize = 13.sp)
                            var subExpanded by remember { mutableStateOf(false) }
                            val subCategoryName = categoryMap[selectedSubCategoryId] ?: "Scegli Sottocategoria"

                            ExposedDropdownMenuBox(
                                expanded = subExpanded,
                                onExpandedChange = { subExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = subCategoryName,
                                    onValueChange = {},
                                    label = { Text("Sottocategoria") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = subExpanded,
                                    onDismissRequest = { subExpanded = false }
                                ) {
                                    subCategories.forEach { sub ->
                                        DropdownMenuItem(
                                            text = { Text(sub.name) },
                                            onClick = {
                                                transactionViewModel.updateSubCategory(sub.id)
                                                subExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    val isFormValid = note.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0.0
                    
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            val finalCategoryId = if (selectedSubCategoryId != 0) selectedSubCategoryId else if (selectedMainCategoryId != 0) selectedMainCategoryId else 0
                            
                            val updatedTransaction = Transaction(
                                id = transactionId,
                                amount = amountValue,
                                type = type,
                                categoryId = finalCategoryId,
                                note = note,
                                date = System.currentTimeMillis()
                            )
                            transactionViewModel.updateTransaction(updatedTransaction)
                            navController.popBackStack()
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Aggiorna Transazione", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
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
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Nome Categoria") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

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
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = parentExpanded,
                                        onDismissRequest = { parentExpanded = false }
                                    ) {
                                        mainCategories.forEach { parent ->
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
                            scope.launch {
                                val isDuplicate = allCategories.any { 
                                    it.name == newCategoryName && it.parentCategoryId == selectedParentId 
                                }
                                
                                if (isDuplicate) {
                                    return@launch 
                                }

                                if (newCategoryName.isNotBlank() && (categoryType == "MAIN" || selectedParentId != null)) {
                                    val newId = categoryViewModel.addCategory(
                                        Category(name = newCategoryName, parentCategoryId = selectedParentId)
                                    ).toInt()
                                    
                                    if (categoryType == "MAIN") {
                                        transactionViewModel.updateMainCategory(newId)
                                    } else {
                                        val parentId = selectedParentId ?: 0
                                        transactionViewModel.updateCategoryPair(parentId, newId)
                                    }
                                    
                                    showAddCategoryDialog = false
                                    newCategoryName = ""
                                    selectedParentId = null
                                    categoryType = "MAIN"
                                }
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
