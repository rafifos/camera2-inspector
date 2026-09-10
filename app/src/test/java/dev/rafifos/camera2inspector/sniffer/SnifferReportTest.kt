package dev.rafifos.camera2inspector.sniffer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnifferReportTest {

    private val hdrMode = "org.codeaurora.qcamera3.sessionParameters.HDRMode"
    private val otherVendor = "com.example.other.key"
    private val aeMode = "android.control.aeMode"

    private fun session(): SnifferSession = SnifferFixtures.session(
        label = "PHOTO_1X",
        frames = listOf(
            SnifferFixtures.frame(
                frameNumber = 10,
                values = linkedMapOf(
                    hdrMode to 0,
                    otherVendor to "abc",
                    aeMode to 1,
                ),
            ),
            SnifferFixtures.frame(
                frameNumber = 11,
                values = linkedMapOf(
                    hdrMode to 24,
                    otherVendor to "abc",
                    aeMode to 1,
                ),
            ),
        ),
        keyMeta = listOf(
            SnifferFixtures.keyMeta(hdrMode, tagId = 0x802e0002L, declaredType = "java.lang.Integer"),
            SnifferFixtures.keyMeta(otherVendor, tagId = 0x802e0018L),
            SnifferFixtures.keyMeta(aeMode, vendor = false),
        ),
    )

    @Test
    fun `json report keeps ids signed unsigned and hex`() {
        val json = JSONObject(SnifferReport.toJson(session()))
        val entry = json.getJSONObject("keyDirectory").getJSONObject(hdrMode)
        assertEquals("0x802e0002", entry.getString("hexId"))
        assertEquals(-2144468990, entry.getInt("signedId"))
        assertEquals(2150498306L, entry.getLong("unsignedId"))
        assertTrue(entry.getBoolean("vendor"))
        assertEquals("java.lang.Integer", entry.getString("type"))
    }

    @Test
    fun `json report splits vendor and standard values`() {
        val json = JSONObject(SnifferReport.toJson(session()))
        val frame = json.getJSONArray("frames").getJSONObject(0)
        val total = frame.getJSONObject("totalResult")
        assertTrue(total.has("standard"))
        val vendor = total.getJSONArray("vendor")
        val names = (0 until vendor.length()).map { vendor.getJSONObject(it).getString("name") }
        assertTrue(names.contains(hdrMode))
        assertTrue(names.contains(otherVendor))
        assertFalse(names.contains(aeMode))
        val hdr = (0 until vendor.length())
            .map { vendor.getJSONObject(it) }
            .first { it.getString("name") == hdrMode }
        assertEquals(0, hdr.getInt("value"))
    }

    @Test
    fun `vendor only option removes standard sections`() {
        val options = SnifferReportOptions(vendorOnly = true)
        val json = JSONObject(SnifferReport.toJson(session(), options))
        val frame = json.getJSONArray("frames").getJSONObject(0)
        assertFalse(frame.getJSONObject("totalResult").has("standard"))
    }

    @Test
    fun `interesting only option keeps hdr keys and drops unrelated vendor keys`() {
        val options = SnifferReportOptions(interestingOnly = true)
        val json = JSONObject(SnifferReport.toJson(session(), options))
        val directory = json.getJSONObject("keyDirectory")
        assertTrue(directory.has(hdrMode))
        assertFalse(directory.has(otherVendor))
        assertFalse(directory.has(aeMode))
    }

    @Test
    fun `csv contains header and vendor row with ids`() {
        val csv = SnifferReport.toCsv(session())
        assertTrue(csv.startsWith("frameNumber,timestamp,capture,section,cameraId,key,vendor,type,hexId,signedId,unsignedId,value"))
        val hdrLine = csv.lines().first { it.contains(hdrMode) }
        assertTrue(hdrLine.contains("0x802e0002"))
        assertTrue(hdrLine.contains("-2144468990"))
    }

    @Test
    fun `markdown contains session table and comparison tables`() {
        val baseline = session()
        val hdr = SnifferFixtures.session(
            label = "HDR",
            frames = listOf(SnifferFixtures.frame(1, linkedMapOf(hdrMode to 24))),
            keyMeta = listOf(SnifferFixtures.keyMeta(hdrMode, tagId = 0x802e0002L)),
        )
        val changes = SessionDiff.diffSessions(baseline, hdr)
        val markdown = SnifferReport.toMarkdown(listOf(baseline, hdr), listOf("PHOTO_1X vs HDR" to changes))
        assertTrue(markdown.contains("| Label |"))
        assertTrue(markdown.contains("| Key |"))
        assertTrue(markdown.contains("## PHOTO_1X vs HDR"))
        assertTrue(markdown.contains(hdrMode))
    }

    @Test
    fun `frame values keep structured json`() {
        val frame = FrameRecord(
            frameNumber = 1,
            sensorTimestampNs = 1000,
            cameraId = "0",
            sequenceId = 1,
            isCaptureFrame = false,
            requestValues = linkedMapOf("android.sensor.info.pixelArraySize" to JSONObject().put("width", 4000).put("height", 3000)),
            partialResults = listOf(linkedMapOf("android.control.aeMode" to 1)),
            totalResultValues = linkedMapOf(
                "android.scaler.cropRegion" to JSONObject().put("left", 0).put("top", 0),
                "android.control.aeMode" to 1,
            ),
            physicalTotalResults = mapOf("2" to linkedMapOf("android.control.aeMode" to 1)),
            jpegTimestampNs = null,
            errors = emptyList(),
        )
        val session = SnifferFixtures.session("STRUCT", listOf(frame))
        val json = JSONObject(SnifferReport.toJson(session))
        val parsed = json.getJSONArray("frames").getJSONObject(0)
        assertEquals(4000, parsed.getJSONObject("request").getJSONObject("standard")
            .getJSONObject("android.sensor.info.pixelArraySize").getInt("width"))
        assertEquals(1, parsed.getJSONArray("partialResults").getJSONObject(0)
            .getJSONObject("standard").getInt("android.control.aeMode"))
        assertTrue(parsed.getJSONObject("physicalTotalResults").has("2"))
    }

    @Test
    fun `byte array values from the serializer survive the report`() {
        val bytes = dev.rafifos.camera2inspector.camera.CameraValueSerializer.toJsonValue(byteArrayOf(1, 2, 3))
        val frame = SnifferFixtures.frame(1, linkedMapOf("com.example.blob" to bytes))
        val session = SnifferFixtures.session("BYTES", listOf(frame))
        val json = JSONObject(SnifferReport.toJson(session))
        val value = json.getJSONArray("frames").getJSONObject(0)
            .getJSONObject("totalResult").getJSONArray("vendor").getJSONObject(0).getJSONObject("value")
        assertEquals(3, value.getInt("length"))
        assertEquals("AQID", value.getString("base64"))
    }

    @Test
    fun `json arrays in values are preserved`() {
        val array = JSONArray(listOf(0, 1, 2, 3, 6))
        val frame = SnifferFixtures.frame(1, linkedMapOf("android.control.aeAvailableModes" to array))
        val session = SnifferFixtures.session("ARRAY", listOf(frame))
        val json = JSONObject(SnifferReport.toJson(session))
        val parsed = json.getJSONArray("frames").getJSONObject(0)
            .getJSONObject("totalResult").getJSONObject("standard")
            .getJSONArray("android.control.aeAvailableModes")
        assertEquals(5, parsed.length())
        assertEquals(6, parsed.getInt(4))
    }
}
