package com.example.guardianeye.ui.alerts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.AlertPriority
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class AlertsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GuardianEyeTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AlertsScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun AlertsScreen() {
        var alerts by remember { mutableStateOf<List<Alert>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var searchQuery by remember { mutableStateOf("") }
        var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
        var showSortMenu by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            val listener = db.collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, e ->
                    isLoading = false
                    if (e != null) {
                        Log.e("AlertsFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }
                    alerts = snapshots?.toObjects(Alert::class.java) ?: emptyList()
                }

            onDispose {
                listener.remove()
            }
        }

        val filteredAlerts = remember(alerts, searchQuery, sortOption) {
            var list = alerts.filter { 
                it.type.name.contains(searchQuery, ignoreCase = true) || 
                it.description.contains(searchQuery, ignoreCase = true)
            }
            
            list = when(sortOption) {
                SortOption.DATE_DESC -> list.sortedByDescending { it.timestamp }
                SortOption.DATE_ASC -> list.sortedBy { it.timestamp }
                SortOption.PRIORITY_DESC -> list.sortedByDescending { it.priority.ordinal }
                SortOption.PRIORITY_ASC -> list.sortedBy { it.priority.ordinal }
            }
            list
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Search & Sort Header
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (filteredAlerts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No alerts found.")
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAlerts) { alert ->
                            AlertItem(alert) {
                                if (alert.isActionTaken) {
                                    navigateToDetail(alert)
                                } else {
                                    navigateToChat(alert)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AlertItem(alert: Alert, onClick: () -> Unit) {
        val priorityColor = when (alert.priority) {
            AlertPriority.CRITICAL -> Color.Red
            AlertPriority.HIGH -> Color(0xFFFFA500) // Orange
            else -> MaterialTheme.colorScheme.primary
        }

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
                        color = Color.Green
                    )
                }
            }
        }
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    private fun navigateToChat(alert: Alert) {
        val action = AlertsFragmentDirections.actionAlertsFragmentToChatFragment(
            alert.id,
            alert.type.name,
            alert.description,
            alert.mediaUrl,
            alert.mediaType
        )
        findNavController().navigate(action)
    }

    private fun navigateToDetail(alert: Alert) {
        val action = AlertsFragmentDirections.actionAlertsFragmentToAlertDetailFragment(
            alert.id,
            alert.type.name,
            alert.description,
            alert.mediaUrl,
            alert.mediaType
        )
        findNavController().navigate(action)
    }
    
    enum class SortOption {
        DATE_DESC, DATE_ASC, PRIORITY_DESC, PRIORITY_ASC
    }
}