package com.ffelixq.reeltranscribe

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val history: List<TranscriptRecord> = emptyList(),
    val current: TranscriptRecord? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingUrl: String? = null,
    val backendUrl: String = "",
    val accessToken: String = ""
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = TranscriptDbHelper(application)
    private val settings = SettingsStore(application)
    private val client = WorkerClient()

    private val _uiState = MutableStateFlow(
        UiState(
            backendUrl = settings.backendUrl,
            accessToken = settings.accessToken
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshHistory()
    }

    fun handleIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return false
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = extractSupportedUrl(sharedText)
        if (url == null) {
            _uiState.update { it.copy(error = "No supported video URL was found in the shared text.") }
        } else {
            transcribeUrl(url)
        }
        return true
    }

    fun transcribeUrl(rawText: String) {
        val url = extractSupportedUrl(rawText)
        if (url == null) {
            _uiState.update { it.copy(error = "Paste or share a supported Instagram, TikTok, YouTube, X, or Facebook URL.") }
            return
        }

        val backend = settings.backendUrl.trim()
        if (backend.isBlank()) {
            _uiState.update {
                it.copy(
                    error = "Set your Cloudflare Worker URL in Settings, then retry.",
                    pendingUrl = url
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                current = null,
                pendingUrl = url,
                backendUrl = backend,
                accessToken = settings.accessToken
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = client.transcribe(backend, settings.accessToken, url)
                val record = TranscriptRecord(
                    sourceUrl = url,
                    platform = platformFor(url),
                    transcript = result.transcript,
                    language = result.language
                )
                val id = db.insert(record)
                record.copy(id = id)
            }.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        history = db.listAll(),
                        current = saved,
                        isLoading = false,
                        error = null,
                        pendingUrl = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Transcription failed."
                    )
                }
            }
        }
    }

    fun retryPending() {
        _uiState.value.pendingUrl?.let(::transcribeUrl)
    }

    fun select(record: TranscriptRecord) {
        _uiState.update { it.copy(current = record, error = null) }
    }

    fun closeCurrent() {
        _uiState.update { it.copy(current = null, error = null) }
    }

    fun delete(record: TranscriptRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            db.delete(record.id)
            _uiState.update {
                it.copy(
                    history = db.listAll(),
                    current = if (it.current?.id == record.id) null else it.current
                )
            }
        }
    }

    fun saveSettings(backendUrl: String, accessToken: String) {
        settings.backendUrl = backendUrl
        settings.accessToken = accessToken
        _uiState.update {
            it.copy(
                backendUrl = settings.backendUrl,
                accessToken = settings.accessToken,
                error = null
            )
        }
    }

    private fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(history = db.listAll()) }
        }
    }

    private fun extractSupportedUrl(text: String): String? {
        val candidates = Regex("https?://[^\\s<>\\\"']+")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', ')', ']', '}') }

        return candidates.firstOrNull { candidate ->
            runCatching {
                val host = Uri.parse(candidate).host?.lowercase().orEmpty()
                isSupportedHost(host)
            }.getOrDefault(false)
        }
    }

    private fun isSupportedHost(host: String): Boolean {
        val supported = listOf(
            "instagram.com",
            "tiktok.com",
            "youtube.com",
            "youtu.be",
            "x.com",
            "twitter.com",
            "facebook.com",
            "fb.watch"
        )
        return supported.any { base -> host == base || host.endsWith("." + base) }
    }

    private fun platformFor(url: String): String {
        val host = Uri.parse(url).host?.lowercase().orEmpty()
        return when {
            "instagram" in host -> "Instagram"
            "tiktok" in host -> "TikTok"
            "youtube" in host || "youtu.be" in host -> "YouTube"
            host == "x.com" || host.endsWith(".x.com") || "twitter" in host -> "X"
            "facebook" in host || "fb.watch" in host -> "Facebook"
            else -> "Video"
        }
    }
}
