package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ciano.expensetracker.data.model.Category
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction

@Composable
fun TransactionItem(
    transaction: Transaction,
    tags: List<Tag>,
    onDetailsRequest: () -> Unit,
    onModifyRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onDetailsRequest() },
        onClick = { onDetailsRequest() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = transaction.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(tag.name, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(tag.color).copy(alpha = 0.2f))
                        )
                    }
                }
            }
            Text(
                text = if (transaction.type == "INCOME") "+€${transaction.amount}" else "-€${transaction.amount}",
                color = if (transaction.type == "INCOME") Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onModifyRequest() }) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Modifica")
            }
        }
    }
}

@Composable
fun CategorySelector(categories: List<Category>, selectedCategoryId: Int?, onCategorySelected: (Int) -> Unit, onParentSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { expanded = true }, onClick = { expanded = true }) {
            Text(text = categories.find { it.categoryId == selectedCategoryId }?.name ?: "Seleziona Categoria", modifier = Modifier.padding(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.filter { it.parentCategoryId == null }.forEach { parent ->
                DropdownMenuItem(text = { Text(parent.name) }, onClick = { onCategorySelected(parent.categoryId); expanded = false })
            }
        }
    }
}

@Composable
fun TagSelector(tags: List<Tag>, selectedTags: Set<Int>, onTagToggled: (Int) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag ->
            FilterChip(selected = selectedTags.contains(tag.tagId), onClick = { onTagToggled(tag.tagId) }, label = { Text(tag.name) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(tag.color)))
        }
    }
}
