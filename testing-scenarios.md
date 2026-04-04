
**Version:** 0.9.0
**Testing Track:** Internal

Thanks for helping test CrowdLink! This guide walks you through 4 short scenarios. When you're done, please fill out the feedback form — the link is at the bottom of this page.

---

## Before You Start

- You need **two people** with Android phones for most of these scenarios
- Make sure **Bluetooth is enabled** on both phones
- Make sure both phones have the **CrowdLink app installed** from the Play Store link
- You do **not** need mobile data or Wi-Fi — that's the whole point of the app!

### Known limitations to be aware of

- **Pairing can occasionally be temperamental** — if it fails the first time, try again. This is caused by a timing issue with the Bluetooth service that's being worked on. Moving closer together and keeping both screens on tends to help.
- **The compass indoors** — GPS doesn't work reliably indoors, so the compass will fall back to an RSSI signal strength indicator instead of a directional arrow. This is expected. There's a debug toggle in **Settings → Debug → Force indoor mode** you can use to deliberately switch between the two modes — disable it for outdoor testing, enable it if you're testing indoors and want to see the indoor fallback screen.

---

## Troubleshooting

If something isn't working, try the steps below before reporting it as a bug — some issues have simple fixes.

**Pairing keeps failing**
- Make sure both phones are unlocked and the CrowdLink app is open and on the pairing screen on both devices
- Keep both phones within half a metre of each other during pairing
- Try again — the first attempt sometimes fails if Bluetooth hasn't fully initialised yet after opening the app
- If it fails three times in a row, force stop the app on both phones (hold app icon → App Info → Force Stop), reopen, and try once more
- Avoid pairing in very busy Bluetooth environments like crowded offices or public transport

**Friend not appearing in the Discovery tab**
- Wait up to 30 seconds — discovery can take a moment after pairing
- Make sure both phones have Bluetooth enabled and the app is open in the foreground
- If they still don't appear, go to Settings and tap **Reset App Data**, then re-pair

**Compass showing indoors when you're actually outdoors**
- If you're outdoors and still seeing the indoor signal screen, GPS may not have got a fix yet — give it 20–30 seconds
- Make sure Location permissions are set to **"Allow all the time"** or **"While using the app"** in your phone's settings for CrowdLink
- Check that **Settings → Debug → Force indoor mode** is switched off

**Compass showing outdoors screen when you're testing indoors**
- Enable **Settings → Debug → Force indoor mode** to force the app into indoor RSSI mode

**Messages not arriving**
- Make sure both phones are within reasonable range of each other (within ~30 metres outdoors)
- Check that the friend appears in the Discovery tab — if they're not discoverable, messages won't send either
- Try sending a message from the other phone first, then reply

**App crashed or froze**
- Don't restart your phone — just force stop the app (hold app icon → App Info → Force Stop) and reopen it
- Note down roughly what you were doing when it happened and include it in your feedback form — that's genuinely useful information

---

## Scenario 1: Pairing Up

**What this is:** Before you can find or message each other, you need to pair your phones once.

**You need:** 2 people, 2 phones, standing near each other

### Steps:

1. Both people open CrowdLink
2. Both tap the **Friends** tab at the bottom of the screen
3. **Person A** taps **"Add Friend"** — a QR code appears on their screen
4. **Person B** taps **"Add Friend"**, then taps **"Scan Friend's QR Code"** — their camera opens
5. Person B points their camera at Person A's QR code
6. A confirmation message should appear on **Person A's** phone
7. Wait a few seconds for the pairing to complete

> **Tip:** If pairing fails, see the Troubleshooting section above. The most common fix is simply trying again with both screens on and phones close together.

### What should happen

- You should both be brought back to the friends screen
- Person A appears in Person B's Friends list, and vice versa

### What to report if something goes wrong

- The camera didn't open
- The QR code wasn't detected
- An error message appeared
- One or both phones didn't show the friend after pairing

---

## Scenario 2: Finding a Friend

**What this is:** Once paired, you can use CrowdLink to locate each other without any internet or phone signal.

**You need:** 2 paired phones, some space to move around (outdoor area is preferred)

### Steps:

