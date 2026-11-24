# Whisper Player — Android + iOS single-codebase migration

Status: discovery and architecture baseline  
Base Android revision: `6340b296e44731f13308cfc7d052f169dd847599`  
Migration branch: `codex/whisper-player-cross-platform-2026-11`

## Product requirement

Deliver one Whisper Player product from one Kotlin codebase, with shared screens, navigation, design system, domain rules, data models, and feature behavior on Android and iOS. Platform-specific implementations are allowed behind shared interfaces where the operating systems require different APIs. Do not remove an existing feature as a shortcut. Do not replace a working Android implementation until its replacement passes the existing Android build and acceptance checks.

## Architecture decision

Use Kotlin Multiplatform (KMP) with Compose Multiplatform for the shared application UI and shared Kotlin logic.

- `composeApp/commonMain`: shared Compose UI, navigation, view models/state holders, domain models, validation, sorting, playlist/queue rules, lyrics parsing, and platform-neutral use cases.
- `composeApp/androidMain`: Android Media3 playback/session/service, MediaStore and Storage Access Framework, Android recording, Android audio effects/output routing, Android notifications, and Android-specific permissions/intents.
- `composeApp/iosMain`: AVFoundation playback/recording/conversion, AVAudioSession and remote controls, Files/document picker, iOS audio route handling, background audio configuration, and iOS notification/APNs integration.
- `shared/data`: shared persistence contracts and portable repositories. Select KMP-compatible storage (for example SQLDelight) after schema/data migration review; do not assume Android Room or DataStore can run on iOS.
- Firebase and Cloudinary are behind shared service interfaces. Preserve auth, community, messaging, upload and media metadata behavior; use native platform SDK bindings or supported KMP clients with equivalent security and lifecycle behavior.
- Keep platform-specific code out of `commonMain`; expose capabilities through explicit interfaces and `expect/actual` only where appropriate.

This is one product and one shared UI/codebase, not two separately maintained apps. Native system adapters remain necessary.

## Source audit

The build workflow expands `AuroraPlayer-stage3-continuation.zip` and overlays the maintained Kotlin files. The expanded source bundle from Android CI was inspected before planning this migration.

Current project is a single Android Gradle module using Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Jetpack Compose, Media3 1.4.1, Room 2.6.1, DataStore 1.1.1, Firebase Android SDK, Coil, and native LAME libraries.

Major Android-coupled areas found:

- App lifecycle/bootstrap: `MainActivity.kt`, `MusicApp.kt`, `AppRoot.kt`, `Theme.kt`.
- Playback/session: `PlaybackService.kt`, `PlaybackConnection.kt`, `NowPlayingScreen.kt`; Media3, Android service, notification/session and intents.
- Local library: `MediaStoreScanner.kt`, `LibraryRepository.kt`, `AppDatabase.kt`, `Daos.kt`, `Entities.kt`, `SettingsRepository.kt`; MediaStore, ContentResolver, ContentObserver, Room, DataStore, Android Context.
- Conversion: `WhisperMediaConverter.kt`, `Lame.java`; MediaExtractor, MediaCodec, MediaFormat, MediaStore and native LAME.
- Recording: `ArticleScreens.kt`, `SocialScreens.kt`; MediaRecorder/MediaPlayer and runtime permissions.
- Audio routing/effects: `AudioOutputRouteCard.kt`, `WhisperScreens.kt`; AudioManager, AudioDeviceCallback, Android Equalizer.
- Media tools: `MediaToolsScreens.kt`, `WhisperScreens.kt`; ContentResolver, MediaStore, SAF document launchers, Android intents.
- Cloud upload: `CloudinaryMediaUploader.kt`; Android Context/Uri and MIME resolution.
- Community/auth: `FirebaseCommunity.kt`, `AuthScreens.kt`, `SocialScreens.kt`; Firebase Auth/Firestore Android SDK.
- Push: `WhisperMessagingService.kt`; FirebaseMessagingService and Android notifications.
- Shared-leaning UI: library, playlist, queue, lyrics, search, article, profile, settings and navigation Compose screens. Many are Compose-based but currently import Android APIs directly and need dependency inversion before moving to commonMain.

## Feature parity register

