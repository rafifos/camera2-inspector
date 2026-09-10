package dev.rafifos.camera2inspector.sniffer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDiffTest {

    private val hdrMode = "org.codeaurora.qcamera3.sessionParameters.HDRMode"
    private val dcg = "org.codeaurora.qcamera3.sessionParameters.EnableHDRDCGMode"
    private val aeMode = "android.control.aeMode"

    private fun meta() = listOf(
        SnifferFixtures.keyMeta(hdrMode, tagId = 0x802e0002L),
        SnifferFixtures.keyMeta(dcg, tagId = 0x802e0018L),
        SnifferFixtures.keyMeta(aeMode, vendor = false),
    )

    @Test
    fun `changed key produces change with old and new value`() {
        val a = SnifferFixtures.frame(1, mapOf(hdrMode to 0, aeMode to 1))
        val b = SnifferFixtures.frame(2, mapOf(hdrMode to 24, aeMode to 1))
        val changes = SessionDiff.diffFrames(a, b) { name -> name == hdrMode }
        assertEquals(1, changes.size)
        val change = changes.first()
        assertEquals(hdrMode, change.key)
        assertEquals(ChangeKind.CHANGED, change.kind)
        assertEquals("0", SessionDiff.canonical(change.oldValue))
        assertEquals("24", SessionDiff.canonical(change.newValue))
        assertEquals(2L, change.frameNumber)
    }

    @Test
    fun `appeared and disappeared keys are reported`() {
        val a = SnifferFixtures.frame(1, mapOf(hdrMode to 0))
        val b = SnifferFixtures.frame(2, mapOf(dcg to 1))
        val changes = SessionDiff.diffFrames(a, b) { true }.associateBy { it.key }
        assertEquals(ChangeKind.DISAPPEARED, changes[hdrMode]?.kind)
        assertEquals(ChangeKind.APPEARED, changes[dcg]?.kind)
    }

    @Test
    fun `diff sessions filters standard keys when vendor only`() {
        val a = SnifferFixtures.session(
            "PHOTO_1X",
            listOf(SnifferFixtures.frame(1, mapOf(hdrMode to 0, aeMode to 1))),
            meta(),
        )
        val b = SnifferFixtures.session(
            "HDR",
            listOf(SnifferFixtures.frame(1, mapOf(hdrMode to 24, aeMode to 2))),
            meta(),
        )
        val vendorOnly = SessionDiff.diffSessions(a, b, onlyVendor = true)
        assertEquals(listOf(hdrMode), vendorOnly.map { it.key })
        val all = SessionDiff.diffSessions(a, b, onlyVendor = false)
        assertEquals(setOf(hdrMode, aeMode), all.map { it.key }.toSet())
    }

    @Test
    fun `session diff records first observation frame and timestamp`() {
        val a = SnifferFixtures.session("A", listOf(SnifferFixtures.frame(1, mapOf(hdrMode to 0))), meta())
        val b = SnifferFixtures.session(
            "B",
            listOf(
                SnifferFixtures.frame(10, mapOf(hdrMode to 0)),
                SnifferFixtures.frame(11, mapOf(hdrMode to 24)),
            ),
            meta(),
        )
        val change = SessionDiff.diffSessions(a, b).first { it.key == hdrMode }
        assertEquals(11L, change.frameNumber)
        assertEquals(11_000_000L, change.firstObservedTimestampNs)
    }

    @Test
    fun `key timeline collapses consecutive equal values`() {
        val session = SnifferFixtures.session(
            "A",
            listOf(
                SnifferFixtures.frame(1, mapOf(hdrMode to 0)),
                SnifferFixtures.frame(2, mapOf(hdrMode to 0)),
                SnifferFixtures.frame(3, mapOf(hdrMode to 24)),
                SnifferFixtures.frame(4, mapOf(hdrMode to 24)),
                SnifferFixtures.frame(5, mapOf(hdrMode to 0)),
            ),
            meta(),
        )
        val timeline = SessionDiff.keyTimeline(session, hdrMode)
        assertEquals(3, timeline.size)
        assertEquals(1L, timeline[0].startFrame)
        assertEquals(2L, timeline[0].endFrame)
        assertEquals(3L, timeline[1].startFrame)
        assertEquals(4L, timeline[1].endFrame)
        assertEquals(5L, timeline[2].startFrame)
        assertEquals(5L, timeline[2].endFrame)
    }

    @Test
    fun `capture window compares baseline with first capture frame`() {
        val session = SnifferFixtures.session(
            "A",
            listOf(
                SnifferFixtures.frame(1, mapOf(hdrMode to 0, dcg to 0)),
                SnifferFixtures.frame(2, mapOf(hdrMode to 0, dcg to 0)),
                SnifferFixtures.frame(3, mapOf(hdrMode to 7, dcg to 1), isCapture = true),
            ),
            meta(),
        )
        val changes = SessionDiff.captureWindowChanges(session).associateBy { it.key }
        assertEquals(ChangeKind.CHANGED, changes[hdrMode]?.kind)
        assertEquals(ChangeKind.CHANGED, changes[dcg]?.kind)
        assertEquals(3L, changes[hdrMode]?.frameNumber)
    }

    @Test
    fun `canonical form distinguishes arrays and scalars`() {
        assertEquals("[1,2]", SessionDiff.canonical(JSONArray(listOf(1, 2))))
        assertEquals("1", SessionDiff.canonical(1))
        assertEquals("\"a\"", SessionDiff.canonical("a"))
        assertEquals("null", SessionDiff.canonical(null))
        assertTrue(SessionDiff.canonical(JSONObject("""{"a":1}""")) == SessionDiff.canonical(JSONObject("""{"a":1}""")))
    }
}
