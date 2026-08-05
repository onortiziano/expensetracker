package it.ciano.expensetracker.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.ui.theme.DarkGrey
import it.ciano.expensetracker.ui.viewmodel.MainViewModel

@Composable
fun settingsButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = DarkGrey,
    contentColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    mainViewModel: MainViewModel,
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGrey,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
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
        content(paddingValues)
    }
}

@Composable
fun SettingButton(label: String, onClick: () -> Unit) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = settingsButtonColors()
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun RestartDialog(show: Boolean, onRestart: () -> Unit) {
    if (show) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.str_ripristino_completato)) },
            text = { Text(stringResource(R.string.str_riavvio_necessario)) },
            confirmButton = {
                TextButton(onClick = onRestart) { Text(stringResource(R.string.str_riavvia_ora)) }
            }
        )
    }
}

fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    Process.killProcess(Process.myPid())
}