| Feature | Shared behavior/UI | Android adapter | iOS adapter / parity note |
|---|---|---|---|
| Home, search, library navigation, themes, Arabic/RTL | Shared Compose UI and state | Existing Android implementation | Compose Multiplatform UI; verify RTL, dynamic type and accessibility |
| Local audio library, artists/albums/folders | Shared metadata model and sorting | MediaStore + SAF | Files picker and optionally MusicKit/MediaPlayer library authorization; iOS cannot freely scan arbitrary device storage like Android |
| Playback, queue, repeat, shuffle, seek, speed, resume | Shared playback contract and queue rules | Media3 ExoPlayer + MediaSessionService | AVPlayer/AVQueuePlayer + AVAudioSession + remote command center |
| Background playback, lock screen, headset/Bluetooth | Shared playback state and actions | Foreground service, media notification, MediaSession | iOS audio background mode, Now Playing metadata and remote commands; OS-controlled behavior |
| Playlists, favorites, history, sort | Shared models/use cases | Room/DataStore today | KMP-compatible persistence; import/export via Files |
| Lyrics (manual, TXT/LRC, timed highlighting) | Shared parser, storage model and UI | Existing Android file picker/storage | iOS document picker and shared parser |
| Video-to-audio MP3 conversion | Shared conversion workflow/progress/errors | MediaCodec/MediaExtractor + LAME | AVFoundation decode + compatible MP3 encoder; validate codec/container support and large-file memory |
| Recording and playback of recordings | Shared recording UI/state | MediaRecorder/MediaPlayer | AVAudioRecorder/AVAudioPlayer; microphone permission and background limits |
| Equalizer, bass/output settings | Shared settings/presets UI | Android audiofx / route APIs | AVAudioEngine processing where supported; iOS does not expose a system-wide app EQ or identical route controls |
| Rename/delete/edit media metadata | Shared user flow and metadata model | MediaStore write requests and SAF | Files-provider scoped access; imported files can be managed only within granted URLs/app sandbox, not arbitrary library-wide files |
| Auth, community, chat, block, articles | Shared feature models/use cases | Firebase Auth/Firestore Android SDK | Firebase Apple SDK or verified KMP client; rules and auth semantics must remain equivalent |
| Push notifications | Shared notification intent model | FCM service + Android notification channels | APNs/FCM on iOS with entitlements, permission and provisioning |
| Cloudinary uploads | Shared upload contract/progress | Existing uploader | Ktor/native URLSession implementation; never embed an API secret in the client |

## Non-negotiable platform constraints

1. iOS sandboxing does not provide Android-style unrestricted access to every local audio file. The iOS app must use user-selected files and/or a separately authorized Apple media-library integration. The UI must explain the available sources clearly.
2. iOS does not permit arbitrary background work or Android-style foreground services. Background audio is supported through the proper audio session/background mode; conversion and uploads must respect iOS execution limits.
3. iOS audio effects and route controls are not identical to Android's AudioEffect/AudioManager APIs. Provide equivalent user-facing behavior only where the iOS audio graph and public APIs support it; never show a fake enabled control.
4. Push notifications require Apple Developer configuration, APNs capability/keys and provisioning for distribution. A simulator build alone does not enable production push.
5. App Store distribution requires signing/provisioning and Apple Developer account access. An unsigned simulator/device build can be prepared before distribution setup, but it is not an App Store release.

These constraints must be handled with transparent platform-aware UI, not by silently deleting features or pretending the APIs are identical.

## Migration sequence and gates

### Phase 0 — Baseline and parity inventory
- Freeze the Android source revision and preserve the current Android CI path.
- Maintain this feature register and add acceptance cases for every current screen/action.
- Capture current schemas, preference keys, playback state and Firebase data contracts before moving persistence.

### Phase 1 — KMP skeleton and shared core
- Add KMP/Compose Multiplatform Gradle modules and Android/iOS targets on this branch only.
- Move pure domain models, sorting, playlist/queue rules, lyric parsing and validation into commonMain.
- Add common interfaces for playback, media library, recording, conversion, persistence, uploads, auth/community and notifications.
- Keep the existing Android app buildable throughout this phase.

### Phase 2 — Shared UI
- Move theme tokens, navigation and platform-neutral Compose screens to commonMain.
- Replace direct Android Context, ActivityResultLauncher, Android Uri, MediaStore, Media3 and Firebase calls in composables with injected state/actions.
- Keep Android-specific pickers and dialogs in androidMain adapters.

### Phase 3 — iOS adapters
- Implement AVFoundation playback/recording/conversion, Files import/export, persistence, audio-session/remote controls and notification integration.
- Implement Firebase/Cloudinary integrations behind the shared contracts.
- Add iOS permission and capability explanations.

### Phase 4 — Feature parity and migration
- Verify every row in the feature parity register on Android and iOS.
- Migrate user data carefully; retain stable playlist IDs, favorites, settings, lyrics, and playback history where the underlying source is portable.
- Do not remove or overwrite the existing Android release workflow until the new shared project produces a verified Android APK/AAB.

### Phase 5 — CI and release
- Android CI: unit tests + debug APK + release bundle.
- iOS CI: macOS runner + simulator build/tests; signed device/App Store archive only after signing configuration is supplied.
- Keep secrets out of source and client binaries. Use GitHub Actions secrets only for CI configuration and server-side secrets only on trusted backends.

## Acceptance gate

A feature is not considered migrated because it compiles. It must pass its platform-specific acceptance tests, including background/lock-screen playback, queue restoration, Bluetooth/headset behavior, file permissions, long media conversion, recording, offline local playback, Arabic RTL, accessibility, auth/community permissions, uploads, and process/app lifecycle. Device behavior must be tested on real Android and iOS hardware before declaring parity.
