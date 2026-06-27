package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel()
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    
    val transactions by transactionViewModel.allTransactions.collectAsState()
    val currency by mainViewModel.currency.collectAsState()
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cronologia", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
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
                        categories = categories,
                        onDeleteRequest = { trans ->
                            transactionViewModel.deleteTransaction(trans)
                        },
                        onClick = { 
                            navController.navigate("${Routes.MODIFY_TRANSACTION}/${transaction.id}") 
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction, 
    currency: String, 
    categories: List<Category>,
    onDeleteRequest: (Transaction) -> Unit,
    onClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                showDeleteDialog = true
            }
            false
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Elimina Transazione") },
            text = { Text(text = "Sei sicuro di voler eliminare questa voce? L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRequest(transaction)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Sì, elimina", color = Color.Red)
                }
            },
            dismissButton = { 
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
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
                        Text(text = transaction.note, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        
                        val category = categories.find { it.id == transaction.categoryId }
                        val categoryDisplayName = if (category != null) {
                            if (category.parentCategoryId != null) {
                                val parent = categories.find { it.id == category.parentCategoryId }
                                "${parent?.name ?: "Sconosciuto"} > ${category.name}"
                            } else {
                                category.name
                            }
                        } else {
                            "Senza Categoria"
                        }
                        
                        Text(text = "Categoria: $categoryDisplayName", fontSize = 12.sp)
                    }
                    Text(
                        text = if (transaction.type == "INCOME") "+%.2f %s".format(transaction.amount, currency) else "-%.2f %s".format(transaction.amount, currency),
                        color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
