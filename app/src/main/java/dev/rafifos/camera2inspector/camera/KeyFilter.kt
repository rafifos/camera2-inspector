package dev.rafifos.camera2inspector.camera

data class KeyQuery(
    val text: String = "",
    val category: KeyCategory = KeyCategory.CHARACTERISTICS,
    val namespace: String? = null,
    val vendorOnly: Boolean = false,
)

fun filterKeyEntries(entries: List<KeyEntry>, query: KeyQuery): List<KeyEntry> {
    val text = query.text.trim()
    return entries.filter { entry ->
        val info = entry.info
        (text.isEmpty() || info.name.contains(text, ignoreCase = true)) &&
            (query.namespace == null || info.namespace == query.namespace) &&
            (!query.vendorOnly || info.vendor)
    }
}

fun entriesForCategory(report: CameraReport, category: KeyCategory): List<KeyEntry> = when (category) {
    KeyCategory.CHARACTERISTICS -> report.characteristics
    KeyCategory.CAPTURE_REQUEST -> report.captureRequests.map { it.toEntry() }
    KeyCategory.CAPTURE_RESULT -> report.captureResults.map { it.toEntry() }
    KeyCategory.PHYSICAL_CAPTURE_REQUEST -> report.physicalCaptureRequests.map { it.toEntry() }
    KeyCategory.SESSION -> report.sessionKeys.map { it.toEntry() }
}

fun categoryCount(report: CameraReport, category: KeyCategory): Int = when (category) {
    KeyCategory.CHARACTERISTICS -> report.characteristics.size
    KeyCategory.CAPTURE_REQUEST -> report.captureRequests.size
    KeyCategory.CAPTURE_RESULT -> report.captureResults.size
    KeyCategory.PHYSICAL_CAPTURE_REQUEST -> report.physicalCaptureRequests.size
    KeyCategory.SESSION -> report.sessionKeys.size
}

fun vendorCategoryCount(report: CameraReport, category: KeyCategory): Int = when (category) {
    KeyCategory.CHARACTERISTICS -> vendorKeyCount(report.characteristics)
    KeyCategory.CAPTURE_REQUEST -> vendorInfoCount(report.captureRequests)
    KeyCategory.CAPTURE_RESULT -> vendorInfoCount(report.captureResults)
    KeyCategory.PHYSICAL_CAPTURE_REQUEST -> vendorInfoCount(report.physicalCaptureRequests)
    KeyCategory.SESSION -> vendorInfoCount(report.sessionKeys)
}

fun distinctNamespaces(entries: List<KeyEntry>): List<Pair<String, Int>> =
    entries.asSequence()
        .filter { it.info.vendor }
        .groupingBy { it.info.namespace }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

