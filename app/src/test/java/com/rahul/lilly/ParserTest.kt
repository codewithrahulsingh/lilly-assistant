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
        assertEquals(Cmd.PlayMusic("kesariya", "spotify"), p("play Kesariya on Spotify"))
        assertEquals(Cmd.PlayMusic("arijit singh"), p("Arijit Singh ke gaane chalao"))
        assertEquals(Cmd.PlayMusic("tum hi ho", "spotify"), p("spotify par tum hi ho chalao"))
        assertEquals(Cmd.PlayMusic("tum hi ho"), p("tum hi ho gaana bajao"))
        assertEquals(Cmd.PlayMusic("kesariya"), p("प्ले केसरिया"))
        assertEquals(Cmd.PlayMusic(""), p("play some music"))
        assertEquals(Cmd.PlayMusic("arijit singh", "youtube"), p("youtube pe arijit singh chalao"))
        assertEquals(Cmd.PlayMusic("kesariya", "youtube"), p("play kesariya on youtube"))
        assertEquals(Cmd.PlayMusic("kesariya", "spotify"), p("open spotify and play kesariya"))
        assertEquals(Cmd.PlayMusic("kesariya", "spotify"), p("स्पॉटिफाई पर केसरिया चलाओ"))
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

    @Test fun spotifyIsKept() {
        assertEquals(Cmd.PlayMusic("arijit singh", "spotify"), p("Spotify pe Arijit Singh ke gaane chalao"))
        assertEquals(Cmd.PlayMusic("kesariya", "spotify"), p("Kesariya Spotify par chalao"))
        assertEquals(Cmd.PlayMusic("kesariya", "spotify"), p("play kesariya in spotify"))
        assertEquals(Cmd.PlayMusic("kesariya", ""), p("play kesariya"))
    }

    @Test fun teaching() {
        assertEquals(Cmd.Teach("good night", "turn off the torch"), p("When I say good night turn off the torch"))
        assertEquals(Cmd.Teach("good night", "turn off the torch"), p("when I say good night, then turn off the torch"))
        assertEquals(Cmd.Teach("boss", "call rohit sharma"), p("when i say boss call Rohit Sharma"))
        assertEquals(Cmd.Teach("gym time", "play workout songs on spotify"), p("When I say gym time play workout songs on Spotify"))
        assertEquals(Cmd.Teach("boss", "rohit ko call karna"), p("jab main boss bolun to Rohit ko call karna"))
        assertEquals(Cmd.Teach("so ja", "torch band karo"), p("jab bhi main so ja bolu toh torch band karo"))
    }
}

class LearnerTest {
    private var stored: String? = null
    private var now = 1_000_000L
    private fun learner() = Learner({ stored }, { stored = it }, { now })

    @Test fun teachAndRewrite() {
        val l = learner()
        assertTrue(l.teach("Good night", "turn off the torch"))
        assertEquals("turn off the torch", l.rewrite("good night"))
        assertEquals("turn off the torch", l.rewrite("Hey Lilly, good night!"))
        assertEquals(null, l.rewrite("good morning"))
    }

    @Test fun learnsFromRetry() {
        val l = learner()
        l.noteFailure("boss ko ghanti maaro")
        now += 10_000
        assertTrue(l.noteSuccess("Rohit ko call karo"))
        assertEquals("rohit ko call karo", l.rewrite("boss ko ghanti maaro"))
        // Bahut der baad wali command se kuch nahi seekhna
        l.noteFailure("kuch ajeeb sa")
        now += 5 * 60_000
        assertTrue(!l.noteSuccess("time kya hua"))
        assertEquals(null, l.rewrite("kuch ajeeb sa"))
    }

