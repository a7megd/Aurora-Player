# Whisper Player iOS host

The iOS host is intentionally thin: SwiftUI creates the application window and embeds the
Kotlin/Compose Multiplatform controller exported by `composeApp`. The application UI and
domain logic belong in the shared Kotlin project, not in a separately maintained Swift UI.

## Current status

- Shared Compose entry point: `composeApp/src/iosMain/.../MainViewController.kt`
- SwiftUI bridge: `WhisperPlayer/WhisperPlayerApp.swift`
- Framework name: `WhisperPlayerUI`
- iOS targets: device arm64, simulator arm64 and simulator x64.

The Xcode project, signing, entitlements, privacy usage descriptions, and capability declarations
are release-integration work and are not represented as complete yet. Do not claim that an IPA
can be installed until an Xcode archive is built and signed.

## Build the shared iOS framework

From the repository root on macOS with Xcode installed:

```bash
gradle -p cross-platform :composeApp:linkDebugFrameworkIosSimulatorArm64
```

To integrate the framework into an Xcode app target, add the `WhisperPlayer` Swift source,
link the generated `WhisperPlayerUI.framework`, and configure the Gradle
`embedAndSignAppleFrameworkForXcode` build phase for the selected Xcode configuration.

## Required before distribution

- Create the Xcode application target and configure its bundle identifier.
- Add background audio mode only after the AVFoundation playback adapter is implemented.
- Add microphone and media-library privacy descriptions only for capabilities actually used.
- Configure APNs/Push Notifications only after the iOS notification adapter exists.
- Configure Apple signing/provisioning for device installation and App Store distribution.
