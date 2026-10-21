package com.framerevive.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/** Lenovo button LEDs are separate from the display backlight. */
class FrameButtonLights(context: Context) : SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val manager = runCatching { context.getSystemService("led") }.getOrNull()
    private var enabled = false
    private var lit = false
    private val off = Runnable { setLight(false) }
    private fun setLight(on: Boolean) {
        if (manager == null) return
        try {
            if (on) manager.javaClass.getMethod("LedOn", Int::class.javaPrimitiveType).invoke(manager, 100)
            else manager.javaClass.getMethod("LedOff").invoke(manager)
            lit = on
        } catch (_: Exception) {
            android.util.Log.w("FrameReviveLights", "Button light control unavailable on this firmware")
        }
    }
    fun start() {
        if (enabled) return
        enabled = true
        setLight(false)
        proximity?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun touch() {
        if (!enabled) return
        if (!lit) setLight(true)
        handler.removeCallbacks(off)
        handler.postDelayed(off, 8000)
    }
    fun stop() {
        enabled = false
        sensors.unregisterListener(this)
        handler.removeCallbacks(off)
        setLight(false)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.isNotEmpty() && event.values[0] < (proximity?.maximumRange ?: 0f)) touch()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
