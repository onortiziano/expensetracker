package it.ciano.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Process
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.core.content.FileProvider
import it.ciano.expensetracker.R
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.ocr.ReceiptOcrEngine
import it.ciano.expensetracker.data.ocr.ReceiptParser
import it.ciano.expensetracker.data.ocr.ReceiptStorage
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.TagViewModel
import it.ciano.expensetracker.ui.viewmodel.SettingsViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))
    val settingsViewModel: SettingsViewModel = viewModel(factory = ViewModelFactory(app))

    val title by transactionViewModel.title.collectAsState()
    val amount by transactionViewModel.amount.collectAsState()
    val note by transactionViewModel.note.collectAsState()
    val type by transactionViewModel.type.collectAsState()
    val selectedMainCategoryId by transactionViewModel.selectedMainCategoryId.collectAsState()
    val selectedSubCategoryId by transactionViewModel.selectedSubCategoryId.collectAsState()
    val selectedTags by transactionViewModel.selectedTags.collectAsState()

    val allCategories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val mainCategories by categoryViewModel.mainCategories.collectAsState(initial = emptyList())
    val categoryMap by categoryViewModel.categoryMap.collectAsState()
    val allTags by tagViewModel.allTags.collectAsState(initial = emptyList())
    val separator = settingsViewModel.decimalSeparator.collectAsState().value

    // --- GESTIONE DATA ---
    val selectedDate by transactionViewModel.selectedDate.collectAsState()
    val effectiveDate = if (selectedDate != 0L) selectedDate else Calendar.getInstance().timeInMillis
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                transactionViewModel.updateDate(calendar.timeInMillis)
            },
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryBudget by remember { mutableStateOf("") }
    var selectedParentId by remember { mutableStateOf<Int?>(null) }
    var categoryType by remember { mutableStateOf("MAIN") }
    
    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf(0xFF6200EE.toInt()) }

    // --- OCR RICEVUTA ---
    var ocrProcessing by remember { mutableStateOf(false) }

    // Legge il risultato della schermata CameraX quando si torna indietro
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        val saved = navBackStackEntry?.savedStateHandle
        val path = saved?.get<String>("receipt_path")
        if (path != null) {
            saved["receipt_path"] = null
            ocrProcessing = true
            scope.launch {
                try {
                    val file = java.io.File(path)
                    val uri = FileProvider.getUriForFile(context, ReceiptStorage.AUTHORITY, file)
                    val result = ReceiptOcrEngine.recognize(uri, context)
                    ocrProcessing = false
                    if (result != null) {
                        val parsed = ReceiptParser.parse(result, separator)
                        transactionViewModel.applyParsedReceipt(parsed, allCategories, separator)
                    } else {
                        Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                    }
                    transactionViewModel.updateReceipt(path)
                } catch (e: Exception) {
                    ocrProcessing = false
                    Toast.makeText(context, context.getString(R.string.str_ocr_fallita), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchCamera() {
        navController.navigate(Routes.CAMERA_CAPTURE)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_nuova_transazione), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.str_torna_indietro))
                    }
                },
                actions = {
                    IconButton(onClick = { launchCamera() }, enabled = !ocrProcessing) {
                        if (ocrProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.str_scatta_ricevuta))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            val screenHeight = maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .heightIn(min = screenHeight)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = stringResource(R.string.str_dettagli_transazione), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        
                        // DATA SELECTOR
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = dateFormat.format(Date(effectiveDate)),
                                onValueChange = {},
                                label = { Text(stringResource(R.string.str_data)) },
                                modifier = Modifier.fillMaxWidth(),
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                            )
                            // Overlay trasparente che intercetta il click
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { datePickerDialog.show() }
                            )
                        }

                        OutlinedTextField(
                            value = title,
                            onValueChange = { transactionViewModel.updateTitle(it) },
                            label = { Text(stringResource(R.string.str_titolo)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { transactionViewModel.updateAmount(it) },
                            label = { Text(stringResource(R.string.str_importo)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = note,
                            onValueChange = { transactionViewModel.updateNote(it) },
                            label = { Text(stringResource(R.string.str_note)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )

                        if (transactionViewModel.receiptUri.value.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                Text(stringResource(R.string.str_ricevuta), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = stringResource(R.string.str_classificazione), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        Text(text = stringResource(R.string.str_tipo_operazione), fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = type == "EXPENSE",
                                onClick = { transactionViewModel.updateType("EXPENSE") },
                                label = { Text(stringResource(R.string.str_uscita)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = type == "INCOME",
                                onClick = { transactionViewModel.updateType("INCOME") },
                                label = { Text(stringResource(R.string.str_entrata)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(text = stringResource(R.string.str_categoria_principale), fontSize = 13.sp)
                        var mainExpanded by remember { mutableStateOf(false) }
                        val mainCategoryName = categoryMap[selectedMainCategoryId] ?: stringResource(R.string.str_scegli_categoria)

                        ExposedDropdownMenuBox(
                            expanded = mainExpanded,
                            onExpandedChange = { mainExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = mainCategoryName,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.str_categoria_principale)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = mainExpanded,
                                onDismissRequest = { mainExpanded = false }
                            ) {
                                mainCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            transactionViewModel.updateMainCategory(category.id)
                                            mainExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.str_aggiungi_nuova), color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        mainExpanded = false
                                        showAddCategoryDialog = true
                                    }
                                )
                            }
                        }

                        val subCategories = allCategories.filter { it.parentCategoryId == selectedMainCategoryId }
                        if (selectedMainCategoryId != 0 && subCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = stringResource(R.string.str_sottocategoria), fontSize = 13.sp)
                            var subExpanded by remember { mutableStateOf(false) }
                            val subCategoryName = categoryMap[selectedSubCategoryId] ?: stringResource(R.string.str_scegli_sottocategoria)

                            ExposedDropdownMenuBox(
                                expanded = subExpanded,
                                onExpandedChange = { subExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    readOnly = true,
                                    value = subCategoryName,
                                    onValueChange = {},
                                    label = { Text(stringResource(R.string.str_sottocategoria)) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = subExpanded,
                                    onDismissRequest = { subExpanded = false }
                                ) {
                                    subCategories.forEach { sub ->
                                        DropdownMenuItem(
                                            text = { Text(sub.name) },
                                            onClick = {
                                                transactionViewModel.updateSubCategory(sub.id)
                                                subExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.str_tag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { showAddTagDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.str_aggiungi_tag), fontSize = 12.sp)
                            }
                        }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTags.contains(tag.tagId),
                                    onClick = { transactionViewModel.toggleTag(tag.tagId) },
                                    label = { Text(tag.name) },
                                    modifier = Modifier.background(Color(tag.color))
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    val sepChar = separator.firstOrNull() ?: ','
                    val containsInvalidChars = amount.any { it.isDigit() == false && it != sepChar }
                    val hasMultipleSeparators = amount.count { it == sepChar } > 1
                    val normalizedAmount = amount.replace(separator, ".")
                    val numericValue = normalizedAmount.toDoubleOrNull() ?: 0.0
                    
                    val isFormValid = title.isNotBlank() && 
                                      !containsInvalidChars && 
                                      !hasMultipleSeparators && 
                                      numericValue > 0.0
                    
                    Button(
                        onClick = {
                            val amountValue = normalizedAmount.toDoubleOrNull() ?: 0.0
                            val finalCategoryId = if (selectedSubCategoryId != 0) selectedSubCategoryId else selectedMainCategoryId
                            
                            val transaction = Transaction(
                                title = title,
                                amount = amountValue,
                                type = type,
                                categoryId = finalCategoryId,
                                note = note,
                                date = effectiveDate,
                                receiptUri = transactionViewModel.receiptUri.value
                            )
                            transactionViewModel.addTransaction(transaction, transactionViewModel.selectedTags.value)
                            navController.popBackStack()
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.str_salva_transazione), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        if (showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddCategoryDialog = false
                    newCategoryName = ""
                    newCategoryBudget = ""
                    selectedParentId = null
                    categoryType = "MAIN"
                },
                title = { Text(stringResource(R.string.str_nuova_categoria), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text(stringResource(R.string.str_nome_categoria)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        val normalizedBudget = newCategoryBudget.replace(separator, ".")
                        val containsWrongSeparator = (separator == "," && newCategoryBudget.contains(".")) || (separator == "." && newCategoryBudget.contains(","))
                        val isBudgetValid = newCategoryBudget.isEmpty() || (!containsWrongSeparator && normalizedBudget.toDoubleOrNull() != null)

                        OutlinedTextField(
                            value = newCategoryBudget,
                            onValueChange = { newCategoryBudget = it },
                            label = { Text(stringResource(R.string.str_budget_opzionale)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = !isBudgetValid,
                            singleLine = true
                        )
                        if (!isBudgetValid) {
                            Text(
                                text = stringResource(R.string.str_numero_non_valido),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = stringResource(R.string.str_tipo_categoria), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = categoryType == "MAIN",
                                    onClick = { 
                                        categoryType = "MAIN"
                                        selectedParentId = null 
                                    },
                                    label = { Text(stringResource(R.string.str_principale)) }
                                )
                                FilterChip(
                                    selected = categoryType == "SUB",
                                    onClick = { categoryType = "SUB" },
                                    label = { Text(stringResource(R.string.str_sottocategoria)) }
                                )
                            }
                        }

                        if (categoryType == "SUB") {
                            var parentExpanded by remember { mutableStateOf(false) }
                            val parentName = selectedParentId?.let { categoryMap[it] } ?: stringResource(R.string.str_seleziona_padre)
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = stringResource(R.string.str_sottocategoria_di), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                ExposedDropdownMenuBox(
                                    expanded = parentExpanded,
                                    onExpandedChange = { parentExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        readOnly = true,
                                        value = parentName,
                                        onValueChange = {},
                                        label = { Text(stringResource(R.string.str_scegli_padre)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = parentExpanded,
                                        onDismissRequest = { parentExpanded = false }
                                    ) {
                                        mainCategories.forEach { parent ->
                                            DropdownMenuItem(
                                                text = { Text(parent.name) },
                                                onClick = {
                                                    selectedParentId = parent.id
                                                    parentExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                val isDuplicate = allCategories.any { 
                                    it.name == newCategoryName && it.parentCategoryId == selectedParentId 
                                }
                                
                                if (isDuplicate) {
                                    return@launch 
                                }

                                if (newCategoryName.isNotBlank() && (categoryType == "MAIN" || selectedParentId != null)) {
                                    val budgetValue = newCategoryBudget.replace(separator, ".").toDoubleOrNull()
                                    val newId = categoryViewModel.addCategory(
                                        Category(name = newCategoryName, budget = budgetValue, parentCategoryId = selectedParentId)
                                    ).toInt()
                                    
                                    if (categoryType == "MAIN") {
                                        transactionViewModel.updateMainCategory(newId)
                                    } else {
                                        val parentId = selectedParentId ?: 0
                                        transactionViewModel.updateSubCategory(newId)
                                    }
                                    
                                    showAddCategoryDialog = false
                                    newCategoryName = ""
                                    newCategoryBudget = ""
                                    selectedParentId = null
                                    categoryType = "MAIN"
                                }
                            }
                        },
                        enabled = newCategoryName.isNotBlank() && (categoryType == "MAIN" || selectedParentId != null)
                    ) { Text(stringResource(R.string.str_salva)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text(stringResource(R.string.str_annulla))
                    }
                }
            )
        }

        if (showAddTagDialog) {
            AlertDialog(
                onDismissRequest = { showAddTagDialog = false },
                title = { Text(stringResource(R.string.str_nuovo_tag), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            label = { Text(stringResource(R.string.str_nome_tag)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Text(stringResource(R.string.str_colore_tag), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val colors = listOf(0xFFF44336.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFFFFEB3B.toInt(), 0xFF9C27B0.toInt(), 0xFFFF9800.toInt())
                            colors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(color), CircleShape)
                                        .clickable { newTagColor = color }
                                        .border(
                                            width = if (newTagColor == color) 3.dp else 0.dp,
                                            color = Color.Black,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (newTagName.isNotBlank()) {
                                    tagViewModel.addTag(newTagName, newTagColor)
                                    showAddTagDialog = false
                                    newTagName = ""
                                }
                            }
                        },
                        enabled = newTagName.isNotBlank()
                    ) { Text(stringResource(R.string.str_crea)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddTagDialog = false }) {
                        Text(stringResource(R.string.str_annulla))
                    }
                }
            )
        }
    }
}
