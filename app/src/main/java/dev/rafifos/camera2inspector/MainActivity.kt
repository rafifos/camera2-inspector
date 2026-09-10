package dev.rafifos.camera2inspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rafifos.camera2inspector.ui.CameraInspectorApp
import dev.rafifos.camera2inspector.ui.theme.Camera2InspectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Camera2InspectorTheme {
                CameraInspectorApp()
            }
        }
    }
}
