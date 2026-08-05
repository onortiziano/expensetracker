package it.ciano.expensetracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@Composable
fun SettingsDataScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val settingsViewModel: SettingsViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    var showRestartDialog by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let { settingsViewModel.backupAll(it) { _ -> } }
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

    SettingsScaffold(
        title = "Gestione backup",
        mainViewModel = mainViewModel,
        navController = navController
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { backupLauncher.launch("backup_expenses.zip") },
                    colors = settingsButtonColors(),
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
                    colors = settingsButtonColors(),
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
        }
    }

    RestartDialog(show = showRestartDialog, onRestart = { restartApp(context) })
}
