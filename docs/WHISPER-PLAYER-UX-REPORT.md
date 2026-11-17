# Whisper Player — UX and Feature Specification
## Reference and design direction
This document studies Spotify's mobile listening experience as a usability reference, then defines an original Whisper Player design. It does not reuse Spotify's logo, proprietary artwork, or exact branded assets.

## Visual system
- Dark-first canvas: #121212; elevated surfaces #181818 and #202020.
- Main text white; secondary text muted gray.
- Whisper identity accent: electric blue, inspired by the user's handwritten mark (not Spotify green).
- Rounded album/playlist cards, strong cover-art hierarchy, consistent 8/12/16/24 dp spacing, readable typography, clear touch targets.
- Theme options: system, dark, light, and AMOLED black.
- Adaptive layouts for portrait, landscape, and tablets; Arabic RTL and English LTR.

## Information architecture
1. Home: greeting, recently added/played, quick access, curated sections based on local library data.
2. Search: instant local search across track and artist metadata, recent searches, filters.
3. Library: songs, albums, artists, folders, favorites, recently played, playlists.
4. Playlist details: cover, title, track count, play/shuffle, add/remove/reorder tracks, sort.
5. Now Playing: artwork, title/artist, seek, previous/play/next, shuffle, repeat, favorite, queue, lyrics.
6. Queue: current/next tracks, reorder, remove, clear, save as playlist.
7. Lyrics: manual text and LRC import, synchronized line highlighting when timestamps exist.
8. Equalizer and playback settings: bands/presets, bass enhancement where supported, playback speed, sleep timer, gapless, crossfade where supported.
9. Settings: theme, accent, AMOLED, language, library scan, permissions, audio behavior, about.

## Feature specification
### Library and playlists
- Create playlists with a user-selected subset of local tracks.
- Add/remove multiple tracks, rename/delete playlists, reorder tracks, playlist folders.
- Favorites, recently played, sorting, filtering, duplicate detection, folder exclusions.
- Persist playlists and playback history across restarts.

### Playback
- Background playback through Media3 service and media notification.
- Queue management, seek, shuffle, repeat-one/repeat-all, restore last session.
- Playback speed and sleep timer.
- Crossfade and gapless only when implementation and device audio pipeline support them.

### Lyrics
- Store manual lyrics per track.
- Import .lrc and synchronize current line using timestamps.
- Automatic online lyrics are not assumed; they require a reliable, licensed provider and network integration.

### Search and discovery
- Search local track/artist/album/folder metadata.
- Search filters and recent queries.
- "Recommendations" must be based on local listening history or clearly labeled as local suggestions; no claim of Spotify-like cloud recommendations.

### Accessibility and localization
- Arabic/English, RTL-aware layouts, content descriptions, scalable text, touch targets, contrast.
- Empty/loading/error states and permission explanations.

## Delivery and validation phases
- Phase A: original brand, launcher icon, home/search/navigation, playlists, queue, lyrics editor, appearance settings.
- Phase B: favorites/history persistence, playlist editing/reordering, robust search filters, queue editing and save-to-playlist.
- Phase C: synchronized LRC, equalizer controls, sleep timer, playback speed, gapless/crossfade where supported.
- Phase D: accessibility, RTL, tablet/landscape adaptation, performance and device QA.

## Current implementation notes
The initial implementation branch already contains Media3 playback, library browsing, mini-player, playlist/queue/lyrics/settings screens and theme settings. The latest changes add individual song selection when creating playlists, direct navigation to Search/Queue/Lyrics/Equalizer/Settings, a local-library Home and Search, and a vector launcher mark inspired by the supplied signature. The Equalizer route and advanced playback options still require real implementation and device testing; lyrics are manually entered unless an LRC importer is added. A successful Gradle build proves compilation, not full device QA.
