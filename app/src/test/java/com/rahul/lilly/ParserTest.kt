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
        assertEquals(RoutineCmd("morning"), p("good morning"))
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

class SplitNameAndPagingTest {
    private val names = listOf(
        "Abhishek 🔫🔫", "Abhishek Badiyasar", "Abhishek Badiysar", "Abhishek Balesra", "Abhishek Bhaiya",
        "Abhishek Bhaiya Hf", "Abhishek Bpt Vgu", "Abhishek Friend", "Abhishek Ji", "Abhishek Jitu",
        "Abhishek Moraya", "Abhishek New", "Abhishek Sain", "Abhishek Shrimal Plot", "Abhishek Vgu",
        "Dr. Abhishek Bhaiya"
    )

    @Test fun splitWordsStillMatch() {
        // Google likhta hai "abhishek badiya sar"
        assertEquals(listOf("Abhishek Badiyasar"), CallChoice.candidates(listOf("abhishek badiya sar"), names))
        assertEquals(listOf("Abhishek Badiyasar"), CallChoice.candidates(listOf("Call Abhishek Badiyasar".removePrefix("Call ")), names))
        assertEquals(listOf("Abhishek Shrimal Plot"), CallChoice.candidates(listOf("abhishek shrimal plot"), names))
        assertEquals(Cmd.Call("abhishek badiya sar"), Parser.parse("call Abhishek badiya sar"))
        val opts = CallChoice.candidates(listOf("abhishek"), names).map { CallChoice.Phone(it, "9876543210", "") }
        assertEquals("Abhishek Badiyasar", CallChoice.pick("badiya sar", opts, true)?.let { opts[it].name })
    }

    @Test fun moreAndShortNames() {
        assertTrue(CallChoice.isMore("more"))
        assertTrue(CallChoice.isMore("aur batao"))
        assertTrue(CallChoice.isMore("aage"))
        assertTrue(CallChoice.isMore("और"))
        assertTrue(!CallChoice.isMore("badiyasar"))
        val opts = names.map { CallChoice.Phone(it, "1", "") }
        val short = CallChoice.shortNames(opts)
        assertEquals("Abhishek", short[0])
        assertEquals("Badiyasar", short[1])
        assertEquals("Shrimal Plot", short[13])
        assertEquals("Dr. Bhaiya", short[15])
    }
}

class AlarmTest {
    private fun p(s: String) = Parser.parse(s)
    private fun at(s: String, nowH: Int = 15, nowM: Int = 0): Pair<Int, Int>? {
        val c = p(s) as? Cmd.Alarm ?: return null
        return TimeParse.resolve(c.at, nowH, nowM)
    }

    @Test fun alarmTimes() {
        assertEquals(Pair(6, 0), at("set an alarm for 6 am"))
        assertEquals(Pair(6, 0), at("subah 6 baje ka alarm lagao"))
        assertEquals(Pair(6, 0), at("सुबह 6 बजे का अलार्म लगाओ"))
        assertEquals(Pair(19, 30), at("alarm for 7:30 pm"))
        assertEquals(Pair(19, 30), at("sadhe saat baje ka alarm", nowH = 15))
        assertEquals(Pair(7, 30), at("sadhe saat baje ka alarm", nowH = 1))
        assertEquals(Pair(8, 15), at("subah sava aath ka alarm"))
        assertEquals(Pair(20, 45), at("raat paune nau baje alarm laga do"))
        assertEquals(Pair(18, 20), at("shaam 6 baj kar 20 minute ka alarm"))
        assertEquals(Pair(18, 0), at("alarm 6 baje", nowH = 15))
        assertEquals(Pair(5, 30), at("wake me up at 5:30"))
        assertEquals(Pair(7, 0), at("set alarm for seven am"))
        assertEquals(Pair(6, 45), at("alarm for 6 45 am"))
        assertEquals(Pair(15, 10), at("10 minute baad alarm lagao", 15, 0))
        assertEquals(Pair(16, 30), at("set an alarm in an hour and a half".replace("an hour and a half", "90 minutes"), 15, 0))
        assertEquals(Pair(15, 30), at("aadhe ghante baad jaga dena", 15, 0))
        assertEquals(Pair(15, 20), at("20 minute ka alarm", 15, 0))
    }

