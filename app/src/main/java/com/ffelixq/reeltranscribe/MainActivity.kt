package com.ffelixq.reeltranscribe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)
        setContent {
            MaterialTheme {
                ReelTranscribeApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(incoming: Intent?) {
        if (viewModel.handleIntent(incoming)) {
            setIntent(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelTranscribeApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(
            initialUrl = state.backendUrl,
            initialToken = state.accessToken,
            onDismiss = { showSettings = false },
            onSave = { url, token ->
                viewModel.saveSettings(url, token)
                showSettings = false
                if (state.pendingUrl != null) viewModel.retryPending()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reel Transcribe") },
                navigationIcon = {
                    if (state.current != null) {
                        IconButton(onClick = viewModel::closeCurrent) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (state.current != null) {
            TranscriptDetail(
                record = state.current!!,
                onDelete = { viewModel.delete(state.current!!) },
                modifier = Modifier.padding(padding)
            )
        } else {
            HomeScreen(
                state = state,
                onTranscribe = viewModel::transcribeUrl,
                onRetry = viewModel::retryPending,
                onSelect = viewModel::select,
                onOpenSettings = { showSettings = true },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    onTranscribe: (String) -> Unit,
    onRetry: () -> Unit,
    onSelect: (TranscriptRecord) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manualUrl by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Share a Reel/TikTok to this app, or paste a link below. Transcription starts automatically when shared.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text("Video URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onTranscribe(manualUrl) },
            enabled = manualUrl.isNotBlank() && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Transcribe")
        }

        if (state.isLoading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Getting the actual transcript…")
            state.pendingUrl?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Could not transcribe", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(message)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.pendingUrl != null && state.backendUrl.isNotBlank()) {
                            OutlinedButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Text(" Retry")
                            }
                        }
                        if (state.backendUrl.isBlank()) {
                            OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Saved transcripts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (state.history.isEmpty() && !state.isLoading) {
            Text("Nothing saved yet. Share a public Instagram Reel or TikTok to get started.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.history, key = { it.id }) { record ->
                    HistoryCard(record = record, onClick = { onSelect(record) })
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(record: TranscriptRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(record.platform, fontWeight = FontWeight.SemiBold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.createdAt)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(record.transcript, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(record.sourceUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TranscriptDetail(
    record: TranscriptRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val researchPrompt = remember(record) { buildResearchPrompt(record) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(record.platform, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        record.language?.let { Text("Language: " + it, style = MaterialTheme.typography.bodySmall) }
        Text(record.sourceUrl, style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { copyToClipboard(context, "Transcript", record.transcript) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Text(" Copy")
            }
            FilledTonalButton(onClick = { copyToClipboard(context, "Research prompt", researchPrompt) }) {
                Text("Copy + GPT prompt")
            }
        }

        Button(onClick = {
            copyToClipboard(context, "Research prompt", researchPrompt)
            openChatGpt(context)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text(" Copy prompt & open ChatGPT")
        }

        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Text(" Delete saved transcript")
        }

        Spacer(Modifier.height(6.dp))
        Text("Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(record.transcript, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SettingsDialog(
    initialUrl: String,
    initialToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Deploy the worker in /worker, then paste its workers.dev URL here.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Worker URL") },
                    placeholder = { Text("https://reel-transcribe.yourname.workers.dev") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("App access token (optional)") },
                    supportingText = { Text("Use the same value as the Worker's APP_TOKEN secret.") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun buildResearchPrompt(record: TranscriptRecord): String = """
I found this social-media video. Use the actual transcript below as the primary description of what the creator said.

Identify the project/tool/topic being discussed. If it is software, find the official website, official documentation, and GitHub repository when one exists. Verify the video's claims rather than assuming they are correct.

Please explain:
- what it does
- whether it is open source
- how it works
- installation/setup
- hardware/software requirements
- API or pricing if relevant
- important limitations
- whether the video's claims are accurate
- similar or competing projects worth comparing

Original video:
""".trimIndent() + "
" + record.sourceUrl + "

TRANSCRIPT:

" + record.transcript

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openChatGpt(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
    if (launchIntent != null) {
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
