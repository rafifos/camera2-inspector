package dev.rafifos.camera2inspector.camera

import android.graphics.Point
import android.graphics.Rect
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Rational
import android.util.Size
import android.util.SizeF
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class SerializedValue(
    val json: Any?,
    val display: String,
    val mode: SerializationMode,
    val rawToString: String?,
    val typeName: String,
)

/**
 * Pure, defensive conversion of Camera2 metadata values into JSON-safe structures.
 * The original value is never mutated and unknown types are preserved through toString().
 */
object CameraValueSerializer {

    const val ANDROID_PREFIX = "android."
    private const val MAX_DISPLAY_CHARS = 6000

    fun isVendorKey(name: String): Boolean = name != "android" && !name.startsWith(ANDROID_PREFIX)

    /**
     * Purely visual grouping hint. The namespace is derived from the key name only and does
     * not imply a manufacturer, a semantic meaning or write support.
     */
    fun extractNamespace(name: String): String {
        if (name.startsWith(ANDROID_PREFIX)) return "android"
        val segments = name.split('.')
        return when {
            segments.size >= 2 -> segments.take(2).joinToString(".")
            segments.size == 1 -> segments[0]
            else -> name
        }
    }

    fun typeNameOf(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> "byte[]"
        is ShortArray -> "short[]"
        is IntArray -> "int[]"
        is LongArray -> "long[]"
        is FloatArray -> "float[]"
        is DoubleArray -> "double[]"
        is BooleanArray -> "boolean[]"
        is CharArray -> "char[]"
        is Array<*> -> "${value.javaClass.componentType?.name ?: "java.lang.Object"}[]"
        else -> value.javaClass.name
    }

    fun serializationModeOf(value: Any?): SerializationMode = when (value) {
        null -> SerializationMode.STRUCTURED
        is String, is Boolean, is Number, is Char -> SerializationMode.STRUCTURED
        is ByteArray, is ShortArray, is IntArray, is LongArray -> SerializationMode.STRUCTURED
        is FloatArray, is DoubleArray, is BooleanArray, is CharArray -> SerializationMode.STRUCTURED
        is Array<*>, is Collection<*>, is Map<*, *> -> SerializationMode.STRUCTURED
        is Size, is SizeF, is Rect, is Point, is Rational, is Range<*>, is Pair<*, *> -> SerializationMode.STRUCTURED
        is StreamConfigurationMap -> SerializationMode.TOSTRING
        else -> SerializationMode.TOSTRING
    }

    fun serialize(value: Any?): SerializedValue {
        val mode = serializationModeOf(value)
        val json = runCatching { toJsonValue(value) }.getOrNull()
        val text = when {
            json == null && value != null -> runCatching { value.toString() }.getOrNull() ?: "<unreadable>"
            else -> renderJson(json)
        }
        return SerializedValue(
            json = json,
            display = text.truncateForDisplay(),
            mode = mode,
            rawToString = rawToString(value),
            typeName = typeNameOf(value),
        )
    }

    fun toJsonValue(value: Any?): Any? = when (value) {
        null -> null
        is String, is Boolean, is Char -> value.toString()
        is Byte, is Short, is Int, is Long -> value
        is Float -> finiteOrString(value.toDouble())
        is Double -> finiteOrString(value)
        is ByteArray -> JSONObject()
            .put("length", value.size)
            .put("base64", Base64.getEncoder().encodeToString(value))
        is ShortArray -> JSONArray(value.toList())
        is IntArray -> JSONArray(value.toList())
        is LongArray -> JSONArray(value.toList())
        is FloatArray -> JSONArray(value.map { finiteOrString(it.toDouble()) })
        is DoubleArray -> JSONArray(value.map { finiteOrString(it) })
        is BooleanArray -> JSONArray(value.toList())
        is CharArray -> JSONArray(value.map { it.toString() })
        is Array<*> -> JSONArray(value.map { toJsonValue(it) })
        is Collection<*> -> JSONArray(value.map { toJsonValue(it) })
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, item) -> put(key.toString(), toJsonValue(item)) }
        }
        is Size -> JSONObject().put("width", value.width).put("height", value.height)
        is SizeF -> JSONObject().put("width", value.width.toDouble()).put("height", value.height.toDouble())
        is Rect -> JSONObject()
            .put("left", value.left)
            .put("top", value.top)
            .put("right", value.right)
            .put("bottom", value.bottom)
        is Point -> JSONObject().put("x", value.x).put("y", value.y)
        is Rational -> JSONObject()
            .put("numerator", value.numerator)
            .put("denominator", value.denominator)
            .put("decimal", value.toDouble())
        is Range<*> -> JSONObject()
            .put("lower", toJsonValue(value.lower))
            .put("upper", toJsonValue(value.upper))
        is Pair<*, *> -> JSONObject()
            .put("first", toJsonValue(value.first))
            .put("second", toJsonValue(value.second))
        else -> value.toString()
    }

    fun toDisplayText(value: Any?): String = renderJson(runCatching { toJsonValue(value) }.getOrNull() ?: value?.toString())

    private fun finiteOrString(number: Double): Any = if (number.isFinite()) number else number.toString()

    private fun renderJson(json: Any?): String = when (json) {
        null -> "null"
        is JSONObject -> runCatching { json.toString(2) }.getOrDefault(json.toString())
        is JSONArray -> runCatching { json.toString(2) }.getOrDefault(json.toString())
        else -> json.toString()
    }

    private fun String.truncateForDisplay(): String =
        if (length <= MAX_DISPLAY_CHARS) this
        else take(MAX_DISPLAY_CHARS) + "\n… (truncated for display; full value preserved in the export)"

    private fun rawToString(value: Any?): String? {
        if (value == null) return null
        if (value is String || value is Number || value is Boolean || value is Char) return null
        val text = runCatching { value.toString() }.getOrNull() ?: return null
        if (IDENTITY_TO_STRING.matches(text)) return null
        return text
    }

    private val IDENTITY_TO_STRING = Regex("^.*@[0-9a-fA-F]{4,}$")
}
