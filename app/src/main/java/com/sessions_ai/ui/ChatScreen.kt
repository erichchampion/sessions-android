package com.sessions_ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sessions_ai.ChatMessage
import com.sessions_ai.tools.Plan
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: AppState,
    onSendMessage: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    onLoadSelectedChat: (String) -> Unit = {},
    onSelectNewChat: () -> Unit = {},
    onDeleteChat: (String) -> Unit = {},
    onRenameChat: (String, String) -> Unit = { _, _ -> },
    onRegeneratePlan: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renamePath by remember { mutableStateOf<String?>(null) }
    var renameName by remember { mutableStateOf("") }
    var deleteConfirmPath by remember { mutableStateOf<String?>(null) }

    if (renamePath != null) {
        AlertDialog(
            onDismissRequest = { renamePath = null; renameName = "" },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val path = renamePath
                    if (path != null && renameName.isNotBlank()) {
                        onRenameChat(path, renameName.trim())
                        renamePath = null
                        renameName = ""
                        scope.launch { drawerState.close() }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renamePath = null; renameName = "" }) { Text("Cancel") } }
        )
    }
    if (deleteConfirmPath != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmPath = null },
            title = { Text("Delete chat?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteChat(deleteConfirmPath!!)
                    deleteConfirmPath = null
                    scope.launch { drawerState.close() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmPath = null }) { Text("Cancel") } }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Chats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                TextButton(
                    onClick = {
                        onSelectNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New chat")
                }
                HorizontalDivider()
                state.chatFilePaths.forEach { path ->
                    val displayName = File(path).parentFile?.name ?: "Chat"
                    val isSelected = state.currentChatPath == path
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        onClick = {
                            onLoadSelectedChat(path)
                            scope.launch { drawerState.close() }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Row {
                                TextButton(onClick = {
                                    renamePath = path
                                    renameName = displayName
                                }) { Text("Rename", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { deleteConfirmPath = path }) { Text("Delete", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.currentModel?.id?.displayName ?: "Sessions-AI") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Chats")
                        }
                    },
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
            if (state.currentPlan != null && state.currentPlan!!.steps.isNotEmpty()) {
                PlanSection(
                    plan = state.currentPlan!!,
                    onRegeneratePlan = onRegeneratePlan
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
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
                            message = ChatMessage(ChatMessage.Role.ASSISTANT, state.displayGeneration), 
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
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

@Composable
fun PlanSection(plan: Plan, onRegeneratePlan: () -> Unit) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Plan",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(
                    onClick = onRegeneratePlan,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Regenerate plan", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                plan.steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = "[${step.status.stringValue}]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 80.dp)
                        )
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
