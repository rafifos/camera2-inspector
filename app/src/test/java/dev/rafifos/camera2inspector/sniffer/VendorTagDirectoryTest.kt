package dev.rafifos.camera2inspector.sniffer

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorTagDirectoryTest {

    private val dump = """
        0x802e0018 (EnableHDRDCGMode) with type 0 (byte) defined in section org.codeaurora.qcamera3.sessionParameters
        org.codeaurora.qcamera3.sessionParameters.HDRMode (802e0002): int32[1]
    """.trimIndent()

    @Test
    fun `dumpsys directory resolves names ids and types`() {
        val directory = VendorTagDirectory.fromDumpsys(dump)
        val enable = directory.info("org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode")
        assertEquals(0x802e0018L, enable?.tagId)
        assertEquals(-2144468968, enable?.signedId)
        assertEquals("0x802e0018", enable?.hexId)
        assertEquals("byte", enable?.dumpType)

        val hdrMode = directory.info("org.codeaurora.qcamera3.sessionParameters.HDRMode")
        assertEquals(0x802e0002L, hdrMode?.tagId)
        assertEquals("int32", hdrMode?.dumpType)
    }

    @Test
    fun `merge fills missing fields and combines sources`() {
        val first = VendorTagDirectory.of(
            listOf(
                VendorTagInfo(
                    name = "com.example.key",
                    section = "com.example",
                    tagId = null,
                    declaredType = "java.lang.Integer",
                    dumpType = null,
                    sources = setOf("characteristics"),
                ),
            ),
        )
        val second = VendorTagDirectory.of(
            listOf(
                VendorTagInfo(
                    name = "com.example.key",
                    section = null,
                    tagId = 0x802e0018L,
                    declaredType = null,
                    dumpType = "int32",
                    sources = setOf("dumpsys"),
                ),
            ),
        )
        val merged = first.merge(second).info("com.example.key")
        assertEquals("java.lang.Integer", merged?.declaredType)
        assertEquals("int32", merged?.dumpType)
        assertEquals(0x802e0018L, merged?.tagId)
        assertEquals(setOf("characteristics", "dumpsys"), merged?.sources)
    }

    @Test
    fun `empty directory merge returns the other side`() {
        val other = VendorTagDirectory.fromDumpsys(dump)
        assertEquals(other.size, VendorTagDirectory.EMPTY.merge(other).size)
        assertEquals(other.size, other.merge(VendorTagDirectory.EMPTY).size)
    }
}
