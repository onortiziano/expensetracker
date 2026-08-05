package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@Composable
fun SettingsCategoriesTagsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))

    SettingsScaffold(
        title = stringResource(R.string.str_gestione_organizzazione),
        mainViewModel = mainViewModel,
        navController = navController
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingButton(label = stringResource(R.string.str_categorie)) {
                navController.navigate(Routes.CATEGORY_MANAGEMENT)
            }
            SettingButton(label = stringResource(R.string.str_gestione_tag)) {
                navController.navigate(Routes.TAG_MANAGEMENT)
            }
        }
    }
}