    @Test fun alarmRepeatAndReminders() {
        assertEquals("daily", (p("har roz subah 6 baje alarm") as Cmd.Alarm).repeat)
        assertEquals("weekdays", (p("set alarm for 7 am on weekdays") as Cmd.Alarm).repeat)
        val r1 = p("remind me to call mom at 5 pm") as Cmd.Alarm
        assertEquals("call mom", r1.label)
        assertEquals(Pair(17, 0), TimeParse.resolve(r1.at, 10, 0))
        assertEquals("call mom", (p("remind me at 5 pm to call mom") as Cmd.Alarm).label)
        assertEquals("take medicine", (p("remind me in 10 minutes to take medicine") as Cmd.Alarm).label)
        assertEquals("mummy ko call karna", (p("5 baje yaad dilana ki mummy ko call karna") as Cmd.Alarm).label)
        assertEquals("mummy ko call karne", (p("mummy ko call karne ke liye 5 baje yaad dilana") as Cmd.Alarm).label)
        assertTrue(p("remind me to buy milk") is Cmd.AlarmAsk)
        assertTrue(p("alarm laga do") is Cmd.AlarmAsk)
    }

    @Test fun timersAndControls() {
        assertEquals(Cmd.Timer(600), p("set a timer for 10 minutes"))
        assertEquals(Cmd.Timer(300), p("5 minute ka timer lagao"))
        assertEquals(Cmd.Timer(5400), p("timer for 1 hour 30 minutes"))
        assertEquals(Cmd.Timer(30), p("30 second ka timer"))
        assertEquals(Cmd.Timer(1800), p("aadha ghanta timer"))
        assertEquals(Cmd.StopAlarm, p("alarm band karo"))
        assertEquals(Cmd.StopAlarm, p("stop the alarm"))
        assertEquals(Cmd.ShowAlarms, p("show my alarms"))
        assertEquals(Cmd.ShowAlarms, p("mere alarm dikhao"))
        // Purani cheezein na tootein
        assertEquals(Cmd.Torch(false), p("torch band karo"))
        assertEquals(Cmd.Media("pause"), p("gaana band karo"))
        assertEquals(Cmd.Remember("mera gym 6 baje hai"), p("yaad rakhna ki mera gym 6 baje hai"))
    }
}

class MusicWakeTest {
    private fun j(vararg w: Pair<String, Double>) =
        "{\"result\":[" + w.joinToString(",") { "{\"conf\":${it.second},\"word\":\"${it.first}\"}" } + "]}"

    @Test fun hearsLillyOverMusic() {
        // Gaane ke shabd [unk] ban kar aas-paas
        val withMusic = j("[unk]" to 1.0, "[unk]" to 1.0, "hey" to 0.7, "lily" to 0.7, "[unk]" to 1.0)
        assertTrue(!WakeCheck.accept(withMusic, "normal", noisy = false))
        assertTrue(WakeCheck.accept(withMusic, "normal", noisy = true))
        // Gaane me bas "lily" jaisa kuch aaya, bharosa kam
        assertTrue(!WakeCheck.accept(j("[unk]" to 1.0, "lily" to 0.6, "[unk]" to 1.0), "normal", noisy = true))
        // Asli shabd bahut zyada = baat-cheet
        assertTrue(!WakeCheck.accept(j("hello" to 0.9, "hi" to 0.8, "hey" to 0.9, "lily" to 0.9), "normal", noisy = true))
    }
}

class NewFeaturesTest {
    private fun p(s: String) = Parser.parse(s)

    @Test fun spokenNumbers() {
        assertEquals(Cmd.Call("9876543210"), p("call nine eight seven six five four three two one zero"))
        assertEquals(Cmd.Call("9876543210"), p("नौ आठ सात छह पांच चार तीन दो एक शून्य पर कॉल करो".replace("छह", "छे")))
        assertEquals(Cmd.Call("9988776655"), p("call double nine double eight double seven double six double five"))
        assertEquals(Cmd.Call("9876543210"), p("call 98765 43210"))
        assertEquals("rohit ko call kar do", Parser.normalize("Rohit ko call kar do"))
        assertEquals("ek do teen", Parser.normalize("ek do teen"))
    }

