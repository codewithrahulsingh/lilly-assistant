package com.rahul.lilly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lilly ki samajh ka QA: har build par apne aap chalta hai. */
class ParserTest {

    private fun p(s: String) = Parser.parse(s)

    @Test fun romanize() {
        assertEquals("open youtube", Parser.roman("ओपन यूट्यूब"))
        assertEquals("rahul ko call karo", Parser.roman("राहुल को कॉल करो"))
        assertEquals("open YouTube", Parser.roman("open YouTube"))
        assertEquals("phone", Parser.roman("फ़ोन"))
    }

    @Test fun wakeWord() {
        assertTrue(Parser.hasWakeWord("hey lilly"))
        assertTrue(Parser.hasWakeWord("हे लिली"))
        assertEquals("open youtube", Parser.stripWakeWord("hey lilly open youtube"))
        assertEquals("", Parser.stripWakeWord("Hey Lily"))
    }

    @Test fun openAppsEnglish() {
        assertEquals(Cmd.OpenApp("youtube"), p("open YouTube"))
        assertEquals(Cmd.OpenApp("youtube"), p("Open the YouTube app"))
        assertEquals(Cmd.OpenApp("whatsapp"), p("please open WhatsApp"))
        assertEquals(Cmd.OpenApp("camera"), p("launch camera"))
        assertEquals(Cmd.OpenApp("google maps"), p("open Google Maps"))
    }

    @Test fun openAppsHinglish() {
        assertEquals(Cmd.OpenApp("youtube"), p("YouTube kholo"))
        assertEquals(Cmd.OpenApp("whatsapp"), p("WhatsApp open karo"))
        assertEquals(Cmd.OpenApp("instagram"), p("instagram khol do"))
        assertEquals(Cmd.OpenApp("phone pe"), p("phone pe kholo"))
        assertEquals(Cmd.OpenApp("youtube", orMusic = true), p("youtube chalao"))
    }

    @Test fun openAppsHindiScript() {
        assertEquals(Cmd.OpenApp("youtube"), p("ओपन यूट्यूब"))
        assertEquals(Cmd.OpenApp("youtube"), p("यूट्यूब खोलो"))
        assertEquals(Cmd.OpenApp("whatsapp"), p("व्हाट्सएप ओपन करो"))
        assertEquals(Cmd.OpenApp("instagram"), p("इंस्टाग्राम खोल दो"))
        assertEquals(Cmd.OpenApp("spotify"), p("ओपन स्पॉटिफाई"))
        assertEquals(Cmd.OpenApp("phonepe"), p("ओपन फोनपे"))
        assertEquals(listOf("gpay", "google pe", "google pay"), Parser.appNames("google pe"))
    }

    @Test fun calls() {
        assertEquals(Cmd.Call("rahul"), p("Rahul ko call karo"))
        assertEquals(Cmd.Call("mummy"), p("call Mummy"))
        assertEquals(Cmd.Call("papa"), p("papa ko phone lagao"))
        assertEquals(Cmd.Call("rahul"), p("राहुल को कॉल करो"))
        assertEquals(Cmd.Call("9876543210"), p("9876543210 ko call karo"))
        assertEquals(Cmd.Call("amit sharma"), p("call Amit Sharma"))
    }

    @Test fun torchTimeDate() {
        assertEquals(Cmd.Torch(true), p("torch jalao"))
        assertEquals(Cmd.Torch(false), p("torch band karo"))
        assertEquals(Cmd.Torch(true), p("टॉर्च जलाओ"))
        assertEquals(Cmd.Torch(false), p("flashlight off"))
        assertEquals(Cmd.Time, p("kitne baje hain"))
        assertEquals(Cmd.Time, p("what's the time"))
        assertEquals(Cmd.Date, p("aaj ki date kya hai"))
        assertEquals(Cmd.Battery, p("battery kitni hai"))
    }

