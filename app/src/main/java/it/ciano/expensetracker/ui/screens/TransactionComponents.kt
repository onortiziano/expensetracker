package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import it.ciano.expensetracker.data.model.Transaction
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.TransactionWithTags
import it.ciano.expensetracker.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transactionWithTags: TransactionWithTags, 
    mainViewModel: MainViewModel,
    categories: List<Category>,
    onDeleteRequest: (Transaction) -> Unit,
    onSingleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val transaction = transactionWithTags.transaction
    val tags = transactionWithTags.tags
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
                        onClick = onSingleClick,
                        onLongClick = onLongClick
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
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Notes, 
                                    contentDescription = "Nota presente", 
                                    tint = Color.Gray, 
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        
                        val category = categories.find { it.id == transaction.categoryId }
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
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Categoria: $categoryDisplayName", 
                                fontSize = 12.sp, 
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            if (tags.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val visibleTags = tags.take(2)
                                    for (tag in visibleTags) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(tag.color), CircleShape)
                                                .border(width = 1.dp, color = Color.Black.copy(alpha = 0.2f), shape = CircleShape)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = tag.name, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                    if (tags.size > 2) {
                                        Text(
                                            text = "+${tags.size - 2}", 
                                            fontSize = 10.sp, 
                                            color = Color.Gray, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = if (transaction.type == "INCOME") 
                            "+" + mainViewModel.formatCurrency(transaction.amount).removePrefix("+") 
                            else "-" + mainViewModel.formatCurrency(transaction.amount).removePrefix("-"),
                        color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    )
}
