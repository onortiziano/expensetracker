package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.TagViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))

    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var note by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var selectedTags by remember { mutableStateOf(setOf<Int>()) }

    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val allTags by tagViewModel.allTags.collectAsState(initial = emptyList())

    // Validazione: il pulsante è attivo solo se titolo e importo sono presenti
    val isSaveEnabled = title.isNotBlank() && amount.isNotBlank() && amount.toDoubleOrNull() != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuova Transazione", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // Fix scrolling
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = amount, 
                onValueChange = { amount = it }, 
                label = { Text("Importo") }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal) // Fix tastiera
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Uscita") }, modifier = Modifier.weight(1f))
                FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Entrata") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text("Categoria", style = MaterialTheme.typography.labelLarge)
            CategorySelector(categories = allCategories, selectedCategoryId = selectedCategoryId, onCategorySelected = { selectedCategoryId = it }, onParentSelected = {})
            Text("Tag", style = MaterialTheme.typography.labelLarge)
            TagSelector(tags = allTags, selectedTags = selectedTags, onTagToggled = { tagId ->
                selectedTags = if (selectedTags.contains(tagId)) selectedTags - tagId else selectedTags + tagId
            })
            Button(
                onClick = {
                    val finalAmount = amount.toDoubleOrNull() ?: 0.0
                    val newTransaction = Transaction(title = title, amount = finalAmount, type = type, categoryId = selectedCategoryId ?: 0, note = note)
                    transactionViewModel.insert(newTransaction)
                    navController.popBackStack()
                },
                enabled = isSaveEnabled, // Fix validazione
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salva Transazione", fontSize = 18.sp)
            }
        }
    }
}
