package com.rahul.lilly

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * "Music control" permission (Android me Notification access). Gaane ka control isi se.
 * "Read my notifications" ON ho tabhi naye messages RAM me rakhta hai (save/bheja kuch nahi).
 */
class MediaListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        if (sbn != null) Inbox.add(this, sbn)
    }
}

/** Naye messages (sirf RAM me) + unka "Reply" button — WhatsApp ko bina khole jawab */
object Inbox {
    data class Msg(
        val app: String, val pkg: String, val title: String, val text: String, val at: Long,
        val reply: android.app.Notification.Action?
    )

    private val list = ArrayList<Msg>()

    fun add(ctx: Context, sbn: android.service.notification.StatusBarNotification) {
        if (!ctx.getSharedPreferences("lilly", Context.MODE_PRIVATE).getBoolean("read_notifs", false)) return
        val pkg = sbn.packageName ?: return
        if (pkg == ctx.packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) return
        val n = sbn.notification ?: return
        if (n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category in setOf(android.app.Notification.CATEGORY_TRANSPORT, android.app.Notification.CATEGORY_PROGRESS,
                android.app.Notification.CATEGORY_SERVICE, android.app.Notification.CATEGORY_SYSTEM)) return
        val ex = n.extras ?: return
        var title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        var text = (ex.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(android.app.Notification.EXTRA_TEXT))
            ?.toString()?.trim() ?: ""
        // Chat wale notification me aakhri message
        (ex.getParcelableArray(android.app.Notification.EXTRA_MESSAGES))?.lastOrNull()?.let { b ->
            (b as? Bundle)?.getCharSequence("text")?.toString()?.let { if (it.isNotBlank()) text = it }
        }
        title = title.replace(Regex("""\s*\(\d+ (?:messages|new messages|संदेश)\)$"""), "").trim()
        if (title.isBlank() && text.isBlank()) return
        if (Regex("""^\d+ new messages?$|^\d+ messages? from \d+ chats?$""", RegexOption.IGNORE_CASE).matches(text)) return
        val reply = (n.actions ?: emptyArray()).firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            ?: android.app.Notification.WearableExtender(n).actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
        val app = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        synchronized(list) {
            list.removeAll { it.pkg == pkg && it.title == title && it.text == text }
            list.add(Msg(app, pkg, title, text.take(300), System.currentTimeMillis(), reply))
            while (list.size > 40) list.removeAt(0)
        }
    }

    fun recent(app: String, sinceMs: Long): List<Msg> = synchronized(list) {
        val now = System.currentTimeMillis()
        list.filter { now - it.at <= sinceMs && (app.isBlank() || it.pkg.contains(app) || it.app.lowercase().contains(app)) }.reversed()
    }

    fun clear(app: String) = synchronized(list) {
        list.removeAll { app.isBlank() || it.pkg.contains(app) || it.app.lowercase().contains(app) }
    }

    /** Jiska message aaya (naam se, ya sabse naya) aur jiska Reply ho sakta hai */
    fun findReply(who: String): Msg? = synchronized(list) {
        val withReply = list.filter { it.reply != null }.reversed()
        if (who.isBlank()) return@synchronized withReply.firstOrNull()
        val w = Parser.normName(who)
        withReply.firstOrNull { Parser.normName(it.title).contains(w) } ?: withReply.firstOrNull { w.split(" ").all { p -> Parser.normName(it.title).contains(p) } }
    }

    /** Notification ke "Reply" se seedha bhejo (WhatsApp khule bina) */
    fun send(ctx: Context, m: Msg, text: String): Boolean {
        val a = m.reply ?: return false
        return try {
            val intent = android.content.Intent()
            val b = Bundle()
            for (ri in a.remoteInputs) b.putCharSequence(ri.resultKey, text)
            android.app.RemoteInput.addResultsToIntent(a.remoteInputs, intent, b)
            a.actionIntent.send(ctx, 0, intent)
            true
        } catch (e: Exception) { false }
    }
}

/**
 * Kisi bhi music app (Spotify, YouTube Music, JioSaavn, Wynk…) ka chalta gaana control karo —
 * bina Lilly helper ke: naam batao, aage/peeche, shuru se, shuffle, repeat, like, gaana dhoondh ke chalao.
 */
object MediaCtl {

    fun allowed(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, MediaListener::class.java).flattenToString()
        return flat.split(':').any { it.equals(me, true) }
    }

    private fun sessions(ctx: Context): List<MediaController> = try {
        val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        m.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
    } catch (e: Exception) { emptyList() }

    /** Jo baj raha hai wo; warna (pkg diya ho toh) us app ka; warna sabse upar wala */
    fun current(ctx: Context, pkg: String? = null): MediaController? {
        val all = sessions(ctx)
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: pkg?.let { p -> all.firstOrNull { it.packageName == p } }
            ?: all.firstOrNull()
    }

    fun nowPlaying(ctx: Context): String? {
        val c = current(ctx) ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        return title + (artist?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
    }

    /** App ka apna button (shuffle / repeat / like) — naam se dhoondh ke dabao */
    private fun custom(c: MediaController, vararg words: String): Boolean {
        val acts = c.playbackState?.customActions ?: return false
        val a = acts.firstOrNull { ca ->
            val n = (ca.action + " " + ca.name).lowercase()
            words.any { n.contains(it) }
        } ?: return false
        c.transportControls.sendCustomAction(a, Bundle())
        return true
    }

    /** true = ho gaya; false = is app me ye nahi hota; null = koi gaana/app chal hi nahi raha */
    fun run(ctx: Context, action: String, arg: String = ""): Boolean? {
        val c = current(ctx) ?: return null
        val t = c.transportControls
        val pos = c.playbackState?.position ?: 0L
        return when (action) {
            "restart" -> { t.seekTo(0); true }
            "forward" -> { t.seekTo(pos + (arg.toLongOrNull() ?: 10) * 1000); true }
            "rewind" -> { t.seekTo((pos - (arg.toLongOrNull() ?: 10) * 1000).coerceAtLeast(0)); true }
            "shuffle_on", "shuffle_off" -> custom(c, "shuffle")
            "repeat_on", "repeat_off" -> custom(c, "repeat")
            "like" -> custom(c, "like", "heart", "add_to_collection", "add to liked", "save", "thumbs_up", "thumbs up", "favorite", "favourite")
            "pause" -> { t.pause(); true }
            "resume" -> { t.play(); true }
            "next" -> { t.skipToNext(); true }
            "previous" -> { t.skipToPrevious(); true }
            else -> false
        }
    }

    /** Is app ko bolo "ye gaana dhoondh ke chalao" (app ka session zinda hona chahiye) */
    fun playFromSearch(ctx: Context, pkg: String, query: String): Boolean {
        val c = sessions(ctx).firstOrNull { it.packageName == pkg } ?: return false
        return try { c.transportControls.playFromSearch(query, Bundle()); true } catch (e: Exception) { false }
    }
}
