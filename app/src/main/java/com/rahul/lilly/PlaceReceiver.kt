package com.rahul.lilly

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build

/** Ghar / office pahunchne par: notification + Lilly bol ke yaad dilaati hai (ek baar) */
class PlaceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val text = intent.getStringExtra("text") ?: return
        val id = intent.getIntExtra("id", 0)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("lilly_place", "Place reminders", NotificationManager.IMPORTANCE_HIGH))
        val n = android.app.Notification.Builder(ctx).apply {
            if (Build.VERSION.SDK_INT >= 26) setChannelId("lilly_place")
            setSmallIcon(android.R.drawable.ic_dialog_map)
            setContentTitle("📍 Reminder")
            setContentText(text)
            setAutoCancel(true)
        }.build()
        try { nm.notify(5000 + id, n) } catch (e: Exception) { }
        try { LillyService.start(ctx, LillyService.ACTION_SAY, text) } catch (e: Exception) { }
        // Ek baar hi: alert hatao
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeProximityAlert(PlaceReminders.pending(ctx, id, text))
        } catch (e: Exception) { }
        PlaceReminders.remove(ctx, id)
    }
}

object PlaceReminders {
    fun pending(ctx: Context, id: Int, text: String): android.app.PendingIntent =
        android.app.PendingIntent.getBroadcast(
            ctx, 7000 + id,
            Intent(ctx, PlaceReceiver::class.java).putExtra("text", text).putExtra("id", id),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
        )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<String> =
        (prefs(ctx).getString("place_rem", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun add(ctx: Context, line: String): Int {
        val l = list(ctx) + line
        prefs(ctx).edit().putString("place_rem", l.joinToString("\n")).apply()
        val id = (prefs(ctx).getInt("place_rem_id", 0) + 1) % 1000
        prefs(ctx).edit().putInt("place_rem_id", id).apply()
        return id
    }

    fun remove(ctx: Context, id: Int) {
        // Simple: sabse purana hatao (list sirf dikhane ke liye)
        val l = list(ctx).drop(1)
        prefs(ctx).edit().putString("place_rem", l.joinToString("\n")).apply()
    }
}
