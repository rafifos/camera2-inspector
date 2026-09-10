package dev.rafifos.camera2inspector.camera

fun keyInfo(
    name: String,
    category: KeyCategory,
    declaredType: String? = "java.lang.Integer",
): KeyInfo = KeyInfo(
    name = name,
    declaredType = declaredType,
    valueType = null,
    vendor = CameraValueSerializer.isVendorKey(name),
    namespace = CameraValueSerializer.extractNamespace(name),
    category = category,
    origin = "test",
)

fun keyEntry(
    name: String,
    category: KeyCategory = KeyCategory.CHARACTERISTICS,
    value: Any? = null,
    valueText: String? = null,
    serialization: SerializationMode = SerializationMode.NOT_READ,
    error: SerializedError? = null,
): KeyEntry = KeyEntry(
    info = keyInfo(name, category),
    value = value,
    valueText = valueText,
    rawToString = null,
    serialization = serialization,
    error = error,
)

fun cameraReport(
    id: String,
    characteristics: List<KeyEntry> = emptyList(),
    captureRequests: List<KeyInfo> = emptyList(),
    captureResults: List<KeyInfo> = emptyList(),
    physicalCaptureRequests: List<KeyInfo> = emptyList(),
    sessionKeys: List<KeyInfo> = emptyList(),
    logicalMultiCamera: Boolean = false,
    physicalCameraIds: List<String> = emptyList(),
    errors: List<SerializedError> = emptyList(),
    notes: List<CameraNote> = emptyList(),
): CameraReport = CameraReport(
    id = id,
    logicalMultiCamera = logicalMultiCamera,
    physicalCameraIds = physicalCameraIds,
    hardwareLevel = "LEVEL_3",
    lensFacing = "BACK",
    sensorOrientation = 90,
    notes = notes,
    errors = errors,
    characteristics = characteristics,
    captureRequests = captureRequests,
    captureResults = captureResults,
    physicalCaptureRequests = physicalCaptureRequests,
    sessionKeys = sessionKeys,
)

fun inspectionReport(cameras: List<CameraReport>): InspectionReport = InspectionReport(
    appName = "Camera2 Inspector",
    appVersion = "1.0.0-test",
    packageName = "dev.rafifos.camera2inspector",
    generatedAt = "2026-01-01T00:00:00Z",
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
    cameras = cameras,
)
