package dev.rafifos.camera2inspector.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.rafifos.camera2inspector.camera.KeyCategory
import dev.rafifos.camera2inspector.export.ReportExporter
import dev.rafifos.camera2inspector.ui.screens.CameraDetailScreen
import dev.rafifos.camera2inspector.ui.screens.HomeScreen
import dev.rafifos.camera2inspector.ui.screens.KeyDetailScreen
import dev.rafifos.camera2inspector.ui.screens.PermissionRequiredScreen

@Composable
fun CameraInspectorApp() {
    val context = LocalContext.current
    val viewModel: InspectorViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasCameraPermission()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.scan()
    }

    if (!hasPermission) {
        PermissionRequiredScreen(
            requestedOnce = requestedOnce,
            onRequestPermission = {
                requestedOnce = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenSettings = { context.openAppSettings() },
        )
        return
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> if (uri != null) viewModel.export(uri) }

    var selectedCameraId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedKeyName by rememberSaveable { mutableStateOf<String?>(null) }

    val report = state.report
    val selectedCamera = report?.cameras?.firstOrNull { it.id == selectedCameraId }
    val selectedCategory = selectedCategoryName?.let { name ->
        KeyCategory.entries.firstOrNull { it.name == name }
    }

    BackHandler(enabled = selectedCamera != null) {
        if (selectedKeyName != null) {
            selectedKeyName = null
        } else {
            selectedCameraId = null
            selectedCategoryName = null
        }
    }

    when {
        selectedCamera == null -> HomeScreen(
            state = state,
            onExport = { exportLauncher.launch(ReportExporter.defaultFileName()) },
            onRetry = { viewModel.scan(force = true) },
            onExportMessageShown = viewModel::consumeExportMessage,
            onCameraClick = { cameraId -> selectedCameraId = cameraId },
        )

        selectedCategory != null && selectedKeyName != null -> KeyDetailScreen(
            camera = selectedCamera,
            category = selectedCategory,
            keyName = selectedKeyName.orEmpty(),
            onBack = { selectedKeyName = null },
        )

        else -> CameraDetailScreen(
            camera = selectedCamera,
            onBack = {
                selectedCameraId = null
                selectedCategoryName = null
            },
            onKeyClick = { entry ->
                selectedCategoryName = entry.info.category.name
                selectedKeyName = entry.info.name
            },
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