    @Test fun contactsNeedTwoUses() {
        val l = learner()
        l.rememberContact("mummy", "Mummy Jio")
        assertEquals(null, l.contactFor("mummy"))
        l.rememberContact("Mummy", "Mummy Jio")
        assertEquals("Mummy Jio", l.contactFor("mummy"))
        assertTrue(l.contactBonus("Mummy Jio") > l.contactBonus("Papa"))
        // Doosra contact aaya toh ginti dobara shuru
        l.rememberContact("mummy", "Mummy Airtel")
        assertEquals(null, l.contactFor("mummy"))
    }

    @Test fun savesAndLoads() {
        val a = learner()
        a.teach("good night", "torch off")
        a.rememberApp("insta", "com.instagram.android", "Instagram")
        a.rememberApp("insta", "com.instagram.android", "Instagram")
        a.setMusicApp("spotify")
        val b = learner()
        assertEquals("torch off", b.rewrite("good night"))
        assertEquals("com.instagram.android", b.appFor("insta"))
        assertEquals("spotify", b.musicApp)
        assertEquals(3, b.items().size)
        b.forget(b.items().first { it.kind == "p" })
        assertEquals(null, learner().rewrite("good night"))
        b.clear()
        assertEquals(0, learner().items().size)
    }
}

class WakeAndCallTest {
    private fun p(s: String) = Parser.parse(s)

    @Test fun noFalseWakeFromHindi() {
        assertTrue(!Parser.hasWakeWord("maine kal naya phone le li"))
        assertTrue(!Parser.hasWakeWord("usne chai le li"))
        assertTrue(Parser.hasWakeWord("hey lilly open youtube"))
        assertTrue(Parser.startsWithWake("Lilly call Rohit"))
        assertTrue(Parser.startsWithWake("hey lily time kya hai"))
        assertTrue(!Parser.startsWithWake("kal maine bagiche me lily ke phool dekhe"))
    }

    @Test fun wakeConfidence() {
        fun j(vararg w: Pair<String, Double>) =
            "{\"result\":[" + w.joinToString(",") { "{\"conf\":${it.second},\"end\":1.0,\"start\":0.5,\"word\":\"${it.first}\"}" } +
                "],\"text\":\"" + w.joinToString(" ") { it.first } + "\"}"
        // Saaf "Hey Lilly"
        assertTrue(WakeCheck.accept(j("hey" to 0.9, "lily" to 0.95), "normal"))
        assertTrue(WakeCheck.accept(j("hey" to 0.9, "lily" to 0.95), "low"))
        // Akela "lily" — bahut saaf ho tabhi
        assertTrue(WakeCheck.accept(j("lily" to 0.97), "normal"))
        assertTrue(!WakeCheck.accept(j("lily" to 0.7), "normal"))
        assertTrue(!WakeCheck.accept(j("lily" to 0.97), "low"))
        assertTrue(WakeCheck.accept(j("lily" to 0.7), "high"))
        // TV / lambi baat-cheet ke beech
        assertTrue(!WakeCheck.accept(j("[unk]" to 1.0, "hi" to 0.6, "[unk]" to 1.0, "lily" to 0.8, "[unk]" to 1.0), "normal"))
        assertTrue(!WakeCheck.accept(j("hello" to 0.9, "[unk]" to 1.0), "normal"))
        // Shabd-wise jaankari na ho
        assertTrue(WakeCheck.accept("{\"text\":\"hey lily\"}", "normal"))
        assertTrue(!WakeCheck.accept("{\"text\":\"\"}", "normal"))
    }

    @Test fun speakerCalls() {
        assertEquals(Cmd.Call("rohit", speaker = true), p("call Rohit on speaker"))
        assertEquals(Cmd.Call("rohit", speaker = true), p("Call Rohit on speakerphone"))
        assertEquals(Cmd.Call("rohit", speaker = true), p("Rohit ko speaker pe call karo"))
        assertEquals(Cmd.Call("mummy", speaker = true), p("mummy ko call karo speaker par"))
        assertEquals(Cmd.Call("papa", speaker = true), p("papa ko speaker on karke call karo"))
        assertEquals(Cmd.Call("rohit", speaker = true), p("रोहित को स्पीकर पर कॉल करो"))
        assertEquals(Cmd.Call("rohit", speaker = false), p("call Rohit"))
    }

