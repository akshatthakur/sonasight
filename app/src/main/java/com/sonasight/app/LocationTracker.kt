package com.sonasight.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/** Keeps a recent location while location tagging is on and the camera is open. */
class LocationTracker(private val context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile var last: Location? = null
        private set

    private val listener = LocationListener { loc ->
        val cur = last
        if (cur == null || loc.time > cur.time || loc.accuracy < cur.accuracy) last = loc
    }

    private var running = false

    fun allowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !allowed()) return
        running = true
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) continue
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { listener.onLocationChanged(it) }
            runCatching { lm.requestLocationUpdates(p, 20_000L, 10f, listener, Looper.getMainLooper()) }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { lm.removeUpdates(listener) }
    }
}
