package dev.rafifos.camera2inspector.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFilterTest {

    private val entries = listOf(
        keyEntry("android.control.aeMode"),
        keyEntry("android.control.afMode"),
        keyEntry("com.example.camera.hdr.mode"),
        keyEntry("com.example.camera.night.mode"),
        keyEntry("org.other.vendor.key"),
    )

    @Test
    fun `empty query returns everything`() {
        assertEquals(entries.size, filterKeyEntries(entries, KeyQuery()).size)
    }

    @Test
    fun `text search is case insensitive`() {
        val result = filterKeyEntries(entries, KeyQuery(text = "HDR"))
        assertEquals(listOf("com.example.camera.hdr.mode"), result.map { it.info.name })
    }

    @Test
    fun `namespace filter matches exactly`() {
        val result = filterKeyEntries(entries, KeyQuery(namespace = "com.example"))
        assertEquals(2, result.size)
    }

    @Test
    fun `vendor only excludes android keys`() {
        val result = filterKeyEntries(entries, KeyQuery(vendorOnly = true))
        assertTrue(result.all { it.info.vendor })
        assertEquals(3, result.size)
    }

    @Test
    fun `distinct namespaces only list vendor namespaces sorted by count`() {
        val result = distinctNamespaces(entries)
        assertEquals(listOf("com.example" to 2, "org.other" to 1), result)
    }

    @Test
    fun `entries for category maps each key class`() {
        val report = cameraReport(
            id = "0",
            characteristics = listOf(keyEntry("android.control.aeMode")),
            captureRequests = listOf(keyInfo("com.example.request", KeyCategory.CAPTURE_REQUEST)),
            captureResults = listOf(keyInfo("com.example.result", KeyCategory.CAPTURE_RESULT)),
            physicalCaptureRequests = listOf(
                keyInfo("com.example.physical", KeyCategory.PHYSICAL_CAPTURE_REQUEST),
            ),
            sessionKeys = listOf(keyInfo("com.example.session", KeyCategory.SESSION)),
        )

        assertEquals(KeyCategory.CHARACTERISTICS, entriesForCategory(report, KeyCategory.CHARACTERISTICS).single().info.category)
        assertEquals(KeyCategory.CAPTURE_REQUEST, entriesForCategory(report, KeyCategory.CAPTURE_REQUEST).single().info.category)
        assertEquals(KeyCategory.CAPTURE_RESULT, entriesForCategory(report, KeyCategory.CAPTURE_RESULT).single().info.category)
        assertEquals(
            KeyCategory.PHYSICAL_CAPTURE_REQUEST,
            entriesForCategory(report, KeyCategory.PHYSICAL_CAPTURE_REQUEST).single().info.category,
        )
        assertEquals(KeyCategory.SESSION, entriesForCategory(report, KeyCategory.SESSION).single().info.category)
    }
}
