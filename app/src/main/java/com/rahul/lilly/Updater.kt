package com.rahul.lilly

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * App ke andar se update: GitHub par nayi APK aayi ho toh download karke wahi install screen kholo.
 * Har APK ek hi key se sign hoti hai, isliye nayi wali purani ke upar install hoti hai (settings, seekh,
 * yaaddasht sab bachi rehti hai).
 */
object Updater {
    private const val API = "https://api.github.com/repos/codewithrahulsingh/lilly-assistant/releases/latest"
    const val APK_URL = "https://github.com/codewithrahulsingh/lilly-assistant/releases/latest/download/Lilly.apk"
    private const val MIME = "application/vnd.android.package-archive"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE)

    fun installed(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    /** "4.10" > "4.9" — ank ke hisaab se */
    fun newer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun status(ctx: Context): String {
        val cur = installed(ctx)
        val latest = prefs(ctx).getString("update_latest", null)
        return if (latest != null && newer(latest, cur)) "New: v$latest — tap to update" else "v$cur — tap to check"
    }

    /** userAsked = button dabaya (tab hamesha check + jawab dikhao); warna chupchaap, 6 ghante me ek baar */
    fun check(act: Activity, userAsked: Boolean, onDone: () -> Unit = {}) {
        val p = prefs(act)
        val now = System.currentTimeMillis()
        if (!userAsked && now - p.getLong("update_checked", 0) < CHECK_EVERY_MS) return
        p.edit().putLong("update_checked", now).apply()
        if (userAsked) Toast.makeText(act, "Checking for updates…", Toast.LENGTH_SHORT).show()
        Thread {
            var latest: String? = null
            var apk: String? = null
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Lilly-app")
                if (c.responseCode == 200) {
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    latest = Regex("""v(\d+(?:\.\d+)*)""").find(j.optString("tag_name"))?.groupValues?.get(1)
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
                    }
                }
                c.disconnect()
            } catch (e: Exception) { }
            act.runOnUiThread {
                if (act.isFinishing) return@runOnUiThread
                val cur = installed(act)
                val l = latest
                if (l != null) p.edit().putString("update_latest", l).apply()
                onDone()
                when {
                    l == null -> if (userAsked) Toast.makeText(act, "Couldn't check right now. Is the internet on?", Toast.LENGTH_LONG).show()
                    newer(l, cur) -> {
                        val shownKey = "update_shown_$l"
                        if (userAsked || !p.getBoolean(shownKey, false)) {
                            p.edit().putBoolean(shownKey, true).apply()
                            AlertDialog.Builder(act)
                                .setTitle("New Lilly v$l is ready")
                                .setMessage(
                                    "You have v$cur.\n\nThe update installs over the current app — your settings, " +
                                        "what Lilly learned and what she remembers all stay."
                                )
                                .setPositiveButton("Update") { _, _ -> download(act, apk ?: APK_URL, l) }
                                .setNegativeButton("Later", null)
                                .show()
                        }
                    }
                    userAsked -> Toast.makeText(act, "You have the latest version (v$cur) ✓", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun download(act: Activity, url: String, version: String) {
        // Android 8+: Lilly ko "apps install karne" ki ijazat chahiye
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, "Allow Lilly to install updates, then tap Update again", Toast.LENGTH_LONG).show()
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
                return
            } catch (e: Exception) { }
        }
        try {
            val dm = act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val name = "Lilly-v$version.apk"
            act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { java.io.File(it, name).delete() }
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Lilly v$version")
                .setDescription("Downloading update…")
                .setMimeType(MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(act, Environment.DIRECTORY_DOWNLOADS, name)
            val id = dm.enqueue(req)
            val app = act.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                    try { app.unregisterReceiver(this) } catch (e: Exception) { }
                    val uri = dm.getUriForDownloadedFile(id)
                    if (uri == null) { openInBrowser(app); return }
                    try {
                        app.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) { openInBrowser(app) }
                }
            }
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else app.registerReceiver(receiver, filter)
            Toast.makeText(act, "Downloading Lilly v$version… the install screen opens when it's done", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            openInBrowser(act)
        }
    }

    /** Kuch bhi na chale toh browser se download */
    private fun openInBrowser(ctx: Context) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
