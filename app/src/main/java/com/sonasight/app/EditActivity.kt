package com.sonasight.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sonasight.app.ui.EditorScreen

/**
 * Opens when you pick SonaSight from "Edit in" in Gallery or Google Photos.
 * It goes straight to the editor without starting the camera, and saving replaces the
 * original, then hands the result back to the app that opened it.
 */
class EditActivity : ComponentActivity() {

    private var processor: FilmProcessor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = target(intent)
        if (uri == null) {
            finish()
            return
        }
        val p = FilmProcessor()
        processor = p
        val prefs = Prefs(this)
        val replace = intent.action == Intent.ACTION_EDIT

        setContent {
            EditorScreen(
                uri = uri,
                processor = p,
                prefs = prefs,
                replace = replace,
                onSaved = { saved ->
                    setResult(RESULT_OK, Intent().setData(saved).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    finish()
                },
                onClose = { finish() },
            )
        }
    }

    private fun target(intent: Intent?): Uri? {
        if (intent == null) return null
        intent.data?.let { return it }
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    override fun onDestroy() {
        processor?.release()
        super.onDestroy()
    }
}
