package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.ui.viewmodel.TagViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagManagementScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))
    val tags by tagViewModel.allTags.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestione Tag", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Torna indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                tagToEdit = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi Tag")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Elenco Tag",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tags) { tag ->
                    TagSwipeItem(
                        tag = tag,
                        onEdit = {
                            tagToEdit = tag
                            showDialog = true
                        },
                        onDeleteRequest = {
                            tagToDelete = tag
                        }
                    )
                }
            }
        }

        if (showDialog) {
            TagDialog(
                tag = tagToEdit,
                onDismiss = { 
                    showDialog = false 
                    tagToEdit = null
                },
                onConfirm = { name, color ->
                    if (tagToEdit == null) {
                        tagViewModel.addTag(name, color)
                    } else {
                        val updated = tagToEdit!!.copy(name = name, color = color)
                        tagViewModel.updateTag(updated)
                    }
                    showDialog = false
                    tagToEdit = null
                }
            )
        }

        if (tagToDelete != null) {
            AlertDialog(
                onDismissRequest = { tagToDelete = null },
                title = { Text("Elimina Tag") },
                text = { Text("Sei sicuro di voler eliminare '${tagToDelete?.name}'? Questa azione non può essere annullata.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tagViewModel.deleteTag(tagToDelete!!)
                            tagToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text("Elimina") }
                },
                dismissButton = {
                    TextButton(onClick = { tagToDelete = null }) { Text("Annulla") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagSwipeItem(
    tag: Tag,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteRequest()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.currentValue) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> Color.Red
                else -> Color.Transparent
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .background(color, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = MaterialTheme.colorScheme.primary)
                } else if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color.White)
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { /* No action */ },
                    onLongClick = onEdit
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color(tag.color)
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TagDialog(
    tag: Tag?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    // Per semplicità nel dialog, usiamo un colore predefinito o quello esistente.
    // L'implementazione di un color picker completo richiederebbe un componente dedicato.
    var color by remember { mutableStateOf(tag?.color ?: android.graphics.Color.BLUE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "Nuovo Tag" else "Modifica Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Tag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(text = "Colore: ${if (tag == null) "Predefinito (Blu)" else "Mantiene colore attuale"}", 
                     style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name, color)
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
