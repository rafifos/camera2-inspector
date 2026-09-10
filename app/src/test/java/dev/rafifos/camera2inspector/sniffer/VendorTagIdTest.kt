package dev.rafifos.camera2inspector.sniffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorTagIdTest {

    @Test
    fun `negative signed id maps to unsigned`() {
        assertEquals(2150498328L, VendorTagId.unsigned(-2144468968))
    }

    @Test
    fun `unsigned id maps back to signed`() {
        assertEquals(-2144468968, VendorTagId.signed(2150498328L))
    }

    @Test
    fun `hex keeps eight digits`() {
        assertEquals("0x802e0018", VendorTagId.hex(-2144468968))
        assertEquals("0x80000000", VendorTagId.hex(Int.MIN_VALUE))
    }

    @Test
    fun `parse accepts hex decimal and negative tokens`() {
        assertEquals(2150498328L, VendorTagId.parse("0x802e0018"))
        assertEquals(2150498328L, VendorTagId.parse("2150498328"))
        assertEquals(2150498328L, VendorTagId.parse("-2144468968"))
    }

    @Test
    fun `parse rejects non numeric tokens`() {
        assertNull(VendorTagId.parse("EnableHDRDCGMode"))
        assertNull(VendorTagId.parse(""))
    }

    @Test
    fun `signed round trip is stable for min and max`() {
        assertEquals(0, VendorTagId.signed(VendorTagId.unsigned(0)))
        assertEquals(Int.MIN_VALUE, VendorTagId.signed(VendorTagId.unsigned(Int.MIN_VALUE)))
        assertEquals(Int.MAX_VALUE, VendorTagId.signed(VendorTagId.unsigned(Int.MAX_VALUE)))
    }
}
