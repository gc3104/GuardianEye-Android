package com.example.guardianeye.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.guardianeye.model.Alert
import com.example.guardianeye.model.ChatMessage
import com.example.guardianeye.ui.theme.GuardianEyeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    data class SelectSuggestion(val text: String) : ChatIntent()
    data class CancelAction(val messageId: String) : ChatIntent()
}

@Composable
fun ChatScreen(
    alert: Alert?,
    viewModel: ChatViewModel
) {
    val messages by viewModel.chatMessages.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    
    LaunchedEffect(alert) { viewModel.initializeChat(alert) }

    ChatContent(
        messages = messages,
        suggestions = suggestions,
        onIntent = { intent ->
            when (intent) {
                is ChatIntent.SendMessage -> viewModel.sendMessage(intent.text, alert)
                is ChatIntent.SelectSuggestion -> viewModel.sendMessage(intent.text, alert)
                is ChatIntent.CancelAction -> viewModel.cancelAction(intent.messageId)
            }
        }
    )
}

@Composable
private fun ChatContent(
    messages: List<ChatMessage>,
    suggestions: List<String>,
    onIntent: (ChatIntent) -> Unit
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        if (suggestions.isNotEmpty()) {
            ScrollableSuggestionRow(suggestions) { onIntent(ChatIntent.SelectSuggestion(it)) }
        }

        ChatInput(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onIntent(ChatIntent.SendMessage(inputText))
                    inputText = ""
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isUser) 
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else 
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = color,
            shape = shape,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp, 8.dp).widthIn(max = 280.dp)) {
                Text(message.message, color = textColor, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun ChatInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask GuardianEye...") },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
            }
        }
    }
}

@Composable
private fun ScrollableSuggestionRow(suggestions: List<String>, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onClick(suggestion) },
                label = { Text(suggestion) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    GuardianEyeTheme {
        ChatContent(
            messages = listOf(
                ChatMessage(id = "1", message = "Suspicious activity detected.", isUser = false, timestamp = System.currentTimeMillis()),
                ChatMessage(id = "2", message = "Show me the footage.", isUser = true, timestamp = System.currentTimeMillis() + 1000)
            ),
            suggestions = listOf("Check Live", "Call Emergency", "Ignore"),
            onIntent = {}
        )
    }
}
