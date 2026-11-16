# Lilly – Personal Voice Assistant (Android)

"Lilly" boliye → Lilly "Haan bolo" kehti hai → apna kaam boliye.

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
- Display over other apps: **ON** (background se apps kholne ke liye)

Phone restart hone ke baad Lilly app ek baar kholna padta hai (Android ka niyam).

## Note
- Speech recognition ke liye phone me Google app hona chahiye.
