package com.example.guardianeye.ui.alerts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.model.AlertType
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

sealed class AlertsIntent {
    data class Search(val query: String) : AlertsIntent()
    data class Sort(val option: SortOption) : AlertsIntent()
    data class DeleteAlert(val alert: Alert) : AlertsIntent()
    data class SelectAlert(val alert: Alert) : AlertsIntent()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel,
    onNavigateToChat: (Alert) -> Unit,
    onNavigateToDetail: (Alert) -> Unit
) {
    val alerts by viewModel.alerts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    AlertsContent(
        alerts = alerts,
        isLoading = isLoading,
        currentPage = pagerState.currentPage,
        onTabSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
        onIntent = { intent ->
            when (intent) {
                is AlertsIntent.DeleteAlert -> viewModel.deleteAlert(intent.alert)
                is AlertsIntent.SelectAlert -> {
                    if (intent.alert.isActionTaken) onNavigateToDetail(intent.alert)
                    else onNavigateToChat(intent.alert)
                }
                else -> { /* Handle search/sort if moved to VM */ }
            }
        },
        pagerContent = { page ->
            AlertsList(
                alerts = alerts,
                isResolvedTab = page == 1,
                onIntent = { intent ->
                    when (intent) {
                        is AlertsIntent.SelectAlert -> {
                            if (intent.alert.isActionTaken) onNavigateToDetail(intent.alert)
                            else onNavigateToChat(intent.alert)
                        }
                        is AlertsIntent.DeleteAlert -> viewModel.deleteAlert(intent.alert)
                        else -> {}
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsContent(
    alerts: List<Alert>,
    isLoading: Boolean,
    currentPage: Int,
    onTabSelected: (Int) -> Unit,
    onIntent: (AlertsIntent) -> Unit,
    pagerContent: @Composable (Int) -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                PrimaryTabRow(selectedTabIndex = currentPage) {
                    Tab(selected = currentPage == 0, onClick = { onTabSelected(0) }, text = { Text("Unresolved") })
                    Tab(selected = currentPage == 1, onClick = { onTabSelected(1) }, text = { Text("Resolved") })
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.padding(padding)) {
                pagerContent(currentPage)
            }
        }
    }
}

@Composable
private fun AlertsList(
    alerts: List<Alert>,
    isResolvedTab: Boolean,
    onIntent: (AlertsIntent) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredAlerts = alerts.filter { it.isActionTaken == isResolvedTab }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(16.dp)
        )

        if (filteredAlerts.isEmpty()) {
            EmptyState(isResolvedTab)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredAlerts) { alert ->
                    AlertCard(alert, onClick = { onIntent(AlertsIntent.SelectAlert(alert)) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search alerts...") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val priorityColor = when (alert.priority) {
        AlertPriority.CRITICAL -> MaterialTheme.colorScheme.error
        AlertPriority.HIGH -> Color(0xFFF44336)
        AlertPriority.MEDIUM -> Color(0xFFFF9800)
        AlertPriority.LOW -> Color(0xFF4CAF50)
    }

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = if (alert.priority == AlertPriority.CRITICAL) 
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) 
            else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(priorityColor))
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.type.name.replace("_", " "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(alert.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTimestamp(alert.timestamp), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun EmptyState(isResolvedTab: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Spacer(Modifier.height(16.dp))
            Text("No ${if (isResolvedTab) "resolved" else "unresolved"} alerts", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(timestamp.toDate())
}

enum class SortOption { DATE_DESC, DATE_ASC, PRIORITY_DESC, PRIORITY_ASC }

@Preview(showBackground = true)
@Composable
fun AlertsScreenPreview() {
    GuardianEyeTheme {
        AlertsContent(
            alerts = listOf(
                Alert(id = "1", type = AlertType.WEAPON, priority = AlertPriority.CRITICAL, description = "Potential weapon detected"),
                Alert(id = "2", type = AlertType.KNOWN_FACE, priority = AlertPriority.LOW, description = "John Doe arrived home")
            ),
            isLoading = false,
            currentPage = 0,
            onTabSelected = {},
            onIntent = {},
            pagerContent = { AlertsList(emptyList(), false, {}) }
        )
    }
}
