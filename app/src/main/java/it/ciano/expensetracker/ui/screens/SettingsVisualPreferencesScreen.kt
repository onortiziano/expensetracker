package it.ciano.expensetracker.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@Composable
fun SettingsVisualPreferencesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val settingsViewModel: SettingsViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    var showRestartDialog by remember { mutableStateOf(false) }

    val currency by settingsViewModel.currency.collectAsState()
    val decimalSeparator by settingsViewModel.decimalSeparator.collectAsState()
    val iconStyle by settingsViewModel.iconStyle.collectAsState()
    val appLanguage by settingsViewModel.appLanguage.collectAsState()

    // Ricrea l'Activity dopo il cambio lingua (su HyperOS le API di sistema non bastano)
    settingsViewModel.onLanguageChanged = {
        (context as? ComponentActivity)?.recreate()
    }

    val currencyOptions = listOf("€", "$", "£", "¥", "₹")
    val separatorOptions = listOf(",", ".")
    val styleOptions = listOf("FILLED", "OUTLINED", "ROUNDED", "SHARP", "TWO_TONE")

    SettingsScaffold(
        title = "Preferenze visualizzazione",
        mainViewModel = mainViewModel,
        navController = navController
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingDropdown(
                label = "Lingua",
                currentValue = when (appLanguage) {
                    "it" -> "Italiano"
                    "en" -> "English"
                    else -> "Sistema"
                },
                options = listOf("Sistema", "Italiano", "English"),
                onOptionSelected = { selected ->
                    val code = when (selected) {
                        "Italiano" -> "it"
                        "English" -> "en"
                        else -> "system"
                    }
                    settingsViewModel.updateAppLanguage(code)
                }
            )

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

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(56.dp),
                onClick = { showRestartDialog = true },
                shape = MaterialTheme.shapes.medium,
                colors = settingsButtonColors()
            ) {
                Text("Salva Impostazioni", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    RestartDialog(show = showRestartDialog, onRestart = { restartApp(context) })
}
