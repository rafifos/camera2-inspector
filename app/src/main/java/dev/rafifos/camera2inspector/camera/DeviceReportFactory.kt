package dev.rafifos.camera2inspector.camera

import android.os.Build

fun currentDeviceReport(): DeviceReport = DeviceReport(
    manufacturer = Build.MANUFACTURER.orEmpty(),
    brand = Build.BRAND.orEmpty(),
    model = Build.MODEL.orEmpty(),
    device = Build.DEVICE.orEmpty(),
    product = Build.PRODUCT.orEmpty(),
    fingerprint = Build.FINGERPRINT.orEmpty(),
    androidVersion = Build.VERSION.RELEASE.orEmpty(),
    sdk = Build.VERSION.SDK_INT,
    hardware = Build.HARDWARE.orEmpty(),
    board = Build.BOARD.orEmpty(),
    supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
)
