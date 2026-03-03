package com.example.guardianeye.ui.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.guardianeye.model.FamilyMember
import com.example.guardianeye.model.MemberRole
import com.example.guardianeye.ui.theme.GuardianEyeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val family by viewModel.familyState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (family == null) {
            EmptyFamilyState(onCreateFamily = { showCreateDialog = true })
        } else {
            FamilyMemberList(
                family!!.name,
                family!!.members,
                onInviteClick = { showInviteDialog = true }
            )
        }

        if (showCreateDialog) {
            CreateFamilyDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name ->
                    viewModel.createFamily(name)
                    showCreateDialog = false
                }
            )
        }

        if (showInviteDialog) {
            InviteMemberDialog(
                onDismiss = { showInviteDialog = false },
                onInvite = { email ->
                    viewModel.inviteMember(email)
                    showInviteDialog = false
                }
            )
        }
    }
}

@Composable
fun EmptyFamilyState(onCreateFamily: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Share,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Family Group Found",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Create a family group to share alerts and monitor together.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onCreateFamily) {
            Text("Create Family")
        }
    }
}

@Composable
fun FamilyMemberList(
    familyName: String,
    members: List<FamilyMember>,
    onInviteClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = familyName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        items(members) { member ->
            FamilyMemberCard(member)
        }

        item {
            OutlinedButton(
                onClick = onInviteClick,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Invite Family Member")
            }
        }
    }
}

@Composable
fun FamilyMemberCard(member: FamilyMember) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = member.displayName.ifEmpty { "Member" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = member.role.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CreateFamilyDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Family Group") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Family Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InviteMemberDialog(onDismiss: () -> Unit, onInvite: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Member") },
        text = {
            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onInvite(email) }, enabled = email.isNotBlank()) {
                Text("Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EmptyFamilyStatePreview() {
    GuardianEyeTheme {
        EmptyFamilyState(onCreateFamily = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FamilyMemberListPreview() {
    GuardianEyeTheme {
        FamilyMemberList(
            familyName = "The Smiths",
            members = listOf(
                FamilyMember(displayName = "John Doe", role = MemberRole.ADMIN),
                FamilyMember(displayName = "Jane Doe", role = MemberRole.MEMBER)
            ),
            onInviteClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FamilyMemberCardPreview() {
    GuardianEyeTheme {
        FamilyMemberCard(
            member = FamilyMember(displayName = "John Doe", role = MemberRole.ADMIN)
        )
    }
}
