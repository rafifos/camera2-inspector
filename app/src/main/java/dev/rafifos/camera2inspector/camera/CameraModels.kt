package dev.rafifos.camera2inspector.camera

/**
 * Where each key was reported by the Camera2 framework. These categories are not
 * interchangeable: a key in one category does not imply support in another.
 */
enum class KeyCategory(val label: String) {
    CHARACTERISTICS("Characteristics"),
    CAPTURE_REQUEST("CaptureRequest"),
    CAPTURE_RESULT("CaptureResult"),
    PHYSICAL_CAPTURE_REQUEST("Physical CaptureRequest"),
    SESSION("Session"),
}

enum class SerializationMode(val label: String) {
    STRUCTURED("structured"),
    TOSTRING("toString"),
    ERROR("error"),
    NOT_READ("not-read"),
}

data class SerializedError(val type: String, val message: String?)

/**
 * Language-neutral notes produced by the scan. The UI renders them in the active locale and the
 * JSON report serializes them in English.
 */
sealed interface CameraNote {
    data object LogicalMultiCamera : CameraNote
    data class PhysicalCameraIds(val ids: List<String>) : CameraNote
    data class KeysNotReported(val origin: String) : CameraNote
    data object CharacteristicsUnreadable : CameraNote
}

data class KeyInfo(
    val name: String,
    val declaredType: String?,
    val valueType: String?,
    val vendor: Boolean,
    val namespace: String,
    val category: KeyCategory,
    val origin: String,
) {
    val typeLabel: String
        get() = declaredType ?: valueType ?: "unavailable"

    val typeSource: String
        get() = when {
            declaredType != null -> "declared"
            valueType != null -> "value"
            else -> "unavailable"
        }

    fun toEntry(): KeyEntry = KeyEntry(
        info = this,
        value = null,
        valueText = null,
        rawToString = null,
        serialization = SerializationMode.NOT_READ,
        error = null,
    )
}

data class KeyEntry(
    val info: KeyInfo,
    val value: Any?,
    val valueText: String?,
    val rawToString: String?,
    val serialization: SerializationMode,
    val error: SerializedError?,
)

data class CameraReport(
    val id: String,
    val logicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val hardwareLevel: String,
    val lensFacing: String,
    val sensorOrientation: Int?,
    val notes: List<CameraNote>,
    val errors: List<SerializedError>,
    val characteristics: List<KeyEntry>,
    val captureRequests: List<KeyInfo>,
    val captureResults: List<KeyInfo>,
    val physicalCaptureRequests: List<KeyInfo>,
    val sessionKeys: List<KeyInfo>,
) {
    fun vendorCount(): Int = vendorKeyCount(characteristics) + vendorInfoCount(captureRequests) +
        vendorInfoCount(captureResults) + vendorInfoCount(physicalCaptureRequests) + vendorInfoCount(sessionKeys)
}

fun vendorKeyCount(entries: List<KeyEntry>): Int = entries.count { it.info.vendor }

fun vendorInfoCount(infos: List<KeyInfo>): Int = infos.count { it.vendor }

data class DeviceReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String,
    val androidVersion: String,
    val sdk: Int,
    val hardware: String,
    val board: String,
    val supportedAbis: List<String>,
)

data class InspectionReport(
    val appName: String,
    val appVersion: String,
    val packageName: String,
    val generatedAt: String,
    val device: DeviceReport,
    val cameras: List<CameraReport>,
    val errors: List<SerializedError> = emptyList(),
)

data class ReportSummary(
    val cameraCount: Int,
    val characteristicCount: Int,
    val vendorCharacteristics: Int,
    val captureRequestCount: Int,
    val vendorCaptureRequests: Int,
    val captureResultCount: Int,
    val vendorCaptureResults: Int,
    val physicalRequestCount: Int,
    val vendorPhysicalRequests: Int,
    val sessionKeyCount: Int,
    val vendorSessionKeys: Int,
) {
    val vendorTotal: Int
        get() = vendorCharacteristics + vendorCaptureRequests + vendorCaptureResults +
            vendorPhysicalRequests + vendorSessionKeys
}

fun InspectionReport.summary(): ReportSummary = ReportSummary(
    cameraCount = cameras.size,
    characteristicCount = cameras.sumOf { it.characteristics.size },
    vendorCharacteristics = cameras.sumOf { vendorKeyCount(it.characteristics) },
    captureRequestCount = cameras.sumOf { it.captureRequests.size },
    vendorCaptureRequests = cameras.sumOf { vendorInfoCount(it.captureRequests) },
    captureResultCount = cameras.sumOf { it.captureResults.size },
    vendorCaptureResults = cameras.sumOf { vendorInfoCount(it.captureResults) },
    physicalRequestCount = cameras.sumOf { it.physicalCaptureRequests.size },
    vendorPhysicalRequests = cameras.sumOf { vendorInfoCount(it.physicalCaptureRequests) },
    sessionKeyCount = cameras.sumOf { it.sessionKeys.size },
    vendorSessionKeys = cameras.sumOf { vendorInfoCount(it.sessionKeys) },
)
