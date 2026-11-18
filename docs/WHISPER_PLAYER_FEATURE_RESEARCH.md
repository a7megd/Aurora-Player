# Whisper Player — Android Music Player Product & Engineering Reference

Research snapshot: 18 November 2026  
Purpose: practical product and engineering reference for evolving Whisper Player into a polished Android-first music player. This is a roadmap, not a claim that every capability is already implemented.

## 1. Executive direction

The strongest modern players combine dependable playback, excellent library and queue management, meaningful sound controls, and a coherent interface. Poweramp is a benchmark for DSP and playback controls; Musicolet for offline-first queues and library tools; AIMP for format breadth and playlist utilities; Symfonium for multi-source libraries, smart playlists, caching, and advanced output. Prioritize a stable local-music experience before adding network services or specialist audiophile features.

Principles:
- Offline-first: local files work without sign-in or internet.
- Playback is authoritative: UI always reflects the actual Media3 player/session.
- Fast and predictable: queue changes, search, sorting, and navigation do not block the main thread.
- Transparent permissions; accessible Arabic/RTL, scalable text, contrast, and reduced motion.
- No fake integrations: third-party services require official APIs, OAuth, and provider permission.

## 2. Benchmark feature inventory

| Area | Mature-player capabilities | Whisper Player recommendation |
|---|---|---|
| Playback | Gapless, crossfade, queue, repeat, shuffle, seek, resume | Core: reliable queue, gapless where supported, repeat/shuffle, resume, sleep timer |
| Library | Artists, albums, folders, genres, search, filters, sorting | Core: fast indexed library, folder view, sort by title/artist/album/date/plays/duration |
| Playlists | Multiple queues, manual reorder, smart playlists, M3U import/export | Core: named playlists, favorites, save queue, import/export; smart rules next |
| Audio | Graphic/parametric EQ, presets, bass/treble, balance, ReplayGain | Core: functional session-bound EQ where supported; presets and per-output settings |
| Lyrics | Embedded/plain/synchronized LRC, current-line highlighting | Core: embedded and local LRC; online provider only through compliant API |
| Metadata | Tag editor, album art, file details | Core: safe tag editing for supported formats, artwork, library refresh |
| UI | Material 3, mini-player, now-playing, themes, gestures | Core: coherent design system, translucent queue cards, animated active track |
| System | MediaSession, notification, lock screen, headset/Bluetooth, Android Auto | Core: correct session and foreground playback; Auto after validation |
| Output | Chromecast, DLNA, USB DAC, hi-res/DSD, bit-perfect | Later: only with supported libraries, device APIs, and test hardware |
| Personalization | Themes, accent colors, widgets, layouts | Core: light/dark/AMOLED, curated themes, widget controls |
| Discovery | Artist info, recommendations, radio | Optional: local listening history and on-device mixes before online discovery |
| Audiobooks | Bookmarks, resume points, silence skip, speed | Later: separate content type and resume model |

Benchmarks:
- Poweramp Google Play feature list: https://play.google.com/store/apps/details?id=com.maxmpz.audioplayer
- Musicolet / Android Authority overview: https://www.androidauthority.com/best-music-player-apps-for-android-208990/
- AIMP Google Play feature list: https://play.google.com/store/apps/details?id=com.aimp.player
- Symfonium official feature overview: https://www.symfonium.app/

## 3. Playback architecture — required foundation

Use AndroidX Media3 ExoPlayer behind a long-lived playback service and MediaSession. UI screens attach through MediaController and observe Player.Listener/state; they must not create independent players. Keep player mutations on its application thread.

Checklist:
1. One ExoPlayer owned by a MediaSessionService (or the existing equivalent).
2. MediaSession exposes metadata, queue, transport controls, repeat/shuffle, and stable media IDs.
3. Foreground notification and audio focus behavior are correct.
4. Handle audio becoming noisy; pause when headphones disconnect according to settings.
5. Persist last media ID, queue, current index, and playback position; restore safely after process death.
6. Observe player callbacks rather than polling UI state every few hundred milliseconds.
7. Handle audio focus loss, transient ducking, Bluetooth route changes, and headset disconnects.
8. Release controllers and service resources correctly.
9. Test screen-off, background, process recreation, Bluetooth, wired headset, calls, and competing audio.

Official references:
- Media3 player app: https://developer.android.com/media/implement/playback-app
- MediaSession playback control and playlist metadata: https://developer.android.com/media/media3/session/control-playback
- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3
- Media3 1.11 announcement: https://developer.android.com/blog/posts/media3-1-11-whats-new

## 4. Queue UX and long-press reorder

The queue is a primary interaction. Keep the active track visibly distinct and make queue changes immediate.

Required behavior:
- Long-press a row to lift it; show elevation, scale, outline, and drag handle.
- Drag up/down to reorder. The row follows the finger; nearest visible row becomes insertion target.
- Animate displaced rows. Auto-scroll near top/bottom edges.
- Commit with Media3 Player.moveMediaItem(from, to), preserving current item and playback position.
- Cancel restores original order; a simple tap must not reorder.
- Keep play-on-tap and remove/add-after-current separate from the drag gesture.
- Use stable unique queue-entry keys. Media IDs alone may not be unique when a song appears twice.
- Provide TalkBack alternatives (accessible move actions or overflow controls) without cluttering the normal layout.
- Respect reduced-motion preferences.

Test first-to-last, last-to-first, adjacent moves, long lists, duplicate entries, current-track reorder, cancel, auto-scroll, RTL, and large text.

## 5. Library and playlist capabilities

