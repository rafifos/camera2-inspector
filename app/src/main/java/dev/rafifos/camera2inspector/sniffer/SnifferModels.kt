package dev.rafifos.camera2inspector.sniffer

import dev.rafifos.camera2inspector.camera.DeviceReport
import dev.rafifos.camera2inspector.camera.SerializedError

data class KeyMeta(
    val name: String,
    val vendor: Boolean,
    val namespace: String,
    val declaredType: String?,
    val valueType: String?,
    val tagId: Long?,
    val dumpType: String?,
) {
    val typeLabel: String
        get() = declaredType ?: valueType ?: dumpType ?: "unavailable"

    val signedId: Int?
        get() = tagId?.let { VendorTagId.signed(it) }

    val unsignedId: Long?
        get() = tagId

    val hexId: String?
        get() = tagId?.let { VendorTagId.hex(it) }
}

data class KeyError(
    val name: String,
    val type: String,
    val message: String?,
)

data class FrameRecord(
    val frameNumber: Long,
    val sensorTimestampNs: Long?,
    val cameraId: String,
    val sequenceId: Int?,
    val isCaptureFrame: Boolean,
    val requestValues: Map<String, Any?>,
    val partialResults: List<Map<String, Any?>>,
    val totalResultValues: Map<String, Any?>?,
    val physicalTotalResults: Map<String, Map<String, Any?>>,
    val jpegTimestampNs: Long?,
    val errors: List<KeyError>,
) {
    val hasTotalResult: Boolean get() = totalResultValues != null
}

enum class CaptureEventKind {
    CAMERA_OPENED,
    SESSION_CONFIGURED,
    REPEATING_STARTED,
    CAPTURE_TRIGGERED,
    CAPTURE_COMPLETED,
    JPEG_AVAILABLE,
    RECORDING_STARTED,
    RECORDING_STOPPED,
    ERROR,
    LOG,
}

data class CaptureEvent(
    val kind: CaptureEventKind,
    val frameNumber: Long?,
    val timestampNs: Long?,
    val description: String,
    val wallTimeMillis: Long,
)

data class SnifferSession(
    val id: String,
    val label: String,
    val cameraId: String,
    val logicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val startedAtMillis: Long,
    val startedAtIso: String,
    val device: DeviceReport,
    val availableSessionKeys: List<String>,
    val sessionParameters: Map<String, Any?>,
    val frames: List<FrameRecord>,
    val events: List<CaptureEvent>,
    val keyMeta: Map<String, KeyMeta>,
    val errors: List<SerializedError>,
    val notes: List<String>,
) {
    val captureFrames: List<FrameRecord> get() = frames.filter { it.isCaptureFrame }

    val vendorKeyNames: Set<String>
        get() = keyMeta.values.filter { it.vendor }.map { it.name }.toSet()
}

fun SnifferSession.metaFor(name: String): KeyMeta? = keyMeta[name]

fun SnifferSession.isVendor(name: String): Boolean = keyMeta[name]?.vendor == true
