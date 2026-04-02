
**Version:** 0.9.0  
**Student:** Fionán Ó Ceallaigh (C22337521)  
**Devices Required:** Minimum 2 Android devices (API 29+) with BLE and camera

---

## Test Case Format

| Field | Description |
|---|---|
| **ID** | Unique test case identifier |
| **Flow** | Feature area |
| **Preconditions** | State required before test begins |
| **Steps** | Numbered steps to execute |
| **Expected Result** | What should happen |
| **Pass/Fail** | Result |
| **Notes** | Any observations |

---

## TC-01 — Onboarding

### TC-01-01: First Launch Onboarding

| Field               | Detail                                                    |
| ------------------- | --------------------------------------------------------- |
| **Flow**            | Onboarding                                                |
| **Preconditions**   | Fresh install, app never opened                           |
| **Steps**           | 1. Open CrowdLink for the first time                      |
| **Expected Result** | Onboarding screens are shown before reaching the main app |
| **Pass/Fail**       |                                                           |
| **Notes**           |                                                           |

### TC-01-02: Onboarding Completes to Discovery

| Field | Detail |
|---|---|
| **Flow** | Onboarding |
| **Preconditions** | App opened for first time, on onboarding screens |
| **Steps** | 1. Progress through all onboarding screens |
| **Expected Result** | User lands on Discovery screen after completing onboarding |
| **Pass/Fail** | |
| **Notes** | |

### TC-01-03: Repeat Launch Skips Onboarding

| Field | Detail |
|---|---|
| **Flow** | Onboarding |
| **Preconditions** | App already installed and opened before |
| **Steps** | 1. Close and reopen the app |
| **Expected Result** | Onboarding is not shown, app opens directly to Discovery |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-02 — Pairing

### TC-02-01: QR Code Generation

| Field | Detail |
|---|---|
| **Flow** | Pairing |
| **Preconditions** | App installed on Device A, on Friends screen |
| **Steps** | 1. Tap "Add Friend" on Device A |
| **Expected Result** | A QR code is displayed on screen |
| **Pass/Fail** | |
| **Notes** | |

### TC-02-02: Successful Pairing (Two Devices)

| Field               | Detail                                                                                                                                                             |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Flow**            | Pairing                                                                                                                                                            |
| **Preconditions**   | App installed on both Device A and B, both on Friends screen, devices physically near each other                                                                   |
| **Steps**           | 1. On Device A, tap "Add Friend" — QR code is displayed 2. On Device B, tap "Scan QR Code" 3. Point Device B camera at Device A's QR code 4. Wait for confirmation |
| **Expected Result** | Both devices show pairing success. Friend appears in the Friends list on both devices                                                                              |
| **Pass/Fail**       |                                                                                                                                                                    |
| **Notes**           |                                                                                                                                                                    |

### TC-02-03: Pairing Timeout — Friend Not Nearby

| Field | Detail |
|---|---|
| **Flow** | Pairing |
| **Preconditions** | Device A has generated QR code, Device B scanned it but is out of BLE range |
| **Steps** | 1. Scan QR on Device B 2. Move Device B out of BLE range 3. Wait 20 seconds |
| **Expected Result** | Error message shown: "Friend did not respond. Try again with both devices nearby." |
| **Pass/Fail** | |
| **Notes** | |

### TC-02-04: Invalid QR Code Rejected

| Field | Detail |
|---|---|
| **Flow** | Pairing |
| **Preconditions** | On pairing screen with camera open |
| **Steps** | 1. Scan a QR code that is not from CrowdLink (e.g. a website QR) |
| **Expected Result** | Error message shown indicating invalid QR code |
| **Pass/Fail** | |
| **Notes** | |

### TC-02-05: Unpair a Friend

| Field | Detail |
|---|---|
| **Flow** | Pairing |
| **Preconditions** | At least one friend paired, on Friends list |
| **Steps** | 1. Long press or select the friend 2. Choose unpair/remove option 3. Confirm |
| **Expected Result** | Friend is removed from the Friends list |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-03 — Discovery

### TC-03-01: Nearby Friend Appears in Discovery

| Field | Detail |
|---|---|
| **Flow** | Discovery |
| **Preconditions** | Two paired devices within BLE range (~10m), both apps open |
| **Steps** | 1. Open Discovery screen on Device A |
| **Expected Result** | Device B's friend name appears in the nearby list within 15 seconds |
| **Pass/Fail** | |
| **Notes** | Target: <10s avg per README |

### TC-03-02: Distance Estimate Displayed

