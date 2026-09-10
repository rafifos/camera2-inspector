package dev.rafifos.camera2inspector.ui

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rafifos.camera2inspector.camera.SerializedError
import dev.rafifos.camera2inspector.sniffer.CameraSessionMonitor
import dev.rafifos.camera2inspector.sniffer.CaptureEvent
import dev.rafifos.camera2inspector.sniffer.FrameRecord
import dev.rafifos.camera2inspector.sniffer.KeyChange
import dev.rafifos.camera2inspector.sniffer.SessionDiff
import dev.rafifos.camera2inspector.sniffer.SnifferReport
import dev.rafifos.camera2inspector.sniffer.SnifferReportOptions
import dev.rafifos.camera2inspector.sniffer.SnifferSession
import dev.rafifos.camera2inspector.sniffer.VendorTagDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SnifferComparison(
    val id: String,
    val title: String,
    val baselineLabel: String,
    val sessionLabel: String,
    val changes: List<KeyChange>,
)

data class SnifferUiState(
    val cameraIds: List<String> = emptyList(),
    val selectedCameraId: String? = null,
    val label: String = "PHOTO_1X",
    val monitorState: CameraSessionMonitor.MonitorState = CameraSessionMonitor.MonitorState(),
    val sessions: List<SnifferSession> = emptyList(),
    val selectedSessionId: String? = null,
    val events: List<CaptureEvent> = emptyList(),
    val importedTagIds: Int = 0,
    val comparisons: List<SnifferComparison> = emptyList(),
    val message: String? = null,
) {
    val selectedSession: SnifferSession?
        get() = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.lastOrNull()
}

class SnifferViewModel(application: Application) : AndroidViewModel(application) {

    private var importedDirectory: VendorTagDirectory = VendorTagDirectory.EMPTY

    private val _state = MutableStateFlow(SnifferUiState())
    val state: StateFlow<SnifferUiState> = _state.asStateFlow()

    private val monitorListener = object : CameraSessionMonitor.Listener {
        override fun onState(state: CameraSessionMonitor.MonitorState) {
            _state.update { it.copy(monitorState = state) }
        }

        override fun onEvent(event: CaptureEvent) {
            _state.update { it.copy(events = (it.events + event).takeLast(EVENT_LIMIT)) }
        }

        override fun onFrame(frame: FrameRecord) = Unit

        override fun onError(error: SerializedError) {
            _state.update { it.copy(message = "${error.type}: ${error.message ?: ""}") }
        }
    }

    private val monitor = CameraSessionMonitor(application, monitorListener)

    init {
        loadCameraIds()
    }

    fun loadCameraIds() {
        val manager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val ids = runCatching { manager?.cameraIdList?.toList().orEmpty() }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                cameraIds = ids,
                selectedCameraId = it.selectedCameraId?.takeIf { id -> ids.contains(id) } ?: ids.firstOrNull(),
            )
        }
    }

    fun selectCamera(cameraId: String) {
        _state.update { it.copy(selectedCameraId = cameraId) }
    }

    fun updateLabel(label: String) {
        _state.update { it.copy(label = label) }
    }

    fun selectSession(sessionId: String) {
        _state.update { it.copy(selectedSessionId = sessionId) }
    }

    fun openCamera() {
        val cameraId = _state.value.selectedCameraId ?: return
        val manager = getApplication<Application>().getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val characteristics = runCatching { manager?.getCameraCharacteristics(cameraId) }.getOrNull()
        val directory = characteristics
            ?.let { VendorTagDirectory.fromCharacteristics(it) }
            ?.merge(importedDirectory)
            ?: importedDirectory
        monitor.open(cameraId, directory)
    }

    fun closeCamera() {
        monitor.close()
    }

    fun startRecording() {
        monitor.startRecording(_state.value.label)
    }

    fun stopRecording() {
        monitor.saveSession { session ->
            if (session == null) {
                _state.update { it.copy(message = "Nothing was recorded in this session.") }
            } else {
                val sessions = _state.value.sessions + session
                _state.update {
                    it.copy(
                        sessions = sessions,
                        selectedSessionId = session.id,
                        message = "Session saved: ${session.label}",
                    )
                }
                recomputeComparisons(sessions)
            }
        }
    }

    fun triggerCapture() {
        monitor.triggerCapture()
    }

    fun importDumpsys(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }.getOrNull()

            if (text.isNullOrEmpty()) {
                _state.update { it.copy(message = "Could not read the selected dump.") }
                return@launch
            }
            importedDirectory = importedDirectory.merge(VendorTagDirectory.fromDumpsys(text))
            val withIds = importedDirectory.all().count { it.tagId != null }
            _state.update {
                it.copy(
                    importedTagIds = withIds,
                    message = "Vendor ids imported: $withIds",
                )
            }
        }
    }

    fun exportSession(
        uri: Uri,
        format: SessionExportFormat,
        options: SnifferReportOptions = SnifferReportOptions(),
    ) {
        val session = _state.value.selectedSession ?: run {
            _state.update { it.copy(message = "No session selected.") }
            return
        }
        export(uri, sessionFileName(session, format)) { SnifferReport.toJson(session, options) }
    }

    fun exportSelectedCsv(uri: Uri, options: SnifferReportOptions = SnifferReportOptions()) {
        val session = _state.value.selectedSession ?: run {
            _state.update { it.copy(message = "No session selected.") }
            return
        }
        export(uri, sessionFileName(session, SessionExportFormat.CSV)) { SnifferReport.toCsv(session, options) }
    }

    fun exportComparison(uri: Uri) {
        val sessions = _state.value.sessions
        if (sessions.isEmpty()) {
            _state.update { it.copy(message = "No session recorded.") }
            return
        }
        val comparisons = _state.value.comparisons.map { it.title to it.changes }
        export(uri, "camera2_comparison_${timestamp()}.md") {
            SnifferReport.toMarkdown(sessions, comparisons)
        }
    }

    private fun recomputeComparisons(sessions: List<SnifferSession>) {
        viewModelScope.launch {
            val comparisons = withContext(Dispatchers.Default) { buildComparisons(sessions) }
            _state.update { it.copy(comparisons = comparisons) }
        }
    }

    private fun buildComparisons(sessions: List<SnifferSession>): List<SnifferComparison> {
        val baseline = sessions.firstOrNull() ?: return emptyList()
        return sessions.drop(1).map { session ->
            SnifferComparison(
                id = "${baseline.id}->${session.id}",
                title = "Keys whose values changed between ${baseline.label} and ${session.label}",
                baselineLabel = baseline.label,
                sessionLabel = session.label,
                changes = SessionDiff.diffSessions(baseline, session),
            )
        }
    }

    private fun export(uri: Uri, fileName: String, build: () -> String) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val payload = build()
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the selected file.")
                    stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                }
            }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { "Exported $fileName" },
                        onFailure = { failure -> "Export failed: ${failure.message ?: failure.javaClass.simpleName}" },
                    ),
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        monitor.shutdown()
    }

    private fun sessionFileName(session: SnifferSession, format: SessionExportFormat): String {
        val safeLabel = session.label.lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_')
        return "camera2_${safeLabel.ifEmpty { "session" }}_${timestamp()}.${format.extension}"
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())

    private companion object {
        const val EVENT_LIMIT = 200
    }
}

enum class SessionExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
}
