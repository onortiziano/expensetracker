package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveTransactionScreen(
    navController: NavHostController
) {
    // 1. RECUPERO ID DALLA ROTTA
    val backStackEntry = androidx.navigation.compose.currentBackStackEntryAsState()
    val transactionId = backStackEntry.value?.arguments?.getInt("transactionId") ?: -1

    // 2. SETUP VIEWMODEL
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )

    // Stato per tenere traccia della transazione da eliminare
    var transactionToDelete by remember { mutableStateOf<it.ciano.expensetracker.data.model.Transaction?>(null) }

    // Carichiamo l'oggetto transazione completo usando l'ID della rotta
    LaunchedEffect(transactionId) {
        viewModel.allTransactions.collect { transactions ->
            transactionToDelete = transactions.find { it.id == transactionId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Elimina Transazione", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icona di avviso
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Elimina",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Sei sicuro di voler eliminare questa transazione?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // BOTTONE ANNULLA
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
							modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text("Annulla")
                    }
                    
                    // BOTTONE CONFERMA ELIMINAZIONE
                    Button(
                        onClick = {
                            transactionToDelete?.let {
                                viewModel.deleteTransaction(it)
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Elimina", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}