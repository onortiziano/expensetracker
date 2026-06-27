package it.ciano.expensetracker.ui.screens

import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    var showRestartDialog by remember { mutableStateOf(false) }
    
    val currency by settingsViewModel.currency.collectAsState()
    val decimalSeparator by settingsViewModel.decimalSeparator.collectAsState()

    val currencyOptions = listOf("€", "$", "£", "¥", "₹")
    val separatorOptions = listOf(",", ".")

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let { settingsViewModel.backupAll(it) { success -> } }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { settingsViewModel.restoreAll(it) { success ->
                if (success) showRestartDialog = true
            } }
        }
    )

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Ripristino Completato") },
            text = { Text("L'app deve riavviarsi per applicare le nuove impostazioni.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Process.killProcess(Process.myPid())
                    }
                ) { Text("Riavvia Ora") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = "Preferenze Visualizzazione", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            
            SettingDropdown(
                label = "Simbolo Valuta",
                currentValue = currency,
                options = currencyOptions,
                onOptionSelected = { settingsViewModel.updateCurrency(it) }
            )

            SettingDropdown(
                label = "Separatore Decimale",
                currentValue = decimalSeparator,
                options = separatorOptions,
                onOptionSelected = { settingsViewModel.updateDecimalSeparator(it) }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(text = "Gestione Dati", style = MaterialTheme.typography.titleMedium, color = Color.Gray)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { backupLauncher.launch("backup_expenses.zip") },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Backup")
                        }
                    }
                )
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { importLauncher.launch(arrayOf("application/octet-stream")) },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Importa")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDropdown(label: String, currentValue: String, options: List<String>, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = currentValue,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(text = option) }, onClick = {
                        onOptionSelected(option)
                        expanded = false
                    })
                }
            }
        }
    }
}
