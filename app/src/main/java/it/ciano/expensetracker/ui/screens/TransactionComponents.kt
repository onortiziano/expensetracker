package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction, 
    mainViewModel: MainViewModel,
    categories: List<Category>,
    tags: List<Tag>,
    onDeleteRequest: (Transaction) -> Unit,
    onDetailsRequest: (Transaction) -> Unit,
    onModifyRequest: (Transaction) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                showDeleteDialog = true
            }
            false
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Elimina Transazione") },
            text = { Text(text = "Sei sicuro di voler eliminare questa voce? L'operazione non può essere annullata.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRequest(transaction)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Sì, elimina", color = Color.Red)
                }
            },
            dismissButton = { 
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onDetailsRequest(transaction) },
                        onLongClick = { onModifyRequest(transaction) }
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = transaction.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (transaction.note.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "📝", fontSize = 14.sp)
                            }
                        }
                        
                        // Gestione Tag (max 2 + n)
                        if (tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.take(2).forEach { tag ->
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text(tag.name, fontSize = 10.sp) },
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                                if (tags.size > 2) {
                                    Text(
                                        text = "+${tags.size - 2}",
                                        fontSize = 10.sp,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

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
                        
                        Text(text = "Categoria: $categoryDisplayName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = if (transaction.type == "INCOME") 
                            "+" + mainViewModel.formatCurrency(transaction.amount).removePrefix("+") 
                            else "-" + mainViewModel.formatCurrency(transaction.amount).removePrefix("-"),
                        color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    )
}
