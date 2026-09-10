package dev.rafifos.camera2inspector.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.CameraReport
import dev.rafifos.camera2inspector.camera.vendorInfoCount
import dev.rafifos.camera2inspector.camera.vendorKeyCount

@Composable
fun CameraCard(
    report: CameraReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_id, report.id),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.logicalMultiCamera) {
                    StatusPill(stringResource(R.string.logical_multi_camera))
                }
                if (report.physicalCameraIds.isNotEmpty()) {
                    StatusPill(
                        stringResource(R.string.physical_ids_short, report.physicalCameraIds.joinToString(", ")),
                    )
                }
            }

            HorizontalDivider()

            StatLine(
                stringResource(R.string.stat_characteristics),
                report.characteristics.size,
                vendorKeyCount(report.characteristics),
            )
            StatLine(
                stringResource(R.string.stat_capture_request),
                report.captureRequests.size,
                vendorInfoCount(report.captureRequests),
            )
            StatLine(
                stringResource(R.string.stat_capture_result),
                report.captureResults.size,
                vendorInfoCount(report.captureResults),
            )
            StatLine(
                stringResource(R.string.stat_physical_request),
                report.physicalCaptureRequests.size,
                vendorInfoCount(report.physicalCaptureRequests),
            )
            StatLine(
                stringResource(R.string.stat_session),
                report.sessionKeys.size,
                vendorInfoCount(report.sessionKeys),
            )
        }
    }
}

@Composable
private fun StatLine(label: String, total: Int, vendor: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.vendor_count, vendor),
                style = MaterialTheme.typography.labelMedium,
                color = if (vendor > 0) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
