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
55|    val context = LocalContext.current
56|    val app = context.applicationContext as Application
57|    val scope = rememberCoroutineScope()
58|    
59|    val transactionViewModel: TransactionViewModel = viewModel(factory = ViewModelFactory(app))
60|    val mainViewModel: MainViewModel = viewModel(factory = ViewModelFactory(app))
61|    val categoryViewModel: CategoryViewModel = viewModel(factory = ViewModelFactory(app))
62|    
64|    val transactionsWithTags by transactionViewModel.allTransactionsWithTags.collectAsState(initial = emptyList())
65|    
66|    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
67|    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
68|    
69|    BackHandler(enabled = drawerState.isOpen) {
70|        scope.launch { drawerState.close() }
71|    }
72|
73|    ModalNavigationDrawer(
74|        drawerState = drawerState,
75|        drawerContent = {
76|            ModalDrawerSheet {
77|                Box(
78|                    modifier = Modifier
79|                        .fillMaxWidth()
80|                        .background(MaterialTheme.colorScheme.primary)
81|                        .padding(24.dp)
82|                ) {
83|                    Column {
84|                        Text(text = "Expense Tracker", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
85|                        Text(text = "Gestione Spese", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
86|                    }
87|                }
88|
89|                Spacer(modifier = Modifier.height(12.dp))
90|
91|                NavigationDrawerItem(
92|                    label = { Text("Home") },
93|                    selected = true,
94|                    onClick = { 
95|                        scope.launch { drawerState.close() }
96|                        navController.navigate(Routes.HOME) 
97|                    },
98|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home, Icons.TwoTone.Home), contentDescription = null) },
99|                    modifier = Modifier.padding(horizontal = 12.dp)
100|                )
101|                
102|                NavigationDrawerItem(
103|                    label = { Text("Cronologia") },
104|                    selected = false,
105|                    onClick = { 
106|                        scope.launch { drawerState.close() }
107|                        navController.navigate(Routes.HISTORY) 
108|                    },
109|                    icon = { Icon(mainViewModel.getIcon(Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List, Icons.AutoMirrored.TwoTone.List), contentDescription = null) },
110|                    modifier = Modifier.padding(horizontal = 12.dp)
111|                )
112|                
113|                NavigationDrawerItem(
114|                    label = { Text("Impostazioni") },
115|                    selected = false,
116|                    onClick = { 
117|                        scope.launch { drawerState.close() }
118|                        navController.navigate(Routes.SETTINGS) 
119|                    },
120|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings, Icons.TwoTone.Settings), contentDescription = null) },
121|                    modifier = Modifier.padding(horizontal = 12.dp)
122|                )
123|            }
124|        }
125|    ) {
126|        Scaffold(
127|            topBar = {
128|                CenterAlignedTopAppBar(
129|                    title = { Text("Expense Tracker") },
130|                    navigationIcon = {
131|                        IconButton(onClick = { 
132|                            scope.launch { drawerState.open() } 
133|                        }) {
134|                            Icon(mainViewModel.getIcon(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu, Icons.Sharp.Menu, Icons.TwoTone.Menu), contentDescription = "Apri Menu")
135|                        }
136|                    }
137|                )
138|            },
139|            floatingActionButton = {
140|                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
141|                    Text("+", fontSize = 24.sp)
142|                }
143|            }
144|        ) { paddingValues ->
145|            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
146|                LazyColumn(
147|                    modifier = Modifier.fillMaxSize(),
148|                    contentPadding = PaddingValues(16.dp),
149|                    verticalArrangement = Arrangement.spacedBy(12.dp)
150|                ) {
151|                    item {
152|                        val totalIncome by transactionViewModel.totalIncome.collectAsState(initial = 0.0)
153|                        val totalExpenses by transactionViewModel.totalExpenses.collectAsState(initial = 0.0)
154|                        val balance = (totalIncome ?: 0.0) - (totalExpenses ?: 0.0)
155|
156|                        ElevatedCard(
157|                            modifier = Modifier.fillMaxWidth(),
158|                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
159|                        ) {
160|                            Column(
161|                                modifier = Modifier.padding(20.dp),
162|                                horizontalAlignment = Alignment.CenterHorizontally,
163|                                verticalArrangement = Arrangement.spacedBy(8.dp)
164|                            ) {
165|                                Text(text = "Bilancio Totale", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
166|                                Text(
167|                                    text = mainViewModel.formatCurrency(balance),
168|                                    style = MaterialTheme.typography.headlineMedium,
169|                                    fontWeight = FontWeight.Bold,
170|                                    color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red
171|                                )
172|                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
173|                                Row(
174|                                    modifier = Modifier.fillMaxWidth(),
175|                                    horizontalArrangement = Arrangement.SpaceBetween
176|                                ) {
177|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
178|                                        Text(text = "Entrate", fontSize = 12.sp, color = Color.Gray)
179|                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome ?: 0.0).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
180|                                    }
181|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
182|                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
183|                                        Text(text = "-" + mainViewModel.formatCurrency(totalExpenses ?: 0.0).removePrefix("-"), color = Color.Red, fontWeight = FontWeight.Bold)
184|                                    }
185|                                }
186|                            }
187|                        }
188|                    }
189|                    
190|                    items(transactionsWithTags) { item ->
191|                        TransactionItem(
192|                            transaction = item.transaction, 
193|                            tags = item.tags,
194|                            onDetailsRequest = { selectedTransaction = item.transaction },
195|                            onModifyRequest = { 
196|                                navController.navigate("${Routes.MODIFY_TRANSACTION}/${item.transaction.transactionId}") 
197|                            }
198|                        )
199|                    }
200|                }
201|            }
202|        }
203|        if (selectedTransaction != null) {
204|            val transaction = selectedTransaction!!
205|            val tags = transactionsWithTags.find { it.transaction.transactionId == transaction.transactionId }?.tags ?: emptyList()
206|            TransactionDetailsDialog(transaction = transaction, tags = tags, onDismiss = { selectedTransaction = null })
207|        }
208|    }
209|}
210|