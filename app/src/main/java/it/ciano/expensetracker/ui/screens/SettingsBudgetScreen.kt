package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import java.util.Calendar

@Composable
fun SettingsBudgetScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val settingsViewModel: SettingsViewModel = viewModel()
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val decimalSeparator by settingsViewModel.decimalSeparator.collectAsState()

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

    SettingsScaffold(
        title = "Gestione budget",
        mainViewModel = mainViewModel,
        navController = navController
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingButton(
                label = "Budget Mensile Totale",
                onClick = {
                    currentGlobalBudget?.let { b ->
                        budgetInput = String.format("%.2f", b.amount).replace(".", decimalSeparator)
                        selectedMonth = b.month
                        selectedYear = b.year
                    } ?: run {
                        budgetInput = ""
                        selectedMonth = currentMonth
                        selectedYear = currentYear
                    }
                    budgetError = null
                    showBudgetDialog = true
                }
            )

            SettingButton(
                label = "Analisi Budget",
                onClick = { navController.navigate(Routes.ANALYTICS) }
            )
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

    val hasWrongSeparator = if (separator == ",") amount.contains(".") else amount.contains(",")
    val parsedAmount = amount.replace(separator, ".").toDoubleOrNull()
    val localError = when {
        amount.isEmpty() -> "Inserisci un importo"
        hasWrongSeparator -> "Utilizzare il separatore corretto ($separator)"
        parsedAmount == null -> "Importo non valido"
        parsedAmount < 0 -> "Importo non valido"
        else -> null
    }

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
                    isError = (localError ?: error) != null,
                    singleLine = true,
                    trailingIcon = {
                        if ((localError ?: error) != null) {
                            Icon(Icons.Default.Error, contentDescription = "Errore", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                if ((localError ?: error) != null) {
                    val displayError = localError ?: error
                    Text(
                        text = displayError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = localError == null) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