Library navigation:
- Songs, Albums, Artists, Folders, Genres, Favorites, Recently Added, Most Played, and Playlists.
- Search title, artist, album, album artist, genre, and filename; debounce and preserve search state.
- Sorting by title, artist, album, album artist, date added, duration, filename, play count, last played; ascending/descending.
- Folder browsing and user-selected scan locations through Android Storage Access Framework where appropriate.
- Incremental indexing, cancellation, and clear empty/error states.

Playlists:
- Create, rename, duplicate, delete, reorder, add/remove tracks.
- Save current queue as playlist and restore it.
- Import/export M3U/M3U8 with URI/path handling and missing-file reporting.
- Smart playlists based on genre, rating, play count, last played, year, and duration.
- Multiple queues are distinct from named playlists.

## 6. Audio and sound processing

Stage safely:
- Verify the equalizer is attached to the active Media3 audio session ID. A slider that only changes UI state is not an equalizer.
- Check effect availability/enabled state; release effects when audio session changes or playback ends.
- Provide bypass, presets, bands, preamp where supported, and reset.
- Bass boost and virtualizer are device-dependent optional effects.
- Per-output profiles (speaker, wired, Bluetooth) require reliable route detection.
- ReplayGain and peak normalization need correct metadata parsing and defined precedence; avoid clipping.
- Test gapless playback with real consecutive files and supported formats.
- Crossfade is separate transition logic and must be tested with gapless/repeat/shuffle.
- Hi-res, DSD, bit-perfect, USB-exclusive output and 64-bit DSP require supported decoders/output paths and hardware validation; they are not simple UI toggles.

## 7. Lyrics and metadata

- Display embedded lyrics when present.
- Support local LRC with timestamp parsing, multiple timestamps per line, offsets, and synchronized highlighting.
- Keep lyric scrolling synchronized without excessive recomposition.
- Allow manual lyric-file selection and clear errors.
- Tag editing must use format-aware libraries and Android scoped-storage/SAF write permissions.
- Cache artwork with size limits; prefer embedded art before remote art.
- Do not scrape lyrics/art from sites without a permitted API or license.

## 8. System integration

- MediaStyle notification with artwork, title/artist, play/pause, previous/next, and correct stop behavior.
- Lock-screen metadata and controls through MediaSession.
- Headset, Bluetooth buttons, Android Auto browse/playback, and output switcher use the same session.
- Home-screen widget shows track and controls. Continuous live spectrum animation is not guaranteed by standard widgets; respect update and battery limits.
- Android Auto needs a compliant browsable media service and real-device validation.
- Casting, DLNA, and cloud providers are separate integrations with distinct protocols and permissions.

## 9. Privacy and service integrations

- Local playback should not need network access unless an online feature is explicitly enabled.
- Spotify playback and offline downloads are governed by official SDK/API terms; protected offline downloads cannot be exported as ordinary audio files or played by an unrelated player.
- Any service integration requires official developer access, OAuth, secure token storage, revocation, and compliant scopes. Phone-number login alone is not API authorization.
- Clearly explain what stays local and what leaves the device.

## 10. Accessibility, performance, and quality

- Comfortable touch targets, semantic labels, TalkBack order, contrast, RTL, scalable typography.
- Lazy lists with stable keys, immutable UI models, no heavy file I/O on main thread.
- Avoid continuous polling. Observe player callbacks and lifecycle-aware state.
- Cache artwork/metadata; cancel stale search and scan jobs.
- Handle 10k–50k track libraries with paging or incremental database queries.
- Avoid animation overload and offer reduced motion.
- Test low-memory devices, process death, permission revocation, malformed tags, missing files, duplicate tracks, and storage removal.

## 11. Prioritized delivery roadmap

### P0 — Reliability
Playback service/session, notification, background playback, audio focus; queue correctness, stable IDs, long-press reorder, duplicate entries; persistence/restore; crash-free scanning; functional equalizer lifecycle.

### P1 — Daily-use essentials
Library sort/filter/search, favorites, playlist CRUD, save/restore queue; local synchronized lyrics, sleep timer, recently added/most played; themes, now-playing polish, mini-player, accessible controls.

### P2 — Advanced local-player features
M3U import/export, smart playlists, tag editor, ReplayGain, gapless/crossfade; Android Auto, home widget, per-output EQ profiles, richer formats where supported.

### P3 — Specialist/network features
NAS/cloud providers, DLNA/casting, USB DAC/DSD/bit-perfect, audiobook mode, radio; online lyrics/art/discovery only with approved APIs and opt-in.

## 12. Acceptance test matrix

| Scenario | Expected result |
|---|---|
| App backgrounded / screen off | Playback continues; notification and lock-screen controls work |
| Bluetooth disconnects | Audio-noisy policy applied; no crash |
| Incoming call / another app audio | Correct audio focus behavior |
| Navigate between screens | Playback and queue remain stable |
| Kill process and reopen | Queue and position restore where possible |
| Reorder a playing queue item | Same track continues from same position |
| Duplicate track in queue | Each entry independently movable/removable |
| Empty/revoked media permission | Clear recovery UI; no crash |
| 20k+ local tracks | Search and scrolling remain responsive |
| Missing/corrupt audio file | Graceful skip/report |
| Arabic RTL + large text | Layout and gestures remain usable |
| Equalizer session changes | Effect reattached or safely disabled/released |

## 13. Engineering decision log

- Prefer Media3 APIs over custom playback internals unless a measured gap requires otherwise.
- Do not upgrade Media3 blindly: check compile SDK, Kotlin/AGP compatibility, and release notes; upgrade in a dedicated tested change.
- Separate UI, playback/session, library/index, metadata, and settings responsibilities.
- Every feature needs acceptance criteria and must be verified on a physical Android device where hardware behavior matters.
- Successful CI packaging proves compilation, not real-device audio quality or every interaction.
