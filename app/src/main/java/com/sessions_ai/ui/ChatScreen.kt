package com.sessions_ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sessions_ai.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppState,
    onSendMessage: (String) -> Unit,
    onNavigateSettings: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.currentModel?.id?.displayName ?: "Sessions-AI") },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false,
                contentPadding = PaddingValues(16.dp)
            ) {
                items(state.messages) { message ->
                    MessageBubble(message = message, isGenerating = false)
                }
                
                if (state.isGenerating) {
                    item {
                        MessageBubble(
                            message = ChatMessage(ChatMessage.Role.ASSISTANT, state.currentGeneration), 
                            isGenerating = true
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask something...") },
                    enabled = !state.isGenerating
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !state.isGenerating
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isGenerating: Boolean) {
    val isUser = message.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isUser) {
                    val (steps, main) = splitAssistantContent(message.content)
                    if (steps != null) {
                        CollapsibleIntermediateStepsView(
                            stepsContent = steps,
                            initiallyExpanded = isGenerating
                        )
                    }
                    if (main.isNotEmpty() || steps == null) {
                        val contentToDisplay = if (steps != null) main else message.content
                        Text(
                            text = contentToDisplay,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

fun splitAssistantContent(content: String): Pair<String?, String> {
    val marker = "### Intermediate steps\n\n"
    val markerIndex = content.indexOf(marker)
    if (markerIndex == -1) {
        return Pair(null, content)
    }

    val afterMarker = content.substring(markerIndex + marker.length)
    val tripleNewlineIndex = afterMarker.indexOf("\n\n\n")

    if (tripleNewlineIndex != -1) {
        val steps = afterMarker.substring(0, tripleNewlineIndex).trim()
        val main = afterMarker.substring(tripleNewlineIndex + 3).trim()
        return Pair(if (steps.isEmpty()) null else steps, main)
    }

    val steps = afterMarker.trim()
    return Pair(if (steps.isEmpty()) null else steps, "")
}

@Composable
fun CollapsibleIntermediateStepsView(stepsContent: String, initiallyExpanded: Boolean) {
    var isExpanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Intermediate steps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    text = if (isExpanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        if (isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stepsContent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