    @Test fun numberChoice() {
        val nums = listOf(
            CallChoice.Phone("Rohit Sharma", "+91 98765 43210", "Mobile"),
            CallChoice.Phone("Rohit Sharma", "0141 222 3345", "Work"),
            CallChoice.Phone("Rohit Sharma", "+91 99887 76612", "Home")
        )
        assertEquals(0, CallChoice.pick("pehla", nums, false))
        assertEquals(1, CallChoice.pick("second one", nums, false))
        assertEquals(1, CallChoice.pick("doosre wale pe", nums, false))
        assertEquals(2, CallChoice.pick("last", nums, false))
        assertEquals(0, CallChoice.pick("mobile wala", nums, false))
        assertEquals(1, CallChoice.pick("office number", nums, false))
        assertEquals(2, CallChoice.pick("ghar wala", nums, false))
        assertEquals(1, CallChoice.pick("jo 3345 pe khatam hota hai", nums, false))
        assertEquals(2, CallChoice.pick("ending 12", nums, false))
        assertEquals(0, CallChoice.pick("1", nums, false))
        assertEquals(null, CallChoice.pick("pata nahi", nums, false))
        assertEquals(null, CallChoice.pick("rohit ko call kar do yaar", nums, false))
        assertTrue(CallChoice.isCancel("rehne do"))
        assertTrue(CallChoice.isCancel("cancel"))
    }

    @Test fun nameChoiceAndDedupe() {
        val names = listOf(
            CallChoice.Phone("Rohit Sharma", "1", ""), CallChoice.Phone("Rohit Gupta", "2", "")
        )
        assertEquals(1, CallChoice.pick("Gupta", names, true))
        assertEquals(0, CallChoice.pick("rohit sharma wala", names, true))
        assertEquals(1, CallChoice.pick("doosra", names, true))
        val d = CallChoice.dedupe(
            listOf(
                CallChoice.Phone("A", "+91 98765 43210", ""),
                CallChoice.Phone("A", "09876543210", "Mobile"),
                CallChoice.Phone("A", "98765-43211", "Work")
            )
        )
        assertEquals(2, d.size)
        assertEquals("Mobile", d[0].label)
    }
}

class MemoryTest {
    private var stored: String? = null
    private var now = 1_000_000L
    private fun mem() = Memory({ stored }, { stored = it }, { now })
    private fun p(s: String) = Parser.parse(s)

    @Test fun parseMemoryCommands() {
        assertEquals(Cmd.Remember("my gym is at 6 pm"), p("Remember that my gym is at 6 pm"))
        assertEquals(Cmd.Remember("mera gym 6 baje hai"), p("yaad rakhna ki mera gym 6 baje hai"))
        assertEquals(Cmd.Remember("mummy ka birthday 12 june ko hai"), p("mummy ka birthday 12 june ko hai ye yaad rakhna"))
        assertEquals(Cmd.ForgetFact("my gym timing"), p("forget my gym timing"))
        assertEquals(Cmd.ForgetFact("gym wali baat"), p("bhool jao ki gym wali baat"))
        assertEquals(Cmd.Recall, p("what do you remember about me"))
        assertEquals(Cmd.Recall, p("mere baare mein kya yaad hai"))
        assertEquals(Cmd.Chat("bye"), p("never mind"))
        assertTrue(p("forget it") !is Cmd.ForgetFact)
    }

    @Test fun factsSaveAndForget() {
        val m = mem()
        assertTrue(m.remember("My gym is at 6 pm"))
        assertTrue(!m.remember("my gym is at 6 pm."))
        assertTrue(m.remember("Mummy's birthday is on 12 June"))
        assertEquals(2, mem().facts().size)
        assertTrue(mem().factsForPrompt().contains("- My gym is at 6 pm"))
        val m2 = mem()
        assertEquals(1, m2.forgetMatching("gym timing 6 pm"))
        assertEquals(1, mem().facts().size)
    }

