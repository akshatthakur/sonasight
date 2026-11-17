package com.sonasight.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
        readLaunch(intent)

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
                granted -> CameraScreen(
                    rig, prefs, shutterPresses, requestedMode,
                    unlocked = ::whenUnlocked,
                ) { uri -> whenUnlocked { editing.value = uri } }
                else -> PermissionScreen {
                    launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readLaunch(intent)) editing.value = null
    }

    /**
     * Works out how we were opened: a launcher shortcut, the system camera button or the
     * lock screen. Opened while locked, the camera shows over the lock screen.
     */
    private fun readLaunch(intent: Intent?): Boolean {
        if (intent == null) return false
        var mode: Mode? = intent.getStringExtra("mode")?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        when (intent.action) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE -> mode = Mode.PHOTO
            MediaStore.INTENT_ACTION_VIDEO_CAMERA -> mode = Mode.VIDEO
        }
        val overLock = intent.action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ||
            intent.getBooleanExtra(EXTRA_LOCKED, false)
        if (overLock) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        if (mode == null) return false
        prefs.mode = mode
        requestedMode.value = mode
        return true
    }

    private val keyguard get() = getSystemService(KEYGUARD_SERVICE) as KeyguardManager

    /**
     * Runs [action] right away when the phone is unlocked. Over the lock screen it asks for the
     * unlock first, so nobody can browse the gallery from a locked phone.
     */
    fun whenUnlocked(action: () -> Unit) {
        if (!keyguard.isKeyguardLocked) {
            action()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                setShowWhenLocked(false)
                action()
            }
        })
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

    companion object {
        const val EXTRA_LOCKED = "locked"
    }
}