    @Test fun whatsappMessages() {
        assertEquals(WhatsAppCmd("rohit", "main 10 minute mein aa raha hoon"), p("Rohit ko WhatsApp pe bolo main 10 minute mein aa raha hoon"))
        assertEquals(WhatsAppCmd("rohit", "main 10 minute mein aa raha hoon"), p("Rohit ko WhatsApp par likho ki main 10 minute mein aa raha hoon"))
        assertEquals(WhatsAppCmd("rohit", "main aa raha hoon"), p("rohit ko whatsapp pe main aa raha hoon bhej do"))
        assertEquals(WhatsAppCmd("mom", "i'll be late"), p("send a WhatsApp to mom saying I'll be late"))
        assertEquals(WhatsAppCmd("rohit", "i'm coming"), p("tell Rohit on WhatsApp that I'm coming"))
        assertEquals(WhatsAppCmd("rohit", ""), p("Rohit ko WhatsApp karo"))
        assertEquals(WhatsAppCmd("papa", ""), p("send a whatsapp message to papa"))
        assertEquals(Cmd.OpenApp("whatsapp"), p("WhatsApp kholo"))
        assertEquals(Cmd.OpenApp("whatsapp"), p("open WhatsApp"))
    }

    @Test fun whatsappCalls() {
        assertEquals(WhatsAppCmd("rohit", kind = "call"), p("Rohit ko WhatsApp call karo"))
        assertEquals(WhatsAppCmd("rohit", kind = "video"), p("Rohit ko WhatsApp pe video call karo"))
        assertEquals(WhatsAppCmd("mom", kind = "call"), p("WhatsApp call mom"))
        assertEquals(WhatsAppCmd("rohit", kind = "video"), p("video call Rohit on WhatsApp"))
        assertEquals(WhatsAppCmd("papa", kind = "call"), p("call papa on whatsapp"))
    }

    @Test fun mapsAndPlaces() {
        assertEquals(NavigateCmd("home"), p("ghar ka rasta dikhao"))
        assertEquals(NavigateCmd("home"), p("navigate to home"))
        assertEquals(NavigateCmd("office"), p("take me to office"))
        assertEquals(NavigateCmd("hawa mahal", "w"), p("hawa mahal ka rasta batao paidal"))
        assertEquals(NavigateCmd("sms hospital"), p("SMS hospital kaise jaun"))
        assertEquals(NavigateCmd("jaipur airport"), p("directions to Jaipur airport"))
        assertEquals(NearbyCmd("petrol pump"), p("nearest petrol pump"))
        assertEquals(NearbyCmd("atm"), p("ATM near me"))
        assertEquals(NearbyCmd("petrol pump"), p("paas mein petrol pump"))
        assertEquals(SaveAddressCmd("home", "malviya nagar jaipur"), p("my home address is Malviya Nagar Jaipur"))
        assertEquals(SaveAddressCmd("home", "malviya nagar jaipur"), p("mera ghar malviya nagar jaipur mein hai"))
    }

    @Test fun notificationsRoutineWeather() {
        assertEquals(NotificationsCmd(), p("kiska message aaya"))
        assertEquals(NotificationsCmd(), p("read my notifications"))
        assertEquals(NotificationsCmd("whatsapp"), p("WhatsApp pe kiska message aaya"))
        assertEquals(RoutineCmd("morning"), p("Good morning Lilly"))
        assertEquals(RoutineCmd("morning"), p("suprabhat"))
        assertEquals(RoutineCmd("night"), p("good night"))
        assertEquals(WeatherCmd(), p("aaj mausam kaisa hai"))
        assertEquals(WeatherCmd(), p("what's the weather"))
        assertEquals(WeatherCmd("tomorrow"), p("kal barish hogi kya"))
        assertEquals(WeatherCmd("tomorrow"), p("will it rain tomorrow"))
    }

    @Test fun phoneSettings() {
        assertEquals(SettingCmd("dnd", "on"), p("do not disturb on karo"))
        assertEquals(SettingCmd("dnd", "off"), p("DND band karo"))
        assertEquals(SettingCmd("brightness", "down"), p("brightness kam karo"))
        assertEquals(SettingCmd("brightness", "max"), p("full brightness"))
        assertEquals(SettingCmd("brightness", "50"), p("set brightness to 50 percent"))
        assertEquals(SettingCmd("ringer", "silent"), p("phone silent karo"))
        assertEquals(SettingCmd("ringer", "vibrate"), p("vibrate mode on karo"))
        assertEquals(SettingCmd("ringer", "normal"), p("normal mode"))
        assertEquals(SettingCmd("panel", "wifi"), p("WiFi settings kholo"))
        assertEquals(SettingCmd("panel", "bluetooth:on"), p("bluetooth on karo"))
        assertEquals(SettingCmd("panel", "hotspot"), p("hotspot on karo"))
        assertEquals(Cmd.Volume("mute"), p("mute karo"))
        assertEquals(Cmd.Battery, p("battery kitni hai"))
    }

