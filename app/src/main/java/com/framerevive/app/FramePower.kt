package com.framerevive.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.time.LocalTime
import java.time.ZonedDateTime

class FrameAdminReceiver : DeviceAdminReceiver()

object FramePower {
    const val WAKE = "com.framerevive.WAKE"
    const val SLEEP = "com.framerevive.SLEEP"
    fun record(context: Context, message: String) {
        context.getSharedPreferences("frame-settings", Context.MODE_PRIVATE).edit()
            .putString("lastPowerEvent", message).putLong("lastPowerEventAt", System.currentTimeMillis()).apply()
        android.util.Log.i("FrameRevivePower", message)
    }
    fun shouldSleep(context: Context): Boolean {
        val p = context.getSharedPreferences("frame-settings", Context.MODE_PRIVATE)
        return p.getBoolean("schedule", false) && FrameSchedule.isSleeping(LocalTime.now(),
            LocalTime.parse(p.getString("sleepStart", "22:00")), LocalTime.parse(p.getString("sleepEnd", "07:00")))
    }
    fun hasScreenControl(context: Context): Boolean = context.getSystemService(DevicePolicyManager::class.java)
        .isAdminActive(ComponentName(context, FrameAdminReceiver::class.java))
    fun sleep(context: Context) {
        if (hasScreenControl(context)) {
            record(context, "Display sleep requested")
            context.getSystemService(DevicePolicyManager::class.java).lockNow()
        }
    }
    @Suppress("DEPRECATION")
    fun wake(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "FrameRevive:scheduledWake").acquire(10_000)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    fun schedule(context: Context) {
        val p = context.getSharedPreferences("frame-settings", Context.MODE_PRIVATE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        for ((code, name, setting, default) in listOf(
            listOf("1", "SLEEP", "sleepStart", "22:00"), listOf("2", "WAKE", "sleepEnd", "07:00"))) {
            // Cancel alarms from 0.5, which depended on a manifest receiver the frame
            // skipped while asleep. The system now launches our activity directly.
            val legacy = PendingIntent.getBroadcast(context, code.toInt(), Intent(context, FrameScheduleReceiver::class.java)
                .setAction("com.framerevive.$name"), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (legacy != null) { alarm.cancel(legacy); legacy.cancel() }
            val intent = Intent(context, MainActivity::class.java).setAction("com.framerevive.$name")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, code.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
            if (p.getBoolean("schedule", false)) {
                val now = ZonedDateTime.now()
                val next = FrameSchedule.nextOccurrence(now, LocalTime.parse(p.getString(setting, default)))
                val whenMs = next.toInstant().toEpochMilli()
                if (android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                    if (name == "WAKE") alarm.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, pending), pending)
                    else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
                } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending)
                p.edit().putLong("next$name", whenMs).apply()
            } else {
                p.edit().remove("next$name").apply()
            }
        }
    }
}

class FrameScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FramePower.record(context, "Schedule receiver: ${intent.action}")
        FramePower.schedule(context)
        when (intent.action) {
            "com.framerevive.SLEEP" -> if (FramePower.shouldSleep(context)) FramePower.sleep(context)
            "com.framerevive.WAKE" -> if (!FramePower.shouldSleep(context)) FramePower.wake(context)
        }
    }
}
