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

    // ---------- Teeno bhashaon me ek hi kaam ----------

    @Test fun sameCommandInThreeLanguages() {
        // English
        assertEquals(Cmd.Call("rohit"), p("Can you call Rohit please"))
        assertEquals(Cmd.Call("mom"), p("call my mom"))
        assertEquals(Cmd.Call("rohit"), p("make a call to Rohit"))
        assertEquals(Cmd.Call("papa"), p("give papa a call"))
        // Hinglish
        assertEquals(Cmd.Call("rohit"), p("Rohit ko call lagao"))
        assertEquals(Cmd.Call("mummy"), p("zara mummy ko phone karo"))
        assertEquals(Cmd.Call("papa"), p("papa se baat karao"))
        // Hindi lipi
        assertEquals(Cmd.Call("mammi"), p("मम्मी को फोन लगाओ"))
        assertEquals(Cmd.Call("rohit"), p("कॉल रोहित"))
    }

    @Test fun englishCommands() {
        assertEquals(Cmd.OpenApp("camera"), p("could you open the camera"))
        assertEquals(Cmd.OpenApp("instagram"), p("go to Instagram"))
        assertEquals(Cmd.Torch(true), p("turn on the flashlight"))
        assertEquals(Cmd.Torch(false), p("switch off the torch please"))
        assertEquals(Cmd.Torch(true), p("turn on the light"))
        assertEquals(Cmd.Time, p("what time is it"))
        assertEquals(Cmd.Date, p("what day is it today"))
        assertEquals(Cmd.Date, p("what's the date today"))
        assertEquals(Cmd.Battery, p("how much battery is left"))
        assertEquals(Cmd.Volume("up"), p("turn up the volume"))
        assertEquals(Cmd.Volume("up"), p("louder"))
        assertEquals(Cmd.Volume("down"), p("lower the volume"))
        assertEquals(Cmd.Volume("down"), p("turn it down"))
        assertEquals(Cmd.Media("next"), p("skip this song"))
        assertEquals(Cmd.Media("next"), p("change the song"))
        assertEquals(Cmd.Media("pause"), p("stop the music"))
        assertEquals(Cmd.Media("pause"), p("pause the song"))
        assertEquals(Cmd.PlayMusic("shape of you"), p("please play Shape of You"))
        assertEquals(Cmd.PlayMusic("arijit singh"), p("listen to Arijit Singh"))
        assertEquals(Cmd.PlayMusic("kesariya", "youtube"), p("search Kesariya on YouTube"))
        assertEquals(Cmd.Chat("how"), p("how are you"))
        assertEquals(Cmd.Chat("who"), p("what's your name"))
        assertEquals(Cmd.Chat("joke"), p("tell me a joke"))
        assertEquals(Cmd.Chat("bye"), p("never mind"))
        assertEquals(Cmd.Chat("hello"), p("good morning"))
        assertEquals(Cmd.Chat("help"), p("what can you do"))
        assertTrue(p("who is the prime minister of india") is Cmd.Unknown)
    }

    @Test fun hindiScriptCommands() {
        assertEquals(Cmd.Time, p("टाइम क्या हुआ है"))
        assertEquals(Cmd.Time, p("समय बताओ"))
        assertEquals(Cmd.Date, p("आज की तारीख क्या है"))
        assertEquals(Cmd.Battery, p("बैटरी कितनी है"))
        assertEquals(Cmd.Volume("up"), p("आवाज बढ़ाओ"))
        assertEquals(Cmd.Volume("down"), p("वॉल्यूम कम करो"))
        assertEquals(Cmd.Media("next"), p("अगला गाना"))
        assertEquals(Cmd.Media("pause"), p("गाना बंद करो"))
        assertEquals(Cmd.Torch(false), p("टॉर्च बंद करो"))
        assertEquals(Cmd.Chat("how"), p("कैसी हो"))
        assertEquals(Cmd.Chat("thanks"), p("धन्यवाद"))
    }

    @Test fun hinglishCommands() {
        assertEquals(Cmd.Time, p("abhi time kya hua hai"))
        assertEquals(Cmd.Date, p("aaj kaun sa din hai"))
        assertEquals(Cmd.Volume("max"), p("awaaz poori kar do"))
        assertEquals(Cmd.Media("next"), p("gaana badlo"))
        assertEquals(Cmd.Media("next"), p("doosra gaana chalao"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("kesariya sunna hai"))
        assertEquals(Cmd.Torch(true), p("light jalao"))
        assertEquals(Cmd.Chat("joke"), p("ek chutkula sunao"))
        assertEquals(Cmd.Chat("bye"), p("rehne do"))
    }

    @Test fun languageDetection() {
        // English (Google ise Hindi lipi me likh de tab bhi)
        assertTrue(Parser.isEnglish("call Rohit"))
        assertTrue(Parser.isEnglish("what time is it"))
        assertTrue(Parser.isEnglish("hey lilly open YouTube"))
        assertTrue(Parser.isEnglish("ओपन यूट्यूब"))
        assertTrue(Parser.isEnglish("play Kesariya on Spotify"))
        // Hinglish / Hindi
        assertTrue(!Parser.isEnglish("Rohit ko call karo"))
        assertTrue(!Parser.isEnglish("YouTube kholo"))
        assertTrue(!Parser.isEnglish("time kya hua"))
        assertTrue(!Parser.isEnglish("राहुल को कॉल करो"))
        assertTrue(!Parser.isEnglish("बैटरी कितनी है"))
        assertTrue(!Parser.isEnglish(""))
    }
}
