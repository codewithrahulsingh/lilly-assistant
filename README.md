# Lilly – Personal Voice Assistant (Android)

**v2.0 — Siri jaisa:** "Lilly" bolo → "ting" ki awaaz, screen jagti hai aur neeche orb aata hai
(lock screen par bhi) → command bolo. "Lilly" pehchanne ka kaam ab poori tarah **phone ke andar (offline)**
hota hai, isliye baar-baar beep nahi, battery kam, aur aapki awaaz bahar nahi jaati jab tak "Lilly" na bolo.

"Hey Lilly" boliye → Lilly "Haan Rahul, bolo kya karu?" kehti hai → apna kaam boliye.

## Lilly abhi kya kar sakti hai
- **Call:** "Rahul ko call karo", "call Mummy", "9876543210 ko call karo"
- **Torch:** "torch jalao" / "torch band karo"
- **Time / Date:** "kitne baje hain", "aaj ki date kya hai"
- **Apps kholna:** "YouTube kholo", "WhatsApp open karo", "camera kholo"
- **Baat-cheet:** "kaisi ho", "tum kaun ho", "thank you"

Ek baar me bhi bol sakte hain: "Lilly, Rahul ko call karo".

## APK
Har push par GitHub Actions APK bana kar **Releases** me `Lilly.apk` daal deta hai.

## Lock screen / background
Lilly ek foreground service me chalti hai (notification: "Lilly sun rahi hai"), isliye app band ho ya
phone lock ho, tab bhi "Lilly, Rahul ko call karo" bolne par call lag jaati hai.
Notification se "Baat karo" ya "Band karo" bhi kar sakte hain.

Pehli baar app kholne par ye allow karein:
- Mic, Contacts, Phone, Notifications
- Battery optimization: **Allow** (warna phone Lilly ko band kar deta hai)

Security ke liye Lilly koi restricted permission (Display over other apps) ya Internet permission nahi leti.
Isliye apps (YouTube wagairah) sirf tab khulte hain jab Lilly app screen par khula ho; call aur torch lock screen se bhi chalte hain.

Phone restart hone ke baad Lilly app ek baar kholna padta hai (Android ka niyam).

## Note
- Speech recognition ke liye phone me Google app hona chahiye.

## Settings (⚙ upar baayein kone me)
- **Lock screen par call:** Band / Sirf favourite ⭐ contacts (default) / Sab contacts
- **Awaaz:** "Agli awaaz try karo" se phone ki saari Hindi awaazein sun kar pasand ki chuno.
  Sabse natural awaaz ke liye *Speech Services by Google* update karein aur Hindi voice data download karein.

## Security
- Internet permission nahi — Lilly khud kuch online nahi bhejti
- Koi restricted permission (overlay/accessibility) nahi
- App data ka backup off
- Lock screen par default sirf favourite contacts ko call

## v3.0
- **Gaane:** "Play Kesariya", "Arijit Singh ke gaane chalao", "YouTube par Kesariya chalao", "gaana band karo", "next song", "volume badhao"
- **AI baat-cheet (optional):** app me AI key (Gemini free / Anthropic) daalne par Lilly kisi bhi baat ka jawab deti hai aur bina dobara "Lilly" bole baat aage chalti hai
- **Behtar app matching:** Google ke saare andaze + milti-julti spelling se app dhoondhti hai
- **QA:** `app/src/test/.../ParserTest.kt` — 70+ commands ke tests har build par chalte hain
