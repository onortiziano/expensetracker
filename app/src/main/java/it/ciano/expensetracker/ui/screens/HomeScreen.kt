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
62|    
63|    val transactionsWithTags by transactionViewModel.allTransactionsWithTags.collectAsState(initial = emptyList())
64|    
65|    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
66|    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
67|    
68|    BackHandler(enabled = drawerState.isOpen) {
69|        scope.launch { drawerState.close() }
70|    }
71|
72|    ModalNavigationDrawer(
73|        drawerState = drawerState,
74|        drawerContent = {
75|            ModalDrawerSheet {
76|                Box(
77|                    modifier = Modifier
78|                        .fillMaxWidth()
79|                        .background(MaterialTheme.colorScheme.primary)
80|                        .padding(24.dp)
81|                ) {
82|                    Column {
83|                        Text(text = "Expense Tracker", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
84|                        Text(text = "Gestione Spese", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
85|                    }
86|                }
87|
88|                Spacer(modifier = Modifier.height(12.dp))
89|
90|                NavigationDrawerItem(
91|                    label = { Text("Home") },
92|                    selected = true,
93|                    onClick = { 
94|                        scope.launch { drawerState.close() }
95|                        navController.navigate(Routes.HOME) 
96|                    },
97|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Home, Icons.Outlined.Home, Icons.Rounded.Home, Icons.Sharp.Home, Icons.TwoTone.Home), contentDescription = null) },
98|                    modifier = Modifier.padding(horizontal = 12.dp)
99|                )
100|                
101|                NavigationDrawerItem(
102|                    label = { Text("Cronologia") },
103|                    selected = false,
104|                    onClick = { 
105|                        scope.launch { drawerState.close() }
106|                        navController.navigate(Routes.HISTORY) 
107|                    },
108|                    icon = { Icon(mainViewModel.getIcon(Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Sharp.List, Icons.AutoMirrored.TwoTone.List), contentDescription = null) },
109|                    modifier = Modifier.padding(horizontal = 12.dp)
110|                )
111|                
112|                NavigationDrawerItem(
113|                    label = { Text("Impostazioni") },
114|                    selected = false,
115|                    onClick = { 
116|                        scope.launch { drawerState.close() }
117|                        navController.navigate(Routes.SETTINGS) 
118|                    },
119|                    icon = { Icon(mainViewModel.getIcon(Icons.Filled.Settings, Icons.Outlined.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings, Icons.TwoTone.Settings), contentDescription = null) },
120|                    modifier = Modifier.padding(horizontal = 12.dp)
121|                )
122|            }
123|        }
124|    ) {
125|        Scaffold(
126|            topBar = {
127|                CenterAlignedTopAppBar(
128|                    title = { Text("Expense Tracker") },
129|                    navigationIcon = {
130|                        IconButton(onClick = { 
131|                            scope.launch { drawerState.open() } 
132|                        }) {
133|                            Icon(mainViewModel.getIcon(Icons.Filled.Menu, Icons.Outlined.Menu, Icons.Rounded.Menu, Icons.Sharp.Menu, Icons.TwoTone.Menu), contentDescription = "Apri Menu")
134|                        }
135|                    }
136|                )
137|            },
138|            floatingActionButton = {
139|                FloatingActionButton(onClick = { navController.navigate(Routes.ADD_TRANSACTION) }) {
140|                    Text("+", fontSize = 24.sp)
141|                }
142|            }
143|        ) { paddingValues ->
144|            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
145|                LazyColumn(
146|                    modifier = Modifier.fillMaxSize(),
147|                    contentPadding = PaddingValues(16.dp),
148|                    verticalArrangement = Arrangement.spacedBy(12.dp)
149|                ) {
150|                    item {
151|                        val totalIncome by transactionViewModel.totalIncome.collectAsState(initial = 0.0)
152|                        val totalExpenses by transactionViewModel.totalExpenses.collectAsState(initial = 0.0)
153|                        val balance = (totalIncome ?: 0.0) - (totalExpenses ?: 0.0)
154|
155|                        ElevatedCard(
156|                            modifier = Modifier.fillMaxWidth(),
157|                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
158|                        ) {
159|                            Column(
160|                                modifier = Modifier.padding(20.dp),
161|                                horizontalAlignment = Alignment.CenterHorizontally,
162|                                verticalArrangement = Arrangement.spacedBy(8.dp)
163|                            ) {
164|                                Text(text = "Bilancio Totale", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
165|                                Text(
166|                                    text = mainViewModel.formatCurrency(balance),
167|                                    style = MaterialTheme.typography.headlineMedium,
168|                                    fontWeight = FontWeight.Bold,
169|                                    color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red
170|                                )
171|                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
172|                                Row(
173|                                    modifier = Modifier.fillMaxWidth(),
174|                                    horizontalArrangement = Arrangement.SpaceBetween
175|                                ) {
176|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
177|                                        Text(text = "Entrate", fontSize = 12.sp, color = Color.Gray)
178|                                        Text(text = "+" + mainViewModel.formatCurrency(totalIncome ?: 0.0).removePrefix("+"), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
179|                                    }
180|                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
181|                                        Text(text = "Uscite", fontSize = 12.sp, color = Color.Gray)
182|                                        Text(text = "-" + mainViewModel.formatCurrency(totalExpenses ?: 0.0).removePrefix("-"), color = Color.Red, fontWeight = FontWeight.Bold)
183|                                    }
184|                                }
185|                            }
186|                        }
187|                    }
188|                    
189|                    items(transactionsWithTags) { item ->
190|                        TransactionItem(
191|                            transaction = item.transaction, 
192|                            tags = item.tags,
193|                            onDetailsRequest = { selectedTransaction = item.transaction },
194|                            onModifyRequest = { 
195|                                navController.navigate("${Routes.MODIFY_TRANSACTION}/${item.transaction.transactionId}") 
196|                            }
197|                        )
198|                    }
199|                }
200|            }
201|        }
202|        if (selectedTransaction != null) {
203|            val transaction = selectedTransaction!!
204|            val tags = transactionsWithTags.find { it.transaction.transactionId == transaction.transactionId }?.tags ?: emptyList()
205|            TransactionDetailsDialog(transaction = transaction, tags = tags, onDismiss = { selectedTransaction = null })
206|        }
207|    }
208|}
209|