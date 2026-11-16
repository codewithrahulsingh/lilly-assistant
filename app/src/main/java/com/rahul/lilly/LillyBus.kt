package com.rahul.lilly

/** Background service aur screen (orb) ke beech ka chhota sa connection. */
object LillyBus {
    interface Ui {
        fun onStatus(text: String)
        fun onHeard(text: String)
        fun onOrb(state: OrbView.State)
        fun onLevel(level: Float)
        fun onRunning(running: Boolean)
        /** Ek baat-cheet poori hui (popup band karne ke liye) */
        fun onSessionEnd() {}
    }

    var ui: Ui? = null
    /** Jo screen abhi dikh rahi hai, wo apps khol sakti hai (Android background se rokta hai) */
    var launcher: ((android.content.Intent) -> Boolean)? = null
    var running = false
        set(value) { field = value; ui?.onRunning(value) }

    var lastStatus = ""
    var lastHeard = ""
    var lastOrb = OrbView.State.IDLE

    fun status(t: String) { lastStatus = t; ui?.onStatus(t) }
    fun heard(t: String) { lastHeard = t; ui?.onHeard(t) }
    fun orb(s: OrbView.State) { lastOrb = s; ui?.onOrb(s) }
    fun level(l: Float) { ui?.onLevel(l) }
    fun endSession() { ui?.onSessionEnd() }
}
