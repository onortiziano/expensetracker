1|package it.ciano.expensetracker.ui.screens
2|
3|import android.app.Application
4|import androidx.compose.foundation.background
5|import androidx.compose.foundation.clickable
6|import androidx.compose.foundation.layout.*
7|import androidx.compose.foundation.lazy.LazyColumn
8|import androidx.compose.foundation.lazy.items
9|import androidx.compose.material.icons.Icons
10|import androidx.compose.material.icons.automirrored.filled.List
11|import androidx.compose.material.icons.automirrored.outlined.List
12|import androidx.compose.material.icons.automirrored.rounded.List
13|import androidx.compose.material.icons.automirrored.sharp.List
14|import androidx.compose.material.icons.automirrored.twotone.List
15|import androidx.compose.material.icons.filled.Home
16|import androidx.compose.material.icons.filled.Menu
17|import androidx.compose.material.icons.filled.Settings
18|import androidx.compose.material.icons.outlined.Home
19|import androidx.compose.material.icons.outlined.Menu
20|import androidx.compose.material.icons.outlined.Settings
21|import androidx.compose.material.icons.rounded.Home
22|import androidx.compose.material.icons.rounded.Menu
23|import androidx.compose.material.icons.rounded.Settings
24|import androidx.compose.material.icons.sharp.Home
25|import androidx.compose.material.icons.sharp.Menu
26|import androidx.compose.material.icons.sharp.Settings
27|import androidx.compose.material.icons.twotone.Home
28|import androidx.compose.material.icons.twotone.Menu
29|import androidx.compose.material.icons.twotone.Settings
30|import androidx.compose.material3.*
31|import androidx.compose.runtime.*
32|import androidx.compose.ui.Alignment
33|import androidx.compose.ui.Modifier
34|import androidx.compose.ui.graphics.Color
35|import androidx.compose.ui.platform.LocalContext
36|import androidx.compose.ui.text.font.FontWeight
37|import androidx.compose.ui.unit.dp
38|import androidx.compose.ui.unit.sp
39|import androidx.lifecycle.viewmodel.compose.viewModel
40|import androidx.navigation.NavHostController
41|import it.ciano.expensetracker.ui.screens.Routes
42|import it.ciano.expensetracker.ui.viewmodel.MainViewModel
43|import it.ciano.expensetracker.ui.viewmodel.TransactionViewModel
44|import it.ciano.expensetracker.ui.viewmodel.ViewModelFactory
45|import it.ciano.expensetracker.data.model.Transaction
46|import it.ciano.expensetracker.data.model.Category
47|import kotlinx.coroutines.launch
48|import androidx.activity.compose.BackHandler
49|import it.ciano.expensetracker.ui.viewmodel.CategoryViewModel
50|import it.ciano.expensetracker.data.model.TransactionWithTags
51|
52|@OptIn(ExperimentalMaterial3Api::class)
53|@Composable
54|fun HomeScreen(navController: NavHostController) {
55|    // --- STATI E VIEWMODEL ---
56|    val context = LocalContext.current
57|    val app = context.applicationContext as Application
58|    val scope = rememberCoroutineScope()
59|    
60|    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
61|    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
62|    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
63|    
65|    
66|    // FIX: Usiamo allTransactionsWithTags per avere i tag nell'item
67|    val transactionsWithTags by transactionViewModel.allTransactionsWithTags.collectAsState(initial = emptyList())
68|    
69|    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
70|    
71|    // Stato per l'apertura/chiusura del menu laterale (Drawer)
72|    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
73|    // Intercetta il tasto indietro di sistema
74|    BackHandler(enabled = drawerState.isOpen) {
75|        scope.launch { drawerState.close() }
76|    }
77|
78|    // --- STRUTTURA CON NAVIGATION DRAWER ---
79|    ModalNavigationDrawer(
80|        drawerState = drawerState,
81|        drawerContent = {
82|            ModalDrawerSheet {
83|                // Intestazione del Menu
84|                Box(
85|                    modifier = Modifier
86|                        .fillMaxWidth()
87|                        .background(MaterialTheme.colorScheme.primary)
88|                        .padding(24.dp)
89|                ) {
90|                    Column {
91|                        Text(
92|                            text = "Expense Tracker",
93|                            color = Color.White,
94|                            fontSize = 20.sp,
95|                            fontWeight = FontWeight.Bold
96|                        )
97|                        Text(
98|                            text = "Gestione Spese",
99|                            color = Color.White.copy(alpha = 0.7f),
100|                            fontSize = 14.sp
101|                        )
102|                    }
103|                }
104|
105|                Spacer(modifier = Modifier.height(12.dp))
106|
107|                // Voci del Menu
108|                NavigationDrawerItem(
109|                    label = { Text("Home") },
110|                    selected = true,
111|                    onClick = { 
112|                        scope.launch { drawerState.close() }
113|                        navController.navigate(Routes.HOME) 
114|                    },
115|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home, Icons.TwoTone.Home), contentDescription = null) },
116|                    modifier = Modifier.padding(horizontal = 12.dp)
117|                )
118|                
119|                NavigationDrawerItem(
120|                    label = { Text("Cronologia") },
121|                    selected = false,
122|                    onClick = { 
123|                        scope.launch { drawerState.close() }
124|                        navController.navigate(Routes.HISTORY) 
125|                    },
126|                    icon = { Icon(mainViewModel.getIcon(Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List, Icons.AutoMirrored.TwoTone.List), contentDescription = null) },
127|                    modifier = Modifier.padding(horizontal = 12.dp)
128|                )
129|                
130|                NavigationDrawerItem(
131|                    label = { Text("Impostazioni") },
132|                    selected = false,
133|                    onClick = { 
134|                        scope.launch { drawerState.close() }
135|                        navController.navigate(Routes.SETTINGS) 
136|                    },
137|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings, Icons.TwoTone.Settings), contentDescription = null) },
138|                    modifier = Modifier.padding(horizontal = 12.dp)
139|                )
140|            }
141|        }
142|    ) {
143|        // --- CONTENUTO PRINCIPALE ---
144|        Scaffold(
145|            topBar = {
146|                CenterAlignedTopAppBar(
147|                    title = { Text("Expense Tracker") },
148|                    navigationIcon = {
149|                        IconButton(onClick = { 
150|                            scope.launch { drawerState.open() } 
151|                        }) {
152|                            Icon(mainViewModel.getIcon(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu, Icons.Sharp.Menu, Icons.TwoTone.Menu), contentDescription = "Apri Menu")
153|                        }
154|                    }
155|                )
156|            },
157|            floatingActionButton = {
158|                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
159|                    Text("+", fontSize = 24.sp)
160|                }
161|            }
162|        ) { paddingValues ->
163|            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
164|                LazyColumn(
165|                    modifier = Modifier.fillMaxSize(),
166|                    contentPadding = PaddingValues(16.dp),
167|                    verticalArrangement = Arrangement.spacedBy(12.dp)
168|                ) {
169|                    // --- CARD RIEPILOGO BILANCIO ---
170|                    item {
171|                        val totalIncome by transactionViewModel.totalIncome.collectAsState(initial = 0.0)
172|                        val totalExpenses by transactionViewModel.totalExpenses.collectAsState(initial = 0.0)
173|                        val balance = (totalIncome ?: 0.0) - (totalExpenses ?: 0.0)
174|
175|                        ElevatedCard(
176|                            modifier = Modifier.fillMaxWidth(),
177|                            colors = CardDefaults.elevatedCardColors(
178|                                containerColor = MaterialTheme.colorScheme.surfaceVariant
179|                            )
180|                        ) {
181|                            Column(
182|                                modifier = Modifier.padding(20.dp),
183|                                horizontalAlignment = Alignment.CenterHorizontally,
184|                                verticalArrangement = Arrangement.spacedBy(8.dp)
185|                            ) {
186|                                Text(text = "Bilancio Totale", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
187|                                Text(
188|                                    text = mainViewModel.formatCurrency(balance),
189|                                    style = MaterialTheme.typography.headlineMedium,
190|                                    fontWeight = FontWeight.Bold,
191|                                    color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red
192|                                )
193|                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
194|                                Row(
195|                                    modifier = Modifier.fillMaxWidth(),
196|                                    horizontalArrangement = Arrangement.SpaceBetween
197|                                ) {
198|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
199|                                        Text(text = "Entrate", fontSize = 12.sp, color = Color.Gray)
200|                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome ?: 0.0).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
201|                                    }
202|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
203|                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
204|                                        Text(text = "-" + mainViewModel.formatCurrency(totalExpenses ?: 0.0).removePrefix("-"), color = Color.Red, fontWeight = FontWeight.Bold)
205|                                    }
206|                                }
207|                            }
208|                        }
209|                    }
210|                    
211|                    items(transactionsWithTags) { item ->
212|                        TransactionItem(
213|                            transaction = item.transaction, 
214|                            tags = item.tags,
215|                            onDetailsRequest = { selectedTransaction = item.transaction },
216|                            onModifyRequest = { 
217|                                navController.navigate("${Routes.MODIFY_TRANSACTION}/${item.transaction.transactionId}") 
218|                            }
219|                        )
220|                    }
221|                }
222|            }
223|        }
224|        if (selectedTransaction != null) {
225|            val transaction = selectedTransaction!!
226|            val tags = transactionsWithTags.find { it.transaction.transactionId == transaction.transactionId }?.tags ?: emptyList()
227|            TransactionDetailsDialog(transaction = transaction, tags = tags, onDismiss = { selectedTransaction = null })
228|        }
229|    }
230|}
231|