package dev.rafifos.camera2inspector.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import java.time.Instant

/**
 * Read-only inspection of the Camera2 API surface exposed by the device.
 *
 * No camera device is opened, no CaptureRequest is submitted and no persistent setting is changed.
 * Every field is read defensively: a failure on one camera, key or list is recorded and the
 * remaining inspection continues.
 */
class CameraInspector(private val context: Context) {

    fun inspect(onProgress: (String) -> Unit = {}): InspectionReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val errors = mutableListOf<SerializedError>()

        val cameraIds = try {
            manager?.cameraIdList?.toList().orEmpty()
        } catch (t: Throwable) {
            errors += t.toSerializedError()
            emptyList()
        }

        val cameras = cameraIds.map { id ->
            onProgress(id)
            inspectCamera(manager, id)
        }

        return InspectionReport(
            appName = "Camera2 Inspector",
            appVersion = appVersion(),
            packageName = context.packageName,
            generatedAt = Instant.now().toString(),
            device = deviceReport(),
            cameras = cameras,
            errors = errors,
        )
    }

    private fun inspectCamera(manager: CameraManager?, cameraId: String): CameraReport {
        val errors = mutableListOf<SerializedError>()
        val notes = mutableListOf<CameraNote>()

        val characteristics = try {
            requireNotNull(manager) { "CameraManager is not available on this device." }
            manager.getCameraCharacteristics(cameraId)
        } catch (t: Throwable) {
            errors += t.toSerializedError()
            return CameraReport(
                id = cameraId,
                logicalMultiCamera = false,
                physicalCameraIds = emptyList(),
                hardwareLevel = "unavailable",
                lensFacing = "unavailable",
                sensorOrientation = null,
                notes = listOf(CameraNote.CharacteristicsUnreadable),
                errors = errors,
                characteristics = emptyList(),
                captureRequests = emptyList(),
                captureResults = emptyList(),
                physicalCaptureRequests = emptyList(),
                sessionKeys = emptyList(),
            )
        }

        val capabilities = characteristics.readOrNull(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
            errors,
        )
        val logicalMultiCamera = capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
        ) == true

        val physicalCameraIds = try {
            characteristics.physicalCameraIds.toList().sortedWith(NATURAL_ORDER)
        } catch (t: Throwable) {
            errors += t.toSerializedError()
            emptyList()
        }

        if (logicalMultiCamera) {
            notes += CameraNote.LogicalMultiCamera
        }
        if (physicalCameraIds.isNotEmpty()) {
            notes += CameraNote.PhysicalCameraIds(physicalCameraIds)
        }

        val captureRequests = readInfoList(
            category = KeyCategory.CAPTURE_REQUEST,
            origin = ORIGIN_CAPTURE_REQUEST,
            errors = errors,
        ) { characteristics.availableCaptureRequestKeys }

        val captureResults = readInfoList(
            category = KeyCategory.CAPTURE_RESULT,
            origin = ORIGIN_CAPTURE_RESULT,
            errors = errors,
        ) { characteristics.availableCaptureResultKeys }

        val physicalCaptureRequests = readInfoList(
            category = KeyCategory.PHYSICAL_CAPTURE_REQUEST,
            origin = ORIGIN_PHYSICAL_REQUEST,
            errors = errors,
        ) { characteristics.availablePhysicalCameraRequestKeys }

        val sessionKeys = readInfoList(
            category = KeyCategory.SESSION,
            origin = ORIGIN_SESSION,
            errors = errors,
        ) { characteristics.availableSessionKeys }

        if (captureRequests.isEmpty()) notes += CameraNote.KeysNotReported(ORIGIN_CAPTURE_REQUEST)
        if (captureResults.isEmpty()) notes += CameraNote.KeysNotReported(ORIGIN_CAPTURE_RESULT)
        if (physicalCaptureRequests.isEmpty()) notes += CameraNote.KeysNotReported(ORIGIN_PHYSICAL_REQUEST)
        if (sessionKeys.isEmpty()) notes += CameraNote.KeysNotReported(ORIGIN_SESSION)

        return CameraReport(
            id = cameraId,
            logicalMultiCamera = logicalMultiCamera,
            physicalCameraIds = physicalCameraIds,
            hardwareLevel = hardwareLevelLabel(
                characteristics.readOrNull(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL,
                    errors,
                ),
            ),
            lensFacing = lensFacingLabel(
                characteristics.readOrNull(
                    CameraCharacteristics.LENS_FACING,
                    errors,
                ),
            ),
            sensorOrientation = characteristics.readOrNull(
                CameraCharacteristics.SENSOR_ORIENTATION,
                errors,
            ),
            notes = notes,
            errors = errors,
            characteristics = readCharacteristics(characteristics, errors),
            captureRequests = captureRequests,
            captureResults = captureResults,
            physicalCaptureRequests = physicalCaptureRequests,
            sessionKeys = sessionKeys,
        )
    }

    private fun readCharacteristics(
        characteristics: CameraCharacteristics,
        errors: MutableList<SerializedError>,
    ): List<KeyEntry> {
        val keys = try {
            characteristics.keys
        } catch (t: Throwable) {
            errors += t.toSerializedError()
            return emptyList()
        }
        return keys
            .map { key -> readCharacteristicEntry(characteristics, key) }
            .sortedWith(ENTRY_ORDER)
    }

    private fun readCharacteristicEntry(
        characteristics: CameraCharacteristics,
        key: CameraCharacteristics.Key<*>,
    ): KeyEntry {
        val info = keyInfo(key, KeyCategory.CHARACTERISTICS, ORIGIN_CHARACTERISTICS)
        return try {
            @Suppress("UNCHECKED_CAST")
            val value = characteristics.get(key as CameraCharacteristics.Key<Any>)
            val serialized = CameraValueSerializer.serialize(value)
            KeyEntry(
                info = info.copy(valueType = value?.let { CameraValueSerializer.typeNameOf(it) }),
                value = serialized.json,
                valueText = serialized.display,
                rawToString = serialized.rawToString,
                serialization = serialized.mode,
                error = null,
            )
        } catch (t: Throwable) {
            KeyEntry(
                info = info,
                value = null,
                valueText = null,
                rawToString = null,
                serialization = SerializationMode.ERROR,
                error = t.toSerializedError(),
            )
        }
    }

    private fun readInfoList(
        category: KeyCategory,
        origin: String,
        errors: MutableList<SerializedError>,
        block: () -> List<*>?,
    ): List<KeyInfo> {
        val keys = try {
            block().orEmpty()
        } catch (t: Throwable) {
            errors += t.toSerializedError()
            return emptyList()
        }
        return keys
            .filterNotNull()
            .map { key -> keyInfo(key, category, origin) }
            .sortedWith(INFO_ORDER)
    }

    private fun keyInfo(key: Any, category: KeyCategory, origin: String): KeyInfo {
        val name = keyName(key)
        return KeyInfo(
            name = name,
            declaredType = KeyTypeResolver.resolveDeclaredType(key),
            valueType = null,
            vendor = CameraValueSerializer.isVendorKey(name),
            namespace = CameraValueSerializer.extractNamespace(name),
            category = category,
            origin = origin,
        )
    }

    private fun keyName(key: Any): String = when (key) {
        is CameraCharacteristics.Key<*> -> key.name
        is CaptureRequest.Key<*> -> key.name
        is CaptureResult.Key<*> -> key.name
        else -> runCatching {
            key.javaClass.getMethod("getName").invoke(key) as? String
        }.getOrNull() ?: key.toString()
    }

    private fun <T> CameraCharacteristics.readOrNull(
        key: CameraCharacteristics.Key<T>,
        errors: MutableList<SerializedError>,
    ): T? = try {
        get(key)
    } catch (t: Throwable) {
        errors += t.toSerializedError()
        null
    }

    private fun hardwareLevelLabel(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN(${level ?: "null"})"
    }

    private fun lensFacingLabel(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN(${facing ?: "null"})"
    }

    private fun deviceReport() = DeviceReport(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
        fingerprint = Build.FINGERPRINT.orEmpty(),
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        hardware = Build.HARDWARE.orEmpty(),
        board = Build.BOARD.orEmpty(),
        supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
    )

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifEmpty { "unknown" }

    private companion object {
        const val ORIGIN_CHARACTERISTICS = "CameraCharacteristics.get(key)"
        const val ORIGIN_CAPTURE_REQUEST = "CameraCharacteristics.getAvailableCaptureRequestKeys()"
        const val ORIGIN_CAPTURE_RESULT = "CameraCharacteristics.getAvailableCaptureResultKeys()"
        const val ORIGIN_PHYSICAL_REQUEST = "CameraCharacteristics.getAvailablePhysicalCameraRequestKeys()"
        const val ORIGIN_SESSION = "CameraCharacteristics.getAvailableSessionKeys()"

        val ENTRY_ORDER = compareByDescending<KeyEntry> { it.info.vendor }
            .thenBy { it.info.name.lowercase() }
        val INFO_ORDER = compareByDescending<KeyInfo> { it.vendor }
            .thenBy { it.name.lowercase() }
        val NATURAL_ORDER = compareBy<String>({ it.length }, { it })
    }
}

internal fun Throwable.toSerializedError(): SerializedError =
    SerializedError(this.javaClass.name, message)
