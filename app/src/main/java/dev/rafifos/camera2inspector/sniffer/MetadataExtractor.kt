package dev.rafifos.camera2inspector.sniffer

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import dev.rafifos.camera2inspector.camera.CameraValueSerializer
import dev.rafifos.camera2inspector.camera.KeyTypeResolver

/**
 * Reads the keys present in a request or result into JSON-safe values and keeps per-session key
 * metadata. Everything is copied during the camera callback because framework result objects are
 * only guaranteed to be valid inside the callback.
 */
class MetadataExtractor(
    private val directory: VendorTagDirectory,
    private val skipKey: (String) -> Boolean = { false },
) {

    data class Extraction(
        val values: Map<String, Any?>,
        val errors: List<KeyError>,
    )

    private val metaCache = LinkedHashMap<String, KeyMeta>()
    private val unavailableDeclaredTypes = HashSet<String>()

    val keyMeta: Map<String, KeyMeta> get() = metaCache

    fun extractRequest(request: CaptureRequest): Extraction {
        val values = LinkedHashMap<String, Any?>()
        val errors = mutableListOf<KeyError>()
        request.keys.forEach { key ->
            val name = runCatching { key.name }.getOrNull() ?: return@forEach
            if (skipKey(name)) return@forEach
            try {
                @Suppress("UNCHECKED_CAST")
                val value = request.get(key as CaptureRequest.Key<Any>)
                values[name] = CameraValueSerializer.toJsonValue(value)
                updateMeta(name, declaredType = reflectDeclaredType(name, key), valueType = value?.let { CameraValueSerializer.typeNameOf(it) })
            } catch (t: Throwable) {
                errors += KeyError(name, t.javaClass.name, t.message)
            }
        }
        return Extraction(values, errors)
    }

    fun extractResult(result: CaptureResult): Extraction {
        val values = LinkedHashMap<String, Any?>()
        val errors = mutableListOf<KeyError>()
        result.keys.forEach { key ->
            val name = runCatching { key.name }.getOrNull() ?: return@forEach
            if (skipKey(name)) return@forEach
            try {
                @Suppress("UNCHECKED_CAST")
                val value = result.get(key as CaptureResult.Key<Any>)
                values[name] = CameraValueSerializer.toJsonValue(value)
                updateMeta(name, declaredType = reflectDeclaredType(name, key), valueType = value?.let { CameraValueSerializer.typeNameOf(it) })
            } catch (t: Throwable) {
                errors += KeyError(name, t.javaClass.name, t.message)
            }
        }
        return Extraction(values, errors)
    }

    private fun reflectDeclaredType(name: String, key: Any): String? {
        metaCache[name]?.declaredType?.let { return it }
        if (name in unavailableDeclaredTypes) return null
        val resolved = runCatching { KeyTypeResolver.resolveDeclaredType(key) }.getOrNull()
        if (resolved == null) unavailableDeclaredTypes += name
        return resolved
    }

    private fun updateMeta(name: String, declaredType: String?, valueType: String?) {
        val current = metaCache[name]
        val info = directory.info(name)
        val resolvedDeclared = current?.declaredType ?: declaredType ?: info?.declaredType
        val resolvedValue = current?.valueType ?: valueType
        val resolvedTag = current?.tagId ?: info?.tagId
        val resolvedDump = current?.dumpType ?: info?.dumpType
        if (current != null &&
            current.declaredType == resolvedDeclared &&
            current.valueType == resolvedValue &&
            current.tagId == resolvedTag &&
            current.dumpType == resolvedDump
        ) {
            return
        }
        metaCache[name] = KeyMeta(
            name = name,
            vendor = current?.vendor ?: CameraValueSerializer.isVendorKey(name),
            namespace = current?.namespace ?: CameraValueSerializer.extractNamespace(name),
            declaredType = resolvedDeclared,
            valueType = resolvedValue,
            tagId = resolvedTag,
            dumpType = resolvedDump,
        )
    }
}
