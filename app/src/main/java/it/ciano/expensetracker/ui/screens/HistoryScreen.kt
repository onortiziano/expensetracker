package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.R
import it.ciano.expensetracker.ui.screens.Routes
import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
import it.ciano.expensetracker.ui.viewmodel.MainViewModel
import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavHostController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as android.app.Application
    
    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
    
    val transactionsWithTags by transactionViewModel.transactionsWithTags.collectAsState()
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_cronologia), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
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
        if (transactionsWithTags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.str_nessuna_transazione), fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(transactionsWithTags) { item ->
                    TransactionItem(
                        transactionWithTags = item, 
                        mainViewModel = mainViewModel,
                        categories = categories,
                        onDeleteRequest = { trans ->
                            transactionViewModel.deleteTransaction(trans)
                        },
                        onSingleClick = { 
                            navController.navigate("${Routes.MODIFY_TRANSACTION}/${item.transaction.id}") 
                        },
                        onLongClick = {
                            // In History non gestiamo il long click specificamente
                        }
                    )
                }
            }
        }
    }
}
