package dev.rafifos.camera2inspector.camera

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraValueSerializerTest {

    @Test
    fun `android keys are not vendor`() {
        assertFalse(CameraValueSerializer.isVendorKey("android.control.aeMode"))
        assertFalse(CameraValueSerializer.isVendorKey("android"))
    }

    @Test
    fun `non android keys are vendor`() {
        assertTrue(CameraValueSerializer.isVendorKey("com.example.camera.hdr.mode"))
        assertTrue(CameraValueSerializer.isVendorKey("qti.someVendorKey"))
    }

    @Test
    fun `namespace keeps the first two segments`() {
        assertEquals(
            "com.example",
            CameraValueSerializer.extractNamespace("com.example.camera.hdr.mode"),
        )
        assertEquals("io.vendor", CameraValueSerializer.extractNamespace("io.vendor.camera.key"))
    }

    @Test
    fun `android namespace is normalized`() {
        assertEquals("android", CameraValueSerializer.extractNamespace("android.control.aeMode"))
    }

    @Test
    fun `single segment name is returned as namespace`() {
        assertEquals("qti", CameraValueSerializer.extractNamespace("qti"))
    }

    @Test
    fun `type names cover primitives and arrays`() {
        assertEquals("java.lang.Integer", CameraValueSerializer.typeNameOf(1))
        assertEquals("int[]", CameraValueSerializer.typeNameOf(intArrayOf(1, 2)))
        assertEquals("java.lang.String[]", CameraValueSerializer.typeNameOf(arrayOf("a", "b")))
        assertEquals("null", CameraValueSerializer.typeNameOf(null))
    }

    @Test
    fun `primitive value is structured`() {
        val value = CameraValueSerializer.serialize(42)
        assertEquals(SerializationMode.STRUCTURED, value.mode)
        assertEquals("42", value.display)
    }

    @Test
    fun `int array is serialized as json array`() {
        val value = CameraValueSerializer.serialize(intArrayOf(1, 2, 3))
        assertEquals(SerializationMode.STRUCTURED, value.mode)
        assertEquals("[1,2,3]", (value.json as JSONArray).toString())
    }

    @Test
    fun `byte array is preserved as base64`() {
        val value = CameraValueSerializer.serialize(byteArrayOf(1, 2, 3))
        val json = value.json as JSONObject
        assertEquals(3, json.getInt("length"))
        assertEquals("AQID", json.getString("base64"))
    }

    @Test
    fun `map and list are structured`() {
        val value = CameraValueSerializer.serialize(mapOf("a" to 1, "b" to listOf(true, "x")))
        assertEquals(SerializationMode.STRUCTURED, value.mode)
        val json = value.json as JSONObject
        assertEquals(1, json.getInt("a"))
        assertEquals(2, json.getJSONArray("b").length())
    }

    @Test
    fun `unknown object falls back to tostring without losing the value`() {
        val value = CameraValueSerializer.serialize(UnknownValue("abc"))
        assertEquals(SerializationMode.TOSTRING, value.mode)
        assertTrue(value.display.contains("abc"))
    }

    @Test
    fun `unknown object with tostring keeps the raw value`() {
        val value = CameraValueSerializer.serialize(UnknownValue("abc"))
        assertEquals("UnknownValue(abc)", value.rawToString)
    }

    @Test
    fun `identity tostring is discarded`() {
        val value = CameraValueSerializer.serialize(PlainValue())
        assertEquals(null, value.rawToString)
    }

    @Test
    fun `non finite double becomes text`() {
        val value = CameraValueSerializer.serialize(Double.NaN)
        assertEquals("NaN", value.display)
    }

    @Test
    fun `null value renders as null`() {
        val value = CameraValueSerializer.serialize(null)
        assertEquals("null", value.display)
    }

    private class UnknownValue(private val text: String) {
        override fun toString(): String = "UnknownValue($text)"
    }

    private class PlainValue
}
