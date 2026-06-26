package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import it.ciano.expensetracker.data.model.Category


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
    var parentSearchText by remember { mutableStateOf("") }
		
		//MAPPA PER I NOMI
		
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
        // Usiamo BoxWithConstraints per conoscere l'altezza massima dello schermo
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            val screenHeight = maxHeight // Altezza disponibile nello schermo

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = screenHeight) // Obbliga la colonna a essere almeno alta quanto lo schermo
                    .imePadding() // Gestisce l'altezza quando appare la tastiera
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween // Spinge i blocchi agli estremi
            ) {
                // --- GRUPPO SUPERIORE (Campi di input) ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // CAMPO IMPORTA
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
							label = { Text("Importo") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    // CAMPO NOTA
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota (opzionale)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // SCELTA TIPO
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

                    // SELEZIONE CATEGORIA CON MENU A TENDINA
					
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
                            // TASTO "+ AGGIUNGI CATEGORIA"
                            DropdownMenuItem(
                                text = { Text("+ Aggiungi Categoria", color = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    expanded = false
                                    // Qui apriremo il dialog di creazione
                                }
                            )
                        }
                    }
                }

                // --- GRUPPO INFERIORE (Bottone) ---
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
                        .padding(bottom = 16.dp), // Un po' di respiro dal bordo inferiore
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
                parentSearchText = ""
            },
            title = { Text("Nuova Categoria", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // NOME CATEGORIA
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nome Categoria") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // SOTTOCATEGORIA DI... (Filtro Padre)
                    Column {
                        Text(
                            text = "Sottocategoria di...", 
                            fontSize = 12.sp, 
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                        OutlinedTextField(
                            value = if (selectedParentId == null) "" else categoryMap[selectedParentId] ?: "",
                            onValueChange = { 
                                parentSearchText = it
                                selectedParentId = null // Resetta la selezione se l'utente inizia a scrivere
                            },
                            label = { Text("Cerca Categoria Padre (lascia vuoto per principale)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (selectedParentId != null) {
                                    IconButton(onClick = { selectedParentId = null }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Rimuovi Padre")
                                    }
                                }
                            }
                        )
                        
                        // LISTA SUGGERIMENTI FILTRATI (Semplificata)
                        if (parentSearchText.isNotEmpty()) {
                            val filteredParents = categories.filter { 
                                it.parentCategoryId == null && 
                                it.name.contains(parentSearchText, ignoreCase = true) 
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                filteredParents.forEach { parent ->
                                    Text(
                                        text = parent.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                selectedParentId = parent.id 
                                                parentSearchText = parent.name
                                            }
                                            .padding(8.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            categoryViewModel.addCategory(
                                Category(name = newCategoryName, parentCategoryId = selectedParentId)
                            )
							showAddCategoryDialog = false
                            newCategoryName = ""
                            selectedParentId = null
                            parentSearchText = ""
                        }
                    },
                    enabled = newCategoryName.isNotBlank()
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