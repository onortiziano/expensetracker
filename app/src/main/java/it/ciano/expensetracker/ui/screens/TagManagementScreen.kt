package it.ciano.expensetracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import it.ciano.expensetracker.data.model.Tag
import it.ciano.expensetracker.ui.viewmodel.TagViewModel
import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagManagementScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(app))
    val tags by tagViewModel.allTags.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var showModifyConfirmDialog by remember { mutableStateOf<Tag?>(null) }
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
                        onEditRequest = {
                            showModifyConfirmDialog = tag
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
                    scope.launch {
                        if (tagToEdit == null) {
                            tagViewModel.addTag(name, color)
                        } else {
                            val updated = tagToEdit!!.copy(name = name, color = color)
                            tagViewModel.updateTag(updated)
                        }
                    }
                    showDialog = false
                    tagToEdit = null
                }
            )
        }

        if (showModifyConfirmDialog != null) {
            AlertDialog(
                onDismissRequest = { showModifyConfirmDialog = null },
                title = { Text("Modifica Tag", fontWeight = FontWeight.Bold) },
                text = { Text("Vuoi modificare i dettagli di questo tag?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tagToEdit = showModifyConfirmDialog
                            showModifyConfirmDialog = null
                            showDialog = true
                        }
                    ) { Text("Sì, modifica") }
                },
                dismissButton = {
                    TextButton(onClick = { showModifyConfirmDialog = null }) { Text("Annulla") }
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
    onEditRequest: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
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
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { /* No action */ },
                    onLongClick = onEditRequest
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDialog(
    tag: Tag?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    var selectedColor by remember { mutableStateOf(tag?.color ?: android.graphics.Color.BLUE) }
    
    val availableColors = listOf(
        android.graphics.Color.RED,
        android.graphics.Color.BLUE,
        android.graphics.Color.GREEN,
        android.graphics.Color.YELLOW,
        android.graphics.Color.MAGENTA,
        android.graphics.Color.CYAN,
        android.graphics.Color.DKGRAY,
        android.graphics.Color.BLACK
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "Nuovo Tag" else "Modifica Tag", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Tag") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Scegli Colore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = Color(color),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(
                                        width = if (selectedColor == color) 3.dp else 1.dp,
                                        color = if (selectedColor == color) Color.Black else Color.Gray,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clickable { selectedColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == color) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (color == android.graphics.Color.BLACK || color == android.graphics.Color.DKGRAY) Color.White else Color.Black
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
                    onConfirm(name, selectedColor)
                },
                enabled = name.isNotBlank()
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