    @Test fun youtubeVideos() {
        assertEquals(Cmd.PlayMusic("cricket highlights", "youtube"), p("YouTube pe cricket highlights chalao"))
        assertEquals(Cmd.PlayMusic("cricket highlights", "youtube"), p("YouTube par cricket highlights dikhao"))
        assertEquals(Cmd.PlayMusic("mr beast", "youtube"), p("play mr beast on youtube"))
    }
}

class LanguageAndTextTest {
    @Test fun keepsHowYouSpokeTheMessage() {
        assertEquals("main 10 minute mein aa raha hoon",
            Parser.originalSpan("Rohit ko WhatsApp pe bolo main 10 minute mein aa raha hoon", "main 10 minute mein aa raha hoon"))
        assertEquals("I'll be late", Parser.originalSpan("send a WhatsApp to mom saying I'll be late", "i'll be late"))
        val hi = "रोहित को व्हाट्सएप पर बोलो मैं दस मिनट में आ रहा हूँ"
        val c = Parser.parse(hi) as WhatsAppCmd
        assertEquals("रोहित".let { Parser.roman(it).lowercase() }, c.target)
        assertEquals("मैं दस मिनट में आ रहा हूँ", Parser.originalSpan(hi, c.text))
        assertEquals(SaveAddressCmd("city", "jaipur"), Parser.parse("my city is Jaipur"))
        assertEquals(SaveAddressCmd("city", "jaipur"), Parser.parse("mera shehar jaipur hai"))
    }

    @Test fun languageFollowsSpeaker() {
        assertTrue(Parser.isEnglish("send a WhatsApp to mom saying I'll be late"))
        assertTrue(Parser.isEnglish("what's the weather"))
        assertTrue(!Parser.isEnglish("mausam kaisa hai"))
        assertTrue(!Parser.isEnglish("ghar ka rasta dikhao"))
        assertTrue(!Parser.isEnglish("रोहित को व्हाट्सएप पर बोलो"))
    }
}

class SameNumberTest {
    @Test fun samePersonSavedTwice() {
        val rows = listOf(
            CallChoice.Phone("Kunal Bhai", "+91 98290 11111", "Mobile"),
            CallChoice.Phone("Kunal Bhai New", "098290 11111", "Mobile"),
            CallChoice.Phone("Kunal Office", "0141 555 6677", "Work")
        )
        val both = listOf("Kunal Bhai", "Kunal Bhai New")
        assertEquals(1, CallChoice.distinctNumbers(both, rows).size)
        assertEquals(listOf("Kunal Bhai"), CallChoice.collapseSameNumbers(both, rows))
        val all = listOf("Kunal Bhai", "Kunal Bhai New", "Kunal Office")
        assertEquals(2, CallChoice.distinctNumbers(all, rows).size)
        assertEquals(listOf("Kunal Bhai", "Kunal Office"), CallChoice.collapseSameNumbers(all, rows))
    }
}

class WhatsAppPhrasingTest {
    private fun p(s: String) = Parser.parse(s)

    @Test fun moreWaysToSay() {
        assertEquals(WhatsAppCmd("nandu", "paise dal de"), p("whatsapp par nandu ko messages karo paise dal de"))
        assertEquals(WhatsAppCmd("nandu", "paise dal de"), p("WhatsApp pe Nandu ko message karo ki paise dal de"))
        assertEquals(WhatsAppCmd("nandu", ""), p("whatsapp par nandu ko message karo"))
        assertEquals(WhatsAppCmd("nandu", "paise dal de"), p("Nandu ko message karo ki paise dal de"))
        assertEquals(WhatsAppCmd("nandu", "paise dal de"), p("Nandu ko message bhejo WhatsApp pe ki paise dal de"))
        assertEquals(WhatsAppCmd("mom", "i'm late"), p("text mom that I'm late"))
        assertEquals(WhatsAppCmd("nandu", ""), p("nandu ko message karo"))
        assertEquals(WhatsAppCmd("nandu", "paise dal de"), p("send a whatsapp to nandu saying paise dal de"))
        // Purane na tootein
        assertEquals(Cmd.Call("rohit"), p("Rohit ko call karo"))
        assertEquals(Cmd.OpenApp("messages"), p("messages kholo"))
    }
}

