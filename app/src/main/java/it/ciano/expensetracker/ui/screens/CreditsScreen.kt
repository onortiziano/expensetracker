package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.model.Debt
import it.ciano.expensetracker.ui.viewmodel.CreditsViewModel
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as Application

    val creditsViewModel: CreditsViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    val openCredits by creditsViewModel.openCredits.collectAsState()
    var selectedDebt by remember { mutableStateOf<Debt?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_crediti), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = mainViewModel.getIcon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                Icons.AutoMirrored.Sharp.ArrowBack,
                                Icons.AutoMirrored.TwoTone.ArrowBack
                            ),
                            contentDescription = stringResource(R.string.str_torna_indietro)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (openCredits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.str_nessun_credito), fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(openCredits) { debt ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDebt = debt }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = debt.name,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                            Text(
                                text = mainViewModel.formatCurrency(debt.amount),
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedDebt != null) {
        val debt = selectedDebt!!
        val rightBackTitle = stringResource(R.string.str_rientro_da, debt.name)
        AlertDialog(
            onDismissRequest = { selectedDebt = null },
            title = { Text(rightBackTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.str_rientro_conferma, debt.name, mainViewModel.formatCurrency(debt.amount))
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        creditsViewModel.settle(
                            debt = debt,
                            rightBackTitle = rightBackTitle,
                            incomeCategoryId = 0
                        )
                        selectedDebt = null
                    }
                ) {
                    Text(stringResource(R.string.str_conferma))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDebt = null }) {
                    Text(stringResource(R.string.str_annulla))
                }
            }
        )
    }
}