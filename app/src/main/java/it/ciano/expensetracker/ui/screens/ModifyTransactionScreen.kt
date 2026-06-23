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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifyTransactionScreen(
    navController: NavHostController
) {
    // 1. RECUPERO ID DALLA ROTTA
    // Recuperiamo l'ID della transazione passato tramite l'AppNavigation
    val backStackEntry = androidx.navigation.compose.currentBackStackEntryAsState()
    val transactionId = backStackEntry.value?.arguments?.getInt("transactionId") ?: -1

    // 2. SETUP VIEWMODEL
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )

    // Stati per i campi del modulo
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var selectedCategoryId by remember { mutableIntStateOf(1) }

    // 3. CARICAMENTO DATI INIZIALI
    // Questo blocco carica i dati della transazione esistente non appena la schermata si apre
    LaunchedEffect(transactionId) {
        viewModel.allTransactions.collect { transactions ->
            val transaction = transactions.find { it.id == transactionId }
            transaction?.let {
                amount = it.amount.toString()
                note = it.note
                type = it.type
                selectedCategoryId = it.categoryId
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
        // Usiamo la struttura "professionale" con BoxWithConstraints per il bottone in fondo
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            val screenHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = screenHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // GRUPPO CAMPI
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

                    // SELEZIONE CATEGORIA
                    Text(text = "Categoria", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Categoria ID: $selectedCategoryId", 
                        fontSize = 14.sp, 
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }

                // BOTTONE AGGIORNA
                Button(
                    onClick = {
                        val amountValue = amount.toDoubleOrNull() ?: 0.0
                        if (amountValue > 0) {
                            val updatedTransaction = Transaction(
                                id = transactionId, // FONDAMENTALE: usiamo l'ID esistente per aggiornare e non creare un nuovo record
                                amount = amountValue,
                                type = type,
                                categoryId = selectedCategoryId,
                                note = note,
                                date = System.currentTimeMillis()
                            )
                            viewModel.updateTransaction(updatedTransaction)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Aggiorna Transazione", fontSize = 18.sp)
                }
            }
        }
    }
}