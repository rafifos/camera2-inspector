package dev.rafifos.camera2inspector.sniffer

import org.json.JSONArray
import org.json.JSONObject

enum class ChangeKind { CHANGED, APPEARED, DISAPPEARED }

data class KeyChange(
    val key: String,
    val vendor: Boolean,
    val kind: ChangeKind,
    val oldValue: Any?,
    val newValue: Any?,
    val frameNumber: Long?,
    val timestampNs: Long?,
    val firstObservedTimestampNs: Long?,
    val groups: List<String>,
) {
    val isInteresting: Boolean get() = groups.isNotEmpty()
}

data class TimelineSegment(
    val value: Any?,
    val startFrame: Long,
    val endFrame: Long,
    val startTimestampNs: Long?,
    val endTimestampNs: Long?,
) {
    val frameCount: Long get() = endFrame - startFrame + 1
}

/**
 * Pure comparison helpers. Values are compared canonically; equal values never produce a change.
 */
object SessionDiff {

    fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is String -> "\"$value\""
        else -> value.toString()
    }

    fun diffValues(
        oldValues: Map<String, Any?>,
        newValues: Map<String, Any?>,
        vendorOf: (String) -> Boolean,
        frameNumber: Long? = null,
        timestampNs: Long? = null,
    ): List<KeyChange> {
        val changes = mutableListOf<KeyChange>()
        val keys = LinkedHashSet<String>().apply {
            addAll(oldValues.keys)
            addAll(newValues.keys)
        }
        keys.forEach { key ->
            val hadOld = oldValues.containsKey(key)
            val hasNew = newValues.containsKey(key)
            val oldValue = oldValues[key]
            val newValue = newValues[key]
            when {
                hadOld && !hasNew -> changes += change(key, vendorOf, ChangeKind.DISAPPEARED, oldValue, null, frameNumber, timestampNs)
                !hadOld && hasNew -> changes += change(key, vendorOf, ChangeKind.APPEARED, null, newValue, frameNumber, timestampNs)
                canonical(oldValue) != canonical(newValue) ->
                    changes += change(key, vendorOf, ChangeKind.CHANGED, oldValue, newValue, frameNumber, timestampNs)
            }
        }
        return changes
    }

    private fun change(
        key: String,
        vendorOf: (String) -> Boolean,
        kind: ChangeKind,
        oldValue: Any?,
        newValue: Any?,
        frameNumber: Long?,
        timestampNs: Long?,
    ) = KeyChange(
        key = key,
        vendor = vendorOf(key),
        kind = kind,
        oldValue = oldValue,
        newValue = newValue,
        frameNumber = frameNumber,
        timestampNs = timestampNs,
        firstObservedTimestampNs = timestampNs,
        groups = InterestingKeys.groupsFor(key),
    )

    fun diffFrames(a: FrameRecord, b: FrameRecord, vendorOf: (String) -> Boolean): List<KeyChange> {
        val oldValues = a.totalResultValues ?: a.requestValues
        val newValues = b.totalResultValues ?: b.requestValues
        return diffValues(oldValues, newValues, vendorOf, b.frameNumber, b.sensorTimestampNs)
    }

    fun representativeValues(session: SnifferSession): Map<String, Any?> =
        session.frames.lastOrNull { it.totalResultValues != null }?.totalResultValues
            ?: session.frames.lastOrNull()?.requestValues
            ?: emptyMap()

    fun diffSessions(
        old: SnifferSession,
        new: SnifferSession,
        onlyVendor: Boolean = true,
    ): List<KeyChange> {
        val vendorOf = { name: String -> old.isVendor(name) || new.isVendor(name) }
        val changes = diffValues(
            representativeValues(old),
            representativeValues(new),
            vendorOf,
        ).filter { !onlyVendor || it.vendor }
        if (changes.isEmpty()) return emptyList()

        val index = firstObservationIndex(new)
        return changes.map { change ->
            val observation = change.newValue?.let { index[change.key]?.get(canonical(it)) }
            change.copy(
                frameNumber = observation?.first ?: change.frameNumber,
                timestampNs = observation?.second ?: change.timestampNs,
                firstObservedTimestampNs = observation?.second ?: change.firstObservedTimestampNs,
            )
        }
    }

    /**
     * Single pass index of key -> (canonical value -> first frame and timestamp). Avoids scanning
     * every frame for every changed key.
     */
    private fun firstObservationIndex(session: SnifferSession): Map<String, Map<String, Pair<Long, Long?>>> {
        val index = HashMap<String, HashMap<String, Pair<Long, Long?>>>()
        session.frames.forEach { frame ->
            val values = frame.totalResultValues ?: frame.requestValues
            values.forEach { (key, value) ->
                val byValue = index.getOrPut(key) { HashMap() }
                val token = canonical(value)
                if (!byValue.containsKey(token)) {
                    byValue[token] = frame.frameNumber to frame.sensorTimestampNs
                }
            }
        }
        return index
    }

    /** Changes between the last frame before the first capture and that capture frame. */
    fun captureWindowChanges(session: SnifferSession, onlyVendor: Boolean = true): List<KeyChange> {
        val capture = session.captureFrames.firstOrNull() ?: return emptyList()
        val baseline = session.frames
            .lastOrNull { it.frameNumber < capture.frameNumber && it.totalResultValues != null }
            ?: session.frames.firstOrNull { it.totalResultValues != null }
            ?: return emptyList()
        return diffFrames(baseline, capture) { session.isVendor(it) }
            .filter { !onlyVendor || it.vendor }
    }

    /** Collapses consecutive equal values of one key into segments, in frame order. */
    fun keyTimeline(session: SnifferSession, key: String): List<TimelineSegment> {
        val segments = mutableListOf<TimelineSegment>()
        var currentValue: Any? = null
        var currentCanonical: String? = null
        var startFrame: Long? = null
        var startTimestamp: Long? = null
        var endFrame: Long? = null
        var endTimestamp: Long? = null

        session.frames.forEach { frame ->
            val hasValue = frame.totalResultValues?.containsKey(key) == true ||
                frame.requestValues.containsKey(key)
            if (!hasValue) return@forEach
            val value = frame.totalResultValues?.get(key) ?: frame.requestValues[key]
            val canonical = canonical(value)
            when {
                startFrame == null -> {
                    currentValue = value
                    currentCanonical = canonical
                    startFrame = frame.frameNumber
                    startTimestamp = frame.sensorTimestampNs
                    endFrame = frame.frameNumber
                    endTimestamp = frame.sensorTimestampNs
                }

                canonical != currentCanonical -> {
                    segments += TimelineSegment(currentValue, startFrame, endFrame!!, startTimestamp, endTimestamp)
                    currentValue = value
                    currentCanonical = canonical
                    startFrame = frame.frameNumber
                    startTimestamp = frame.sensorTimestampNs
                    endFrame = frame.frameNumber
                    endTimestamp = frame.sensorTimestampNs
                }

                else -> {
                    endFrame = frame.frameNumber
                    endTimestamp = frame.sensorTimestampNs
                }
            }
        }
        if (startFrame != null) {
            segments += TimelineSegment(currentValue, startFrame, endFrame!!, startTimestamp, endTimestamp)
        }
        return segments
    }

    /** Keys that first appear or change inside capture frames and are stable outside them. */
    fun captureOnlyChanges(session: SnifferSession, onlyVendor: Boolean = true): List<KeyChange> {
        val captures = session.captureFrames
        if (captures.isEmpty()) return emptyList()
        val captureFrames = captures.toSet()

        val previewValues = session.frames
            .filter { it !in captureFrames }
            .mapNotNull { it.totalResultValues }
        if (previewValues.isEmpty()) return emptyList()

        val commonPreview = previewValues.first().keys.filter { key ->
            previewValues.all { canonical(it[key]) == canonical(previewValues.first()[key]) }
        }.toSet()

        val changes = mutableListOf<KeyChange>()
        captures.forEach { capture ->
            val values = capture.totalResultValues ?: return@forEach
            values.forEach { (key, value) ->
                if (key in commonPreview) return@forEach
                val vendor = session.isVendor(key)
                if (onlyVendor && !vendor) return@forEach
                val previewValue = previewValues.lastOrNull()?.get(key)
                changes += change(
                    key = key,
                    vendorOf = { vendor },
                    kind = if (previewValues.lastOrNull()?.containsKey(key) == true) ChangeKind.CHANGED else ChangeKind.APPEARED,
                    oldValue = previewValue,
                    newValue = value,
                    frameNumber = capture.frameNumber,
                    timestampNs = capture.sensorTimestampNs,
                )
            }
        }
        return changes.distinctBy { it.key to canonical(it.newValue) }
    }
}