| Field | Detail |
|---|---|
| **Flow** | Discovery |
| **Preconditions** | Paired friend visible in discovery list |
| **Steps** | 1. Observe the distance shown next to friend in Discovery list |
| **Expected Result** | A distance estimate in metres is shown and updates as devices move |
| **Pass/Fail** | |
| **Notes** | Target accuracy: ±15m per README |

### TC-03-03: Friend Disappears When Out of Range

| Field | Detail |
|---|---|
| **Flow** | Discovery |
| **Preconditions** | Friend currently visible in Discovery list |
| **Steps** | 1. Move Device B outside BLE range 2. Wait |
| **Expected Result** | Friend disappears from the Discovery list |
| **Pass/Fail** | |
| **Notes** | |

### TC-03-04: Navigate to Compass from Discovery

| Field | Detail |
|---|---|
| **Flow** | Discovery → Compass |
| **Preconditions** | Paired friend visible in Discovery list |
| **Steps** | 1. Tap compass/direction option on a friend in Discovery |
| **Expected Result** | Compass screen opens pointing toward the friend |
| **Pass/Fail** | |
| **Notes** | |

### TC-03-05: Navigate to Map from Discovery

| Field | Detail |
|---|---|
| **Flow** | Discovery → Map |
| **Preconditions** | Paired friend visible in Discovery list, GPS available |
| **Steps** | 1. Tap map option on a friend in Discovery |
| **Expected Result** | Map screen opens with the friend's pin highlighted |
| **Pass/Fail** | |
| **Notes** | |

### TC-03-06: Navigate to Chat from Discovery

| Field | Detail |
|---|---|
| **Flow** | Discovery → Chat |
| **Preconditions** | Paired friend visible in Discovery list |
| **Steps** | 1. Tap chat option on a friend in Discovery |
| **Expected Result** | Chat screen opens for that friend |
| **Pass/Fail** | |
| **Notes** | |

### TC-03-07: Discovery Nav Bar Returns to Root

| Field | Detail |
|---|---|
| **Flow** | Discovery navigation |
| **Preconditions** | Navigated to a map view via Discovery (map?friendId=...) |
| **Steps** | 1. From Discovery, tap a friend's map option 2. On the map view, tap Discovery in the bottom nav bar |
| **Expected Result** | Returns to the main Discovery screen, not the map view |
| **Pass/Fail** | |
| **Notes** | Bug fixed in v0.9.0 |

---

## TC-04 — Map

### TC-04-01: Map Loads Offline

| Field | Detail |
|---|---|
| **Flow** | Map |
| **Preconditions** | Airplane mode enabled or no internet, map tile cache downloaded |
| **Steps** | 1. Enable Airplane Mode 2. Open Map tab |
| **Expected Result** | Map renders correctly with cached tiles |
| **Pass/Fail** | |
| **Notes** | |

### TC-04-02: Friend Pin Displayed on Map

| Field | Detail |
|---|---|
| **Flow** | Map |
| **Preconditions** | Paired friend nearby with GPS enabled on both devices |
| **Steps** | 1. Open Map tab |
| **Expected Result** | Friend's pin appears on the map at their location, showing their name |
| **Pass/Fail** | |
| **Notes** | |

### TC-04-03: Friend Pins Have Distinct Colours

| Field | Detail |
|---|---|
| **Flow** | Map |
| **Preconditions** | Two or more paired friends visible on map |
| **Steps** | 1. Open Map tab with 2+ friends in range |
| **Expected Result** | Each friend's pin has a different colour |
| **Pass/Fail** | |
| **Notes** | Colour derived from device ID hash |

### TC-04-04: Navigate to Chat from Map Pin

| Field | Detail |
|---|---|
| **Flow** | Map → Chat |
| **Preconditions** | Friend pin visible on map |
| **Steps** | 1. Tap friend pin 2. Tap chat option on the card |
| **Expected Result** | Chat screen opens for that friend |
| **Pass/Fail** | |
| **Notes** | |

### TC-04-05: Navigate to Compass from Map Pin

| Field | Detail |
|---|---|
| **Flow** | Map → Compass |
| **Preconditions** | Friend pin visible on map |
| **Steps** | 1. Tap friend pin 2. Tap compass option on the card |
| **Expected Result** | Compass screen opens for that friend |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-05 — Compass

### TC-05-01: Compass Points Toward Friend

| Field | Detail |
|---|---|
| **Flow** | Compass |
| **Preconditions** | GPS available on both devices, paired friend in range |
| **Steps** | 1. Open compass for a specific friend 2. Face different directions |
| **Expected Result** | Arrow updates and points in the direction of the friend |
| **Pass/Fail** | |
| **Notes** | |

### TC-05-02: Indoor Mode (No GPS)

