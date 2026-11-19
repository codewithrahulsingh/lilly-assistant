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

/** Sirf "Music control" permission ke liye (Android chalte gaane ka control isi se deta hai). Kuch padhta/save nahi karta. */
class MediaListener : NotificationListenerService()

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
