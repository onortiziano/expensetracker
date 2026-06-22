package it.ciano.expensetracker.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController

// Definiamo le "rotte" (gli indirizzi) delle nostre pagine
object Routes {
    const val HOME = "home"
    const val ADD_TRANSACTION = "add_transaction"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Il NavHost è il contenitore che cambia schermata
    NavHost(
        navController = navController, 
        startDestination = Routes.HOME // Partiamo dalla Home
    ) {
        composable(Routes.HOME) {
            HomeScreen(navController)
        }
        
        composable(Routes.ADD_TRANSACTION) {
            // Questa schermata la creeremo tra poco
            AddTransactionScreen(navController)
        }
        
        composable(Routes.HISTORY) {
            // Questa schermata la creeremo tra poco
            HistoryScreen(navController)
        }
        
        composable(Routes.SETTINGS) {
            // Questa schermata la creeremo tra poco
            SettingsScreen(navController)
        }
    }
}
