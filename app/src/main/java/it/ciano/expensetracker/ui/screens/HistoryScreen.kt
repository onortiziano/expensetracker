package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    // 1. ViewModel per le transazioni
    val transactionViewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )
    
    // 2. ViewModel per la valuta
    val mainViewModel: MainViewModel = viewModel()
    
    // 3. ViewModel per le categorie
    val categoryViewModel: CategoryViewModel = viewModel(
        factory = ViewModelFactory(app)
    )
    
    // 4. Osserviamo i dati in tempo reale
    val transactions by transactionViewModel.allTransactions.collectAsState()
    val currency by mainViewModel.currency.collectAsState()
    
    // 5. Recupero categorie e creazione mappa (Usando CategoryViewModel)
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { 
        categories.associate { it.id to it.name } 
    }
    
    // Stato per l'eliminazione (quale transazione vogliamo cancellare?)
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cronologia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Nessuna transazione registrata", fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(
					    transaction = transaction,
                        currency = currency,
                        categoryMap = categoryMap,
                        onClick = { 
                            navController.navigate("${Routes.MODIFY_TRANSACTION}/${transaction.id}") 
                        },
                        onSwipeToDelete = { 
                            transactionToDelete = transaction 
                        }
                    )
                }
            }
        }

        // --- DIALOG DI CONFERMA ELIMINAZIONE ---
        if (transactionToDelete != null) {
            AlertDialog(
                onDismissRequest = { transactionToDelete = null },
                title = { Text(text = "Elimina Transazione") },
                text = { Text(text = "Sei sicuro di voler eliminare questa voce? L'operazione non può essere annullata.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            transactionViewModel.deleteTransaction(transactionToDelete!!)
                            transactionToDelete = null
                        }
                    ) {
                        Text("Sì, elimina", color = Color.Red)
                    }
                },
                dismissButton = { 
                    TextButton(onClick = { transactionToDelete = null }) {
                        Text("Annulla")
                    }
                }
            )
        }
    }
}