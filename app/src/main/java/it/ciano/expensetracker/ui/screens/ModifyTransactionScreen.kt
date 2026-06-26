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
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))

    // STATI PER I CAMPI (Usiamo rememberSaveable per la rotazione, anche se ora l'app è in portrait)
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var selectedMainCategoryId by rememberSaveable { mutableIntStateOf(0) }
    var selectedSubCategoryId by rememberSaveable { mutableIntStateOf(0) }

    // CATEGORIE
    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val mainCategories by categoryViewModel.mainCategories.collectAsState(initial = emptyList())
    val categoryMap by categoryViewModel.categoryMap.collectAsState()

    // CARICAMENTO DATI INIZIALI
    LaunchedEffect(transactionId) {
        transactionViewModel.allTransactions.collect { transactions ->
            val transaction = transactions.find { it.id == transactionId }
            transaction?.let {
                amount = it.amount.toString()
                note = it.note
                type = it.type
                selectedMainCategoryId = it.categoryId
                
                // Se la categoria selezionata è un figlio, dobbiamo resettare il padre
                // per far sì che il dropdown mostri correttamente la gerarchia
                val category = allCategories.find { it.id == it.categoryId }
                if (category?.parentCategoryId != null) {
                    selectedMainCategoryId = category.parentCategoryId!!
                    selectedSubCategoryId = it.categoryId
                } else {
                    selectedSubCategoryId = 0
                }
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

                // CARD 1: DETTAGLI
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
                    }
                }

                // CARD 2: CLASSIFICAZIONE
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
                                onClick = { type = "EXPENSE" },
                                label = { Text("Uscita") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = type == "INCOME",
                                onClick = { type = "INCOME" },
                                label = { Text("Entrata") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(text = "Categoria Principale", fontSize = 13.sp)
                        var mainExpanded by remember { mutableStateOf(false) }
                        val mainCategoryName = categoryMap[selectedMainCategoryId] ?: "Scegli Categoria"

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
                                            selectedMainCategoryId = category.id
                                            selectedSubCategoryId = 0
                                            mainExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("+ Aggiungi Nuova", color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        mainExpanded = false
                                        // In una versione completa, qui apriremo il dialog
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
                                                selectedSubCategoryId = sub.id
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
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            if (amountValue > 0) {
                                val finalCategoryId = if (selectedSubCategoryId != 0) selectedSubCategoryId else selectedMainCategoryId
                                
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
                            }
                        },
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
    }
}
