package dev.rafifos.camera2inspector.sniffer

import org.json.JSONArray
import org.json.JSONObject

data class SnifferReportOptions(
    val vendorOnly: Boolean = false,
    val interestingOnly: Boolean = false,
    val includeRequests: Boolean = true,
    val includePartialResults: Boolean = true,
    val includeTotalResults: Boolean = true,
    val maxFrames: Int? = null,
)

/**
 * Serializes recorded sessions. The JSON report is the primary artifact; CSV and Markdown are
 * summaries. Nothing here interprets a value.
 */
object SnifferReport {

    fun toJson(session: SnifferSession, options: SnifferReportOptions = SnifferReportOptions()): String {
        val frames = options.maxFrames?.let { session.frames.takeLast(it) } ?: session.frames
        val root = JSONObject()
        root.put(
            "app",
            JSONObject()
                .put("name", "Camera2 Inspector")
                .put("report", "session-sniffer")
                .put("version", 1)
                .put("generatedAt", java.time.Instant.now().toString()),
        )
        root.put("session", sessionJson(session))
        root.put("keyDirectory", keyDirectoryJson(session, options))
        root.put("frames", JSONArray(frames.map { frameJson(session, it, options) }))
        return root.toString(2)
    }

    fun toCsv(session: SnifferSession, options: SnifferReportOptions = SnifferReportOptions()): String {
        val builder = StringBuilder()
        builder.append("frameNumber,timestamp,capture,section,cameraId,key,vendor,type,hexId,signedId,unsignedId,value\n")
        val frames = options.maxFrames?.let { session.frames.takeLast(it) } ?: session.frames
        frames.forEach { frame ->
            frame.requestValues.forEach { (key, value) ->
                if (includeKey(session, key, options)) {
                    builder.append(csvRow(session, frame, "request", key, value))
                }
            }
            frame.totalResultValues?.forEach { (key, value) ->
                if (includeKey(session, key, options)) {
                    builder.append(csvRow(session, frame, "totalResult", key, value))
                }
            }
        }
        return builder.toString()
    }

