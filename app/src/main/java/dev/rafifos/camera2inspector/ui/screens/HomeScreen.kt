package dev.rafifos.camera2inspector.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.DeviceReport
import dev.rafifos.camera2inspector.camera.ReportSummary
import dev.rafifos.camera2inspector.camera.SerializedError
import dev.rafifos.camera2inspector.ui.InspectorUiState
import dev.rafifos.camera2inspector.ui.components.CameraCard
import dev.rafifos.camera2inspector.ui.components.SummaryCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    state: InspectorUiState,
    onExport: () -> Unit,
    onRetry: () -> Unit,
    onExportMessageShown: () -> Unit,
    onCameraClick: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onExportMessageShown()
        }
    }

    val report = state.report

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = {
                    Text(
                        text = report?.device?.let { "${it.manufacturer} ${it.model}".trim() }
                            ?: stringResource(R.string.home_subtitle_fallback),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (report != null && !state.isScanning) {
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.rescan),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = report != null && !state.isScanning,
                enter = scaleIn(
                    animationSpec = motionScheme.defaultSpatialSpec<Float>(),
                    initialScale = 0.8f,
                ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec<Float>()),
                exit = scaleOut(
                    animationSpec = motionScheme.fastSpatialSpec<Float>(),
                    targetScale = 0.8f,
                ) + fadeOut(animationSpec = motionScheme.fastEffectsSpec<Float>()),
            ) {
                MediumExtendedFloatingActionButton(
                    onClick = onExport,
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    text = { Text(stringResource(R.string.export)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isScanning) {
                item(key = "scanning") { ScanningCard(state.statusMessage) }
            }

            state.errorMessage?.let { message ->
                item(key = "error") { ErrorCard(message = message, onRetry = onRetry) }
            }

            if (report != null) {
                item(key = "device") { DeviceCard(report.device) }

                item(key = "summary") { SummarySection(state.summary) }

                item(key = "cameras-header") {
                    Text(
                        text = stringResource(R.string.cameras_title, report.cameras.size),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }

                if (report.cameras.isEmpty()) {
                    item(key = "no-cameras") {
                        Card {
                            Text(
                                text = stringResource(R.string.cameras_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                items(report.cameras, key = { it.id }) { camera ->
                    CameraCard(report = camera, onClick = { onCameraClick(camera.id) })
                }

                if (report.errors.isNotEmpty()) {
                    item(key = "report-errors") { ReportErrorsCard(report.errors) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanningCard(statusMessage: String?) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>(),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            ContainedLoadingIndicator()
            Text(
                text = statusMessage ?: stringResource(R.string.scan_status_default),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_failed_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scan_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(device: DeviceReport) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.device_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            DeviceLine(stringResource(R.string.field_manufacturer), device.manufacturer)
            DeviceLine(stringResource(R.string.field_model), device.model)
            DeviceLine(
                stringResource(R.string.field_android),
                "${device.androidVersion} (SDK ${device.sdk})",
            )
            DeviceLine(stringResource(R.string.field_device), device.device)
            DeviceLine(stringResource(R.string.field_product), device.product)
            DeviceLine(stringResource(R.string.field_hardware), device.hardware)
            DeviceLine(stringResource(R.string.field_fingerprint), device.fingerprint, monospace = true)
        }
    }
}

@Composable
private fun DeviceLine(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummarySection(summary: ReportSummary?) {
    if (summary == null) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                title = stringResource(R.string.summary_cameras),
                value = summary.cameraCount,
                highlighted = false,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.summary_vendor_characteristics),
                value = summary.vendorCharacteristics,
                highlighted = summary.vendorCharacteristics > 0,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                title = stringResource(R.string.summary_vendor_capture_request),
                value = summary.vendorCaptureRequests,
                highlighted = summary.vendorCaptureRequests > 0,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.summary_vendor_capture_result),
                value = summary.vendorCaptureResults,
                highlighted = summary.vendorCaptureResults > 0,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(
                title = stringResource(R.string.summary_vendor_physical_request),
                value = summary.vendorPhysicalRequests,
                highlighted = summary.vendorPhysicalRequests > 0,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                title = stringResource(R.string.summary_vendor_session_keys),
                value = summary.vendorSessionKeys,
                highlighted = summary.vendorSessionKeys > 0,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = stringResource(
                R.string.summary_totals,
                summary.characteristicCount,
                summary.captureRequestCount,
                summary.captureResultCount,
                summary.physicalRequestCount,
                summary.sessionKeyCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportErrorsCard(errors: List<SerializedError>) {
    val noMessage = stringResource(R.string.no_message)
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.report_errors_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            errors.forEach { error ->
                Text(
                    text = "${error.type}: ${error.message ?: noMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
