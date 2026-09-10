package dev.rafifos.camera2inspector.sniffer

/**
 * Parses the vendor tag directory out of a `dumpsys media.camera` capture.
 *
 * Two line formats carry ids:
 *  - descriptor dump: `0x80000000 (private_property) with type 0 (byte) defined in section <section>`
 *  - static metadata: `<section>.<name> (802e0002): int32[1]`
 *
 * The parser does not interpret anything; it only extracts names, ids and the type string that the
 * service printed.
 */
object DumpsysVendorTagParser {

    data class ParsedTag(
        val name: String,
        val section: String?,
        val tagId: Long?,
        val typeName: String?,
    )

    private val DESCRIPTOR = Regex(
        """^\s*(0x[0-9a-fA-F]+)\s+\(([^)]+)\)\s+with\s+type\s+(\d+)\s+\(([^)]+)\)\s+defined\s+in\s+section\s+(\S+)""",
    )

    private val STATIC_METADATA = Regex(
        """^\s*([A-Za-z_][A-Za-z0-9_.]*)\s+\(([0-9a-fA-F]{1,8})\):\s*([A-Za-z0-9_]+)""",
    )

    fun parse(text: String): List<ParsedTag> {
        val byName = LinkedHashMap<String, ParsedTag>()

        text.lineSequence().forEach { line ->
            DESCRIPTOR.find(line)?.let { match ->
                val id = VendorTagId.parse(match.groupValues[1]) ?: return@let
                val shortName = match.groupValues[2]
                val typeName = match.groupValues[4]
                val section = match.groupValues[5]
                val fullName = if (shortName.contains('.')) shortName else "$section.$shortName"
                putTag(byName, fullName, section, id, typeName)
                return@forEach
            }

            STATIC_METADATA.find(line)?.let { match ->
                val name = match.groupValues[1]
                if (name.startsWith("android.")) return@let
                val id = VendorTagId.parse("0x" + match.groupValues[2]) ?: return@let
                val typeName = match.groupValues[3]
                putTag(byName, name, sectionOf(name), id, typeName)
            }
        }

        return byName.values.toList()
    }

    private fun putTag(
        target: MutableMap<String, ParsedTag>,
        name: String,
        section: String?,
        id: Long,
        typeName: String?,
    ) {
        val existing = target[name]
        target[name] = if (existing == null) {
            ParsedTag(name, section, id, typeName)
        } else {
            existing.copy(
                section = existing.section ?: section,
                tagId = existing.tagId ?: id,
                typeName = existing.typeName ?: typeName,
            )
        }
    }

    private fun sectionOf(name: String): String? {
        val index = name.lastIndexOf('.')
        return if (index > 0) name.substring(0, index) else null
    }
}
