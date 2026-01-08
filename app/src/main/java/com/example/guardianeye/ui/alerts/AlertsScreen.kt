package com.example.guardianeye.ui.alerts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel = viewModel(),
    onNavigateToChat: (Alert) -> Unit,
    onNavigateToDetail: (Alert) -> Unit
) {
    val alerts by viewModel.alerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Unresolved") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Resolved") }
                )
            }

            HorizontalPager(state = pagerState) { page ->
                val isResolvedTab = page == 1
                AlertsList(
                    alerts = alerts,
                    isResolvedTab = isResolvedTab,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToDetail = onNavigateToDetail,
                    onDelete = { id -> viewModel.deleteAlert(id) }
                )
            }
        }
    }
}

@Composable
fun AlertsList(
    alerts: List<Alert>,
    isResolvedTab: Boolean,
    onNavigateToChat: (Alert) -> Unit,
    onNavigateToDetail: (Alert) -> Unit,
    onDelete: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }

    val filteredAlerts = remember(alerts, searchQuery, sortOption, isResolvedTab) {
        var list = alerts.filter { alert ->
            alert.isActionTaken == isResolvedTab &&
            (alert.type.name.contains(searchQuery, ignoreCase = true) || 
             alert.description.contains(searchQuery, ignoreCase = true))
        }
        
        list = when(sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> list.sortedBy { it.timestamp }
            SortOption.PRIORITY_DESC -> list.sortedByDescending { it.priority.ordinal }
            SortOption.PRIORITY_ASC -> list.sortedBy { it.priority.ordinal }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Sort Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search alerts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Date (Newest)") },
                        onClick = { sortOption = SortOption.DATE_DESC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest)") },
                        onClick = { sortOption = SortOption.DATE_ASC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Priority (Critical First)") },
                        onClick = { sortOption = SortOption.PRIORITY_DESC; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Priority (Low First)") },
                        onClick = { sortOption = SortOption.PRIORITY_ASC; showSortMenu = false }
                    )
                }
            }
        }

        if (filteredAlerts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ${if (isResolvedTab) "resolved" else "unresolved"} alerts found.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredAlerts) { alert ->
                    AlertItem(
                        alert = alert, 
                        onClick = {
                            if (alert.isActionTaken) {
                                onNavigateToDetail(alert)
                            } else {
                                onNavigateToChat(alert)
                            }
                        },
                        onDelete = { onDelete(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlertItem(alert: Alert, onClick: () -> Unit, onDelete: () -> Unit) {
    val priorityColor = when (alert.priority) {
        AlertPriority.CRITICAL -> MaterialTheme.colorScheme.error
        AlertPriority.HIGH -> Color(0xFFFFA500) // Orange
        else -> MaterialTheme.colorScheme.primary
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Indicator
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.small,
                color = priorityColor
            ) {}

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.type.name.replace("_", " "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = alert.description.ifEmpty { "No description" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = formatTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (alert.isActionTaken) {
                Text(
                    text = "Resolved",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Green,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { 
                            onDelete()
                            showMenu = false 
                        }
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(timestamp.toDate())
}

enum class SortOption {
    DATE_DESC, DATE_ASC, PRIORITY_DESC, PRIORITY_ASC
}
