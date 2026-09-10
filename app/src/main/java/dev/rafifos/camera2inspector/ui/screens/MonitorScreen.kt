package dev.rafifos.camera2inspector.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.sniffer.SnifferSession
import dev.rafifos.camera2inspector.ui.SessionExportFormat
import dev.rafifos.camera2inspector.ui.SnifferComparison
import dev.rafifos.camera2inspector.ui.SnifferUiState
import dev.rafifos.camera2inspector.ui.SnifferViewModel
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onBack: () -> Unit,
    viewModel: SnifferViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportSession(it, SessionExportFormat.JSON) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportSelectedCsv(it) } }

    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.exportComparison(it) } }

    val dumpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importDumpsys(it) } }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.monitor_title)) },
                subtitle = { Text(stringResource(R.string.monitor_subtitle, state.selectedCameraId.orEmpty())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = null,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "limitation") { LimitationCard() }
            item(key = "cameras") { CameraChips(state, viewModel::selectCamera) }
            item(key = "label") {
                OutlinedTextField(
                    value = state.label,
                    onValueChange = viewModel::updateLabel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.monitor_label)) },
                    placeholder = { Text(stringResource(R.string.monitor_label_hint)) },
                    singleLine = true,
                )
            }
            item(key = "controls") { Controls(state, viewModel) }
            item(key = "status") { StatusCard(state) }
            item(key = "dump") {
                OutlinedButton(onClick = { dumpLauncher.launch(arrayOf("text/*", "application/octet-stream")) }) {
                    Text(stringResource(R.string.monitor_import_dump))
                }
                if (state.importedTagIds > 0) {
                    Text(
                        text = stringResource(R.string.monitor_imported_ids, state.importedTagIds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "sessions-header") {
                Text(
                    text = stringResource(R.string.monitor_sessions),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            item(key = "session-actions") {
                SessionActions(
                    hasSessions = state.sessions.isNotEmpty(),
                    onExportJson = { jsonLauncher.launch("camera2_session.json") },
                    onExportCsv = { csvLauncher.launch("camera2_session.csv") },
                    onExportMarkdown = { markdownLauncher.launch("camera2_comparison.md") },
                )
            }
            if (state.sessions.isEmpty()) {
                item(key = "no-sessions") {
                    Text(
                        text = stringResource(R.string.monitor_no_sessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.sessions, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    selected = session.id == state.selectedSession?.id,
                    onClick = { viewModel.selectSession(session.id) },
                )
            }
            items(state.comparisons, key = { it.id }) { comparison ->
                ComparisonCard(comparison)
            }
            item(key = "events-header") {
                Text(
                    text = stringResource(R.string.monitor_events),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            item(key = "events") { EventsCard(state) }
        }
    }
}

@Composable
private fun LimitationCard() {
    Card {
        Text(
            text = stringResource(R.string.monitor_limitation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun CameraChips(state: SnifferUiState, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.cameraIds, key = { it }) { id ->
            FilterChip(
                selected = id == state.selectedCameraId,
                onClick = { onSelect(id) },
                label = { Text("Camera $id") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(state: SnifferUiState, viewModel: SnifferViewModel) {
    val buttonShapes = ButtonDefaults.shapes()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.monitorState.cameraOpen) {
                OutlinedButton(onClick = viewModel::closeCamera, shapes = buttonShapes) {
                    Text(stringResource(R.string.monitor_close_camera))
                }
            } else {
                Button(onClick = viewModel::openCamera, shapes = buttonShapes) {
                    Text(stringResource(R.string.monitor_open_camera))
                }
            }
            Button(
                onClick = if (state.monitorState.recording) viewModel::stopRecording else viewModel::startRecording,
                enabled = state.monitorState.sessionConfigured,
                shapes = buttonShapes,
            ) {
                Text(
                    stringResource(
                        if (state.monitorState.recording) {
                            R.string.monitor_stop_recording
                        } else {
                            R.string.monitor_start_recording
                        },
                    ),
                )
            }
        }
        OutlinedButton(
            onClick = viewModel::triggerCapture,
            enabled = state.monitorState.sessionConfigured,
            shapes = buttonShapes,
        ) {
            Text(stringResource(R.string.monitor_capture))
        }
    }
}

@Composable
private fun StatusCard(state: SnifferUiState) {
    val monitor = state.monitorState
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.monitor_status),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            StatusLine(stringResource(R.string.monitor_status), monitor.status ?: "—")
            StatusLine(stringResource(R.string.monitor_frames_seen), monitor.framesSeen.toString())
            StatusLine(stringResource(R.string.monitor_frames_recorded), monitor.framesRecorded.toString())
            StatusLine(stringResource(R.string.monitor_captures), monitor.capturesTriggered.toString())
            StatusLine(
                stringResource(R.string.monitor_vendor_request_keys),
                monitor.vendorRequestKeysLastFrame.toString(),
            )
            StatusLine(
                stringResource(R.string.monitor_vendor_result_keys),
                monitor.vendorResultKeysLastFrame.toString(),
            )
            StatusLine(
                stringResource(R.string.monitor_last_frame),
                monitor.lastFrameNumber?.toString() ?: "—",
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionActions(
    hasSessions: Boolean,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportMarkdown: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onExportJson, enabled = hasSessions) {
            Text(stringResource(R.string.monitor_export_json))
        }
        OutlinedButton(onClick = onExportCsv, enabled = hasSessions) {
            Text(stringResource(R.string.monitor_export_csv))
        }
        OutlinedButton(onClick = onExportMarkdown, enabled = hasSessions) {
            Text(stringResource(R.string.monitor_export_comparison))
        }
    }
}

@Composable
private fun SessionRow(session: SnifferSession, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(session.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.monitor_session_summary,
                    session.cameraId,
                    session.frames.size,
                    session.captureFrames.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonCard(comparison: SnifferComparison) {
    val baselineLabel = comparison.baselineLabel
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.monitor_comparison_changes, comparison.changes.size, baselineLabel),
                style = MaterialTheme.typography.titleSmall,
            )
            if (comparison.changes.isEmpty()) {
                Text(
                    text = stringResource(R.string.monitor_comparison_none, baselineLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            comparison.changes.take(COMPARISON_PREVIEW).forEach { change ->
                Text(
                    text = change.key,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${compact(change.oldValue)} → ${compact(change.newValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (comparison.changes.size > COMPARISON_PREVIEW) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    text = "+ ${comparison.changes.size - COMPARISON_PREVIEW}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventsCard(state: SnifferUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.events.isEmpty()) {
                Text(
                    text = stringResource(R.string.monitor_no_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.events.takeLast(EVENT_PREVIEW).reversed().forEach { event ->
                Text(
                    text = "${event.kind.name} ${event.frameNumber?.let { "frame $it" } ?: ""} " +
                        event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun compact(value: Any?): String = when (value) {
    null -> "null"
    is JSONObject -> value.toString()
    is JSONArray -> value.toString()
    else -> value.toString()
}

private const val COMPARISON_PREVIEW = 8
private const val EVENT_PREVIEW = 12
