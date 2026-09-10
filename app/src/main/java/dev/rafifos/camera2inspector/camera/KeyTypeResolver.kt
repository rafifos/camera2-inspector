package dev.rafifos.camera2inspector.camera

/**
 * Best-effort declared type lookup for Camera2 metadata keys.
 *
 * The public SDK only exposes [android.hardware.camera2.CameraCharacteristics.Key.getName], and the
 * declared type lives in the hidden `CameraMetadataNative.Key` (field `mType`). Reflection is used
 * exclusively for read-only diagnosis of the type name. It never writes metadata, never touches
 * camera state and every failure degrades to "type unavailable" without aborting the inspection.
 */
object KeyTypeResolver {

    fun resolveDeclaredType(key: Any): String? {
        readDirectField(key)?.let { return it }
        readViaPublicMethod(key)?.let { return it }
        readViaWrappedMetadataKey(key)?.let { return it }
        return null
    }

    private fun readDirectField(key: Any): String? = runCatching {
        val field = key.javaClass.getDeclaredField("mType")
        field.isAccessible = true
        (field.get(key) as? Class<*>)?.name
    }.getOrNull()

    private fun readViaPublicMethod(key: Any): String? = runCatching {
        val method = key.javaClass.getMethod("getValueType")
        (method.invoke(key) as? Class<*>)?.name
    }.getOrNull()

    private fun readViaWrappedMetadataKey(key: Any): String? = runCatching {
        val wrapper = key.javaClass.getDeclaredField("mKey")
        wrapper.isAccessible = true
        val metadataKey = wrapper.get(key) ?: return@runCatching null

        val typeField = metadataKey.javaClass.getDeclaredField("mType")
        typeField.isAccessible = true
        (typeField.get(metadataKey) as? Class<*>)?.name?.takeIf { it.isNotEmpty() }
            ?: runCatching {
                val method = metadataKey.javaClass.getMethod("getType")
                (method.invoke(metadataKey) as? Class<*>)?.name
            }.getOrNull()
    }.getOrNull()
}