    fun toMarkdown(
        sessions: List<SnifferSession>,
        comparisons: List<Pair<String, List<KeyChange>>>,
    ): String {
        val builder = StringBuilder()
        builder.append("# Camera2 session comparison\n\n")
        builder.append("Generated at ${java.time.Instant.now()}\n\n")

        builder.append("## Sessions\n\n")
        builder.append("| Label | Camera | Physical IDs | Frames | Captures | Started |\n")
        builder.append("|---|---|---|---|---|---|\n")
        sessions.forEach { session ->
            builder.append(
                "| ${escape(session.label)} | ${escape(session.cameraId)} | " +
                    "${escape(session.physicalCameraIds.joinToString(" "))} | ${session.frames.size} | " +
                    "${session.captureFrames.size} | ${escape(session.startedAtIso)} |\n",
            )
        }
        builder.append('\n')

        builder.append("## Values by session\n\n")
        val keys = interestingKeysFor(sessions)
        if (keys.isEmpty()) {
            builder.append("No vendor key from the interest list was observed in these sessions.\n\n")
        } else {
            builder.append("| Key | ").append(sessions.joinToString(" | ") { escape(it.label) })
                .append(" | Note |\n")
            builder.append("|---|").append(sessions.joinToString("|") { "---" }).append("|---|\n")
            keys.forEach { key ->
                builder.append("| `").append(escape(key)).append("` |")
                sessions.forEach { session ->
                    val value = SessionDiff.representativeValues(session)[key]
                    builder.append(' ').append(escape(compact(value))).append(" |")
                }
                builder.append(' ').append(InterestingKeys.groupsFor(key).joinToString(", ")).append(" |\n")
            }
            builder.append('\n')
        }

        comparisons.forEach { (title, changes) ->
            builder.append("## $title\n\n")
            if (changes.isEmpty()) {
                builder.append("No difference observed.\n\n")
                return@forEach
            }
            builder.append("| Key | Change | Previous | New | Frame | Timestamp | Groups |\n")
            builder.append("|---|---|---|---|---|---|---|\n")
            changes.forEach { change ->
                builder.append("| `").append(escape(change.key)).append("` |")
                builder.append(' ').append(change.kind.name.lowercase()).append(" |")
                builder.append(' ').append(escape(compact(change.oldValue))).append(" |")
                builder.append(' ').append(escape(compact(change.newValue))).append(" |")
                builder.append(' ').append(change.frameNumber?.toString() ?: "").append(" |")
                builder.append(' ').append(change.timestampNs?.toString() ?: "").append(" |")
                builder.append(' ').append(change.groups.joinToString(", ")).append(" |\n")
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun sessionJson(session: SnifferSession): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("label", session.label)
        put("cameraId", session.cameraId)
        put("logicalMultiCamera", session.logicalMultiCamera)
        put("physicalCameraIds", JSONArray(session.physicalCameraIds))
        put("startTimestamp", session.startedAtIso)
        put(
            "deviceInfo",
            JSONObject()
                .put("manufacturer", session.device.manufacturer)
                .put("brand", session.device.brand)
                .put("model", session.device.model)
                .put("device", session.device.device)
                .put("product", session.device.product)
                .put("hardware", session.device.hardware)
                .put("androidVersion", session.device.androidVersion)
                .put("sdk", session.device.sdk)
                .put("fingerprint", session.device.fingerprint),
        )
        put("availableSessionKeys", JSONArray(session.availableSessionKeys))
        put("sessionParameters", JSONObject(session.sessionParameters.mapValues { it.value ?: JSONObject.NULL }))
        put("captureFrames", JSONArray(session.captureFrames.map { it.frameNumber }))
        put("notes", JSONArray(session.notes))
        if (session.errors.isNotEmpty()) {
            put(
                "errors",
                JSONArray(session.errors.map { error ->
                    JSONObject().put("type", error.type).put("message", error.message ?: JSONObject.NULL)
                }),
            )
        }
    }

    private fun keyDirectoryJson(session: SnifferSession, options: SnifferReportOptions): JSONObject =
        JSONObject().apply {
            session.keyMeta.values
                .filter { includeKey(session, it.name, options) }
                .sortedBy { it.name }
                .forEach { meta ->
                    put(
                        meta.name,
                        JSONObject()
                            .put("vendor", meta.vendor)
                            .put("namespace", meta.namespace)
                            .put("type", meta.typeLabel)
                            .put("declaredType", meta.declaredType ?: JSONObject.NULL)
                            .put("observedValueType", meta.valueType ?: JSONObject.NULL)
                            .put("dumpType", meta.dumpType ?: JSONObject.NULL)
                            .put("hexId", meta.hexId ?: JSONObject.NULL)
                            .put("signedId", meta.signedId ?: JSONObject.NULL)
                            .put("unsignedId", meta.unsignedId ?: JSONObject.NULL)
                            .put("groups", JSONArray(InterestingKeys.groupsFor(meta.name))),
                    )
                }
        }

    private fun frameJson(
        session: SnifferSession,
        frame: FrameRecord,
        options: SnifferReportOptions,
    ): JSONObject = JSONObject().apply {
        put("frameNumber", frame.frameNumber)
        put("timestamp", frame.sensorTimestampNs ?: JSONObject.NULL)
        put("cameraId", frame.cameraId)
        put("sequenceId", frame.sequenceId ?: JSONObject.NULL)
        put("isCaptureFrame", frame.isCaptureFrame)
        put("jpegTimestamp", frame.jpegTimestampNs ?: JSONObject.NULL)
        if (options.includeRequests) {
            put("request", sectionJson(session, frame.requestValues, options, vendorAsArray = true))
        }
        if (options.includePartialResults && frame.partialResults.isNotEmpty()) {
            put(
                "partialResults",
                JSONArray(frame.partialResults.map { sectionJson(session, it, options, vendorAsArray = true) }),
            )
        }
        if (options.includeTotalResults) {
            frame.totalResultValues?.let {
                put("totalResult", sectionJson(session, it, options, vendorAsArray = true))
            }
        }
        if (frame.physicalTotalResults.isNotEmpty()) {
            val physical = JSONObject()
            frame.physicalTotalResults.forEach { (id, values) ->
                physical.put(id, sectionJson(session, values, options, vendorAsArray = true))
            }
            put("physicalTotalResults", physical)
        }
        if (frame.errors.isNotEmpty()) {
            put(
                "errors",
                JSONArray(frame.errors.map { error ->
                    JSONObject().put("key", error.name).put("type", error.type).put("message", error.message ?: JSONObject.NULL)
                }),
            )
        }
    }

    private fun sectionJson(
        session: SnifferSession,
        values: Map<String, Any?>,
        options: SnifferReportOptions,
        vendorAsArray: Boolean,
    ): JSONObject {
        val standard = JSONObject()
        val vendor = JSONArray()
        values.forEach { (key, value) ->
            if (!includeKey(session, key, options)) return@forEach
            val meta = session.keyMeta[key]
            val isVendor = meta?.vendor ?: dev.rafifos.camera2inspector.camera.CameraValueSerializer.isVendorKey(key)
            if (isVendor) {
                vendor.put(vendorEntry(meta ?: fallbackMeta(key), value))
            } else {
                standard.put(key, value ?: JSONObject.NULL)
            }
        }
        return JSONObject().apply {
            if (!options.vendorOnly) put("standard", standard)
            put("vendor", vendor)
        }
    }

    private fun vendorEntry(meta: KeyMeta, value: Any?): JSONObject = JSONObject().apply {
        put("name", meta.name)
        put("type", meta.typeLabel)
        put("hexId", meta.hexId ?: JSONObject.NULL)
        put("signedId", meta.signedId ?: JSONObject.NULL)
        put("unsignedId", meta.unsignedId ?: JSONObject.NULL)
        put("groups", JSONArray(InterestingKeys.groupsFor(meta.name)))
        put("value", value ?: JSONObject.NULL)
    }

    private fun fallbackMeta(name: String): KeyMeta = KeyMeta(
        name = name,
        vendor = dev.rafifos.camera2inspector.camera.CameraValueSerializer.isVendorKey(name),
        namespace = dev.rafifos.camera2inspector.camera.CameraValueSerializer.extractNamespace(name),
        declaredType = null,
        valueType = null,
        tagId = null,
        dumpType = null,
    )

    private fun includeKey(session: SnifferSession, key: String, options: SnifferReportOptions): Boolean {
        val meta = session.keyMeta[key]
        val vendor = meta?.vendor ?: dev.rafifos.camera2inspector.camera.CameraValueSerializer.isVendorKey(key)
        if (options.vendorOnly && !vendor) return false
        if (options.interestingOnly && !InterestingKeys.isInteresting(key)) return false
        return true
    }

    private fun interestingKeysFor(sessions: List<SnifferSession>): List<String> {
        val keys = LinkedHashSet<String>()
        sessions.forEach { session ->
            session.keyMeta.values
                .filter { it.vendor && InterestingKeys.isInteresting(it.name) }
                .forEach { keys += it.name }
        }
        return keys.sorted()
    }

    private fun csvRow(
        session: SnifferSession,
        frame: FrameRecord,
        section: String,
        key: String,
        value: Any?,
    ): String {
        val meta = session.keyMeta[key]
        return listOf(
            frame.frameNumber.toString(),
            frame.sensorTimestampNs?.toString() ?: "",
            frame.isCaptureFrame.toString(),
            section,
            frame.cameraId,
            key,
            (meta?.vendor ?: false).toString(),
            meta?.typeLabel ?: "",
            meta?.hexId ?: "",
            meta?.signedId?.toString() ?: "",
            meta?.unsignedId?.toString() ?: "",
            csvEscape(compact(value)),
        ).joinToString(",") + "\n"
    }

    private fun compact(value: Any?): String = when (value) {
        null -> "null"
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    private fun escape(text: String): String = text.replace("|", "\\|").replace("\n", " ")

    private fun csvEscape(text: String): String {
        if (text.none { it == ',' || it == '"' || it == '\n' }) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }
}
