package com.rahul.lilly

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Phone restart hone par Android mic wali service ko khud se shuru nahi karne deta,
 * isliye ek notification dikhate hain — tap karte hi Lilly phir se sunne lagti hai.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences("lilly", Context.MODE_PRIVATE).getBoolean("enabled", true)
        if (enabled) LillyService.notifyTapToStart(context)
    }
}
