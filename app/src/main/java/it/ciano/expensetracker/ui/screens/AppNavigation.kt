package it.ciano.expensetracker.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Definiamo le "rotte" (gli indirizzi) delle nostre pagine
object Routes {
    const val HOME = "home"
    const val ADD_TRANSACTION = "add_transaction"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val MODIFY_TRANSACTION = "modify_transaction"
    const val REMOVE_TRANSACTION = "remove_transaction"
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
            AddTransactionScreen(navController)
        }
        
        composable(Routes.HISTORY) {
            HistoryScreen(navController)
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }

        // Per la Modifica
        composable(
            route = "${Routes.MODIFY_TRANSACTION}/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getInt("transactionId") ?: -1
            ModifyTransactionScreen(navController, transactionId)
        }

        // Per la Rimozione
        composable(
            route = "${Routes.REMOVE_TRANSACTION}/{transactionId}",
            arguments = listOf(navArgument("transactionId") { type = NavType.IntType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getInt("transactionId") ?: -1
            RemoveTransactionScreen(navController, transactionId)
        }
    }
}