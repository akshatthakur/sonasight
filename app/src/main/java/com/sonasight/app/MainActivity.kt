package com.sonasight.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sonasight.app.ui.CameraScreen
import com.sonasight.app.ui.PermissionScreen

class MainActivity : ComponentActivity() {

    private lateinit var rig: CameraRig
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        rig = CameraRig(this, this, FilmProcessor())

        setContent {
            var granted by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                granted = result[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
            }
            if (granted) {
                CameraScreen(rig, prefs)
            } else {
                PermissionScreen {
                    launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
            }
        }
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        rig.shutdown()
        super.onDestroy()
    }
}
