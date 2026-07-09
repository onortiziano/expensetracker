package it.ciano.expensetracker.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.*
import it.ciano.expensetracker.ui.viewmodel.AnalyticsViewModel
import it.ciano.expensetracker.ui.viewmodel.BudgetComparison
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.ui.components.BudgetBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: AnalyticsViewModel = viewModel(factory = ViewModelFactory(app))

    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()
    val comparisonData by viewModel.monthlyComparison.collectAsState(initial = emptyList())
    
    var selectedMonthDetail by remember { mutableStateOf<BudgetComparison?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val startDatePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.updateStartDate(cal.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }

    val endDatePicker = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.updateEndDate(cal.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analisi Budget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna indietro")
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
                    Text(text = "Periodo di Analisi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = dateFormat.format(Date(startDate)),
                            onValueChange = {},
                            label = { Text("Da") },
                            modifier = Modifier.weight(1f).clickable { startDatePicker.show() },
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
                        )
                        OutlinedTextField(
                            value = dateFormat.format(Date(endDate)),
                            onValueChange = {},
                            label = { Text("A") },
                            modifier = Modifier.weight(1f).clickable { endDatePicker.show() },
                            readOnly = true,
                            trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
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
                    Text(text = "Confronto Budget vs Spesa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    BudgetBarChart(
                        data = comparisonData,
                        onMonthClick = { selectedMonthDetail = it }
                    )
                }
            }

            // DETTAGLIO MESE SELEZIONATO
            if (selectedMonthDetail != null) {
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
                            text = "Dettaglio ${selectedMonthDetail!!.monthLabel}", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold
                        )
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Budget Pianificato:")
                            Text("${String.format("%.2f", selectedMonthDetail!!.plannedBudget)}€", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Spesa Effettiva:")
                            Text("${String.format("%.2f", selectedMonthDetail!!.actualSpending)}€", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Differenza:", fontWeight = FontWeight.Bold)
                            val diff = selectedMonthDetail!!.plannedBudget - selectedMonthDetail!!.actualSpending
                            Text(
                                "${String.format("%.2f", diff)}€", 
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
