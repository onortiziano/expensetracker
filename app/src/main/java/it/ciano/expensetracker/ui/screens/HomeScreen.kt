package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.sharp.List
import androidx.compose.material.icons.automirrored.twotone.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material.icons.sharp.Menu
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Menu
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.ui.screens.Routes
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.Category
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.data.model.TransactionWithTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    // --- STATI E VIEWMODEL ---
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    
    // FIX: Usiamo allTransactionsWithTags per avere i tag nell'item
    val transactionsWithTags by transactionViewModel.allTransactionsWithTags.collectAsState(initial = emptyList())
    
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    
    // Stato per l'apertura/chiusura del menu laterale (Drawer)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Intercetta il tasto indietro di sistema
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // --- STRUTTURA CON NAVIGATION DRAWER ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Intestazione del Menu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Expense Tracker",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gestione Spese",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voci del Menu
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = true,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.HOME) 
                    },
                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home, Icons.TwoTone.Home), contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text("Cronologia") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.HISTORY) 
                    },
                    icon = { Icon(mainViewModel.getIcon(Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List, Icons.AutoMirrored.TwoTone.List), contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.SETTINGS) 
                    },
                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings, Icons.TwoTone.Settings), contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        // --- CONTENUTO PRINCIPALE ---
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Expense Tracker") },
                    navigationIcon = {
                        IconButton(onClick = { 
                            scope.launch { drawerState.open() } 
                        }) {
                            Icon(mainViewModel.getIcon(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu, Icons.Sharp.Menu, Icons.TwoTone.Menu), contentDescription = "Apri Menu")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
                    Text("+", fontSize = 24.sp)
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- CARD RIEPILOGO BILANCIO ---
                    item {
                        val totalIncome by transactionViewModel.totalIncome.collectAsState(initial = 0.0)
                        val totalExpenses by transactionViewModel.totalExpenses.collectAsState(initial = 0.0)
                        val balance = (totalIncome ?: 0.0) - (totalExpenses ?: 0.0)

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "Bilancio Totale", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Text(
                                    text = mainViewModel.formatCurrency(balance),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Entrate", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome ?: 0.0).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "-" + mainViewModel.formatCurrency(totalExpenses ?: 0.0).removePrefix("-"), color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    
                    items(transactionsWithTags) { item ->
                        TransactionItem(
                            transaction = item.transaction, 
                            tags = item.tags,
                            onDetailsRequest = { selectedTransaction = item.transaction },
                            onModifyRequest = { 
                                navController.navigate("${Routes.MODIFY_TRANSACTION}/${item.transaction.transactionId}") 
                            }
                        )
                    }
                }
            }
        }
        if (selectedTransaction != null) {
            val transaction = selectedTransaction!!
            val tags = transactionsWithTags.find { it.transaction.transactionId == transaction.transactionId }?.tags ?: emptyList()
            TransactionDetailsDialog(transaction = transaction, tags = tags, onDismiss = { selectedTransaction = null })
        }
    }
}
