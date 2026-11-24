# Whisper Player cross-platform feature parity

This is the acceptance checklist for replacing the Android-only implementation with one shared Kotlin/Compose application. A shared interface or placeholder screen is **not** counted as a completed feature. Each row is complete only when the shared UI is connected to real Android and iOS adapters and has platform tests.

## Status definitions

- **Contract only**: the shared API/model exists; there is no end-to-end implementation yet.
- **Android legacy**: the current Android implementation exists in `overrides/`, but has not been migrated to the shared application.
- **Not started on iOS**: an iOS implementation and device/simulator validation are still required.
- **Acceptance**: functional tests on both platforms, including lifecycle/background behavior where relevant.

## Feature matrix

| Feature | Existing Android source / evidence | Shared layer | iOS status | Acceptance requirement |
|---|---|---|---|---|
| Local audio library and scan | `MediaStoreScanner.kt`, `LibraryRepository.kt` | `LocalMediaLibrary` contract, `Track` model | Not started on iOS | Permission states, scan, refresh, imported files and metadata |
| Playback, queue and repeat | `PlaybackService.kt`, `PlaybackConnection.kt` | `PlaybackController`, `PlaybackQueue` with tested next/previous, repeat-one/all/off, and adapter-supplied shuffle order | Not started on iOS | Connect queue navigation to Android/iOS player adapters; test play/pause/seek, repeat, shuffle, speed and lifecycle |
| Background audio and system controls | `PlaybackService.kt` | `PlaybackController` contract | Not started on iOS | Lock-screen controls, audio focus/session, interruption and route changes |
| Now playing UI | `NowPlayingScreen.kt` | Compose shell only | Not started on iOS | Artwork, progress, transport, queue, lyrics entry and responsive layouts |
| Playlists and current queue | Android screens in `WhisperScreens.kt` | `Playlist` with tested insert/remove/reorder/rename operations; `LibraryStateStore` contract | Not started on iOS | Connect shared operations to persistent Android/iOS storage and UI; test create/edit/delete, reorder, persist and restore |
| Favorites and playback history | Android library state | `LibraryStateStore`; play count and last-played fields | Not started on iOS | Persist favorites, counts and recent-played timestamps |
| Sorting | Android library UI | `SongSort` includes title/artist/album/date/recent/most-played/duration | Not started on iOS | Ascending/descending and stable ordering |
| Synchronized lyrics | Android player UI | `LrcParser` and tests | Not started on iOS | LRC parsing, seek synchronization and fallback for missing lyrics |
| Recording | Android recording UI/service | `AudioRecorder` contract | Not started on iOS | Permission, record/pause/resume/stop, saved file and interruption |
| Video-to-MP3 conversion | `MediaToolsScreens.kt`, `WhisperMediaConverter.kt`, `Lame.java` | `VideoToAudioConverter` contract | Not started on iOS | Local file picker, conversion progress, supported formats, history and output |
| File manager and metadata | Android media screens | `MediaFileManager` contract | Not started on iOS | Rename/delete and metadata edits within OS sandbox/permission limits |
| Authentication | `AuthScreens.kt` | `AccountService` contract | Not started on iOS | Sign in/out, session restore and error handling |
| Community, chat and blocking | `SocialScreens.kt`, `FirebaseCommunity.kt` | `CommunityService` contract | Not started on iOS | Messages, unread/read, archive, block/unblock and security rules |
| Cloud uploads | `CloudinaryMediaUploader.kt` | `MediaCloudUploader` contract | Not started on iOS | Secure upload configuration, progress, failures and free-tier limits |
| Notifications | `WhisperMessagingService.kt` | `LocalNotificationService` contract | Not started on iOS | Permission, delivery and deep links |
| Themes and preferences | Android settings UI | `AppSettings`, `SettingsStore` | Not started on iOS | Persist theme, AMOLED/accent color, sorting and playback preferences |
| Audio effects/output routing | `AudioOutputRouteCard.kt` and player settings | `AudioEffectsController`, `PlatformCapabilities` | Not started on iOS | Expose only OS-supported controls; explain unavailable system features |
| YouTube share routing | Android share-intent handler | Not yet integrated | Not started on iOS | Open YouTube URLs in YouTube/browser; do not extract streams |
| App navigation and responsive UI | Android legacy app root | Compose Multiplatform placeholder shell only | SwiftUI host bridge only | All real feature screens, Arabic/RTL, accessibility and phone/tablet layouts |

## Build and release gates

1. Shared-core unit tests pass.
2. Compose Android and iOS Simulator frameworks compile in CI.
3. Android adapters and iOS adapters pass their platform tests.
4. Every feature above is exercised end-to-end on both platforms, or documented as an OS limitation with an equivalent user flow.
5. Android release APK and iOS archive are built from the same shared UI/domain revision.
6. Install and smoke-test on a real Android device and a real iPhone before calling the app complete.

## Current verified state (2026-11-24)

- The shared-core and Compose Android UI CI job passed in workflow run [45461154591](https://github.com/a7megd/Aurora-Player/actions/runs/45461154591).
- The iOS job in that run was still queued when this status was recorded.
- Shared models, LRC parser, queue navigation/repeat behavior, playlist editing operations and platform service contracts are domain foundations; they are not yet connected end-to-end to Android/iOS UI or platform playback adapters.
- The shared Compose UI is only a navigation/placeholder shell.
- No installable iOS IPA, completed Xcode project/signing setup, or cross-platform release has been produced.
- The existing Android implementation remains separate and must not be removed until all acceptance gates pass.
