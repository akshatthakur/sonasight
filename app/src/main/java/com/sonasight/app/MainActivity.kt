package com.sonasight.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sonasight.app.ui.CameraScreen
import com.sonasight.app.ui.EditorScreen
import com.sonasight.app.ui.PermissionScreen

class MainActivity : ComponentActivity() {

    private lateinit var rig: CameraRig
    private lateinit var prefs: Prefs

    /** Bumped by the volume keys; the camera screen fires the shutter when it changes. */
    private val shutterPresses = mutableIntStateOf(0)
    private val requestedMode = mutableStateOf<Mode?>(null)
    private val editing = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        rig = CameraRig(this, this, FilmProcessor())
        readShortcut(intent)

        setContent {
            var granted by remember { mutableStateOf(has(Manifest.permission.CAMERA)) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                granted = result[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
            }
            val edit = editing.value
            when {
                edit != null -> {
                    BackHandler { editing.value = null }
                    EditorScreen(edit, rig.processor, prefs) { editing.value = null }
                }
                granted -> CameraScreen(rig, prefs, shutterPresses, requestedMode) { editing.value = it }
                else -> PermissionScreen {
                    launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (readShortcut(intent)) editing.value = null
    }

    /** The launcher shortcuts open straight into stills or motion. */
    private fun readShortcut(intent: Intent?): Boolean {
        val m = intent?.getStringExtra("mode") ?: return false
        val mode = runCatching { Mode.valueOf(m) }.getOrNull() ?: return false
        prefs.mode = mode
        requestedMode.value = mode
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (editing.value == null && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (event.repeatCount == 0) shutterPresses.intValue += 1
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        rig.shutdown()
        super.onDestroy()
    }
}
