package dev.rafifos.camera2inspector.sniffer

import android.hardware.camera2.CameraCharacteristics
import dev.rafifos.camera2inspector.camera.CameraValueSerializer
import dev.rafifos.camera2inspector.camera.KeyTypeResolver

data class VendorTagInfo(
    val name: String,
    val section: String?,
    val tagId: Long?,
    val declaredType: String?,
    val dumpType: String?,
    val sources: Set<String>,
) {
    val signedId: Int?
        get() = tagId?.let { VendorTagId.signed(it) }

    val unsignedId: Long?
        get() = tagId

    val hexId: String?
        get() = tagId?.let { VendorTagId.hex(it) }

    val resolvedType: String?
        get() = declaredType ?: dumpType
}

/**
 * Name to id/type map for vendor metadata keys. Never assigns meaning to a tag; it only records
 * what the device and an optional `dumpsys media.camera` capture report.
 */
class VendorTagDirectory private constructor(
    private val tags: Map<String, VendorTagInfo>,
) {
    val size: Int get() = tags.size

    fun info(name: String): VendorTagInfo? = tags[name]

    fun all(): Collection<VendorTagInfo> = tags.values

    fun merge(other: VendorTagDirectory): VendorTagDirectory {
        if (other.tags.isEmpty()) return this
        if (tags.isEmpty()) return other

        val merged = LinkedHashMap(tags)
        other.tags.forEach { (name, incoming) ->
            val current = merged[name]
            merged[name] = if (current == null) incoming else current.merge(incoming)
        }
        return VendorTagDirectory(merged)
    }

    private fun VendorTagInfo.merge(other: VendorTagInfo): VendorTagInfo = copy(
        section = section ?: other.section,
        tagId = tagId ?: other.tagId,
        declaredType = declaredType ?: other.declaredType,
        dumpType = dumpType ?: other.dumpType,
        sources = sources + other.sources,
    )

    companion object {
        val EMPTY: VendorTagDirectory = VendorTagDirectory(emptyMap())

        fun of(tags: List<VendorTagInfo>): VendorTagDirectory =
            VendorTagDirectory(tags.associateBy { it.name })

        fun fromCharacteristics(characteristics: CameraCharacteristics): VendorTagDirectory {
            val keys = runCatching { characteristics.keys }.getOrNull().orEmpty()
            val map = LinkedHashMap<String, VendorTagInfo>()
            keys.forEach { key ->
                val name = runCatching { key.name }.getOrNull() ?: return@forEach
                if (!CameraValueSerializer.isVendorKey(name)) return@forEach
                val info = VendorTagInfo(
                    name = name,
                    section = CameraValueSerializer.extractNamespace(name),
                    tagId = null,
                    declaredType = KeyTypeResolver.resolveDeclaredType(key),
                    dumpType = null,
                    sources = setOf(SOURCE_CHARACTERISTICS),
                )
                map[name] = info
            }
            return VendorTagDirectory(map)
        }

        fun fromDumpsys(text: String): VendorTagDirectory {
            val map = LinkedHashMap<String, VendorTagInfo>()
            DumpsysVendorTagParser.parse(text).forEach { parsed ->
                val info = VendorTagInfo(
                    name = parsed.name,
                    section = parsed.section,
                    tagId = parsed.tagId,
                    declaredType = null,
                    dumpType = parsed.typeName,
                    sources = setOf(SOURCE_DUMPSYS),
                )
                val current = map[parsed.name]
                map[parsed.name] = current?.merge(info) ?: info
            }
            return VendorTagDirectory(map)
        }

        private fun VendorTagInfo.merge(other: VendorTagInfo): VendorTagInfo = copy(
            section = section ?: other.section,
            tagId = tagId ?: other.tagId,
            declaredType = declaredType ?: other.declaredType,
            dumpType = dumpType ?: other.dumpType,
            sources = sources + other.sources,
        )

        const val SOURCE_CHARACTERISTICS = "characteristics"
        const val SOURCE_DUMPSYS = "dumpsys"
    }
}
