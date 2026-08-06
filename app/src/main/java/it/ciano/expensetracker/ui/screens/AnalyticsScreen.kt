package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.util.*
import it.ciano.expensetracker.R
import it.ciano.expensetracker.ui.viewmodel.AnalyticsViewModel
import it.ciano.expensetracker.ui.viewmodel.BudgetComparison
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.ui.components.BudgetBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = (context.applicationContext as android.app.Application)
    val viewModel: AnalyticsViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val comparisonData by viewModel.monthlyComparison.collectAsState(initial = emptyList())

    var selectedMonthDetail by remember { mutableStateOf<BudgetComparison?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val errorStartAfterEnd = stringResource(R.string.str_errore_data_da_a)
    val errorEndBeforeStart = stringResource(R.string.str_errore_data_a_da)

    if (showStartPicker) {
        val startState = rememberDatePickerState(
            initialSelectedDateMillis = startDate
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showStartPicker = false
                    val picked = startState.selectedDateMillis
                    if (picked != null && picked > endDate) {
                        dateError = errorStartAfterEnd
                    } else if (picked != null) {
                        dateError = null
                        viewModel.updateStartDate(picked)
                    }
                }) { Text(stringResource(R.string.str_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.str_annulla)) }
            }
        ) {
            DatePicker(state = startState)
        }
    }

    if (showEndPicker) {
        val endState = rememberDatePickerState(
            initialSelectedDateMillis = endDate
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showEndPicker = false
                    val picked = endState.selectedDateMillis
                    if (picked != null && picked < startDate) {
                        dateError = errorEndBeforeStart
                    } else if (picked != null) {
                        dateError = null
                        viewModel.updateEndDate(picked)
                    }
                }) { Text(stringResource(R.string.str_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.str_annulla)) }
            }
        ) {
            DatePicker(state = endState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_analisi_budget), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // SELEZIONE PERIODO
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = stringResource(R.string.str_periodo_analisi), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).clickable { showStartPicker = true }
                        ) {
                            OutlinedTextField(
                                value = dateFormat.format(Date(startDate)),
                                onValueChange = { },
                                label = { Text(stringResource(R.string.str_da)) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                enabled = false,
                                isError = dateError != null,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f).clickable { showEndPicker = true }
                        ) {
                            OutlinedTextField(
                                value = dateFormat.format(Date(endDate)),
                                onValueChange = { },
                                label = { Text(stringResource(R.string.str_a)) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                enabled = false,
                                isError = dateError != null,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
                            )
                        }
                    }
                    if (dateError != null) {
                        Text(
                            text = dateError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }
                }
            }

            // GRAFICO
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = stringResource(R.string.str_confronto_budget_spesa), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    BudgetBarChart(
                        data = comparisonData,
                        onMonthClick = { selectedMonthDetail = it }
                    )
                }
            }

            // DETTAGLIO MESE SELEZIONATO
            val m = selectedMonthDetail
            if (m != null) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.str_dettaglio_mese, m.monthLabel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.str_budget_pianificato))
                            Text(mainViewModel.formatCurrency(m.plannedBudget), fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.str_spesa_effettiva))
                            Text(mainViewModel.formatCurrency(m.actualSpending), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.str_differenza), fontWeight = FontWeight.Bold)
                            val diff = m.plannedBudget - m.actualSpending
                            Text(
                                mainViewModel.formatCurrency(diff),
                                fontWeight = FontWeight.Bold,
                                color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
