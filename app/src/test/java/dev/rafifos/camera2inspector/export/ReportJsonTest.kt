package dev.rafifos.camera2inspector.export

import dev.rafifos.camera2inspector.camera.KeyCategory
import dev.rafifos.camera2inspector.camera.SerializationMode
import dev.rafifos.camera2inspector.camera.SerializedError
import dev.rafifos.camera2inspector.camera.cameraReport
import dev.rafifos.camera2inspector.camera.inspectionReport
import dev.rafifos.camera2inspector.camera.keyEntry
import dev.rafifos.camera2inspector.camera.keyInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportJsonTest {

    private fun sampleReport() = inspectionReport(
        listOf(
            cameraReport(
                id = "0",
                logicalMultiCamera = true,
                physicalCameraIds = listOf("2", "3"),
                characteristics = listOf(
                    keyEntry(
                        name = "android.control.aeMode",
                        value = 1,
                        valueText = "1",
                        serialization = SerializationMode.STRUCTURED,
                    ),
                    keyEntry(
                        name = "com.example.camera.hdr.mode",
                        value = 2,
                        valueText = "2",
                        serialization = SerializationMode.STRUCTURED,
                    ),
                    keyEntry(
                        name = "com.example.camera.broken",
                        serialization = SerializationMode.ERROR,
                        error = SerializedError("java.lang.SecurityException", "denied"),
                    ),
                ),
                captureRequests = listOf(
                    keyInfo("com.example.request.key", KeyCategory.CAPTURE_REQUEST),
                ),
                captureResults = listOf(
                    keyInfo("android.control.aeMode", KeyCategory.CAPTURE_RESULT),
                ),
                physicalCaptureRequests = listOf(
                    keyInfo("com.example.physical.key", KeyCategory.PHYSICAL_CAPTURE_REQUEST),
                ),
                sessionKeys = listOf(
                    keyInfo("com.example.session.key", KeyCategory.SESSION),
                ),
            ),
        ),
    )

    @Test
    fun `report contains stable top level sections`() {
        val json = ReportJson.buildJson(sampleReport())

        assertEquals("Camera2 Inspector", json.getJSONObject("app").getString("name"))
        assertEquals("TestManufacturer", json.getJSONObject("device").getString("manufacturer"))
        assertEquals("2026-01-01T00:00:00Z", json.getString("generatedAt"))
        assertTrue(json.has("summary"))
        assertEquals(1, json.getJSONArray("cameras").length())
    }

    @Test
    fun `summary counts vendor keys across categories`() {
        val summary = ReportJson.buildJson(sampleReport()).getJSONObject("summary")

        assertEquals(1, summary.getInt("cameraCount"))
        assertEquals(3, summary.getInt("characteristics"))
        assertEquals(2, summary.getInt("vendorCharacteristics"))
        assertEquals(1, summary.getInt("captureRequests"))
        assertEquals(1, summary.getInt("vendorCaptureRequests"))
        assertEquals(1, summary.getInt("captureResults"))
        assertEquals(0, summary.getInt("vendorCaptureResults"))
        assertEquals(1, summary.getInt("physicalCaptureRequests"))
        assertEquals(1, summary.getInt("vendorPhysicalCaptureRequests"))
        assertEquals(1, summary.getInt("sessionKeys"))
        assertEquals(1, summary.getInt("vendorSessionKeys"))
    }

    @Test
    fun `camera entry preserves categories and physical ids`() {
        val camera = ReportJson.buildJson(sampleReport()).getJSONArray("cameras").getJSONObject(0)

        assertEquals("0", camera.getString("id"))
        assertTrue(camera.getBoolean("logicalMultiCamera"))
        assertEquals("2", camera.getJSONArray("physicalCameraIds").getString(0))
        assertEquals(3, camera.getJSONArray("characteristics").length())
        assertEquals(1, camera.getJSONArray("captureRequestKeys").length())
        assertEquals(1, camera.getJSONArray("captureResultKeys").length())
        assertEquals(1, camera.getJSONArray("physicalCaptureRequestKeys").length())
        assertEquals(1, camera.getJSONArray("sessionKeys").length())
    }

    @Test
    fun `characteristic entry exposes name, vendor flag, value and error`() {
        val keys = ReportJson.buildJson(sampleReport())
            .getJSONArray("cameras").getJSONObject(0)
            .getJSONArray("characteristics")

        val android = keys.getJSONObject(0)
        assertEquals("android.control.aeMode", android.getString("name"))
        assertFalse(android.getBoolean("vendor"))
        assertEquals(1, android.getInt("value"))
        assertEquals("android", android.getString("namespace"))

        val vendor = keys.getJSONObject(1)
        assertEquals("com.example", vendor.getString("namespace"))
        assertTrue(vendor.getBoolean("vendor"))

        val broken = keys.getJSONObject(2)
        assertFalse(broken.has("value"))
        assertEquals("error", broken.getString("serialization"))
        assertEquals(
            "java.lang.SecurityException",
            broken.getJSONObject("error").getString("type"),
        )
    }

    @Test
    fun `json string is valid utf8 parsable`() {
        val text = ReportJson.toJsonString(sampleReport())
        val parsed = JSONObject(text)
        assertTrue(parsed.has("cameras"))
    }
}
