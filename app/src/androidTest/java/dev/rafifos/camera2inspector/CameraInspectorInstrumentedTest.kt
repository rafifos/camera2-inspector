package dev.rafifos.camera2inspector

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rafifos.camera2inspector.camera.CameraInspector
import dev.rafifos.camera2inspector.camera.SerializationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraInspectorInstrumentedTest {

    @Test
    fun inspectionCompletesAndReportsEveryCameraWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val report = CameraInspector(context).inspect()

        assertEquals("Camera2 Inspector", report.appName)
        assertTrue(report.device.model.isNotEmpty())
        assertTrue(report.device.sdk > 0)

        report.cameras.forEach { camera ->
            assertTrue(
                "Camera ${camera.id} deve ter characteristics ou erro registrado",
                camera.characteristics.isNotEmpty() || camera.errors.isNotEmpty(),
            )
            camera.characteristics.forEach { entry ->
                assertTrue(
                    "Key ${entry.info.name} deve ter valor serializado ou erro",
                    entry.serialization != SerializationMode.ERROR || entry.error != null,
                )
            }
        }
    }
}
