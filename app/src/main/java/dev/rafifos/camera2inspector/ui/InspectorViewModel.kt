package dev.rafifos.camera2inspector.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.CameraInspector
import dev.rafifos.camera2inspector.camera.InspectionReport
import dev.rafifos.camera2inspector.camera.ReportSummary
import dev.rafifos.camera2inspector.camera.summary
import dev.rafifos.camera2inspector.export.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InspectorUiState(
    val isScanning: Boolean = false,
    val statusMessage: String? = null,
    val report: InspectionReport? = null,
    val summary: ReportSummary? = null,
    val errorMessage: String? = null,
    val exportMessage: String? = null,
)

class InspectorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(InspectorUiState())
    val state: StateFlow<InspectorUiState> = _state.asStateFlow()

    private fun text(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun scan(force: Boolean = false) {
        val current = _state.value
        if (current.isScanning) return
        if (!force && current.report != null) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true,
                    statusMessage = text(R.string.scan_status_preparing),
                    errorMessage = null,
                )
            }
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    CameraInspector(getApplication()).inspect { cameraId ->
                        _state.update { it.copy(statusMessage = text(R.string.scan_status_camera, cameraId)) }
                    }
                }
            }
            result
                .onSuccess { report ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            statusMessage = null,
                            report = report,
                            summary = report.summary(),
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            statusMessage = null,
                            errorMessage = throwable.message ?: throwable.javaClass.simpleName,
                        )
                    }
                }
        }
    }

    fun export(uri: Uri) {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            val result = runCatching { ReportExporter.writeToUri(getApplication(), uri, report) }
            _state.update {
                it.copy(
                    exportMessage = result.fold(
                        onSuccess = { text(R.string.export_success) },
                        onFailure = { failure ->
                            text(R.string.export_failure, failure.message ?: failure.javaClass.simpleName)
                        },
                    ),
                )
            }
        }
    }

    fun consumeExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }
}