class PaymentModeTest {
    @Test fun paymentMode() {
        assertEquals(SettingCmd("helper", "off"), Parser.parse("payment mode"))
        assertEquals(SettingCmd("helper", "off"), Parser.parse("Lilly payment mode on karo"))
        assertEquals(SettingCmd("helper", "off"), Parser.parse("helper band karo"))
        assertEquals(SettingCmd("helper", "off"), Parser.parse("turn off the helper"))
    }
}

class PersonaTest {
    @org.junit.Test fun boyTalk() {
        org.junit.Assert.assertEquals("मैं कॉल लगा रहा हूँ", Persona.masculine("मैं कॉल लगा रही हूँ"))
        org.junit.Assert.assertEquals("मैं लिली हूँ। मैं मदद करूँगा", Persona.masculine("मैं लिली हूँ। मैं मदद करूँगी"))
        org.junit.Assert.assertEquals("बैटरी खत्म होगी", Persona.masculine("बैटरी खत्म होगी"))
        org.junit.Assert.assertEquals("Main call laga raha hoon", Persona.masculine("Main call laga rahi hoon"))
        org.junit.Assert.assertEquals("main samajh nahi paaya", Persona.masculine("main samajh nahi paayi"))
    }
}

class SmartTest {
    private fun eq(a: Any, b: Any) = org.junit.Assert.assertEquals(a, b)
    @org.junit.Test fun siriLikeCommands() {
        eq(Cmd.PlayMusic("kesariya", "spotify"), Parser.parse("spotify kholo aur kesariya bajao"))
        eq(Cmd.PlayMusic("kesariya", ""), Parser.parse("gaana chalao kesariya"))
        eq(Cmd.PlayMusic("", ""), Parser.parse("koi accha sa gana sunao"))
        eq(Cmd.PlayMusic("", "spotify"), Parser.parse("mujhe gaane sunne hai spotify pe"))
        eq(WhatsAppCmd("rahul", "kal milte hai", "msg"), Parser.parse("rahul ko whatsapp karo ki kal milte hai"))
        eq(WhatsAppCmd("mummy", "", "call"), Parser.parse("whatsapp pe mummy ko call karo"))
        eq(WhatsAppCmd("papa", "", "video"), Parser.parse("whatsapp par video call karo papa ko"))
        eq(WhatsAppCmd("ankit", "hi", "msg"), Parser.parse("send hi to ankit on whatsapp"))
        eq(Cmd.Remember("My date of birth is 12 june 1998"), Parser.parse("meri dob 12 june 1998 hai"))
        org.junit.Assert.assertTrue(Parser.parse("meri date of birth kya hai") is Cmd.RecallAbout)
        eq(Cmd.Date, Parser.parse("aaj date kya hai"))
        org.junit.Assert.assertTrue(Smart.isRefusal("Sorry, I can't access your WhatsApp"))
    }
}

class VoiceMatchTest {
    @org.junit.Test fun gateAndScore() {
        val me = floatArrayOf(1f, 0.2f, 0f)
        val p = VoicePrint(mutableListOf(me, floatArrayOf(0.9f, 0.25f, 0.05f), floatArrayOf(1f, 0.15f, 0f)))
        org.junit.Assert.assertTrue(p.score(floatArrayOf(1f, 0.2f, 0.02f)) > 0.9f)
        org.junit.Assert.assertTrue(p.score(floatArrayOf(0f, 0f, 1f)) < 0.2f)
        var t = 0L
        val g = WakeGate { t }
        org.junit.Assert.assertTrue(g.decide(0.6f, "normal"))
        org.junit.Assert.assertFalse(g.decide(0.1f, "normal"))
        org.junit.Assert.assertFalse(g.decide(0.42f, "normal")) // thoda kam — pehli baar nahi
        t = 3000
        org.junit.Assert.assertTrue(g.decide(0.42f, "normal"))  // turant dobara — haan
    }
}

