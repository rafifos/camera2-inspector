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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
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

private sealed interface NavTarget {
    data object Home : NavTarget
    data class CameraDetail(val cameraId: String) : NavTarget
    data class KeyDetail(val cameraId: String, val category: KeyCategory, val keyName: String) : NavTarget
}

private fun NavTarget.depth(): Int = when (this) {
    NavTarget.Home -> 0
    is NavTarget.CameraDetail -> 1
    is NavTarget.KeyDetail -> 2
}

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

    BackHandler(enabled = selectedCameraId != null) {
        if (selectedKeyName != null) {
            selectedKeyName = null
        } else {
            selectedCameraId = null
            selectedCategoryName = null
        }
    }

    val cameraId = selectedCameraId
    val keyName = selectedKeyName
    val navTarget: NavTarget = when {
        selectedCamera == null || cameraId == null -> NavTarget.Home
        selectedCategory != null && keyName != null ->
            NavTarget.KeyDetail(cameraId, selectedCategory, keyName)

        else -> NavTarget.CameraDetail(selectedCamera.id)
    }

    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = navTarget,
        transitionSpec = {
            val forward = targetState.depth() >= initialState.depth()
            val spatial = if (forward) {
                motionScheme.defaultSpatialSpec<IntOffset>()
            } else {
                motionScheme.fastSpatialSpec<IntOffset>()
            }
            val effects = if (forward) {
                motionScheme.defaultEffectsSpec<Float>()
            } else {
                motionScheme.fastEffectsSpec<Float>()
            }
            val direction = if (forward) 1 else -1
            (
                slideInHorizontally(animationSpec = spatial) { width -> direction * width / 6 } +
                    fadeIn(animationSpec = effects)
                )
                .togetherWith(
                    slideOutHorizontally(animationSpec = spatial) { width -> -direction * width / 6 } +
                        fadeOut(animationSpec = effects),
                )
        },
        label = "screen",
    ) { target ->
        when (target) {
            NavTarget.Home -> HomeScreen(
                state = state,
                onExport = { exportLauncher.launch(ReportExporter.defaultFileName()) },
                onRetry = { viewModel.scan(force = true) },
                onExportMessageShown = viewModel::consumeExportMessage,
                onCameraClick = { cameraId -> selectedCameraId = cameraId },
            )

            is NavTarget.CameraDetail -> report
                ?.cameras
                ?.firstOrNull { it.id == target.cameraId }
                ?.let { camera ->
                    CameraDetailScreen(
                        camera = camera,
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

            is NavTarget.KeyDetail -> report
                ?.cameras
                ?.firstOrNull { it.id == target.cameraId }
                ?.let { camera ->
                    KeyDetailScreen(
                        camera = camera,
                        category = target.category,
                        keyName = target.keyName,
                        onBack = { selectedKeyName = null },
                    )
                }
        }
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
