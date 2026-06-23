package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController
) {
    // --- CORREZIONE QUI ---
    // Recuperiamo il contesto reale dell'app
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: TransactionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ViewModelFactory(app)
    )

    // Stati per i campi del modulo
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
				. imePadding()
				.verticalScroll(rememberScrollState()
				),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- CAMPO IMPORTA ---
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Importo") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            // --- CAMPO NOTA ---
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- SCELTA TIPO (ENTRATA/USCITA) ---
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

            // --- SELEZIONE CATEGORIA (Semplificata per ora) ---
            Text(text = "Categoria", fontWeight = FontWeight.Bold)
            // Nota: Qui in seguito metteremo un menu a tendina reale con le categorie del DB
            Text(text = "Categoria ID: $selectedCategoryId", fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.Gray)

            Spacer(modifier = Modifier.weight(32.dp))

            // --- BOTTONE SALVA ---
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
                        navController.popBackStack() // Torna alla Home
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Salva Transazione", fontSize = 18.sp)
            }
        }
    }
}