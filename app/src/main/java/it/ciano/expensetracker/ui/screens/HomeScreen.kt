package it.ciano.expensetracker.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import it.ciano.expensetracker.data.model.TransactionWithTags
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    
    // USiamo le transazioni con i tag
    val transactionsWithTags by transactionViewModel.transactionsWithTags.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Stato per il Dialog di Dettaglio
    var selectedTransactionForDetails by remember { mutableStateOf<TransactionWithTags?>(null) }
    var showModifyConfirmDialog by remember { mutableStateOf<Transaction?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
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
                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "-" + mainViewModel.formatCurrency(totalExpenses).removePrefix("-"), color = Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    
                    items(transactionsWithTags) { transactionWithTags ->
                        TransactionItem(
                            transactionWithTags = transactionWithTags, 
                            mainViewModel = mainViewModel,
                            categories = categories,
                            onDeleteRequest = { trans ->
                                transactionViewModel.deleteTransaction(trans)
                            },
                            onSingleClick = { 
                                selectedTransactionForDetails = transactionWithTags
                            },
                            onLongClick = {
                                showModifyConfirmDialog = transactionWithTags.transaction
                            }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOG DETTAGLIO ---
    if (selectedTransactionForDetails != null) {
        val details = selectedTransactionForDetails!!
        AlertDialog(
            onDismissRequest = { selectedTransactionForDetails = null },
            title = { Text(details.transaction.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Importo: ${mainViewModel.formatCurrency(details.transaction.amount)}", fontWeight = FontWeight.Medium)
                    val category = categories.find { it.id == details.transaction.categoryId }
                    val categoryDisplayName = if (category != null) {
                        if (category.parentCategoryId != null && category.parentCategoryId != 0) {
                            val parent = categories.find { it.id == category.parentCategoryId }
                            "${parent?.name ?: "Sconosciuto"} > ${category.name}"
                        } else {
                            category.name
                        }
                    } else {
                        "Senza Categoria"
                    }
                    Text(text = "Categoria: $categoryDisplayName")
                    
                    if (details.transaction.note.isNotBlank()) {
                        HorizontalDivider()
                        Text(text = "Note:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = details.transaction.note)
                    }
                    
                    if (details.tags.isNotEmpty()) {
                        HorizontalDivider()
                        Text(text = "Tag:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            details.tags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(tag.color), CircleShape)
                                        .border(width = 1.dp, color = Color.Black.copy(alpha = 0.2f), shape = CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = tag.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTransactionForDetails = null }) {
                    Text("Chiudi")
                }
            }
        )
    }

    // --- DIALOG CONFERMA MODIFICA ---
    if (showModifyConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showModifyConfirmDialog = null },
            title = { Text("Modifica Transazione", fontWeight = FontWeight.Bold) },
            text = { Text("Vuoi modificare i dettagli di questa transazione?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trans = showModifyConfirmDialog!!
                        showModifyConfirmDialog = null
                        navController.navigate("${Routes.MODIFY_TRANSACTION}/${trans.id}")
                    }
                ) {
                    Text("Sì, modifica")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModifyConfirmDialog = null }) {
                    Text("Annulla")
                }
            }
        )
    }
}
