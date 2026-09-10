package dev.rafifos.camera2inspector.sniffer

/**
 * Conversions for AOSP vendor tag ids. The camera service prints ids as signed 32-bit ints and
 * as 32-bit hex; both refer to the same unsigned value. Nothing here interprets a tag.
 */
object VendorTagId {

    private const val UNSIGNED_MASK = 0xFFFFFFFFL

    fun unsigned(signed: Int): Long = signed.toLong() and UNSIGNED_MASK

    fun signed(unsigned: Long): Int = (unsigned and UNSIGNED_MASK).toInt()

    fun hex(unsigned: Long): String = "0x%08x".format(unsigned and UNSIGNED_MASK)

    fun hex(signed: Int): String = hex(unsigned(signed))

    /**
     * Parses `0x802e0018`, `-2144468968` or `2150498328`. Returns the unsigned 32-bit value or
     * null when the token is not an id.
     */
    fun parse(token: String): Long? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        val value = when {
            trimmed.startsWith("0x", ignoreCase = true) ->
                trimmed.substring(2).toLongOrNull(16)

            else -> trimmed.toLongOrNull()
        } ?: return null
        return value and UNSIGNED_MASK
    }
}
