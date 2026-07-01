package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@Composable
fun RemoveTransactionScreen(navController: NavHostController, transactionId: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))

    AlertDialog(
        onDismissRequest = { navController.popBackStack() },
        title = { Text("Elimina Transazione") },
        text = { Text("Sei sicuro di voler eliminare questa transazione? L'operazione è irreversibile.") },
        confirmButton = {
            TextButton(onClick = {
                // Corretto da deleteTransaction a delete per allineamento col ViewModel
                transactionViewModel.delete(transactionId)
                navController.popBackStack()
            }) {
                Text("Elimina", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Annulla")
            }
        }
    )
}
