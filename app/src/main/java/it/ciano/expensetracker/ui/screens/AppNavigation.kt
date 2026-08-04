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
    const val CATEGORY_MANAGEMENT = "category_management"
    const val TAG_MANAGEMENT = "tag_management"
    const val ANALYTICS = "analytics"
    const val SETTINGS_VISUAL_PREFERENCES = "settings_visual_preferences"
    const val SETTINGS_BUDGET_MANAGEMENT = "settings_budget_management"
    const val SETTINGS_CATEGORY_TAG_MANAGEMENT = "settings_category_tag_management"
    const val SETTINGS_DATA_MANAGEMENT = "settings_data_management"
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

        composable(Routes.CATEGORY_MANAGEMENT) {
            CategoryManagementScreen(navController)
        }

        composable(Routes.TAG_MANAGEMENT) {
            TagManagementScreen(navController)
        }

        composable(Routes.ANALYTICS) {
            AnalyticsScreen(navController)
        }

        composable(Routes.SETTINGS_VISUAL_PREFERENCES) {
            SettingsVisualPreferencesScreen(navController)
        }

        composable(Routes.SETTINGS_BUDGET_MANAGEMENT) {
            SettingsBudgetScreen(navController)
        }

        composable(Routes.SETTINGS_CATEGORY_TAG_MANAGEMENT) {
            SettingsCategoriesTagsScreen(navController)
        }

        composable(Routes.SETTINGS_DATA_MANAGEMENT) {
            SettingsDataScreen(navController)
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