package com.sessions_ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sessions_ai.LLMModelCatalog
import com.sessions_ai.LLMModelConfiguration
import com.sessions_ai.LLMModelID
import com.sessions_ai.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentModel: LLMModelConfiguration?,
    downloadStates: Map<LLMModelID, DownloadState>,
    onModelSelected: (LLMModelID) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text("Select Model to Chat", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap a model to download and load it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            items(LLMModelCatalog.models.values.toList()) { config ->
                val state = downloadStates[config.id] ?: DownloadState.Idle
                ModelRow(
                    config = config,
                    isSelected = currentModel?.id == config.id,
                    downloadState = state,
                    onClick = { onModelSelected(config.id) }
                )
            }
        }
    }
}

@Composable
fun ModelRow(
    config: LLMModelConfiguration,
    isSelected: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = config.id.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Template: ${config.chatTemplate.name}", style = MaterialTheme.typography.bodySmall)
            Text(text = "Size: ${LLMModelConfiguration.formatFileSize(config.expectedFileSize)}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            when (downloadState) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${(downloadState.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                is DownloadState.Completed -> {
                    Text("Downloaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                is DownloadState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Failed: ${downloadState.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                is DownloadState.Idle -> {
                    Text("Tap to Download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
