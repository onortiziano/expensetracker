package it.ciano.expensetracker.ui.screens

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import it.ciano.expensetracker.R
import it.ciano.expensetracker.ui.theme.DarkGrey
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.data.ocr.ReceiptOcrEngine
import it.ciano.expensetracker.data.ocr.ReceiptCaptureManager
import it.ciano.expensetracker.data.ocr.ReceiptParser
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import android.util.Log
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

    // --- OCR RICEVUTA (quick-scan) ---
    var ocrProcessing by remember { mutableStateOf(false) }

    // Legge il risultato della schermata CameraX quando si torna indietro
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        val saved = navBackStackEntry?.savedStateHandle
        val path = saved?.get<String>("receipt_path")
        if (path != null) {
            val receiptPath = path
            saved["receipt_path"] = null
            ocrProcessing = true
            scope.launch {
                try {
                    val file = java.io.File(receiptPath)
                    Log.d("HomeOCR", "File exists: ${file.exists()}, size: ${file.length()}")
                    val uri = FileProvider.getUriForFile(context, ReceiptCaptureManager.AUTHORITY, file)
                    Log.d("HomeOCR", "URI: $uri")
                    val text = ReceiptOcrEngine.recognize(uri, context)
                    Log.d("HomeOCR", "OCR text: ${text?.take(200) ?: "NULL"}")
                    ocrProcessing = false
                    transactionViewModel.updateType("EXPENSE")
                    if (text != null) {
                        val parsed = ReceiptParser.parse(text)
                        Log.d("HomeOCR", "Parsed: amount=${parsed.amount}, date=${parsed.date}, title=${parsed.title}, category=${parsed.suggestedCategoryName}")
                        transactionViewModel.applyParsedReceipt(parsed, categories)
                    } else {
                        Log.w("HomeOCR", "OCR returned null")
                        Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                    }
                    transactionViewModel.updateReceipt(receiptPath)
                    navController.navigate(Routes.ADD_TRANSACTION)
                } catch (e: Exception) {
                    Log.e("HomeOCR", "Error in OCR flow: ${e.message}", e)
                    ocrProcessing = false
                    Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchCamera() {
        navController.navigate(Routes.CAMERA_CAPTURE)
    }

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
                        .background(DarkGrey)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.str_gestione_spese),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.str_home)) },
                    selected = true,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home, Icons.TwoTone.Home), contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.str_cronologia)) },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        navController.navigate(Routes.HISTORY) 
                    },
                    icon = { Icon(mainViewModel.getIcon(Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List, Icons.AutoMirrored.TwoTone.List), contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.str_impostazioni)) },
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
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = { 
                            scope.launch { drawerState.open() } 
                        }) {
                            Icon(mainViewModel.getIcon(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu, Icons.Sharp.Menu, Icons.TwoTone.Menu), contentDescription = stringResource(R.string.str_apri_menu))
                        }
                    }
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { launchCamera() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        if (ocrProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.str_scatta_ricevuta))
                        }
                    }
                    FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
                        Text("+", fontSize = 24.sp)
                    }
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
                                Text(text = stringResource(R.string.str_bilancio_totale), style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                                        Text(text = stringResource(R.string.str_entrate), fontSize = 12.sp, color = Color.Gray)
                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = stringResource(R.string.str_uscite), fontSize = 12.sp, color = Color.Gray)
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
                    Text(text = stringResource(R.string.str_importo_con, mainViewModel.formatCurrency(details.transaction.amount)), fontWeight = FontWeight.Medium)
                    val category = categories.find { it.id == details.transaction.categoryId }
                    val categoryDisplayName = if (category != null) {
                        if (category.parentCategoryId != null && category.parentCategoryId != 0) {
                            val parent = categories.find { it.id == category.parentCategoryId }
                            "${parent?.name ?: stringResource(R.string.str_sconosciuto)} > ${category.name}"
                        } else {
                            category.name
                        }
                    } else {
                        stringResource(R.string.str_senza_categoria)
                    }
                    Text(text = stringResource(R.string.str_categoria_con, categoryDisplayName))
                    
                    if (details.transaction.note.isNotBlank()) {
                        HorizontalDivider()
                        Text(text = stringResource(R.string.str_note_colon), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = details.transaction.note)
                    }
                    
                    if (details.tags.isNotEmpty()) {
                        HorizontalDivider()
                        Text(text = stringResource(R.string.str_tag_colon), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

                    if (details.transaction.receiptUri.isNotBlank()) {
                        HorizontalDivider()
                        Text(text = stringResource(R.string.str_ricevuta), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val bmp = remember(details.transaction.receiptUri) {
                            BitmapFactory.decodeFile(details.transaction.receiptUri)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.str_ricevuta),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(stringResource(R.string.str_nessuna_ricevuta_trovata))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTransactionForDetails = null }) {
                    Text(stringResource(R.string.str_chiudi))
                }
            }
        )
    }

    // --- DIALOG CONFERMA MODIFICA ---
    if (showModifyConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showModifyConfirmDialog = null },
            title = { Text(stringResource(R.string.str_modifica_transazione), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.str_conferma_modifica_transazione)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trans = showModifyConfirmDialog!!
                        showModifyConfirmDialog = null
                        navController.navigate("${Routes.MODIFY_TRANSACTION}/${trans.id}")
                    }
                ) {
                    Text(stringResource(R.string.str_si_modifica))
                }
            },
            dismissButton = {
                TextButton(onClick = { showModifyConfirmDialog = null }) {
                    Text(stringResource(R.string.str_annulla))
                }
            }
        )
    }
}
