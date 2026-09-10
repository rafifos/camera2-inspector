package dev.rafifos.camera2inspector.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rafifos.camera2inspector.R
import dev.rafifos.camera2inspector.camera.CameraNote
import dev.rafifos.camera2inspector.camera.KeyCategory

@Composable
fun CameraNote.text(): String = when (this) {
    CameraNote.LogicalMultiCamera -> stringResource(R.string.note_logical_multi_camera)
    is CameraNote.PhysicalCameraIds -> stringResource(R.string.note_physical_ids, ids.joinToString(", "))
    is CameraNote.KeysNotReported -> stringResource(R.string.note_keys_not_reported, origin)
    CameraNote.CharacteristicsUnreadable -> stringResource(R.string.note_characteristics_unreadable)
}

/**
 * Neutral, factual availability notes. No key is described as writable or configurable simply
 * because it appears in the CaptureRequest key list.
 */
@Composable
fun availabilityNote(category: KeyCategory): String = stringResource(category.availabilityNoteRes)

@get:StringRes
private val KeyCategory.availabilityNoteRes: Int
    get() = when (this) {
        KeyCategory.CHARACTERISTICS -> R.string.availability_characteristics
        KeyCategory.CAPTURE_REQUEST -> R.string.availability_capture_request
        KeyCategory.CAPTURE_RESULT -> R.string.availability_capture_result
        KeyCategory.PHYSICAL_CAPTURE_REQUEST -> R.string.availability_physical_request
        KeyCategory.SESSION -> R.string.availability_session
    }