| Field | Detail |
|---|---|
| **Flow** | Compass |
| **Preconditions** | GPS unavailable (indoors) |
| **Steps** | 1. Open compass for a friend while indoors |
| **Expected Result** | Indoor Mode indicator displayed, RSSI-based distance shown instead |
| **Pass/Fail** | |
| **Notes** | |

### TC-05-03: Back Navigation from Compass

| Field | Detail |
|---|---|
| **Flow** | Compass |
| **Preconditions** | On compass screen |
| **Steps** | 1. Tap back button |
| **Expected Result** | Returns to the previous screen |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-06 — Messaging

### TC-06-01: Send a Message

| Field | Detail |
|---|---|
| **Flow** | Chat |
| **Preconditions** | Two paired devices within range, chat screen open |
| **Steps** | 1. Type a message in the chat input 2. Tap send |
| **Expected Result** | Message appears in the chat on Device A as sent, and appears on Device B within 15 seconds |
| **Pass/Fail** | |
| **Notes** | |

### TC-06-02: Messages Are Encrypted in Transit

| Field | Detail |
|---|---|
| **Flow** | Chat / Encryption |
| **Preconditions** | Two paired devices, shared key established during pairing |
| **Steps** | 1. Send a message between devices 2. Verify payload via logs — look for `0xFF` prefix on outgoing BLE payload |
| **Expected Result** | Payload is prefixed with `0xFF`, ciphertext is not human-readable |
| **Pass/Fail** | |
| **Notes** | Technical test — requires logcat |

### TC-06-03: Messages in Correct Order

| Field | Detail |
|---|---|
| **Flow** | Chat |
| **Preconditions** | Two paired devices |
| **Steps** | 1. Send 5 messages in sequence from Device A 2. Check order on Device B |
| **Expected Result** | Messages appear in the same order they were sent |
| **Pass/Fail** | |
| **Notes** | Bug fixed in v0.9.0 — timestamps now use meshMessage.timestamp |

### TC-06-04: Message Delivery via Mesh Relay

| Field | Detail |
|---|---|
| **Flow** | Chat / Mesh |
| **Preconditions** | Device A and C paired. Device B (unpaired with C) is in range of both. Device A and C out of direct BLE range |
| **Steps** | 1. Send message from Device A to Device C 2. Device B acts as relay |
| **Expected Result** | Message reaches Device C via Device B relay |
| **Pass/Fail** | |
| **Notes** | Store-and-forward — may take several seconds longer than direct |

---

## TC-07 — SOS

### TC-07-01: Send SOS Alert

| Field | Detail |
|---|---|
| **Flow** | SOS |
| **Preconditions** | Two paired devices, both in range |
| **Steps** | 1. Trigger SOS on Device A |
| **Expected Result** | Device B receives an SOS notification and the SOS alert screen appears |
| **Pass/Fail** | |
| **Notes** | |

### TC-07-02: SOS Only Reaches Paired Friends

| Field | Detail |
|---|---|
| **Flow** | SOS / Encryption |
| **Preconditions** | Device A paired with B, Device C nearby but not paired with A |
| **Steps** | 1. Trigger SOS on Device A |
| **Expected Result** | Device B receives the SOS. Device C does not receive it |
| **Pass/Fail** | |
| **Notes** | Changed in v0.9.0 — SOS is now encrypted per-friend |

### TC-07-03: SOS Navigate To Opens Map

| Field | Detail |
|---|---|
| **Flow** | SOS → Map |
| **Preconditions** | SOS alert received on Device B, sender has GPS coordinates |
| **Steps** | 1. On the SOS alert screen, tap "Navigate To" |
| **Expected Result** | Map screen opens with the SOS sender's pin highlighted |
| **Pass/Fail** | |
| **Notes** | Bug fixed in v0.9.0 — previously opened compass |

### TC-07-04: SOS Alert Opens Chat

| Field | Detail |
|---|---|
| **Flow** | SOS → Chat |
| **Preconditions** | SOS alert screen open |
| **Steps** | 1. Tap the chat button on the SOS alert screen |
| **Expected Result** | Chat screen opens for the SOS sender |
| **Pass/Fail** | |
| **Notes** | |

### TC-07-05: SOS Alert Dismiss

| Field | Detail |
|---|---|
| **Flow** | SOS |
| **Preconditions** | SOS alert screen open |
| **Steps** | 1. Tap Dismiss on the SOS alert screen |
| **Expected Result** | SOS alert screen closes, returns to previous screen |
| **Pass/Fail** | |
| **Notes** | |

### TC-07-06: SOS Navigate To Without GPS Coordinates

