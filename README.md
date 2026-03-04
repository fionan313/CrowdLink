# CrowdLink

> Find your friends at festivals when your phone signal dies

[![Android CI](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml/badge.svg)](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Min SDK](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

## What is it?

CrowdLink helps you find your friends at festivals and concerts when your phone signal dies. It uses Bluetooth and a custom mesh networking protocol to pass messages between devices with no internet connection, no mobile network, and no infrastructure of any kind.

A survey of 42 people aged 18-35 found that 85.7% had lost contact with friends at events, with 64.3% separated for over 10 minutes. Cellular networks fail in large crowds. CrowdLink works around that entirely.

---

## Features

### Working now
- BLE device discovery - finds nearby friends in ~5 seconds
- RSSI-based distance estimation (±0.6m accuracy, 68% noise reduction via moving average)
- Friend pairing via QR code
- Multi-hop BLE mesh networking, messages relay between devices with TTL and probabilistic flooding
- End-to-end message delivery confirmed across physical devices
- WiFi Direct messaging (fallback transport)
- ESP32 relay node support for extended range
- In-app notifications for incoming mesh messages
- Settings screen with BLE, mesh, LoRa, and privacy sections
- Compass/arrow view for directional friend-finding with GPS fallback

### Planned
- Single-scan QR pairing with confirmation dialog
- End-to-end encryption
- Offline map with cached tiles
- User evaluation study (10+ participants)

---

## How the mesh works

Each device acts as both a sender and a relay. When you send a message it gets written to nearby devices via BLE GATT. Each device that receives it checks if it's the intended recipient. 

If not, it forwards it on with a decremented TTL. A probabilistic relay (75% chance) prevents the network from flooding while still reaching distant nodes.

Messages are identified by UUID and tracked in a `SeenMessageCache` so duplicates are dropped. The practical result is that a message can reach a friend several hops away with no direct connection between your two devices.

---

## Architecture

Clean Architecture with three layers:

```
Presentation  →  Jetpack Compose + ViewModels
Domain        →  Use cases + repository interfaces  
Data          →  BLE, WiFi Direct, Room DB, mesh engine
```

The mesh transport sits entirely in the data layer. `MeshRoutingEngine` is pure Kotlin with no Android dependencies, which makes it straightforward to test.

**Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coroutines + Flow, JUnit 4, MockK, Turbine

---

## Getting started

You need at least two physical Android devices — BLE and WiFi Direct do not work on emulators.

```bash
git clone https://github.com/fionan313/CrowdLink.git
cd CrowdLink
./gradlew installDebug
```

Requires Android Studio Hedgehog or later, JDK 17, Android SDK API 28+.

**Permissions used:** Bluetooth scan/advertise, fine location (required for BLE on Android), camera (QR scanning), WiFi Direct.

---

## Testing

```bash
./gradlew testDebugUnitTest
open app/build/reports/tests/testDebugUnitTest/index.html
```

Current status: 20/21 unit tests passing, 82% coverage on core domain logic. Integration tests moved to `androidTest/` for Room and Context support.

---

## Performance

| Metric | Target | Result |
|--------|--------|--------|
| Discovery time | <10s | 4.5s avg |
| Distance accuracy | ±15m | ±0.6m |
| Battery drain | <5%/hr | 4.2%/hr |
| Cross-device success | >90% | 100% |

Tested on Nothing Phone 2a (API 35) and Samsung Note 10+ (API 31).

---

## Project structure

```
app/src/main/java/com/fyp/crowdlink/
├── data/
│   ├── ble/          # BleAdvertiser, BLEScanner, DeviceRepositoryImpl
│   ├── mesh/         # MeshRoutingEngine, MeshMessageSerialiser, SeenMessageCache
│   ├── p2p/          # WifiDirectManager
│   ├── local/        # Room database, DAOs, entities
│   └── repository/   # FriendRepositoryImpl, MessageRepositoryImpl
├── domain/
│   ├── model/        # Friend, Message, MeshMessage, NearbyFriend, DeviceLocation
│   ├── repository/   # Repository interfaces
│   └── usecase/      # EstimateDistance, SendMessage, PairFriend, ShareLocation
├── presentation/
│   ├── discovery/    # Nearby device list
│   ├── friends/      # Paired friends list
│   ├── pairing/      # QR scan and generate
│   ├── chat/         # Messaging UI
│   ├── compass/      # Directional friend-finding
│   ├── relay/        # ESP32 relay node discovery
│   ├── settings/     # Settings screen
│   └── MainActivity.kt
├── di/               # Hilt AppModule
└── CrowdLinkApplication.kt
```

---

## Known issues

- 16KB page size warning on Android 15+ (no functional impact)
- API 28 devices occasionally need BLE toggled to reconnect (80-90% success vs 100% on API 31+)
- GATT error 133 occurs intermittently — this is an Android BLE stack issue, not a bug in the app

---

## Academic context

Final Year Project — TU Dublin, BSc Computer Science (TU856), 2025/2026.

Student: Fionán Ó Ceallaigh (C22337521). 
Supervisor: Bryan Duggan.

This project is not open for external contributions during the assessment period. Submissions open after April 2026.

AI tools (Claude, Gemini) were used for debugging assistance and code review/cleanup.

---

## License

MIT — see [LICENSE](LICENSE) for details.
