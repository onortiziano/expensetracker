package it.ciano.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.generation.RecurringDateCalculator
import it.ciano.expensetracker.data.model.RecurringTransaction
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringCalendar(
    transactions: List<RecurringTransaction>,
    mainViewModel: MainViewModel
) {
    var displayedMonth by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedDay by remember { mutableStateOf<Calendar?>(null) }

    val activeTransactions = transactions.filter { it.isActive }

    val year = displayedMonth.get(Calendar.YEAR)
    val month = displayedMonth.get(Calendar.MONTH)

    val occurrencesByDay = remember(year, month, activeTransactions) {
        val map = mutableMapOf<Int, List<RecurringTransaction>>()
        activeTransactions.forEach { t ->
            RecurringDateCalculator.occurrencesInMonth(t, year, month).forEach { epoch ->
                val day = Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.DAY_OF_MONTH)
                map[day] = (map[day] ?: emptyList()) + t
            }
        }
        map
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { shiftMonth(displayedMonth, -1) { displayedMonth = it } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                }
                Text(
                    text = "${monthsName(month)} $year",
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { shiftMonth(displayedMonth, 1) { displayedMonth = it } }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }

            val firstDay = displayedMonth.clone() as Calendar
            firstDay.set(Calendar.DAY_OF_MONTH, 1)
            val startColumn = (firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
            val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 6).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (0 until 7).forEach { col ->
                            val dayNumber = week * 7 + col - startColumn + 1
                            if (dayNumber in 1..daysInMonth) {
                                val due: List<RecurringTransaction>? = occurrencesByDay[dayNumber]
                                DayCell(
                                    day = dayNumber,
                                    hasDue = !due.isNullOrEmpty(),
                                    onClick = {
                                        if (!due.isNullOrEmpty()) {
                                            selectedDay = Calendar.getInstance().apply {
                                                clear()
                                                set(year, month, dayNumber, 0, 0, 0)
                                            }
                                        }
                                    }
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f)) { Spacer(modifier = Modifier.height(36.dp)) }
                            }
                        }
                    }
                }
            }

            if (occurrencesByDay.isEmpty()) {
                Text(
                    text = stringResource(R.string.str_nessuna_scadenza),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }

    val dayDialog = selectedDay
    if (dayDialog != null) {
        val dayKey = dayDialog.get(Calendar.DAY_OF_MONTH)
        val occurrences = occurrencesByDay[dayKey] ?: emptyList()
        AlertDialog(
            onDismissRequest = { selectedDay = null },
            title = {
                Text(stringResource(R.string.str_ricorrenze_il_giorno,
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(dayDialog.time)))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    occurrences.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(t.title, fontWeight = FontWeight.Medium)
                            Text(mainViewModel.formatCurrency(t.amount))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDay = null }) {
                    Text(stringResource(R.string.str_chiudi))
                }
            }
        )
    }
}

@Composable
private fun RowScope.DayCell(day: Int, hasDue: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clickable(enabled = hasDue, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = day.toString(), fontSize = 13.sp)
        if (hasDue) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

private fun shiftMonth(current: Calendar, delta: Int, onChanged: (Calendar) -> Unit) {
    val next = current.clone() as Calendar
    next.add(Calendar.MONTH, delta)
    next.set(Calendar.DAY_OF_MONTH, 1)
    onChanged(next)
}

@Composable
private fun monthsName(month: Int): String {
    val names = arrayOf(
        stringResource(R.string.str_mese_gennaio), stringResource(R.string.str_mese_febbraio),
        stringResource(R.string.str_mese_marzo), stringResource(R.string.str_mese_aprile),
        stringResource(R.string.str_mese_maggio), stringResource(R.string.str_mese_giugno),
        stringResource(R.string.str_mese_luglio), stringResource(R.string.str_mese_agosto),
        stringResource(R.string.str_mese_settembre), stringResource(R.string.str_mese_ottobre),
        stringResource(R.string.str_mese_novembre), stringResource(R.string.str_mese_dicembre)
    )
    return names[month]
}