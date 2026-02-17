# CrowdLink

> Offline friend-finding for crowded events when cellular networks fail

[![Android CI](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml/badge.svg)](https://github.com/fionan313/CrowdLink/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Min SDK](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

## About

CrowdLink is an Android application that enables friends to find each other at crowded events (festivals, concerts, protests) when cellular networks fail due to congestion. Using Bluetooth Low Energy for device discovery and WiFi Direct for messaging, CrowdLink operates **entirely offline** without relying on cellular or internet infrastructure.

**Problem Solved:**
- 85.7% of attendees experience friend separation at events
- 64.3% lose contact for over 10 minutes
- Cellular networks fail in crowds (>50,000 people)
- No infrastructure dependency required

---

## Features

### Current (v0.6.0-alpha)
- **Bluetooth Low Energy Discovery** - Find nearby friends in 4.5 seconds (avg)
- **Distance Estimation** - RSSI-based ranging (±0.6m accuracy)
- **Friend Pairing** - Secure out-of-band pairing via QR codes
- **WiFi Direct Messaging** - Peer-to-peer text communication (Beta)
- **Cross-Device Support** - Android API 28-35 (93% success rate)
- **Low Battery Usage** - 4.2%/hour consumption
- **RSSI Smoothing** - 68% noise reduction via moving average

### Planned (Weeks 8-12)
- End-to-end encryption
- Location sharing with privacy controls
- Visual map interface
- Store-and-forward mesh networking

---

## Project Status

**Current Phase:** Implementation Phase (Week 7)  
**Completion:** ~55% of planned features  
**Timeline:** 12-week Final Year Project (TU Dublin)

| Phase | Status | Features |
|-------|--------|----------|
| Week 3-5: BLE Discovery | Complete | Device discovery, distance estimation |
| Week 6: Friend Pairing | Complete | QR code pairing |
| Week 7-8: Messaging | In Progress | WiFi Direct communication |
| Week 8: Encryption | Planned | End-to-end encryption |
| Week 9: Location Sharing | Planned | GPS + privacy controls |
| Week 10: Polish & Testing | Planned | Bug fixes, optimization |
| Week 11: User Evaluation | Planned | 10+ participant study |
| Week 12: Final Submission | Planned | Documentation complete |

---

## Architecture

CrowdLink follows **Clean Architecture** principles with MVVM pattern:
```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Jetpack Compose UI + ViewModels)      │
├─────────────────────────────────────────┤
│           Domain Layer                  │
│   (Use Cases + Repository Interfaces)   │
├─────────────────────────────────────────┤
│            Data Layer                   │
│  (BLE, WiFi Direct, Room DB, Repos)     │
└─────────────────────────────────────────┘
```

**Tech Stack:**
- **Language:** Kotlin 1.9.10
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt (Dagger)
- **Database:** Room
- **Async:** Kotlin Coroutines + Flow
- **Architecture:** Clean Architecture + MVVM
- **Testing:** JUnit 4, MockK, Turbine

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK (API 28+)
- **2 Physical Android Devices** (BLE and WiFi Direct require real hardware)

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/fionat33/CrowdLink.git
cd CrowdLink
```

2. **Open in Android Studio**
```bash
open -a "Android Studio" .
```

3. **Sync Gradle**
- Wait for Android Studio to sync dependencies
- Resolve any SDK version mismatches

4. **Run on Device**
- Connect Android device via USB
- Enable Developer Options & USB Debugging
- Click Run or:
```bash
./gradlew installDebug
```

### Permissions Required
- Bluetooth (API 28-30) / Bluetooth Scan/Advertise (API 31+)
- Fine Location (required for BLE scanning on Android)
- Camera (QR code scanning)
- WiFi (WiFi Direct messaging)

---

## Testing

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Run with Coverage
```bash
./gradlew testDebugUnitTestCoverage
```

### View Test Report
```bash
open app/build/reports/tests/testDebugUnitTest/index.html
```

**Current Test Status:**
- **Unit Tests:** 20/21 passing (95%)
- **Integration Tests:** 100% passing (Moved to `androidTest` for Room/Context support)
- **Coverage:** 82% (core domain logic)

---

## 📊 Performance Metrics

| Metric | Target | Achieved | Status |
|--------|--------|----------|--------|
| Discovery Time | <10s | 4.5s avg | ✅ Exceeds (55% faster) |
| Distance Accuracy | ±15m | ±0.6m | ✅ Exceeds (97% better) |
| Battery Consumption | <5%/hr | 4.2%/hr | ✅ Meets |
| Cross-Device Success | >90% | 100% | ✅ Exceeds |
| RSSI Stability | N/A | 68% variance reduction | ✅ Bonus |

**Test Environment:** TU Dublin Grangegorman campus (outdoor)  
**Test Devices:** Nothing Phone 2a (API 35), Samsung Note 10+ (API 31)

---

## Project Structure
```
app/src/main/java/com/fyp/crowdlink/
├── data/                           # Data layer
│   ├── ble/                        # Bluetooth implementation
│   ├── local/                      # Room database (Friends/Messages)
│   └── p2p/                        # WiFi Direct implementation
├── domain/                         # Business logic
│   ├── model/                      # Domain models (Friend, Message, etc.)
│   ├── repository/                 # Repository interfaces
│   └── usecase/                    # Use cases (EstimateDistance, SendMessage)
├── presentation/                   # UI layer
│   ├── discovery/                  # Nearby device list
│   ├── friends/                    # Paired friends list
│   ├── pairing/                    # QR Scan & Generate
│   ├── chat/                       # Messaging UI
│   └── MainActivity.kt
├── di/                             # Dependency injection
└── CrowdLinkApplication.kt         # Application class
```

---

## Research & Validation

### Survey Data (42 Respondents, Ages 18-35)
- 85.7% experience friend separation at events
- 64.3% lose contact >10 minutes
- 88.1% would use offline solution
- 69% abandon apps due to battery drain
- 85.7% require ±20m accuracy (CrowdLink: ±0.6m)

### Comparative Analysis
| Solution | Infrastructure | Privacy | Offline | Range |
|----------|---------------|---------|---------|-------|
| **CrowdLink** | ❌ None | ✅ P2P | ✅ Yes | ~30m |
| Apple Find My | ✅ Required | ⚠️ Apple servers | ⚠️ Partial | ~10m |
| Totem Compass | ✅ Required | ⚠️ Centralized | ❌ No | ~100m |
| Cellular/SMS | ✅ Required | ⚠️ Carrier logs | ❌ No | Unlimited* |

*Fails in crowds (the exact problem CrowdLink solves)

---

## Documentation

- **[Interim Report](docs/Interim_Report.pdf)** - Academic submission (90 pages)
- **[Project Proposal](docs/Project_Proposal.pdf)** - Initial planning
- **[Survey Results](docs/Survey_Analysis.pdf)** - User research data
- **[API Documentation](docs/api/)** - Code documentation (generated)

---

## Contributing

This is an academic Final Year Project (FYP) and is **not open for external contributions** during the assessment period (November 2024 - March 2025).

**After project submission (April 2025):**
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

**Academic Use:** This project is submitted as part of TU856 BSc (Hons) in Computer Science at TU Dublin. Please cite appropriately if referencing this work.

---

## Author

**Fionán Ó Ceallaigh**
- Student ID: C22337521
- Institution: TU Dublin
- Supervisor: Bryan Duggan
- Program: TU856 - Computer Science (Final Year)
- Academic Year: 2025/2026

---

## Acknowledgements

- **Bryan Duggan** - Project supervisor for guidance and feedback
- **Survey Participants** - 42 respondents who validated the problem statement
- **TU Dublin** - Resources and facilities for development and testing
- **AI Tools** - ChatGPT and Claude for debugging assistance and code review (documented in Appendix C)

---

## Known Issues

- [ ] 16KB page size warning for Android 15+ (does not affect functionality)
- [ ] API 28 devices occasionally require BLE toggle (80-90% success vs 100% on API 31+)

See [Issues](https://github.com/fionat33/CrowdLink/issues) for full list.

---

## Contact

For academic inquiries: c22337521@mytudublin.ie

**Note:** This is a university project under active development. Features and documentation will evolve throughout the academic term.

---

## Project Metrics

![Lines of Code](https://img.shields.io/badge/lines%20of%20code-~1200-blue)
![Test Coverage](https://img.shields.io/badge/coverage-82%25-green)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Version](https://img.shields.io/badge/version-0.6.0--alpha-blue)

---

<p align="center">
  <sub>Fionán Ó Ceallaigh | Final Year Project | TU Dublin 2025/2026</sub>
</p>
