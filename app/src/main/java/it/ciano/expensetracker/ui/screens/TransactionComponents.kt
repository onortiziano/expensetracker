package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    tags: List<Tag>,
    onDetailsRequest: () -> Unit,
    onModifyRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onDetailsRequest,
                onLongClick = onModifyRequest
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (transaction.note.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Note,
                            contentDescription = "Ha note",
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.take(2).forEach { tag ->
                            Surface(
                                color = Color(tag.color).copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {                                
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(tag.color)
                                )
                            }
                        }
                        if (tags.size > 2) {
                            Text(
                                text = "+${tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Text(
                text = if (transaction.type == "EXPENSE") "-€${String.format("%.2f", transaction.amount)}" else "+€${String.format("%.2f", transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == "EXPENSE") Color(0xFFF44336) else Color(0xFF4CAF50)
            )
        }
    }
}