| Field | Detail |
|---|---|
| **Flow** | SOS |
| **Preconditions** | SOS received from a sender with no GPS (indoors) |
| **Steps** | 1. Open SOS alert where lat/lon are 0.0 |
| **Expected Result** | "Navigate To" button is not shown or is disabled |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-08 — Friends Screen

### TC-08-01: Friends List Shows Paired Friends

| Field | Detail |
|---|---|
| **Flow** | Friends |
| **Preconditions** | At least one friend paired |
| **Steps** | 1. Open Friends tab |
| **Expected Result** | All paired friends are listed |
| **Pass/Fail** | |
| **Notes** | |

### TC-08-02: Navigate to Chat from Friends

| Field | Detail |
|---|---|
| **Flow** | Friends → Chat |
| **Preconditions** | At least one friend in list |
| **Steps** | 1. Tap chat on a friend in Friends list |
| **Expected Result** | Chat screen opens for that friend |
| **Pass/Fail** | |
| **Notes** | |

### TC-08-03: Navigate to Compass from Friends

| Field | Detail |
|---|---|
| **Flow** | Friends → Compass |
| **Preconditions** | At least one friend in list |
| **Steps** | 1. Tap compass on a friend in Friends list |
| **Expected Result** | Compass screen opens for that friend |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-09 — Settings

### TC-09-01: Open Settings

| Field | Detail |
|---|---|
| **Flow** | Settings |
| **Preconditions** | App open |
| **Steps** | 1. Tap Settings in the bottom nav bar |
| **Expected Result** | Settings screen opens |
| **Pass/Fail** | |
| **Notes** | |

### TC-09-02: Navigate to Profile

| Field | Detail |
|---|---|
| **Flow** | Settings → Profile |
| **Preconditions** | On Settings screen |
| **Steps** | 1. Tap Profile option |
| **Expected Result** | Profile screen opens |
| **Pass/Fail** | |
| **Notes** | |

### TC-09-03: Back from Profile

| Field | Detail |
|---|---|
| **Flow** | Settings |
| **Preconditions** | On Profile screen |
| **Steps** | 1. Tap back |
| **Expected Result** | Returns to Settings screen |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-10 — Offline & Non-Functional

### TC-10-01: App Functions in Airplane Mode

| Field | Detail |
|---|---|
| **Flow** | Offline |
| **Preconditions** | Airplane mode enabled, Bluetooth re-enabled manually |
| **Steps** | 1. Enable Airplane Mode 2. Re-enable Bluetooth 3. Open CrowdLink on two paired devices |
| **Expected Result** | Discovery, messaging, and map all function without internet |
| **Pass/Fail** | |
| **Notes** | Core requirement |

### TC-10-02: Discovery Time Under 15 Seconds

| Field | Detail |
|---|---|
| **Flow** | Performance |
| **Preconditions** | Two paired devices, BLE on, apps open |
| **Steps** | 1. Note time when both apps are opened 2. Note when friend appears in Discovery |
| **Expected Result** | Friend appears within 15 seconds (target <10s) |
| **Pass/Fail** | |
| **Notes** | |

### TC-10-03: Battery Drain Under 5% Per Hour

| Field | Detail |
|---|---|
| **Flow** | Performance |
| **Preconditions** | App running in background with BLE scanning active |
| **Steps** | 1. Note battery % 2. Leave app running in background for 1 hour 3. Note battery % again |
| **Expected Result** | Battery drain is ≤5% over the hour |
| **Pass/Fail** | |
| **Notes** | Target from NFR1 |

### TC-10-04: No Data Sent to External Servers

| Field | Detail |
|---|---|
| **Flow** | Privacy |
| **Preconditions** | Airplane mode on, Bluetooth on |
| **Steps** | 1. Use the app normally in Airplane Mode for 10 minutes |
| **Expected Result** | App functions fully — confirms no dependency on external servers |
| **Pass/Fail** | |
| **Notes** | |

---

## TC-11 — Relay Node (ESP32 + LoRa)

### TC-11-01: Relay Node Discovered

| Field | Detail |
|---|---|
| **Flow** | Relay |
| **Preconditions** | ESP32 LoRa relay node powered on nearby |
| **Steps** | 1. From Discovery, tap relay node discovery option |
| **Expected Result** | Relay node is listed on the relay discovery screen |
| **Pass/Fail** | |
| **Notes** | Optional hardware component |

### TC-11-02: Back from Relay Discovery

| Field | Detail |
|---|---|
| **Flow** | Relay |
| **Preconditions** | On relay discovery screen |
| **Steps** | 1. Tap back |
| **Expected Result** | Returns to Discovery screen |
| **Pass/Fail** | |
| **Notes** | |

---

*Last updated: April 2026 — v0.9.0*
