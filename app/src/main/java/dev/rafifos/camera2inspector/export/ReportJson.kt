package dev.rafifos.camera2inspector.export

import dev.rafifos.camera2inspector.camera.CameraNote
import dev.rafifos.camera2inspector.camera.CameraReport
import dev.rafifos.camera2inspector.camera.DeviceReport
import dev.rafifos.camera2inspector.camera.InspectionReport
import dev.rafifos.camera2inspector.camera.KeyEntry
import dev.rafifos.camera2inspector.camera.KeyInfo
import dev.rafifos.camera2inspector.camera.ReportSummary
import dev.rafifos.camera2inspector.camera.SerializationMode
import dev.rafifos.camera2inspector.camera.SerializedError
import dev.rafifos.camera2inspector.camera.summary
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON projection of an [InspectionReport]. Contains no Android dependency so it can be
 * unit tested on the JVM and reused by other exporters in the future.
 */
object ReportJson {

    fun toJsonString(report: InspectionReport): String = buildJson(report).toString(2)

    fun buildJson(report: InspectionReport): JSONObject = JSONObject().apply {
        put(
            "app",
            JSONObject()
                .put("name", report.appName)
                .put("version", report.appVersion)
                .put("package", report.packageName),
        )
        put("generatedAt", report.generatedAt)
        put("device", deviceJson(report.device))
        put("summary", summaryJson(report.summary()))
        if (report.errors.isNotEmpty()) put("errors", errorArray(report.errors))
        put("cameras", JSONArray(report.cameras.map { cameraJson(it) }))
    }

    private fun deviceJson(device: DeviceReport): JSONObject = JSONObject().apply {
        put("manufacturer", device.manufacturer)
        put("brand", device.brand)
        put("model", device.model)
        put("device", device.device)
        put("product", device.product)
        put("fingerprint", device.fingerprint)
        put("hardware", device.hardware)
        put("board", device.board)
        put("androidVersion", device.androidVersion)
        put("sdk", device.sdk)
        put("supportedAbis", JSONArray(device.supportedAbis))
    }

    private fun summaryJson(summary: ReportSummary): JSONObject = JSONObject().apply {
        put("cameraCount", summary.cameraCount)
        put("characteristics", summary.characteristicCount)
        put("vendorCharacteristics", summary.vendorCharacteristics)
        put("captureRequests", summary.captureRequestCount)
        put("vendorCaptureRequests", summary.vendorCaptureRequests)
        put("captureResults", summary.captureResultCount)
        put("vendorCaptureResults", summary.vendorCaptureResults)
        put("physicalCaptureRequests", summary.physicalRequestCount)
        put("vendorPhysicalCaptureRequests", summary.vendorPhysicalRequests)
        put("sessionKeys", summary.sessionKeyCount)
        put("vendorSessionKeys", summary.vendorSessionKeys)
        put("vendorTotal", summary.vendorTotal)
    }

    private fun cameraJson(camera: CameraReport): JSONObject = JSONObject().apply {
        put("id", camera.id)
        put("logicalMultiCamera", camera.logicalMultiCamera)
        put("physicalCameraIds", JSONArray(camera.physicalCameraIds))
        put("hardwareLevel", camera.hardwareLevel)
        put("lensFacing", camera.lensFacing)
        putOrNull("sensorOrientation", camera.sensorOrientation)
        put("notes", JSONArray(camera.notes.map { noteText(it) }))
        if (camera.errors.isNotEmpty()) put("errors", errorArray(camera.errors))
        put("characteristics", JSONArray(camera.characteristics.map { keyEntryJson(it) }))
        put("captureRequestKeys", JSONArray(camera.captureRequests.map { keyInfoJson(it) }))
        put("captureResultKeys", JSONArray(camera.captureResults.map { keyInfoJson(it) }))
        put("physicalCaptureRequestKeys", JSONArray(camera.physicalCaptureRequests.map { keyInfoJson(it) }))
        put("sessionKeys", JSONArray(camera.sessionKeys.map { keyInfoJson(it) }))
    }

    private fun keyInfoJson(info: KeyInfo): JSONObject = JSONObject().apply {
        put("name", info.name)
        put("type", info.typeLabel)
        put("typeSource", info.typeSource)
        put("vendor", info.vendor)
        put("namespace", info.namespace)
        put("category", info.category.label)
        put("origin", info.origin)
    }

    private fun keyEntryJson(entry: KeyEntry): JSONObject = keyInfoJson(entry.info).apply {
        put("serialization", entry.serialization.label)
        if (entry.serialization != SerializationMode.ERROR) {
            put("value", entry.value ?: JSONObject.NULL)
        }
        entry.rawToString?.let { put("toString", it) }
        entry.error?.let { put("error", errorJson(it)) }
    }

    private fun errorJson(error: SerializedError): JSONObject = JSONObject().apply {
        put("type", error.type)
        putOrNull("message", error.message)
    }

    private fun noteText(note: CameraNote): String = when (note) {
        CameraNote.LogicalMultiCamera ->
            "Device reports REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA."

        is CameraNote.PhysicalCameraIds ->
            "Physical camera IDs reported: ${note.ids.joinToString(", ")}."

        is CameraNote.KeysNotReported ->
            "${note.origin} returned no keys."

        CameraNote.CharacteristicsUnreadable ->
            "CameraCharacteristics could not be read for this camera."
    }

    private fun errorArray(errors: List<SerializedError>): JSONArray =
        JSONArray(errors.map { errorJson(it) })

    private fun JSONObject.putOrNull(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }
}