    @Test fun music() {
        assertEquals(Cmd.PlayMusic("kesariya"), p("play Kesariya"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("play Kesariya on Spotify"))
        assertEquals(Cmd.PlayMusic("arijit singh"), p("Arijit Singh ke gaane chalao"))
        assertEquals(Cmd.PlayMusic("tum hi ho"), p("spotify par tum hi ho chalao"))
        assertEquals(Cmd.PlayMusic("tum hi ho"), p("tum hi ho gaana bajao"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("प्ले केसरिया"))
        assertEquals(Cmd.PlayMusic(""), p("play some music"))
        assertEquals(Cmd.PlayMusic("arijit singh", "youtube"), p("youtube pe arijit singh chalao"))
        assertEquals(Cmd.PlayMusic("kesariya", "youtube"), p("play kesariya on youtube"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("open spotify and play kesariya"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("स्पॉटिफाई पर केसरिया चलाओ"))
        assertEquals(Cmd.OpenApp("kesariya", orMusic = true), p("kesariya chalao"))
    }

    @Test fun mediaControls() {
        assertEquals(Cmd.Media("pause"), p("pause"))
        assertEquals(Cmd.Media("pause"), p("gaana band karo"))
        assertEquals(Cmd.Media("pause"), p("stop music"))
        assertEquals(Cmd.Media("resume"), p("gaana chalao"))
        assertEquals(Cmd.Media("resume"), p("play"))
        assertEquals(Cmd.Media("resume"), p("resume"))
        assertEquals(Cmd.Media("next"), p("next song"))
        assertEquals(Cmd.Media("next"), p("agla gaana"))
        assertEquals(Cmd.Media("next"), p("agla gaana chalao"))
        assertEquals(Cmd.Media("previous"), p("pichla gaana"))
        assertEquals(Cmd.Media("previous"), p("previous song"))
    }

    @Test fun volume() {
        assertEquals(Cmd.Volume("up"), p("volume badhao"))
        assertEquals(Cmd.Volume("down"), p("awaaz kam karo"))
        assertEquals(Cmd.Volume("up"), p("volume up"))
        assertEquals(Cmd.Volume("max"), p("full volume"))
        assertEquals(Cmd.Volume("mute"), p("mute karo"))
    }

    @Test fun chat() {
        assertEquals(Cmd.Chat("how"), p("kaisi ho"))
        assertEquals(Cmd.Chat("thanks"), p("thank you"))
        assertEquals(Cmd.Chat("who"), p("tum kaun ho"))
        assertEquals(Cmd.Chat("bye"), p("band karo"))
        assertTrue(p("india ka pradhan mantri kaun hai") is Cmd.Unknown)
        assertTrue(p("mujhe neend nahi aa rahi") is Cmd.Unknown)
    }

    @Test fun appMatching() {
        fun best(spoken: String, labels: List<String>) =
            labels.maxByOrNull { Parser.appScore(spoken, it) }?.takeIf { Parser.appScore(spoken, it) >= 50 }
        val apps = listOf(
            "YouTube", "YouTube Music", "WhatsApp", "Instagram", "PhonePe", "GPay", "Spotify",
            "Google Maps", "Chrome", "Camera", "Settings", "Amazon Shopping", "Play Store", "Snapchat",
            "Telegram", "Facebook", "Calculator", "Clock", "Photos", "Messages", "Flipkart", "Netflix"
        )
        assertEquals("YouTube", best("youtube", apps))
        assertEquals("YouTube Music", best("youtube music", apps))
        assertEquals("WhatsApp", best("whatsapp", apps))
        assertEquals("WhatsApp", best("watsapp", apps))
        assertEquals("WhatsApp", best("vhatsaep", apps))
        assertEquals("Instagram", best("instagram", apps))
        assertEquals("Instagram", best("instagraam", apps))
        assertEquals("PhonePe", best("phonepe", apps))
        assertEquals("PhonePe", best("fonpe", apps))
        assertEquals("Spotify", best("spotify", apps))
        assertEquals("Spotify", best("spotifai", apps))
        assertEquals("Google Maps", best("maps", apps))
        assertEquals("Amazon Shopping", best("amazon", apps))
        assertEquals("Play Store", best("play store", apps))
        assertEquals("Flipkart", best("flipcart", apps))
        assertEquals(null, best("kesariya", apps))
        assertEquals(null, best("tum hi ho", apps))
    }

    @Test fun contactMatching() {
        assertTrue(Parser.contactScore("rahul", "Rahul Singh") >= 80)
        assertTrue(Parser.contactScore("राहुल", "Rahul Singh") >= 80)
        assertTrue(Parser.contactScore("mummy", "Mummy") == 100)
        assertEquals(0, Parser.contactScore("rahul", "Priya Sharma"))
        assertTrue(Parser.contactScore("rahul", "Rahul Singh") > Parser.contactScore("rahul", "Rahulkumar"))
    }
}
