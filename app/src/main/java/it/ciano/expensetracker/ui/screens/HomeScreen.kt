package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.navigation.Routes
import it.ciano.expensetracker.ui.viewmodels.MainViewModel
import it.ciano.expensetracker.ui.viewmodels.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodels.ViewModelFactory
import it.ciano.expensetracker.data.model.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    // 1. Recupero dei ViewModel e dello Stato
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel()
    
    // Collezioniamo la valuta e le transazioni in tempo reale
    val currency by mainViewModel.currency.collectAsState()
    val transactions by transactionViewModel.getAllTransactions().collectAsState(initial = emptyList())
    
    // Stato per il menu a tendina (panino)
    var menuExpanded by remember { mutableStateOf(false) }
    
    // Stato per l'eliminazione (quale transazione vogliamo cancellare?)
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Expense Tracker") },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Cronologia") },
                                onClick = { 
                                    menuExpanded = false
                                    navController.navigate(Routes.HISTORY) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Impostazioni") },
                                onClick = { 
                                    menuExpanded = false
                                    navController.navigate(Routes.SETTINGS) 
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
			FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
                Text("+", fontSize = 24.sp)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction, 
                        currency = currency,
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
                            transactionViewModel.deleteTransaction(transactionToDelete!!.id)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction, 
    currency: String, 
    onClick: () -> Unit, 
    onSwipeToDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onSwipeToDelete()
            }
            false // L'elemento torna al centro (effetto molla)
        }
    )

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
        },
        content = {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = transaction.note, fontWeight = FontWeight.Bold)
						Text(text = "Categoria ID: ${transaction.categoryId}", fontSize = 12.sp)
                    }
                    Text(
                        text = if (transaction.type == "INCOME") "+${transaction.amount} $currency" else "-${transaction.amount} $currency",
                        color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}