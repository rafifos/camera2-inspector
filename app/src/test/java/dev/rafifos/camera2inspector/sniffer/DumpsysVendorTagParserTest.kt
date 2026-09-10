package dev.rafifos.camera2inspector.sniffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpsysVendorTagParserTest {

    private val sample = """
        == Vendor tags: ==
          Dumping vendor tag descriptors for vendor with id 10542202519338036161
          Dumping configured vendor tag descriptors: 1135 entries
            0x80000000 (private_property) with type 0 (byte) defined in section org.codeaurora.qcamera3.internal_private
            0x802e0018 (EnableHDRDCGMode) with type 0 (byte) defined in section org.codeaurora.qcamera3.sessionParameters
        == Camera HAL device static information: ==
          API2 camera characteristics:
            org.codeaurora.qcamera3.sessionParameters.HDRMode (802e0002): int32[1]
            android.request.availableRequestKeys (00009): int32[217]
              [524303 (intrinsicCalibrationMaximumResolution) -2129199104 (is_motcamera2) -2129199103 (current_mode) ]
    """.trimIndent()

    @Test
    fun `parses descriptor lines with section and short name`() {
        val tags = DumpsysVendorTagParser.parse(sample)
        val tag = tags.first { it.name == "org.codeaurora.qcamera3.internal_private.private_property" }
        assertEquals(0x80000000L, tag.tagId)
        assertEquals("org.codeaurora.qcamera3.internal_private", tag.section)
        assertEquals("byte", tag.typeName)

        val hdr = tags.first { it.name == "org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode" }
        assertEquals(0x802e0018L, hdr.tagId)
        assertEquals("org.codeaurora.qcamera3.sessionParameters", hdr.section)
    }

    @Test
    fun `parses static metadata lines with full name and type`() {
        val tags = DumpsysVendorTagParser.parse(sample)
        val hdr = tags.first { it.name == "org.codeaurora.qcamera3.sessionParameters.HDRMode" }
        assertEquals(0x802e0002L, hdr.tagId)
        assertEquals("int32", hdr.typeName)
        assertEquals("org.codeaurora.qcamera3.sessionParameters", hdr.section)
    }

    @Test
    fun `ignores android keys and unknown lines`() {
        val tags = DumpsysVendorTagParser.parse(sample)
        assertTrue(tags.none { it.name.startsWith("android.") })
        assertTrue(tags.none { it.name.contains("intrinsicCalibrationMaximumResolution") })
    }

    @Test
    fun `negative ids in key arrays do not produce bogus entries`() {
        val tags = DumpsysVendorTagParser.parse(sample)
        assertEquals(3, tags.size)
    }
}