class ExtrasTest {
    private fun eq(a: Any, b: Any) = org.junit.Assert.assertEquals(a, b)
    @org.junit.Test fun newFeatures() {
        eq(CallLogCmd("missed"), Parser.parse("kisne call kiya tha"))
        eq(CallLogCmd("back"), Parser.parse("jisne call kiya use call karo"))
        eq(CallLogCmd("redial"), Parser.parse("last number pe call karo"))
        eq(ListCmd("shopping", "add", "doodh aur bread"), Parser.parse("doodh aur bread list mein daalo"))
        eq(ListCmd("shopping", "read"), Parser.parse("meri list padho"))
        eq(ListCmd("notes", "add", "gas ka bill bharna hai"), Parser.parse("note karo ki gas ka bill bharna hai"))
        eq(CalendarCmd("read", "tomorrow"), Parser.parse("kal ki meeting kya hai"))
        eq(WhatsAppCmd("rahul", "main aa raha hoon", "sms"), Parser.parse("rahul ko sms karo ki main aa raha hoon"))
        eq("torch jalao" to "volume badhao", Extras.splitTwo("torch jalao aur volume badhao") { Parser.parse(it) }!!)
        org.junit.Assert.assertNull(Extras.splitTwo("spotify kholo aur kesariya bajao") { Parser.parse(it) })
        org.junit.Assert.assertNull(Extras.splitTwo("rahul ko whatsapp karo ki main aur ankit aa rahe hai") { Parser.parse(it) })
    }
}

class SpellingTest {
    @org.junit.Test fun fixesAndTwoCommands() {
        org.junit.Assert.assertEquals("WiFi band karo aur torch chalao", Parser.roman("vi-fi band karo aur torch chalao"))
        org.junit.Assert.assertEquals(SettingCmd("panel", "wifi:off"), Parser.parse("vi-fi band karo"))
        org.junit.Assert.assertEquals("vi-fi band karo" to "torch chalao", Extras.splitTwo(Parser.normalize("vi-fi band karo aur torch chalao")) { Parser.parse(it) }?.let { it.first.replace("wifi", "vi-fi") to it.second })
        org.junit.Assert.assertEquals("WhatsApp kholo", Parser.roman("watsap kholo"))
        org.junit.Assert.assertEquals(SettingCmd("panel", "bluetooth:on"), Parser.parse("blue tooth chalu karo"))
    }
}

class AbuseTest {
    @org.junit.Test fun gaaliMode() {
        org.junit.Assert.assertTrue(Abuse.has("bc torch jala"))
        org.junit.Assert.assertEquals("torch jala", Abuse.strip("bc torch jala"))
        org.junit.Assert.assertFalse(Abuse.has("amazon sale kab hai"))
        org.junit.Assert.assertFalse(Abuse.has("chhod do"))
        org.junit.Assert.assertEquals(true, Abuse.toggle("gaali mode on karo"))
        org.junit.Assert.assertEquals(false, Abuse.toggle("abuse mode band karo"))
        org.junit.Assert.assertNull(Abuse.toggle("torch jalao"))
    }
}

class AbuseSpellingTest {
    @org.junit.Test fun variants() {
        for (t in listOf("bahan chod pagal hai kya", "behenchod", "bhen chod", "madarchod", "maadar chod", "chutiye", "bhosdike", "bhosadi ke", "gaandu", "lode", "harami", "kamine", "fuck you", "bc"))
            org.junit.Assert.assertTrue(t, Abuse.has(t))
        for (t in listOf("chhutti kab hai", "amazon sale", "bahan ko call karo", "mera bhai", "chod do yaar", "london ka mausam", "bhojan", "behen ki shaadi"))
            org.junit.Assert.assertFalse(t, Abuse.has(t))
    }
}

class AbuseDevanagariTest {
    @org.junit.Test fun hindiScript() {
        for (t in listOf("मादरचोद", "भेनचोद", "बीसी", "तेरी माँ की", "f*** you", "साले"))
            org.junit.Assert.assertTrue(t, Abuse.has(Parser.normalize(t)))
        org.junit.Assert.assertTrue(Abuse.reply(true, false, "bahanchod").second.isNotBlank())
        org.junit.Assert.assertTrue(Abuse.isLecture("Rahul, kripya aise shabdon ka istemal mat kijiye"))
        org.junit.Assert.assertFalse(Abuse.isLecture("Delhi ka mausam aaj saaf rahega"))
    }
}

