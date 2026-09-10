# Camera2 Inspector

Camera2 Inspector is a read-only diagnostic tool for Android. It reports what the Camera2
framework and the camera HAL expose on a given device: camera IDs, CameraCharacteristics,
available CaptureRequest, CaptureResult, physical camera request and session keys, and vendor keys
when the device publishes them. The output is a JSON report you can export and keep for reference.

Nothing in the app is tied to a vendor, a model or a chipset. It does not open the camera, submit
CaptureRequests, create sessions or change persistent settings.

## What it reads

### Characteristics

`CameraManager.cameraIdList` gives the camera IDs. For each ID the app reads
`CameraCharacteristics.getKeys()` and then `get(key)` for every key. A key that throws
(`SecurityException`, `IllegalArgumentException`, `AssertionError`, and others) gets an error entry
in the report, and the scan continues with the next key.

Values are kept as structured JSON when a safe representation exists. Otherwise the original
`toString()` is stored and the entry is marked with `"serialization": "toString"`.

### CaptureRequest

`CameraCharacteristics.getAvailableCaptureRequestKeys()`.

These are the keys the platform reports as available for a CaptureRequest. No value is read or
inferred for this category. A key appearing here does not tell the app which values are valid, and
it does not prove the key is writable. The app never sends a CaptureRequest.

### CaptureResult

`CameraCharacteristics.getAvailableCaptureResultKeys()`.

Results are outputs of the camera pipeline. They are not configuration, and the app does not treat
them as such.

### Physical CaptureRequest

`CameraCharacteristics.getAvailablePhysicalCameraRequestKeys()` (API 28 and later).

These keys apply to the physical cameras behind a logical camera. When the device reports physical
camera IDs, the UI and the report show which IDs are associated with the logical camera.

### Session

`CameraCharacteristics.getAvailableSessionKeys()` (API 28 and later).

Session keys are reported for session configuration. The app creates no session and changes no
value.

## Vendor keys

A key counts as vendor when its name does not start with `android.`. The check uses the name only.
There is no hardcoded list of manufacturers and no assumption that a prefix belongs to a specific
company.

The namespace shown in the UI (for example `com.example`) is a display grouping derived from the
key name. It carries no meaning beyond that. Keys that the device does not report are not invented
and do not appear in the report.

## Types and serialization

`CameraValueSerializer` handles strings, booleans, numbers, all primitive arrays, generic arrays,
`List`, `Map`, `Size`, `SizeF`, `Rect`, `Point`, `Rational`, `Range` and `Pair`. Byte arrays are
stored as base64 with their length. Known Android types become readable JSON:

```json
{"width": 4000, "height": 3000}
{"left": 0, "top": 0, "right": 4000, "bottom": 3000}
{"numerator": 1, "denominator": 2, "decimal": 0.5}
```

Anything else keeps its `toString()` output, with the serialization mode recorded next to it.

The Android SDK does not expose the declared type of a metadata key; there is no `getValueType()`
on `CameraCharacteristics.Key`, `CaptureRequest.Key` or `CaptureResult.Key`. The app reports the
type of the value when it has one, and otherwise tries to read the declared type through reflection
on the hidden `mType` field. On current Android versions the hidden API restrictions usually block
that read for CaptureRequest, CaptureResult and session keys. In that case the entry is exported as
`"type": "unavailable"` with `"typeSource": "unavailable"`. The app does not guess a type.

## Export

The Export button opens the system file picker through
`ActivityResultContracts.CreateDocument("application/json")`. The report is written as
`camera2_report_<timestamp>.json` in UTF-8. No storage permission is required.

The JSON is stable in structure:

```json
{
  "app": { "name": "Camera2 Inspector", "version": "1.0.0", "package": "..." },
  "generatedAt": "2026-01-01T00:00:00Z",
  "device": {
    "manufacturer": "...", "brand": "...", "model": "...", "device": "...",
    "product": "...", "fingerprint": "...", "hardware": "...", "board": "...",
    "androidVersion": "...", "sdk": 36, "supportedAbis": ["arm64-v8a"]
  },
  "summary": {
    "cameraCount": 2,
    "characteristics": 203, "vendorCharacteristics": 0,
    "captureRequests": 248, "vendorCaptureRequests": 130,
    "captureResults": 138, "vendorCaptureResults": 0,
    "physicalCaptureRequests": 0, "vendorPhysicalCaptureRequests": 0,
    "sessionKeys": 129, "vendorSessionKeys": 124,
    "vendorTotal": 254
  },
  "cameras": [
    {
      "id": "0",
      "logicalMultiCamera": true,
      "physicalCameraIds": ["2", "3", "5"],
      "hardwareLevel": "LEVEL_3",
      "lensFacing": "BACK",
      "sensorOrientation": 90,
      "notes": ["..."],
      "characteristics": [
        {
          "name": "android.sensor.info.pixelArraySize",
          "type": "android.util.Size",
          "typeSource": "value",
          "vendor": false,
          "namespace": "android",
          "category": "Characteristics",
          "origin": "CameraCharacteristics.get(key)",
          "serialization": "structured",
          "value": { "width": 4000, "height": 3000 },
          "toString": "..."
        }
      ],
      "captureRequestKeys": [],
      "captureResultKeys": [],
      "physicalCaptureRequestKeys": [],
      "sessionKeys": []
    }
  ]
}
```