1. Both people open CrowdLink and go to the **Discovery** tab
2. Wait up to 15 seconds — your friend should appear in the list with a proximity label (e.g. "Very Close", "Nearby", "In range")
3. Locate your friend's name and try the following:
   - Tap the **compass icon** — an arrow should point in the direction of your friend
   - Go back, then tap the **map icon** — your friend's location should appear as a pin on a map
   - Try moving further apart and see if the proximity label changes

> **Testing indoors?** The compass will show an RSSI signal strength indicator instead of a directional arrow — this is expected behaviour. Enable **Settings → Debug → Force indoor mode** to test the indoor fallback screen deliberately. For best results with the GPS compass, test outdoors in an open area with a clear view of the sky.

### What should happen

- Friend appears in the list within 15 seconds
- Proximity label updates as you move
- Compass arrow points roughly toward your friend (outdoors)
- Map shows your friend's pin with their name on it

### What to report if something goes wrong

- Friend never appeared in the list
- Took much longer than 15 seconds
- Compass arrow didn't move or pointed the wrong way
- Map was blank or the pin was in the wrong place
- App crashed

---

## Scenario 3: Messaging

**What this is:** Send short messages to a paired friend without any internet connection.

**You need:** 2 paired phones within range of each other

### Steps:

1. From the **Discovery** tab, tap your friend's name
2. Tap the **chat icon**
3. Type a short message and tap send
4. Have your friend do the same from their phone
5. Send a few messages back and forth

> **Tip:** There can be a short delay of a few seconds on the first message — this is normal while the Bluetooth connection establishes. Subsequent messages in the same session are usually faster.

### What should happen

- Messages appear on both screens
- Messages are in the correct order (oldest at top, newest at bottom)
- Your messages appear on the right, received messages on the left
- Notifications appear for incoming messages — tapping one should open the relevant chat

### What to report if something goes wrong

- Message never arrived on the other phone
- Messages appeared in the wrong order
- Notifications didn't appear or didn't open the chat
- The send button didn't work
- App crashed or froze

---

## Scenario 4: SOS Alert

**What this is:** If someone needs help, they can send an emergency alert to all their paired friends.

**You need:** 2 paired phones

### Steps:

1. **Person A** triggers the SOS alert from within the app
2. **Person B** waits for the alert to come through, then taps the notification
3. When Person B receives the alert, tap **"Navigate To"**
4. Also try tapping the **chat** button on the alert screen
5. Try dismissing the alert using the **Dismiss** button

> **Tip:** The SOS alert uses the same Bluetooth mesh as messaging. If Person B doesn't receive it within 30 seconds, check that both phones are discoverable in the Discovery tab first.

### What should happen

- Person B receives the SOS notification and the alert screen appears
- "Navigate To" opens the map with Person A's location pinned
- Chat button opens a conversation with Person A
- Dismiss closes the alert and returns to the previous screen

### What to report if something goes wrong

- Person B never received the alert
- "Navigate To" opened a compass instead of the map
- The alert screen was blank or showed wrong information
- Dismiss didn't work

---

## Testing Without Any Signal

> **Tip:** If you want to confirm everything works with zero connectivity, enable **Airplane Mode** on both phones, then manually re-enable **Bluetooth** (swipe down from the top, tap Bluetooth). Repeat Scenarios 2 and 3 — everything should work exactly the same. The offline map may take a moment to load cached tiles. Make sure to disable Airplane Mode when you're done!

---

## General Feedback

After testing, please fill out the feedback form — it only takes a few minutes and is a huge help:

**[Fill out the feedback form](https://forms.gle/CBLFZf7LZRgWoB9A7)**

When filling it out, include:
- Which phone model and Android version you used
- Which scenarios you completed
- Anything that felt confusing, even if it technically worked
- Any crashes or freezes (a screenshot or screen recording is very helpful)

---

## Useful Links

- **App overview:** [fionan313.github.io/CrowdLink](https://fionan313.github.io/CrowdLink/)
- **Testing instructions:** [fionan313.github.io/CrowdLink/testing-scenarios](https://fionan313.github.io/CrowdLink/testing-scenarios)
- **Install the app:** [Google Play internal test link](https://play.google.com/apps/internaltest/4701574068877153406) *(make sure your email has been added to the tester list first!)*
- **Feedback form:** [forms.gle/CBLFZf7LZRgWoB9A7](https://forms.gle/CBLFZf7LZRgWoB9A7)

Thanks again for your time!