class AbuseMoreTest {
    @org.junit.Test fun moreWords() {
        for (t in listOf("bakchod", "bhadwe", "haramzade", "abe kutte", "suar", "nalayak", "dhakkan", "jhaatu", "teri maa ka", "piss off", "wtf"))
            org.junit.Assert.assertTrue(t, Abuse.has(t))
        for (t in listOf("mera kutta kaha hai", "jhatpat batao", "taste kaisa hai", "tatkal ticket book karo"))
            org.junit.Assert.assertFalse(t, Abuse.has(t))
        repeat(20) { org.junit.Assert.assertTrue(Abuse.reply(true, false, "bakchod").first.isNotBlank()) }
    }
}

class MishearTest {
    @org.junit.Test fun fixes() {
        org.junit.Assert.assertEquals(CallLogCmd("missed"), Parser.parse("misi call kitni hai"))
        org.junit.Assert.assertEquals(CallLogCmd("redial"), Parser.parse("ri dial karo"))
        org.junit.Assert.assertEquals("missed call kitni hai", Parser.roman("मिस्ड कॉल कितनी है"))
        org.junit.Assert.assertTrue(Parser.parse("kal 5 baje alarm") is Cmd.Alarm)
    }
}

class MusicAppsTest {
    @org.junit.Test fun apps() {
        org.junit.Assert.assertEquals(Cmd.PlayMusic("kesariya", "ytmusic"), Parser.parse("youtube music par kesariya chalao"))
        org.junit.Assert.assertEquals(Cmd.PlayMusic("tum hi ho", "jiosaavn"), Parser.parse("jiosaavn pe tum hi ho bajao"))
        org.junit.Assert.assertEquals(Cmd.OpenApp("spotify"), Parser.parse("spotify kholo"))
    }
}

class MusicControlTest {
    @org.junit.Test fun controls() {
        val eq = { a: Any, b: Any -> org.junit.Assert.assertEquals(a, b) }
        eq(Cmd.Media("pause"), Parser.parse("gaana band karo"))
        eq(Cmd.Media("next"), Parser.parse("aage wala gaana chalao"))
        eq(Cmd.Media("resume"), Parser.parse("gaana wapas chalao"))
        eq(MusicCmd("nowplaying"), Parser.parse("ye kaunsa gaana hai"))
        eq(MusicCmd("queue", "kesariya"), Parser.parse("iske baad kesariya lagao"))
        eq(MusicCmd("repeat_on"), Parser.parse("is gaane ko repeat karo"))
        eq(MusicCmd("forward", "30"), Parser.parse("30 second aage karo"))
        eq(MusicCmd("like"), Parser.parse("ye gaana like karo"))
        org.junit.Assert.assertTrue(Parser.parse("dobara call karo rahul ko") !is CallLogCmd)
    }
}

class ReplySleepTest {
    @org.junit.Test fun parse() {
        val eq = { a: Any, b: Any -> org.junit.Assert.assertEquals(a, b) }
        eq(ReplyCmd("rahul", "5 minute me aata hoon"), Parser.parse("rahul ko reply karo ki 5 minute me aata hoon"))
        eq(ReplyCmd("", "ok"), Parser.parse("reply karo ki ok"))
        eq(ReplyCmd("ankit", "on my way"), Parser.parse("reply to ankit saying on my way"))
        eq(NotificationsCmd("", "rahul"), Parser.parse("rahul ka message padho"))
        eq(MusicCmd("sleep", "1800"), Parser.parse("30 minute baad gaana band karo"))
        eq(MusicCmd("sleep_cancel"), Parser.parse("sleep timer band karo"))
        org.junit.Assert.assertTrue(Parser.parse("10 minute baad yaad dilana ki chai") is Cmd.Alarm)
        eq(Cmd.Timer(300), Parser.parse("5 minute ka timer lagao"))
    }
}

class NotifPhrasesTest {
    @org.junit.Test fun phrases() {
        for (t in listOf("notifications chek karo", "check my notifications", "notification dekho", "messages check karo", "kisne message kiya"))
            org.junit.Assert.assertTrue(t, Parser.parse(t) is NotificationsCmd)
    }
}

class LearnedSpellingTest {
    @org.junit.Test fun custom() {
        Parser.setCustomSpellings(mapOf("baidya sar" to "badiya sir", "noti" to "notifications"))
        org.junit.Assert.assertEquals("call badiya sir", Parser.roman("call baidya sar"))
        org.junit.Assert.assertEquals("notifications check karo", Parser.roman("noti check karo"))
        org.junit.Assert.assertEquals("notice board", Parser.roman("notice board"))
        Parser.setCustomSpellings(emptyMap())
    }
}