    @Test fun answerCache() {
        val m = mem()
        assertTrue(m.isCacheable("what is the capital of japan"))
        assertTrue(!m.isCacheable("what is the weather today"))
        assertTrue(!m.isCacheable("aaj ki khabar kya hai"))
        assertTrue(!m.isCacheable("hello"))
        m.cacheAnswer("What is the capital of Japan?", "Tokyo.", "Tokyo.", english = true)
        assertEquals("Tokyo.", mem().cachedAnswer("what is the capital of japan", true)?.spoken)
        assertEquals(null, mem().cachedAnswer("what is the capital of japan", false))
        now += 40L * 24 * 60 * 60 * 1000
        assertEquals(null, mem().cachedAnswer("what is the capital of japan", true))
    }
}

class ManyContactsTest {
    private val names = listOf(
        "Abhishek 🔫🔫", "Abhishek Badiyasar", "Abhishek Badiysar", "Abhishek Balesra", "Abhishek Bhaiya",
        "Abhishek Bhaiya Hf", "Abhishek Bpt Vgu", "Abhishek Friend", "Abhishek Ji", "Abhishek Jitu",
        "Abhishek Moraya", "Abhishek New", "Abhishek Sain", "Abhishek Shrimal Plot", "Abhishek Vgu",
        "Dr. Abhishek Bhaiya", "Rahul Singh", "Mummy"
    )
    private val opts = CallChoice.candidates(listOf("abhishek"), names).map { CallChoice.Phone(it, "9876543210", "") }
    private fun pick(a: String) = CallChoice.pick(a, opts, true)?.let { opts[it].name }

    @Test fun asksWhenManyShareTheName() {
        // "Abhishek 🔫🔫" ka naam bilkul "Abhishek" hai, phir bhi baaki 15 ke saath poochhna chahiye
        assertEquals(16, CallChoice.candidates(listOf("abhishek"), names).size)
        assertEquals(16, CallChoice.candidates(listOf("अभिषेक"), names).size)
        // Poora naam bola toh seedha
        assertEquals(listOf("Abhishek Bhaiya"), CallChoice.candidates(listOf("abhishek bhaiya"), names))
        assertEquals(listOf("Abhishek Jitu"), CallChoice.candidates(listOf("abhishek jitu"), names))
        assertEquals(listOf("Rahul Singh"), CallChoice.candidates(listOf("rahul"), names))
        // Zyada call kiya hua upar
        assertEquals("Abhishek Vgu", CallChoice.candidates(listOf("abhishek"), names) { if (it == "Abhishek Vgu") 9 else 0 }.first())
        assertEquals("Abhishek", CallChoice.speakName("Abhishek 🔫🔫"))
    }

    @Test fun picksBySecondName() {
        assertEquals("Abhishek Bhaiya", pick("bhaiya"))
        assertEquals("Abhishek Bhaiya Hf", pick("bhaiya hf wale"))
        assertEquals("Dr. Abhishek Bhaiya", pick("dr bhaiya"))
        assertEquals("Abhishek Friend", pick("friend"))
        assertEquals("Abhishek Friend", pick("friend wale abhishek ko"))
        assertEquals("Abhishek Ji", pick("ji"))
        assertEquals("Abhishek Jitu", pick("jitu"))
        assertEquals("Abhishek Vgu", pick("vgu"))
        assertEquals("Abhishek Bpt Vgu", pick("bpt vgu"))
        assertEquals("Abhishek Badiyasar", pick("badiyasar"))
        assertEquals("Abhishek Shrimal Plot", pick("plot wale"))
        assertEquals("Abhishek Moraya", pick("morya"))
        assertEquals(opts[4].name, pick("number 5"))
        assertEquals(opts[2].name, pick("teesra"))
        assertEquals(null, pick("pata nahi yaar"))
    }
}
