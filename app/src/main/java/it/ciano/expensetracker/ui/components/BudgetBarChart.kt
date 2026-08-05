package it.ciano.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import it.ciano.expensetracker.ui.viewmodel.BudgetComparison

@Composable
fun BudgetBarChart(
    data: List<BudgetComparison>,
    onMonthClick: (BudgetComparison) -> Unit
) {
    if (data.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.str_nessun_dato), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val maxVal = remember(data) {
        val maxBudget = data.maxOfOrNull { it.plannedBudget } ?: 0.0
        val maxSpending = data.maxOfOrNull { it.actualSpending } ?: 0.0
        maxOf(maxBudget, maxSpending, 1.0)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Legenda
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.str_budget))
            Spacer(modifier = Modifier.width(16.dp))
            LegendItem(color = MaterialTheme.colorScheme.error, label = stringResource(R.string.str_spesa))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Area Grafico
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onMonthClick(item) }
                    ) {
                        // Gruppo di barre per il mese
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Barra Budget
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height((item.plannedBudget / maxVal * 200).dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            // Barra Spesa
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height((item.actualSpending / maxVal * 200).dp)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.monthLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp)
    }
}