Keys that fail to read carry an `error` object with `type` and `message`. A failed key never stops
the rest of the scan.

Report content (key names, categories, notes, error types) stays in English regardless of the UI
locale, so reports from different devices remain comparable.

## User interface

The UI uses Material 3 Expressive and follows the M3 Expressive design guidance: the expressive
motion scheme, dynamic color on Android 12 and later, a vibrant fallback scheme, emphasized
typography and the expressive component set. It ships in English by default with a Brazilian
Portuguese translation.

The home screen uses a large flexible app bar with a subtitle and a medium extended FAB. While a
scan runs, the card shows the shape-morphing contained loading indicator. Camera detail and key
detail use medium flexible app bars with subtitles; the search field uses the expressive rounded,
tonal text field style, and keys are rendered with expressive list items that morph their shape
when pressed. Buttons in the app use the expressive pressed-shape behavior, and screen changes
animate with the spatial and effects spring specs from the motion scheme.

The home screen shows the device, the summary, the camera cards and the export button. Vendor
CaptureRequest and Physical Request counts are highlighted when they are above zero. Tapping a
camera opens its detail screen, which has a text search, category chips and namespace chips.
Tapping a key opens its detail view with the full name, type, category, origin, value, original
`toString()` and copy buttons.

The scan runs on a background dispatcher. The UI shows which camera is being read and stays
responsive.

## Requirements

- Android Studio with AGP 9.4 or later. The project uses AGP's built-in Kotlin support, so the
  `org.jetbrains.kotlin.android` plugin is intentionally absent.
- JDK 17 (the JBR bundled with Android Studio works).
- Android SDK with `compileSdk 37`. `minSdk` is 28, `targetSdk` is 37.

## Build, test and install

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # needs a connected device or emulator
./gradlew :app:lintDebug
./gradlew :app:installDebug                # or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permission

The manifest declares `android.permission.CAMERA`, requested at runtime. On several Android
versions `getCameraCharacteristics` works without it, but the app asks anyway to stay compatible
with implementations that require it. If the user denies the permission, the app shows an
explanation and offers a retry and a shortcut to the app settings. It does not crash.

## Project layout

```
app/src/main/java/dev/rafifos/camera2inspector/
├── MainActivity.kt
├── camera/
│   ├── CameraInspector.kt          read-only Camera2 access
│   ├── CameraModels.kt             data model and summary
│   ├── CameraValueSerializer.kt    defensive value serialization
│   ├── KeyFilter.kt                search, filters, namespaces
│   └── KeyTypeResolver.kt          diagnostic-only reflection
├── export/
│   ├── ReportJson.kt               pure JSON builder
│   └── ReportExporter.kt           file writing through SAF
└── ui/
    ├── CameraInspectorApp.kt       permission gate and navigation
    ├── InspectorViewModel.kt
    ├── KeyTexts.kt                 localized note helpers
    ├── theme/Theme.kt
    ├── components/
    └── screens/
```

String resources live in `res/values/strings.xml` (English) and `res/values-pt-rBR/strings.xml`
(Brazilian Portuguese).

## Tests

Unit tests cover vendor classification, namespace extraction, serialization (including fallbacks),
key filtering, summary counts and the JSON structure. The instrumentation test runs a real scan on
a device and checks that every reported camera either produces characteristics or records an error,
and that a failed key carries an error instead of aborting the report.

## Known limitations

- Vendor keys are not interpreted. The app knows nothing about their meaning, valid values or
  write support. Namespaces are text only.
- Declared types for CaptureRequest, CaptureResult and session keys often show as `unavailable`
  because the public SDK does not expose them and hidden API restrictions block reflection.
- `availablePhysicalCameraRequestKeys()` and `availableSessionKeys()` can return empty lists even
  on complete HALs. The report records this in `notes`.
- The app does not test whether a key accepts a given value. That would require opening the camera
  and sending requests, which is outside the read-only scope.
- A report reflects what the device exposes at scan time. Firmware updates can change it.
- Fallback values such as `StreamConfigurationMap.toString()` are preserved as text, not as
  structure. The relevant standard fields also appear as separate characteristics.

## Privacy

The app sends nothing off the device. A report leaves the app only when the user taps Export and
picks a destination in the system file picker.

## Build notes

- AGP 9.4.0 with built-in Kotlin (KGP 2.2.10), and the Compose compiler pinned to the same 2.2.10.
- Material 3 Expressive APIs (`MaterialExpressiveTheme`, `LinearWavyProgressIndicator`) only exist
  in `material3 1.5.0-alpha*`. The project pins `1.5.0-alpha24`, which is compatible with Kotlin
  2.2.10 and Compose BOM 2026.09.00 (Compose UI 1.12.1). Keep that pairing when upgrading.
- `minSdk 28` covers physical multicamera and session key APIs without version guards.

## Possible future work

- CSV and Markdown export.
- Grouping keys by namespace.
- Comparing two camera IDs on the same device.
- Diffing keys present in CaptureRequest but missing from CaptureResult.
- Optional controlled CaptureRequest testing.
