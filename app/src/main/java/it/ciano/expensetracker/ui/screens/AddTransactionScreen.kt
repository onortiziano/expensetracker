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
fun AddTransactionScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = viewModel(
        factory = ViewModelFactory(app)
    )

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

                    // SELEZIONE CATEGORIA
                    Text(text = "Categoria", fontWeight = FontWeight.Bold)
                    Text(
                        text = "Categoria ID: $selectedCategoryId", 
                        fontSize = 14.sp, 
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
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
    }
}