package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add
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
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.TagViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))

    val transactionsWithTags by transactionViewModel.allTransactionsWithTags.collectAsState()
    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categoryMap by categoryViewModel.categoryMap.collectAsState()
    val allTags by tagViewModel.allTags.collectAsState(initial = emptyList())
    
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Expense Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Impostazioni")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_transaction") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // --- SEZIONE TOTALI ---
            val income by transactionViewModel.totalIncome.collectAsState()
            val expenses by transactionViewModel.totalExpenses.collectAsState()
            val balance = (income ?: 0.0) - (expenses ?: 0.0)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TotalCard("Entrate", income ?: 0.0, Color(0xFF4CAF50), Modifier.weight(1f))
                TotalCard("Uscite", expenses ?: 0.0, Color(0xFFF44336), Modifier.weight(1f))
            }

            TotalBalanceCard(balance)

            Spacer(modifier = Modifier.height(24.dp))

            // --- LISTA TRANSAZIONI ---
            Text(
                text = "Ultime Operazioni",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(transactionsWithTags) { item ->
                    TransactionItem(
                        transaction = item.transaction,
                        tags = item.tags,
                        onDetailsRequest = { 
                            selectedTransaction = item.transaction 
                        },
                        onModifyRequest = { 
                            navController.navigate("modify_transaction/${item.transaction.transactionId}") 
                        }
                    )
                }
            }
        }

        // Dialog di Dettaglio
        if (selectedTransaction != null) {
            val transaction = selectedTransaction!!
            val tags = transactionsWithTags.find { it.transaction.transactionId == transaction.transactionId }?.tags ?: emptyList()
            TransactionDetailsDialog(
                transaction = transaction,
                tags = tags,
                onDismiss = { selectedTransaction = null }
            )
        }
    }
}

@Composable
fun TotalCard(label: String, amount: Double, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
            Text(
                text = "€${String.format("%.2f", amount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun TotalBalanceCard(balance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Saldo Attuale", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "€${String.format("%.2f", balance)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (balance >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}
