package it.ciano.expensetracker.ui.screens

import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val settingsViewModel: SettingsViewModel = viewModel()
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    var showRestartDialog by remember { mutableStateOf(false) }
    
    val currency by settingsViewModel.currency.collectAsState()
    val decimalSeparator by settingsViewModel.decimalSeparator.collectAsState()
    val iconStyle by settingsViewModel.iconStyle.collectAsState()
    
    val currentCalendar = Calendar.getInstance()
    val currentMonth = currentCalendar.get(Calendar.MONTH) + 1
    val currentYear = currentCalendar.get(Calendar.YEAR)
    
    val currentGlobalBudget by settingsViewModel.getBudgetForMonth(currentMonth, currentYear).collectAsState(initial = null)
    val totalCategoryBudget by categoryViewModel.getTotalCategoryBudget().collectAsState(initial = 0.0)
    
    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf("") }
    var budgetError by remember { mutableStateOf<String?>(null) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var selectedYear by remember { mutableStateOf(currentYear) }

    val currencyOptions = listOf("€", "$", "£", "¥", "₹")
    val separatorOptions = listOf(",", ".")
    val styleOptions = listOf("FILLED", "OUTLINED", "ROUNDED", "SHARP", "TWO_TONE")

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let { settingsViewModel.backupAll(it) { _success -> } }
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
                title = { Text("Impostazioni", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = mainViewModel.getIcon(
                                Icons.Filled.ArrowBack, 
                                Icons.Outlined.ArrowBack, 
                                Icons.Rounded.ArrowBack, 
                                Icons.Sharp.ArrowBack, 
                                Icons.TwoTone.ArrowBack
                            ), 
                            contentDescription = "Torna indietro"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
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

            SettingDropdown(
                label = "Stile Icone",
                currentValue = iconStyle,
                options = styleOptions,
                onOptionSelected = { settingsViewModel.updateIconStyle(it) }
            )
            
            Text(text = "Gestione Organizzazione", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            
            SettingButton(
                label = "Budget Mensile Totale",
                onClick = { showBudgetDialog = true }
            )
            
            SettingButton(
                label = "Analisi Budget",
                onClick = { navController.navigate(Routes.ANALYTICS) }
            )
            
            SettingButton(
                label = "Categorie",
                onClick = { navController.navigate(Routes.CATEGORY_MANAGEMENT) }
            )
            
            SettingButton(
                label = "Gestione Tag",
                onClick = { navController.navigate("tag_management") }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(text = "Gestione Dati", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { backupLauncher.launch("backup_expenses.zip") },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = mainViewModel.getIcon(
                                    Icons.Default.CloudUpload, 
                                    Icons.Outlined.CloudUpload, 
                                    Icons.Rounded.CloudUpload, 
                                    Icons.Sharp.CloudUpload, 
                                    Icons.TwoTone.CloudUpload
                                ), 
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Backup")
                        }
                    }
                )
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = mainViewModel.getIcon(
                                    Icons.Default.CloudDownload, 
                                    Icons.Outlined.CloudDownload, 
                                    Icons.Rounded.CloudDownload, 
                                    Icons.Sharp.CloudDownload, 
                                    Icons.TwoTone.CloudDownload
                                ), 
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Importa")
                        }
                    }
                )
            }
            
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp),
                onClick = { showRestartDialog = true },
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Salva Impostazioni", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            amount = budgetInput,
            onAmountChange = { budgetInput = it },
            selectedMonth = selectedMonth,
            onMonthChange = { selectedMonth = it },
            selectedYear = selectedYear,
            onYearChange = { selectedYear = it },
            onConfirm = {
                val hasWrongSeparator = if (decimalSeparator == ",") {
                    budgetInput.contains(".")
                } else {
                    budgetInput.contains(",")
                }
                
                val normalizedBudget = budgetInput.replace(decimalSeparator, ".")
                val budgetValue = normalizedBudget.toDoubleOrNull()
                
                if (budgetValue == null || hasWrongSeparator) {
                    budgetError = "Utilizzare il separatore corretto ($decimalSeparator)"
                } else if (budgetValue < totalCategoryBudget) {
                    budgetError = "Il budget totale non può essere inferiore alla somma dei budget categoria (${String.format("%.2f", totalCategoryBudget).replace(".", decimalSeparator)}€)"
                } else {
                    budgetError = null
                    settingsViewModel.saveGlobalBudget(budgetValue, selectedMonth, selectedYear)
                    showBudgetDialog = false
                }
            },
            onDismiss = { showBudgetDialog = false },
            error = budgetError,
            separator = decimalSeparator
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedMonth: Int,
    onMonthChange: (Int) -> Unit,
    selectedYear: Int,
    onYearChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    error: String?,
    separator: String
) {
    val months = listOf("Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (currentYear - 2..currentYear + 2).toList().map { it.toString() }
    
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget Mensile Totale", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Month Selector
                    ExposedDropdownMenuBox(
                        expanded = monthExpanded,
                        onExpandedChange = { monthExpanded = !monthExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = months[selectedMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            label = { Text("Mese") },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false }
                        ) {
                            months.forEachIndexed { index, month ->
                                DropdownMenuItem(
                                    text = { Text(text = month, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        onMonthChange(index + 1)
                                        monthExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Year Selector
                    ExposedDropdownMenuBox(
                        expanded = yearExpanded,
                        onExpandedChange = { yearExpanded = !yearExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedYear.toString(),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            label = { Text("Anno") },
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        ExposedDropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false }
                        ) {
                            years.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(text = year, style = MaterialTheme.typography.bodySmall) },
                                    onClick = {
                                        onYearChange(year.toInt())
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text("Budget per questo mese (€)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    singleLine = true,
                    trailingIcon = {
                        if (error != null) {
                            Icon(Icons.Default.Error, contentDescription = "Errore", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
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

@Composable
fun SettingButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            }
        }
    )
}
