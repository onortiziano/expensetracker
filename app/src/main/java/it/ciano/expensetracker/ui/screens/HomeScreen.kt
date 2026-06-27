package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    // --- STATI E VIEWMODEL ---
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel()
	val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
	
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
	val categoryMap = remember(categories) { 
        categories.associate { it.id to it.name } 
    }
	
    val currency by mainViewModel.currency.collectAsState()
    val transactions by transactionViewModel.allTransactions.collectAsState()
    
    // Stato per l'apertura/chiusura del menu laterale (Drawer)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
	    // Intercetta il tasto indietro di sistema
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Stato per l'eliminazione
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

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
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text("Cronologia") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.HISTORY) 
						},
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.SETTINGS) 
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
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
                            Icon(Icons.Default.Menu, contentDescription = "Apri Menu")
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
                        val totalIncome by transactionViewModel.totalIncome.collectAsState()
                        val totalExpenses by transactionViewModel.totalExpenses.collectAsState()
                        val balance = totalIncome - totalExpenses

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
                                    text = "$balance $currency",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red
                                )
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Entrate", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "+$totalIncome $currency", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "-$totalExpenses $currency", color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    
                    items(transactions) { transaction ->
                        TransactionItem(
                            transaction = transaction, 
                            currency = currency,
                            categories = categories,
                            onClick = { 
                                navController.navigate("${Routes.MODIFY_TRANSACTION}/${transaction.id}") 
                            },
                            onSwipeToDelete = { 
                                transactionToDelete = transaction 
                            }
                        )
                    }
                }
            }

            // DIALOG DI CONFERMA ELIMINAZIONE
            if (transactionToDelete != null) {
                AlertDialog(
                    onDismissRequest = { transactionToDelete = null },
                    title = { Text(text = "Elimina Transazione") },
                    text = { Text(text = "Sei sicuro di voler eliminare questa voce? L'operazione non può essere annullata.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                transactionViewModel.deleteTransaction(transactionToDelete!!)
                                transactionToDelete = null
                            }
                        ) {
                            Text("Sì, elimina", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { transactionToDelete = null }) {
                            Text("Annulla")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItem(
    transaction: Transaction, 
    currency: String, 
    categories: List<Category>,
    onClick: () -> Unit, 
    onSwipeToDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onSwipeToDelete()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
		backgroundContent = {
            val isSwipingLeft = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val color = if (isSwipingLeft || dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) 
                        Color(0xFFD32F2F) else Color.Transparent
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Elimina", tint = Color.White)
            }
        },
        content = {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onClick() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = transaction.note, fontWeight = FontWeight.Bold)
                        
                        // LOGICA PADRE > FIGLIO
                        val category = categories.find { it.id == transaction.categoryId }
                        val categoryDisplayName = if (category != null) {
                            if (category.parentCategoryId != null) {
                                val parent = categories.find { it.id == category.parentCategoryId }
                                "${parent?.name ?: "Sconosciuto"} > ${category.name}"
                            } else {
                                category.name
                            }
                        } else {
                            "Senza Categoria"
                        }
                        
                        Text(text = "Categoria: $categoryDisplayName", fontSize = 12.sp)
                    }
                    Text(
                        text = if (transaction.type == "INCOME") "+${transaction.amount} $currency" else "-${transaction.amount} $currency",
                        color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}