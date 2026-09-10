package dev.rafifos.camera2inspector.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportSummaryTest {

    @Test
    fun `summary aggregates totals and vendor counts across cameras`() {
        val camera0 = cameraReport(
            id = "0",
            characteristics = listOf(
                keyEntry("android.control.aeMode", serialization = SerializationMode.STRUCTURED, value = 1),
                keyEntry("com.example.camera.hdr", serialization = SerializationMode.STRUCTURED, value = 2),
            ),
            captureRequests = listOf(
                keyInfo("android.control.aeMode", KeyCategory.CAPTURE_REQUEST),
                keyInfo("com.example.request", KeyCategory.CAPTURE_REQUEST),
            ),
            captureResults = listOf(keyInfo("android.control.aeMode", KeyCategory.CAPTURE_RESULT)),
            physicalCaptureRequests = listOf(
                keyInfo("com.example.physical", KeyCategory.PHYSICAL_CAPTURE_REQUEST),
            ),
            sessionKeys = listOf(keyInfo("android.control.aeMode", KeyCategory.SESSION)),
        )
        val camera1 = cameraReport(
            id = "1",
            characteristics = listOf(keyEntry("android.control.aeMode")),
        )

        val summary = inspectionReport(listOf(camera0, camera1)).summary()

        assertEquals(2, summary.cameraCount)
        assertEquals(3, summary.characteristicCount)
        assertEquals(1, summary.vendorCharacteristics)
        assertEquals(2, summary.captureRequestCount)
        assertEquals(1, summary.vendorCaptureRequests)
        assertEquals(1, summary.captureResultCount)
        assertEquals(0, summary.vendorCaptureResults)
        assertEquals(1, summary.physicalRequestCount)
        assertEquals(1, summary.vendorPhysicalRequests)
        assertEquals(1, summary.sessionKeyCount)
        assertEquals(0, summary.vendorSessionKeys)
        assertEquals(3, summary.vendorTotal)
    }

    @Test
    fun `empty report has zeroed summary`() {
        val summary = inspectionReport(emptyList()).summary()
        assertEquals(0, summary.cameraCount)
        assertEquals(0, summary.vendorTotal)
    }
}
