# CrowdLink

> Find your friends at festivals, marches, and crowded events - no signal required

[![Android CI](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml/badge.svg)](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Min SDK](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

## What is it?

CrowdLink is an offline peer-to-peer Android app for finding and staying in touch with friends at crowded events. It works entirely over Bluetooth Low Energy - no internet, no mobile network, no server infrastructure of any kind.

It was built to solve a specific problem: cellular networks fail in large crowds. A survey of 42 people aged 18-35 found that 85.7% had lost contact with friends at events, and 64.3% were separated for over 10 minutes. CrowdLink works around network failure entirely by communicating directly between devices.

Designed for festivals, concerts, protests, large hikes, or anywhere people gather and signal becomes unreliable.

---

## Features

- **BLE device discovery** — finds nearby paired friends in ~5 seconds with no infrastructure
- **RSSI-based distance estimation** — ±0.6m accuracy with 68% noise reduction via moving average
- **QR code pairing** — one-time in-person setup, exchanges a 32-byte shared encryption key
- **End-to-end encryption** — AES-256-GCM via Google Tink across all messages, location updates, and SOS alerts
- **Multi-hop BLE mesh networking** — messages relay between devices using TTL-controlled probabilistic flooding (75% relay chance, max 6 hops)
- **Offline map** — friend locations pinned on a cached OSM map via MapLibre, works with no internet
- **GPS location sharing** — coordinates relayed over the mesh, so friends appear on the map even beyond direct BLE range
- **Compass view** — directional arrow pointing toward a friend, falls back to RSSI proximity indoors
- **SOS alerts** — encrypted emergency alert sent to all paired friends with Navigate To and Chat options
- **In-app messaging** — encrypted chat between paired friends, delivered directly or via mesh relay
- **User profile** — display name, phone number and status message set on first launch and editable in settings
- **Onboarding flow** — guided first-run setup explaining how the app works and capturing the user's display name
- **Privacy controls** — Ghost Mode disables all radios, location sharing can be toggled per session, mesh relay can be disabled so your device stops forwarding messages for others
- **In-app notifications** — incoming messages and SOS alerts trigger notifications when the app is backgrounded
- **Data management** — clear message history, clear map cache, unpair all friends, or full app reset from settings
- **ESP32 + LoRa relay node support** *(partial)* — the Android app can discover and connect to ESP32 relay nodes over BLE and forward queued messages to them; full bidirectional LoRa relay is in progress

---

## How the mesh works

Each device acts as both a sender and a relay. When you send a message, it is encrypted and written to nearby devices via BLE GATT. Each receiving device checks if it is the intended recipient — if so, it decrypts and delivers it. If not, it forwards the message with a decremented TTL.

A 75% probabilistic relay prevents the network from flooding while still achieving good delivery across multiple hops. Messages are identified by UUID and tracked in a `SeenMessageCache` so duplicates are always dropped. Users can opt out of relaying entirely via the mesh relay toggle in settings.

GPS coordinates are also relayed through the mesh — a friend can appear on your map even when they are outside your direct BLE range.

---

## How encryption works

All communication is end-to-end encrypted using AES-256-GCM via Google Tink. A 32-byte shared symmetric key is generated during QR pairing and stored locally against each paired friend. Every outgoing payload — messages, location updates, SOS alerts — is encrypted before it leaves the device.

Encrypted payloads are prefixed with `0xFF`. Relay nodes forward ciphertext unchanged without decrypting it. On the receiving end, if MAC address randomisation makes sender identification unreliable, the app tries each paired friend's key until decryption succeeds.

---

## How pairing works

Pairing happens once, in person, before an event — similar in concept to AirDrop. One person generates a QR code containing their device ID, display name, and shared encryption key. The other scans it. Both devices confirm over BLE and store each other in the local Room database.

Physical proximity is required by design. It prevents remote impersonation and ensures both parties deliberately consent to the connection.

---

## Architecture

Clean Architecture with three layers:

```
Presentation  →  Jetpack Compose + ViewModels
Domain        →  Use cases + repository interfaces
Data          →  BLE, Room DB, mesh engine, encryption
```

The mesh transport and encryption sit entirely in the data layer. `MeshRoutingEngine` is pure Kotlin with no Android dependencies, making it straightforward to unit test.

**Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coroutines + Flow, MapLibre, Google Tink, JUnit 4, MockK, Turbine

---

## Getting started

You need at least two physical Android devices — BLE does not work on emulators.

```bash
git clone https://github.com/fionan313/CrowdLink.git
cd CrowdLink
./gradlew installDebug
```

Requires Android Studio Hedgehog or later, JDK 17, Android SDK API 28+.

**Permissions used:** Bluetooth scan/advertise, fine location (required for BLE on Android 12+), camera (QR scanning).

---

## Testing

```bash
./gradlew testDebugUnitTest
open app/build/reports/tests/testDebugUnitTest/index.html
```

**69 unit tests** covering mesh routing, encryption, pairing, and message delivery. Integration tests are in `androidTest/` for Room and Context-dependent components.

---

## Performance

> ⚠️ These figures are from controlled testing on two devices during development. A broader user evaluation study is currently in progress — results will be updated here when complete.

| Metric | Target | Result |
|---|---|---|
| Discovery time | <10s | 4.5s avg |
| Distance accuracy | ±1.5m | ±0.6m |
| Battery drain | <5%/hr | 4.2%/hr |
| Mesh Delivery | >90% | 94% (3-hop) |
| SOS Latency | <2s | 0.8s avg |

Tested on Nothing Phone 2a (API 35) and Samsung Note 10+ (API 31).

---

## Project structure

```
app/src/main/java/com/fyp/crowdlink/
├── data/
│   ├── ble/           # BleAdvertiser, BleScanner, DeviceRepositoryImpl, RelayNodeConnection
│   ├── mesh/          # MeshRoutingEngine, MeshMessageSerialiser, SeenMessageCache
│   ├── notifications/ # MeshNotificationManager
│   ├── local/
│   │   ├── dao/       # Room DAOs
│   │   └── entity/    # Room entities
│   └── repository/    # FriendRepositoryImpl, MessageRepositoryImpl, LocationRepositoryImpl
├── domain/
│   ├── model/         # Friend, Message, MeshMessage, NearbyFriend, DeviceLocation
│   ├── repository/    # Repository interfaces
│   └── usecase/       # EstimateDistance, SendMessage, PairFriend, ShareLocation
├── presentation/
│   ├── chat/          # Messaging UI
│   ├── compass/       # Directional friend-finding
│   ├── discovery/     # Nearby device list
│   ├── friends/       # Paired friends list
│   ├── map/           # Offline map with friend pins
│   ├── onboarding/    # First-run onboarding flow
│   ├── pairing/       # QR scan and generate
│   ├── relay/         # ESP32 relay node discovery
│   ├── settings/      # Settings, profile, privacy controls
│   ├── sos/           # SOS alert
│   └── MainActivity.kt
├── ui/
│   └── theme/         # Material theme, colours, typography
├── di/                # Hilt AppModule
└── CrowdLinkApplication.kt
```

---

## Known limitations

- **MTU & Payload Constraints**: The protocol is constrained by a 512-byte BLE MTU. With a 100-byte routing header, the maximum single-packet payload is 412 bytes. There is currently no support for packet fragmentation.
- **SOS Scaling (Key-Loop)**: To bypass Android MAC address randomization, SOS alerts are decrypted using a "Key-Loop" (brute-forcing all paired keys). While reliable for small groups, this scales O(n) and may introduce latency if a user has dozens of paired friends.
- **Static Mesh Gate**: The 75% probabilistic relay gate is currently hardcoded. In extremely dense environments (e.g., >50 devices in range), this may still lead to congestion; in very sparse environments, it may reduce reliability.
- **Direct-Only SOS**: SOS alerts are currently delivered to all paired friends within direct BLE range. Multi-hop mesh relay for SOS is a planned enhancement.
- **Hardware Limitations**: BLE range is approximately 10–100m depending on environment. GATT error 133 occurs intermittently due to Android BLE stack instability.
- **Device Compatibility**: API 28 devices occasionally need BLE toggled to reconnect (80–90% reliability vs 100% on API 31+). 16KB page size warning on Android 15+ has no functional impact.
- **Android Only**: iOS implementation is out of scope for this project.

---

## Academic context

Final Year Project — TU Dublin, B.Sc. Computer Science (TU856), 2025/2026.

Student: Fionán Ó Ceallaigh (C22337521). Supervisor: Bryan Duggan.

This project is not open for external contributions during the assessment period. Submissions open after April 2026.

AI tools (Claude, Gemini) were used for debugging assistance and code review/clean-up.