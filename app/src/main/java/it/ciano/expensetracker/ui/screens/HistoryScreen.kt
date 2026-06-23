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
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )

    // Osserviamo la lista delle transazioni in tempo reale dal ViewModel
    val transactions by viewModel.allTransactions.collectAsState()

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
            // Stato vuoto: mostriamo un messaggio se non ci sono transazioni
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Nessuna transazione registrata", fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            // Lista delle transazioni
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
                        onModify = { 
                            navController.navigate("modify_transaction/${transaction.id}") 
                        },
                        onRemove = { 
                            navController.navigate("remove_transaction/${transaction.id}") 
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
	onModify: () -> Unit,
    onRemove: () -> Unit
) {
    val isExpense = transaction.type == "EXPENSE"
    val amountColor = if (isExpense) Color(0xFFE57373) else Color(0xFF81C784) // Rosso chiaro o Verde chiaro
    val amountPrefix = if (isExpense) "-" else "+"

    // Formattazione della data
    val dateFormatted = remember(transaction.date) {
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        sdf.format(Date(transaction.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Informazioni Transazione
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (transaction.note.isBlank()) "Senza nota" else transaction.note,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = dateFormatted,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Importo
            Text(
                text = "$amountPrefix${String.format("%.2f", transaction.amount)} €",
                color = amountColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )

            // Azioni rapide (Modifica e Elimina)
            Row {
                IconButton(onClick = onModify) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(20.dp), tint = Color.Red)
                }
            }
        }
    }
}