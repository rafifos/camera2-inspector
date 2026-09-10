package dev.rafifos.camera2inspector.sniffer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import dev.rafifos.camera2inspector.camera.SerializedError
import dev.rafifos.camera2inspector.camera.currentDeviceReport
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Owns one Camera2 session and records what the API exposes for it: the requests this app sends,
 * partial results, total results, physical camera results, callbacks and frame timing.
 *
 * It is passive with respect to vendor state: it never sets a vendor key, never changes exposure,
 * HDR, OIS or AF, and it cannot observe another process's session, which the platform does not
 * allow.
 */
class CameraSessionMonitor(
    context: Context,
    private val listener: Listener,
    private val config: Config = Config(),
) {

    interface Listener {
        fun onState(state: MonitorState)
        fun onEvent(event: CaptureEvent)
        fun onFrame(frame: FrameRecord)
        fun onError(error: SerializedError)
    }

    data class Config(
        val maxFrames: Int = 600,
        val includeHeavyKeys: Boolean = false,
        val maxEvents: Int = 400,
    )

    data class MonitorState(
        val cameraId: String? = null,
        val cameraOpen: Boolean = false,
        val sessionConfigured: Boolean = false,
        val recording: Boolean = false,
        val framesSeen: Long = 0,
        val framesRecorded: Int = 0,
        val capturesTriggered: Int = 0,
        val vendorRequestKeysLastFrame: Int = 0,
        val vendorResultKeysLastFrame: Int = 0,
        val lastFrameNumber: Long? = null,
        val status: String? = null,
    )

    private val manager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val thread = HandlerThread("Camera2Sniffer").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor: Executor = Executor { command -> handler.post(command) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var characteristics: CameraCharacteristics? = null
    private var directory: VendorTagDirectory = VendorTagDirectory.EMPTY
    private var extractor = MetadataExtractor(directory) { shouldSkip(it) }

    private var cameraId: String? = null
    private var repeatingSequenceId: Int? = null
    private var captureSequenceId: Int? = null

    private var recording = false
    private var label: String = ""
    private var sessionId: String = ""
    private var sessionStartedMillis: Long = 0L
    private var sessionStartedIso: String = ""
    private val frames = LinkedHashMap<Long, FrameRecord>()
    private val events = mutableListOf<CaptureEvent>()
    private val errors = mutableListOf<SerializedError>()
    private val notes = mutableListOf<String>()
    private val startedTimes = HashMap<Long, Long>()

    private var state = MonitorState()
    private var lastCounterEmitMillis = 0L

    fun open(cameraId: String, directory: VendorTagDirectory = VendorTagDirectory.EMPTY) {
        handler.post {
            if (device != null) {
                emitEvent(CaptureEventKind.LOG, null, null, "Camera $cameraId already open")
                return@post
            }
            val manager = manager ?: run {
                fail("CameraManager is not available")
                return@post
            }
            try {
                this.directory = directory
                characteristics = manager.getCameraCharacteristics(cameraId)
                this.cameraId = cameraId
                updateState { it.copy(cameraId = cameraId, status = "Opening camera $cameraId") }
                Log.d(TAG, "Opening camera $cameraId")
                openCamera(manager, cameraId)
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(manager: CameraManager, cameraId: String) {
        manager.openCamera(cameraId, deviceCallback, handler)
    }

    fun startRecording(label: String) {
        handler.post {
            frames.clear()
            events.clear()
            errors.clear()
            notes.clear()
            startedTimes.clear()
            captureSequenceId = null
            extractor = MetadataExtractor(directory) { shouldSkip(it) }
            recording = true
            this.label = label.ifBlank { "SESSION" }
            sessionId = UUID.randomUUID().toString()
            sessionStartedMillis = System.currentTimeMillis()
            sessionStartedIso = Instant.ofEpochMilli(sessionStartedMillis).toString()
            updateState {
                it.copy(
                    recording = true,
                    framesRecorded = 0,
                    framesSeen = 0,
                    capturesTriggered = 0,
                    status = "Recording ${this.label}",
                    lastFrameNumber = null,
                    vendorRequestKeysLastFrame = 0,
                    vendorResultKeysLastFrame = 0,
                )
            }
            emitEvent(CaptureEventKind.RECORDING_STARTED, null, null, "Recording started: ${this.label}")
            Log.d(TAG, "Recording started: ${this.label}")
        }
    }

    /**
     * Builds a session from everything recorded so far. Runs on the camera thread and reports back
     * through [onResult] on that same thread. Returns an empty result when nothing was recorded.
     */
    fun saveSession(onResult: (SnifferSession?) -> Unit) {
        handler.post {
            val hasContent = frames.isNotEmpty()
            if (!recording && !hasContent) {
                onResult(null)
                return@post
            }
            recording = false
            emitEvent(CaptureEventKind.RECORDING_STOPPED, null, null, "Recording stopped")
            updateState { it.copy(recording = false, status = "Recording stopped") }

            val characteristics = characteristics
            val result = SnifferSession(
                id = sessionId,
                label = label.ifBlank { "SESSION" },
                cameraId = cameraId.orEmpty(),
                logicalMultiCamera = characteristics.isLogicalMultiCamera(),
                physicalCameraIds = characteristics.physicalIds(),
                startedAtMillis = sessionStartedMillis,
                startedAtIso = sessionStartedIso,
                device = currentDeviceReport(),
                availableSessionKeys = characteristics.availableSessionKeyNames(),
                sessionParameters = emptyMap(),
                frames = frames.values.toList(),
                events = events.toList(),
                keyMeta = extractor.keyMeta,
                errors = errors.toList(),
                notes = notes.toList() + NOTE_NO_VENDOR_WRITES,
            )
            Log.d(
                TAG,
                "Session saved: ${result.label}, frames=${result.frames.size}, captures=${result.captureFrames.size}",
            )
            onResult(result)
        }
    }

    fun triggerCapture(jpegOrientation: Int? = null) {
        handler.post {
            val device = device ?: run {
                fail("Camera is not open")
                return@post
            }
            val session = session ?: run {
                fail("Session is not configured")
                return@post
            }
            val reader = jpegReader ?: run {
                fail("JPEG output is not configured")
                return@post
            }
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    if (jpegOrientation != null) {
                        set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)
                    }
                }.build()
                val sequence = session.capture(request, captureCallback, handler)
                captureSequenceId = sequence
                updateState { it.copy(capturesTriggered = it.capturesTriggered + 1) }
                emitEvent(
                    CaptureEventKind.CAPTURE_TRIGGERED,
                    null,
                    null,
                    "Still capture triggered (sequence $sequence)",
                )
                Log.d(TAG, "Capture triggered, sequence=$sequence")
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun close(onClosed: (() -> Unit)? = null) {
        handler.post {
            runCatching { session?.close() }
            runCatching { previewReader?.close() }
            runCatching { jpegReader?.close() }
            runCatching { device?.close() }
            session = null
            previewReader = null
            jpegReader = null
            device = null
            characteristics = null
            cameraId = null
            recording = false
            updateState {
                it.copy(
                    cameraOpen = false,
                    sessionConfigured = false,
                    recording = false,
                    status = "Closed",
                )
            }
            onClosed?.invoke()
        }
    }

    fun shutdown() {
        close()
        thread.quitSafely()
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            device = camera
            updateState { it.copy(cameraOpen = true, status = "Camera open, configuring session") }
            emitEvent(CaptureEventKind.CAMERA_OPENED, null, null, "Camera ${camera.id} opened")
            Log.d(TAG, "Camera ${camera.id} opened")
            configureSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            emitEvent(CaptureEventKind.LOG, null, null, "Camera disconnected")
            runCatching { camera.close() }
            device = null
            updateState { it.copy(cameraOpen = false, status = "Disconnected") }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            fail("Camera error $error")
            runCatching { camera.close() }
            device = null
        }
    }

    private fun configureSession(camera: CameraDevice) {
        val characteristics = characteristics ?: return
        val map = runCatching {
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        }.getOrNull()
        if (map == null) {
            fail("Stream configuration map is not available")
            return
        }

        val previewSizes = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()
        val previewSize = previewSizes
            .filter { it.width >= 640 && it.height >= 480 }
            .minByOrNull { it.width.toLong() * it.height }
            ?: previewSizes.minByOrNull { it.width.toLong() * it.height }
        val jpegSize = map.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width.toLong() * it.height }

        if (previewSize == null || jpegSize == null) {
            fail("No suitable preview or JPEG size reported")
            return
        }

        Log.d(
            TAG,
            "Outputs: preview=${previewSize.width}x${previewSize.height} yuv, " +
                "jpeg=${jpegSize.width}x${jpegSize.height} jpeg",
        )

        previewReader = ImageReader.newInstance(
            previewSize.width,
            previewSize.height,
            ImageFormat.YUV_420_888,
            3,
        ).apply {
            setOnImageAvailableListener({ reader -> reader.acquireLatestImage()?.close() }, handler)
        }
        jpegReader = ImageReader.newInstance(
            jpegSize.width,
            jpegSize.height,
            ImageFormat.JPEG,
            2,
        ).apply {
            setOnImageAvailableListener({ reader -> onJpegAvailable(reader) }, handler)
        }

        val outputs = listOf(
            OutputConfiguration(previewReader!!.surface),
            OutputConfiguration(jpegReader!!.surface),
        )
        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            sessionCallback,
        )

        try {
            camera.createCaptureSession(sessionConfig)
        } catch (t: Throwable) {
            fail(t)
        }
    }

    private val sessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(configuredSession: CameraCaptureSession) {
            session = configuredSession
            updateState { it.copy(sessionConfigured = true, status = "Session configured") }
            val outputs = listOfNotNull(
                previewReader?.let { "${it.width}x${it.height} YUV" },
                jpegReader?.let { "${it.width}x${it.height} JPEG" },
            )
            emitEvent(
                CaptureEventKind.SESSION_CONFIGURED,
                null,
                null,
                "Session configured with ${outputs.size} outputs: ${outputs.joinToString(", ")}",
            )
            val availableSessionKeys = characteristics.availableSessionKeyNames()
            notes += "Available session keys: ${availableSessionKeys.size}. " +
                "This app sets no session parameter."
            Log.d(TAG, "Session configured, available session keys=${availableSessionKeys.size}")

            val device = device ?: return
            try {
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewReader!!.surface)
                }.build()
                repeatingSequenceId = configuredSession.setRepeatingRequest(request, captureCallback, handler)
                emitEvent(
                    CaptureEventKind.REPEATING_STARTED,
                    null,
                    null,
                    "Repeating preview started (sequence $repeatingSequenceId)",
                )
                updateState { it.copy(status = "Preview running") }
                Log.d(TAG, "Repeating preview started, sequence=$repeatingSequenceId")
            } catch (t: Throwable) {
                fail(t)
            }
        }

        override fun onConfigureFailed(configuredSession: CameraCaptureSession) {
            fail("Session configuration failed")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        override fun onCaptureStarted(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long,
        ) {
            startedTimes[frameNumber] = timestamp
            updateCounters { it.copy(lastFrameNumber = frameNumber) }
        }

        override fun onCaptureProgressed(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult,
        ) {
            if (!recording) return
            val frameNumber = partialResult.frameNumber
            try {
                val extraction = extractor.extractResult(partialResult)
                addErrors(extraction.errors)
                val current = frames[frameNumber]
                frames[frameNumber] = (current ?: emptyFrame(frameNumber, partialResult)).copy(
                    partialResults = (current?.partialResults ?: emptyList()) + extraction.values,
                )
            } catch (t: Throwable) {
                fail(t)
            }
        }

        override fun onCaptureCompleted(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            updateCounters { it.copy(framesSeen = it.framesSeen + 1) }
            if (!recording) return

            val frameNumber = result.frameNumber
            if (frames.size >= config.maxFrames) {
                recording = false
                notes += "Recording stopped at the ${config.maxFrames} frame limit."
                updateState { it.copy(recording = false, status = "Frame limit reached") }
                emitEvent(CaptureEventKind.LOG, frameNumber, result.sensorTimestampNs, "Frame limit reached")
                return
            }

            try {
                val isCapture = captureSequenceId != null && result.sequenceId == captureSequenceId
                val requestExtraction = extractor.extractRequest(result.request)
                val totalExtraction = extractor.extractResult(result)
                val physicalExtractions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    result.physicalCameraTotalResults.mapValues { (_, value) ->
                        extractor.extractResult(value).values
                    }
                } else {
                    emptyMap()
                }
                addErrors(requestExtraction.errors)
                addErrors(totalExtraction.errors)

                val current = frames[frameNumber]
                val vendorRequestKeys = requestExtraction.values.keys.count { extractor.keyMeta[it]?.vendor == true }
                val vendorResultKeys = totalExtraction.values.keys.count { extractor.keyMeta[it]?.vendor == true }

                val record = FrameRecord(
                    frameNumber = frameNumber,
                    sensorTimestampNs = result.sensorTimestampNs,
                    cameraId = cameraId.orEmpty(),
                    sequenceId = result.sequenceId,
                    isCaptureFrame = isCapture,
                    requestValues = requestExtraction.values,
                    partialResults = current?.partialResults.orEmpty(),
                    totalResultValues = totalExtraction.values,
                    physicalTotalResults = physicalExtractions,
                    jpegTimestampNs = current?.jpegTimestampNs,
                    errors = current?.errors.orEmpty() + requestExtraction.errors.map { KeyError(it.name, it.type, it.message) },
                )
                frames[frameNumber] = record
                listener.onFrame(record)
                updateCounters {
                    it.copy(
                        framesRecorded = frames.size,
                        vendorRequestKeysLastFrame = vendorRequestKeys,
                        vendorResultKeysLastFrame = vendorResultKeys,
                        lastFrameNumber = frameNumber,
                    )
                }
                if (isCapture) {
                    val start = startedTimes[frameNumber]
                    emitEvent(
                        CaptureEventKind.CAPTURE_COMPLETED,
                        frameNumber,
                        result.sensorTimestampNs,
                        "Capture result completed (sequence ${result.sequenceId}" +
                            (start?.let { ", started at $it" } ?: "") + ")",
                    )
                    Log.d(TAG, "Capture completed frame=$frameNumber vendorResultKeys=$vendorResultKeys")
                }
            } catch (t: Throwable) {
                fail(t)
            }
        }

        override fun onCaptureFailed(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure,
        ) {
            emitEvent(
                CaptureEventKind.ERROR,
                if (failure.frameNumber >= 0) failure.frameNumber else null,
                null,
                "Capture failed: reason ${failure.reason}, sequence ${failure.sequenceId}",
            )
        }

        override fun onCaptureSequenceCompleted(
            captureSession: CameraCaptureSession,
            sequenceId: Int,
            frameNumber: Long,
        ) {
            Log.d(TAG, "Sequence $sequenceId completed at frame $frameNumber")
        }

        override fun onCaptureSequenceAborted(captureSession: CameraCaptureSession, sequenceId: Int) {
            emitEvent(CaptureEventKind.ERROR, null, null, "Sequence $sequenceId aborted")
        }

        override fun onCaptureBufferLost(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            target: Surface,
            frameNumber: Long,
        ) {
            emitEvent(CaptureEventKind.ERROR, frameNumber, null, "Capture buffer lost for a target surface")
        }
    }

    private fun onJpegAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (t: Throwable) {
            fail(t)
            null
        } ?: return

        try {
            val timestamp = image.timestamp
            emitEvent(CaptureEventKind.JPEG_AVAILABLE, null, timestamp, "JPEG buffer available (timestamp $timestamp)")
            val key = frames.entries.firstOrNull { it.value.sensorTimestampNs == timestamp }?.key
            if (key != null) {
                val updated = frames.getValue(key).copy(jpegTimestampNs = timestamp, isCaptureFrame = true)
                frames[key] = updated
                listener.onFrame(updated)
            }
            Log.d(TAG, "JPEG available timestamp=$timestamp matchedFrame=$key")
        } finally {
            image.close()
        }
    }

    private fun emptyFrame(frameNumber: Long, result: CaptureResult) = FrameRecord(
        frameNumber = frameNumber,
        sensorTimestampNs = result.sensorTimestampNs,
        cameraId = cameraId.orEmpty(),
        sequenceId = result.sequenceId,
        isCaptureFrame = false,
        requestValues = emptyMap(),
        partialResults = emptyList(),
        totalResultValues = null,
        physicalTotalResults = emptyMap(),
        jpegTimestampNs = null,
        errors = emptyList(),
    )

    private fun addErrors(raw: List<KeyError>) {
        if (errors.size >= ERROR_LIMIT) return
        raw.forEach { error ->
            if (errors.size < ERROR_LIMIT) {
                errors += SerializedError("${error.name}: ${error.type}", error.message)
            }
        }
    }

    private fun shouldSkip(name: String): Boolean {
        if (config.includeHeavyKeys) return false
        return HEAVY_KEYS.any { name == it || name.startsWith(it) }
    }

    private fun updateState(transform: (MonitorState) -> MonitorState) {
        state = transform(state)
        listener.onState(state)
    }

    /**
     * Counter updates happen once per frame. Emitting every one of them makes the UI recompose at
     * camera frame rate, so they are throttled while the internal state stays current.
     */
    private fun updateCounters(transform: (MonitorState) -> MonitorState) {
        state = transform(state)
        val now = System.currentTimeMillis()
        if (now - lastCounterEmitMillis >= COUNTER_EMIT_INTERVAL_MS) {
            lastCounterEmitMillis = now
            listener.onState(state)
        }
    }

    private fun emitEvent(
        kind: CaptureEventKind,
        frameNumber: Long?,
        timestampNs: Long?,
        description: String,
    ) {
        Log.d(TAG, "$kind frame=$frameNumber $description")
        if (events.size < config.maxEvents) {
            val event = CaptureEvent(kind, frameNumber, timestampNs, description, System.currentTimeMillis())
            events += event
            listener.onEvent(event)
        }
    }

    private fun fail(message: String) {
        val error = SerializedError("SnifferError", message)
        if (errors.size < ERROR_LIMIT) errors += error
        Log.e(TAG, message)
        emitEvent(CaptureEventKind.ERROR, null, null, message)
        listener.onError(error)
        updateState { it.copy(status = message) }
    }

    private fun fail(t: Throwable) {
        val type = if (t is CameraAccessException) {
            "CameraAccessException(${t.reason})"
        } else {
            t.javaClass.name
        }
        val error = SerializedError(type, t.message)
        if (errors.size < ERROR_LIMIT) errors += error
        Log.e(TAG, "Sniffer failure", t)
        emitEvent(CaptureEventKind.ERROR, null, null, "$type: ${t.message}")
        listener.onError(error)
        updateState { it.copy(status = t.message ?: type) }
    }

    private fun CameraCharacteristics?.isLogicalMultiCamera(): Boolean {
        val capabilities = runCatching {
            this?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        }.getOrNull()
        return capabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA,
        ) == true
    }

    private fun CameraCharacteristics?.physicalIds(): List<String> {
        val ids = runCatching { this?.physicalCameraIds }.getOrNull() ?: return emptyList()
        return ids.sortedWith(compareBy({ it.length }, { it }))
    }

    private fun CameraCharacteristics?.availableSessionKeyNames(): List<String> {
        val keys = runCatching { this?.availableSessionKeys }.getOrNull() ?: return emptyList()
        return keys.mapNotNull { runCatching { it.name }.getOrNull() }
    }

    private val CaptureResult.sensorTimestampNs: Long?
        get() = runCatching { get(CaptureResult.SENSOR_TIMESTAMP) }.getOrNull()

    private companion object {
        const val TAG = "Camera2Sniffer"
        const val ERROR_LIMIT = 500
        const val COUNTER_EMIT_INTERVAL_MS = 250L
        const val NOTE_NO_VENDOR_WRITES =
            "This app writes no vendor key and does not modify the camera device state."

        val HEAVY_KEYS = listOf(
            "android.statistics.lensShadingMap",
            "android.sensor.noiseProfile",
            "android.hotPixelMap",
        )
    }
}
