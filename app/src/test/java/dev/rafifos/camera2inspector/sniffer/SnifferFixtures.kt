package dev.rafifos.camera2inspector.sniffer

import dev.rafifos.camera2inspector.camera.DeviceReport

object SnifferFixtures {

    fun keyMeta(
        name: String,
        vendor: Boolean = true,
        tagId: Long? = null,
        declaredType: String? = null,
    ): KeyMeta = KeyMeta(
        name = name,
        vendor = vendor,
        namespace = name.substringBeforeLast('.', ""),
        declaredType = declaredType,
        valueType = null,
        tagId = tagId,
        dumpType = null,
    )

    fun frame(
        frameNumber: Long,
        values: Map<String, Any?>,
        isCapture: Boolean = false,
        timestampNs: Long = frameNumber * 1_000_000,
    ): FrameRecord = FrameRecord(
        frameNumber = frameNumber,
        sensorTimestampNs = timestampNs,
        cameraId = "0",
        sequenceId = 1,
        isCaptureFrame = isCapture,
        requestValues = emptyMap(),
        partialResults = emptyList(),
        totalResultValues = values,
        physicalTotalResults = emptyMap(),
        jpegTimestampNs = if (isCapture) timestampNs else null,
        errors = emptyList(),
    )

    fun session(
        label: String,
        frames: List<FrameRecord>,
        keyMeta: List<KeyMeta> = emptyList(),
    ): SnifferSession = SnifferSession(
        id = label,
        label = label,
        cameraId = "0",
        logicalMultiCamera = true,
        physicalCameraIds = listOf("2", "3", "5"),
        startedAtMillis = 0L,
        startedAtIso = "2026-01-01T00:00:00Z",
        device = DeviceReport(
            manufacturer = "TestManufacturer",
            brand = "TestBrand",
            model = "TestModel",
            device = "testdevice",
            product = "testproduct",
            fingerprint = "test/fingerprint",
            androidVersion = "16",
            sdk = 36,
            hardware = "testhw",
            board = "testboard",
            supportedAbis = listOf("arm64-v8a"),
        ),
        availableSessionKeys = emptyList(),
        sessionParameters = emptyMap(),
        frames = frames,
        events = emptyList(),
        keyMeta = keyMeta.associateBy { it.name },
        errors = emptyList(),
        notes = emptyList(),
    )
}
