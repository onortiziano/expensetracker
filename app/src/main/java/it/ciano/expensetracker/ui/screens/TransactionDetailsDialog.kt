package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.data.model.Transaction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailsDialog(
    transaction: Transaction,
    tags: List<Tag>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dettagli Transazione", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Titolo", transaction.title)
                DetailRow("Importo", "€${transaction.amount}")
                DetailRow("Tipo", if (transaction.type == "INCOME") "Entrata" else "Uscita")
                DetailRow("Note", transaction.note.ifBlank { "Nessuna nota" })
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tag", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                
                if (tags.isEmpty()) {
                    Text("Nessun tag associato", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { },
                                label = { Text(tag.name, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(tag.color).copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
