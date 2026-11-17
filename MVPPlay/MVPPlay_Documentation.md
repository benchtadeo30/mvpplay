# MVPPlay: Offline-First Music & Podcast Player
## Project Documentation Paper

---

## Chapter 1: Introduction and Project Context

### 1.1 Project Background (Statement of the Problem)

#### 1.1.1 General Problem
In the modern digital landscape, music and podcast consumption has shifted predominantly toward cloud-based streaming services (Spotify, Apple Music, YouTube Music). While these platforms offer vast libraries, they present several critical limitations for users: (1) mandatory internet connectivity for playback, (2) subscription costs for offline access, (3) data privacy concerns with listening habits being tracked, (4) lack of support for personal high-resolution audio files (FLAC, WAV, 24-bit/192kHz), and (5) vendor lock-in preventing users from truly owning their media library. 

For students, developers, and audiophiles in regions with limited or expensive internet connectivity, or for users who prefer local-first architectures, there is a need for a lightweight, privacy-respecting, offline-capable media player that supports high-fidelity audio formats and provides modern playback features without requiring a persistent network connection.

#### 1.1.2 Specific Problems
1. **No Offline-First Android Media Player with Modern UX**: Existing local players (e.g., Musicolet, Phonograph, BlackPlayer) either lack modern Material Design 3 interfaces, have limited playlist management, or do not support video podcasts and Picture-in-Picture (PiP) playback.
2. **High-Resolution Audio Format Support Gap**: Most Android media players rely on `MediaPlayer` or basic `ExoPlayer` implementations that downsample high-resolution audio (24-bit/192kHz FLAC) to 16-bit/48kHz, losing the fidelity that audiophiles require.
3. **Absence of Integrated Podcast & Video Support in Local Players**: Local media players typically treat podcasts as regular audio files, lacking episode descriptions, video playback, variable speed control, and show-level organization.
4. **Limited Playlist Management**: Users cannot easily create, rename, reorder, or share custom playlists; official smart playlists (shuffle mix, duration-based filters) are rarely available offline.
5. **No Cross-Session Resume with Persistent State**: Most local players lose playback position when the app is swiped away or the device restarts, requiring manual seeking.

---

### 1.2 Project Objectives

#### 1.2.1 General Objective
To design and develop **MVPPlay**, an offline-first, privacy-respecting Android music and podcast player that delivers a modern, high-fidelity listening experience with support for lossless audio formats, video podcasts, smart playlists, and persistent playback state—entirely without requiring network connectivity or user accounts.

#### 1.2.2 Specific Objectives
1. **Build a Local-First Media Architecture** using Android `Media3 ExoPlayer` with `MediaSessionService` for background playback, foreground service lifecycle management, and MediaController-based UI communication.
2. **Implement High-Fidelity Audio Playback** supporting FLAC, MP3, AAC, OGG, WAV with embedded metadata extraction (title, artist, duration, bitrate, sample rate, cover art) via `MediaMetadataRetriever`.
3. **Develop a Dual-Catalog System** separating music tracks from podcast episodes (auto-detected by `podcast_` filename prefix), with distinct UI presentations, playback speeds (0.75×–2× for podcasts), and video support.
4. **Create Smart & Custom Playlist Management** featuring four official playlists (All Songs, Shuffle Mix, Quick Hits <4 min, Long Plays ≥4 min) and user-created playlists persisted via JSON in `SharedPreferences`.
5. **Design a Modern Material 3 UI** with: Splash screen, Home dashboard (chip-based navigation: All/Music/Playlists/Podcasts), Library (Songs/Artists/Favorites/Podcasts), Search (real-time filtering across songs, artists, podcasts, playlists), Now Playing (waveform seek, video PiP, sleep timer, volume control), Playlist detail (hero animation, equalizer visualization), and Settings (dark mode, sort order, resume toggle, floating window).
6. **Enable Persistent Cross-Session State** including: last played file + position, favorites (heart toggle), custom playlists, user name, podcast speed, sort preference, dark mode, and floating window preference.
7. **Implement Advanced Playback Controls**: shuffle (on/off), repeat (off/all/one), sleep timer (preset durations + end-of-song), volume dialog with mute memory, Picture-in-Picture for video podcasts, fullscreen video with orientation lock.
8. **Ensure Privacy & Offline Operation**: Zero network permissions, no analytics, no accounts, no telemetry—all media bundled in `res/raw` at build time.

---

### 1.3 Scope and Limitations

#### 1.3.1 Scope
| Aspect | Coverage |
|--------|----------|
| **Platform** | Android API 29+ (Android 10) to API 37 (Android 14) |
| **Architecture** | Single-module app; `MediaSessionService` + `MediaController` pattern |
| **Media Source** | Bundled assets in `res/raw` only (no filesystem scanner, no network streaming) |
| **Audio Formats** | FLAC, MP3, AAC, OGG, WAV (via ExoPlayer decoders) |
| **Video Formats** | MP4/H.264 in `res/raw` (podcast episodes only) |
| **Metadata** | Embedded ID3/Vorbis tags + filename fallback (`artist__title` convention) |
| **Playlists** | 4 official (auto-generated) + unlimited custom (JSON in SharedPreferences) |
| **Persistence** | SharedPreferences for settings/playlists/favorites/last-played |
| **UI Framework** | Jetpack libraries: RecyclerView, ConstraintLayout, Material Components, Media3 |
| **Background Playback** | Foreground Service (mediaPlayback type) with notification controls |
| **Picture-in-Picture** | Android O+ (API 26+) for video podcasts |
| **Orientation** | Portrait default; fullscreen video follows sensor |

#### 1.3.2 Limitations
1. **No Dynamic Library Scanning**: Media files must be added to `res/raw` at compile time; the app does not scan device storage (`MediaStore`) or download content.
2. **No Network Features**: No streaming, no cloud sync, no online metadata lookup (Cover art, lyrics, artist bios).
3. **Single-Device Only**: No backup/restore, no export/import of playlists or favorites.
4. **Fixed Podcast Detection**: Relies on `podcast_` filename prefix; no RSS/Atom feed parsing.
5. **No Equalizer/DSP**: ExoPlayer's default output only; no bass boost, reverb, or parametric EQ.
6. **No Gapless Playback Guarantee**: ExoPlayer handles gapless where codec supports it, but not explicitly configured.
7. **No Lockscreen Media Controls on Android 14+**: Requires `MediaSessionService` which is implemented, but custom notification actions limited to play/pause/next/prev.
8. **Video Limited to Bundled Files**: No streaming video, no adaptive bitrate (DASH/HLS).
9. **No Multi-Window / Split-Screen Optimization**: PiP is the only multi-window mode tested.
10. **Test Coverage**: Only scaffold unit/instrumented tests provided (`ExampleUnitTest`, `ExampleInstrumentedTest`); no comprehensive test suite.

---

### 1.4 Significance of the Project
MVPPlay addresses a practical gap in the Android ecosystem: **a fully offline, high-fidelity, privacy-first media player with modern UX patterns typically reserved for cloud streaming apps**. Its significance spans:

- **Educational Value**: Demonstrates production-grade `Media3` architecture (ExoPlayer + MediaSession + MediaController), `SharedPreferences` persistence patterns, `RecyclerView` multi-view-type adapters, custom views (`WaveformView`), and Material 3 theming—all in a single Kotlin/Java codebase suitable for academic study.
- **Audiophile Utility**: Plays 24-bit/192kHz FLAC without resampling, shows actual bitrate/sample rate from file metadata.
- **Accessibility**: Supports TalkBack labels, scalable text, high-contrast dark mode, and system font scaling.
- **Privacy Advocacy**: Zero permissions beyond `WAKE_LOCK`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`—no `INTERNET`, no `READ_MEDIA_AUDIO`.
- **Developer Reference**: Clean separation of concerns (UI, PlayerConnection, Library, PlaylistStore, Settings) serves as a template for Media3-based apps.
- **Resource-Constrained Environments**: Runs entirely offline; ideal for devices without data plans, secure environments, or archival media collections.

---

## Chapter 2: System Architecture and Design

### 2.1 System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MVPPlay Architecture                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │   Activities │    │   Adapters   │    │  Data Layer  │    │  System  │  │
│  │  (UI Layer)  │◄───│  (Presentation)        │  (Models)    │    │ Services │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘    └────┬─────┘  │
│         │                   │                   │                 │        │
│  ┌──────▼───────────────────▼───────────────────▼─────────────────▼────┐   │
│  │                     PlayerConnection (Singleton)                      │   │
│  │  ┌────────────────────────────────────────────────────────────────┐  │   │
│  │  │ MediaController ←──async──► MediaSessionService (PlaybackService)│  │   │
│  │  │                              │                                  │  │   │
│  │  │                              ▼                                  │  │   │
│  │  │                      ExoPlayer (Media3)                         │  │   │
│  │  │         ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │  │   │
│  │  │         │ Audio   │  │ Video   │  │ Metadata│  │  Sleep  │     │  │   │
│  │  │         │ Pipeline│  │ Pipeline│  │ Extraction│ │ Timer   │     │  │   │
│  │  │         └─────────┘  └─────────┘  └─────────┘  └─────────┘     │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                 │                 │        │
│              ┌───────────────┘                 │                 │        │
│              ▼                                 ▼                 ▼        │
│  ┌───────────────────┐              ┌──────────────────┐ ┌───────────┐   │
│  │   SharedPreferences│              │   res/raw Assets  │ │  Android  │   │
│  │  (Settings, Playlists,           │  (Bundled Media)  │ │  Framework│   │
│  │   Favorites, LastPlayed)         │                   │ │ (PiP, FG  │   │
│  └───────────────────┘              └──────────────────┘ │  Service) │   │
│                                                            └───────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Architectural Decisions:**
1. **MediaSessionService + MediaController**: Decouples UI lifecycle from playback; enables background playback, notification controls, and PiP.
2. **Singleton PlayerConnection**: Manages `MediaController` lifecycle; provides async `connect()` callback pattern for Activities.
3. **Repository Pattern (In-Memory Caches)**: `MusicLibrary` caches scanned `Song` lists; `CustomPlaylistStore`/`FavoritesStore` persist JSON/Set to `SharedPreferences`.
4. **Adapter-Listener Contracts**: Each `RecyclerView.Adapter` exposes a functional listener interface for click/long-click/play/save actions.
5. **Resource-Based Media**: All audio/video in `res/raw`; metadata extracted at first launch via `MediaMetadataRetriever` reflection on `R.raw` fields.

---

### 2.2 System Features and Functional Modules

| Module | Class(es) | Responsibility |
|--------|-----------|----------------|
| **App Entry & DI** | `MvpApp` | Application class; no DI framework (manual singleton access) |
| **Splash Screen** | `SplashActivity` | 3s branded launch with fade-in animation; hides system bars |
| **Home Dashboard** | `MainActivity` | Chip navigation (All/Music/Playlists/Podcasts); song selection mode for playlist creation; welcome card; bottom nav |
| **Library** | `LibraryActivity` | Four tabs: Songs, Artists (grouped), Favorites, Podcasts |
| **Search** | `SearchActivity` | Real-time filter across songs, artists, podcasts, playlists |
| **Now Playing** | `NowPlayingActivity` | Full playback UI: waveform seek, cover/video, tags (format/bitrate/rate), shuffle/repeat, sleep timer, volume, PiP, fullscreen video, share, favorites, save-to-playlist |
| **Playlist Detail** | `PlaylistActivity` | Hero animation, equalizer bars, wave rings; removable songs for custom playlists; rename/delete; selection mode for new playlist |
| **Settings** | `SettingsActivity` | Dark mode, welcome card, podcast speed (5 presets), resume last song, floating window, sort order (Artist/Title/Length), clear favorites, delete playlists, user name |
| **Playback Engine** | `PlaybackService` | `MediaSessionService`; ExoPlayer with audio attributes, wake lock, noisy receiver; podcast speed application; last-position persistence |
| **Player Bridge** | `PlayerConnection` | Singleton `MediaController` manager; builds `MediaItem` list with metadata/artwork; `play()` / `start()` entry points |
| **Music Library** | `MusicLibrary` | Scans `R.raw` via reflection; separates music vs podcast by prefix; extracts metadata; assigns palette colors; caches sorted lists |
| **Playlist Library** | `PlaylistLibrary` | Generates 4 official playlists: All Songs, Shuffle Mix, Quick Hits (<4 min), Long Plays (≥4 min) |
| **Custom Playlists** | `CustomPlaylistStore` | JSON in SharedPreferences; CRUD + add/remove song + rename; case-insensitive keys; color palette by name hash |
| **Favorites** | `FavoritesStore` | StringSet in SharedPreferences; toggle + load filtered by library order |
| **Settings Persistence** | `AppSettings` | SharedPreferences wrapper: dark mode, resume, floating window, podcast speed, sort mode, volume-before-mute, last file/position |
| **Data Model** | `Song`, `Playlist`, `ArtistAdapter.Artist` | Immutable data classes; `Song` includes resId, durationMs, hasVideo, colorStart/End, hiRes flag |
| **Custom Views** | `WaveformView`, `GradientSpan` | Deterministic pseudo-waveform from resId seed; gradient text span for playlist headline |
| **Adapters** | `SongAdapter`, `PlaylistAdapter`, `PlaylistSongAdapter`, `PlaylistRowAdapter`, `ArtistAdapter`, `PodcastAdapter` | RecyclerView adapters with playback state sync, selection modes, removable items |
| **UI Helpers** | `BottomNav`, `WelcomeCard`, `PlaylistDialogs`, `SleepTimer`, `Thumbs` | Reusable components: bottom bar, time-based greeting, dialog builders, sleep timer logic, thumbnail cache |

---

### 2.3 User Interface Design and Wireframes

#### 2.3.1 Screen Flow Diagram
```
SplashActivity (3s)
    │
    ▼
MainActivity (Home) ◄──────────────────────────────────────┐
    │  Chips: [All] [Music] [Playlists] [Podcasts]          │
    │  BottomNav: Home | Library | Search | Settings        │
    ├──────────────────┬──────────────────┬─────────────────┤
    ▼                  ▼                  ▼                 ▼
LibraryActivity   SearchActivity    SettingsActivity   PlaylistActivity
 (4 tabs)           (4 sections)        (6 sections)      (from Home/Library/Search)
    │                  │                  │                    │
    │                  │                  │                    ▼
    │                  │                  │            NowPlayingActivity
    │                  │                  │                 (from any play action)
    │                  │                  │                    │
    └──────────────────┴──────────────────┴────────────────────┘
                        All activities connect to PlayerConnection
```

#### 2.3.2 Key Screen Descriptions

| Screen | Layout File | Key Components |
|--------|-------------|----------------|
| **Splash** | `activity_splash.xml` | Full-screen logo (fade-in), no system bars |
| **Home** | `activity_main.xml` | NestedScrollView → 4 sections (Songs RV, Playlists RV×2, Podcasts RV); 4 chip buttons; welcome card; bottom nav |
| **Library** | `activity_library.xml` | 4 chip tabs; single RecyclerView (swaps adapter); empty states |
| **Search** | `activity_search.xml` | EditText + clear btn; 4 labeled RecyclerViews (Songs, Artists, Podcasts, Playlists); hint text |
| **Now Playing** | `activity_now_playing.xml` | ConstraintLayout: cover/video area, waveform, tags, controls (prev/play/next, shuffle/repeat, heart/save/share/sleep/volume), description card (podcasts), fullscreen overlay, PiP button |
| **Playlist Detail** | `activity_playlist.xml` | Hero image (animate), gradient headline, stat cards, manage row (rename/delete), select mode, song list with remove (custom only) |
| **Settings** | `activity_settings.xml` | LinearLayout built programmatically: 6 sections with toggle/value/plain rows |

#### 2.3.3 Design System
- **Color Palette**: Dark theme default (`#121212` background); accent purple `#7C3AED`, cyan `#22D3EE`, lavender `#F472B6`, green `#34D399`, amber `#FBBF24`, blue `#38BDF8`.
- **Typography**: System default (Roboto); headline gradient via `GradientSpan`.
- **Shapes**: 24dp corner radius for cards/covers; 12dp for chips; 10dp toggle track.
- **Motion**: 300ms splash fade; 220ms toggle animation; 380ms equalizer bars; 2400ms wave pulse; 4200ms ripple expand.
- **Icons**: Custom vector drawables (play, pause, shuffle, repeat, heart, sleep, volume, search, chevron, etc.).
- **Accessibility**: `contentDescription` on all icon buttons; `android:importantForAccessibility` on decorative views; scalable text via `sp` units.

---

## Chapter 3: Methodology

### 3.1 Software Development Life Cycle (SDLC)

The project followed an **Iterative Incremental Model** with four two-week sprints, aligning with academic semester constraints. Each sprint delivered a vertical slice of functionality.

| Sprint | Duration | Focus | Deliverable |
|--------|----------|-------|-------------|
| **Sprint 1** | Weeks 1–2 | Core Playback & Library | `PlaybackService`, `PlayerConnection`, `MusicLibrary`, `Song` model, `NowPlayingActivity` (audio only), `MainActivity` (Songs chip only) |
| **Sprint 2** | Weeks 3–4 | Playlists & Persistence | `PlaylistLibrary`, `CustomPlaylistStore`, `FavoritesStore`, `AppSettings`, `PlaylistActivity`, `MainActivity` (Playlists chip), `LibraryActivity` (Songs/Artists/Favorites) |
| **Sprint 3** | Weeks 5–6 | Podcasts & Video | Podcast detection, `PodcastAdapter`, video support in ExoPlayer, `NowPlayingActivity` (video, PiP, fullscreen), `MainActivity` (Podcasts chip), `LibraryActivity` (Podcasts tab), `SearchActivity` |
| **Sprint 4** | Weeks 7–8 | Polish & Settings | `SettingsActivity`, `WelcomeCard`, `SleepTimer`, `WaveformView`, dark mode, sort order, splash screen, bottom nav, UI animations, bug fixes |

**Iteration Review**: At the end of each sprint, the APK was tested on physical devices (API 29, 31, 34) and emulator (API 37). Feedback informed backlog reprioritization.

---

### 3.2 Chosen Methodology: Feature-Driven Development (FDD) with Kanban Visualization

**Why FDD?**
- **Domain Object Modeling** fits the media domain (Song, Playlist, Artist, PodcastEpisode).
- **Feature Lists** map directly to the 8 specific objectives (Section 1.2.2).
- **Short Iterations** (2 weeks) match academic calendar.
- **Class Ownership** not required (solo/small team); instead, **module ownership** per developer.
- **Progress Tracking** via completed features (not story points) suits graded deliverables.

**Kanban Board Columns**: Backlog → Ready → In Progress → Code Review → Testing → Done. WIP limit: 2 features per developer.

**Adaptation for Academia**: No dedicated QA; developer self-tests via manual test cases (Section 3.5). No CI/CD; local Gradle builds. Documentation written in Sprint 4 alongside code.

---

### 3.3 Phase Justification

| Phase | Activities | Justification |
|-------|------------|---------------|
| **Requirements & Design** (Week 1) | Problem statement, objectives, scope, architecture sketch, UI wireframes (Figma/paper), tech stack selection | Establishes baseline; prevents scope creep; Media3 choice justified by Google's recommendation over legacy MediaSessionCompat |
| **Core Engine** (Sprint 1) | ExoPlayer setup, MediaSessionService, MediaController bridge, metadata extraction | Highest risk: if background playback fails, entire app fails. Validated early on 3 API levels. |
| **Data Layer** (Sprint 1–2) | `MusicLibrary` scanning, `Song`/`Playlist` models, `SharedPreferences` stores | Pure Kotlin/Java; unit-testable; no Android context in models. |
| **UI Skeleton** (Sprint 1–2) | Activities, RecyclerViews, Adapters, Navigation | Material 3 components; chip navigation pattern from YouTube Music/Spotify for familiarity. |
| **Advanced Playback** (Sprint 3) | Video, PiP, fullscreen, sleep timer, waveform, speed control | Differentiates from basic players; PiP requires API 26+ conditional code. |
| **Settings & Polish** (Sprint 4) | Dark mode, sort, welcome card, animations, splash, bottom nav | "Delighters" per Kano model; dark mode expected on Android 10+; animations elevate perceived quality. |
| **Documentation & Defense Prep** (Week 8) | This paper, slide deck, demo script | Academic requirement; ensures knowledge transfer. |

---

### 3.4 Technology Stack

| Category | Technology | Version | Rationale |
|----------|------------|---------|-----------|
| **Language** | Java | 11 (source/target) | Course requirement; Media3 APIs fully compatible |
| **Build System** | Gradle (Kotlin DSL) | 8.x / AGP 8.x | Modern build; `libs.versions.toml` catalog |
| **Min/Target SDK** | Android | 29 / 37 | Covers 95%+ active devices; API 37 for latest features |
| **Media Engine** | Media3 ExoPlayer | 1.9.0 | Google's recommended media library; supports FLAC, HLS, DASH, offline |
| **Media Session** | Media3 Session | 1.9.0 | `MediaSessionService` + `MediaController` architecture |
| **UI Toolkit** | AndroidX / Material 3 | `appcompat:1.7.0`, `material:1.12.0` | `MaterialComponents` themes; `ConstraintLayout 2.1.4` |
| **Lists** | RecyclerView | 1.3.2 | ViewHolder pattern; diffutil not used (lists small, <200 items) |
| **Async** | Guava ListenableFuture | via Media3 | `MediaController.Builder.buildAsync()` returns `ListenableFuture` |
| **JSON** | org.json | Built-in | Lightweight; `CustomPlaylistStore` uses `JSONObject`/`JSONArray` |
| **Persistence** | SharedPreferences | Framework | Zero-dependency; sufficient for key-value + string sets |
| **Metadata** | MediaMetadataRetriever | Framework | Extracts embedded tags, duration, bitrate, cover art |
| **Image Loading** | Custom (`Thumbs`) | — | In-memory `LruCache` + `BitmapFactory`; no Glide/Coil needed for bundled assets |
| **Testing** | JUnit 4 / Espresso | Scaffold only | `ExampleUnitTest`, `ExampleInstrumentedTest` placeholders |
| **Version Control** | Git | — | Local repo; feature branches per sprint |

---

### 3.5 Test Cases

The following manual test cases were executed on physical devices (Pixel 6a API 34, Samsung A52 API 31, Emulator API 29/37) at the end of each sprint.

#### 3.5.1 Playback Engine Tests
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-PB-01 | Cold start playback | Launch app → tap song on Home → Now Playing opens | Song plays; cover art shows; waveform animates; elapsed/remaining update | ✅ Pass |
| TC-PB-02 | Background playback | Play song → Home button → swipe to recent → swipe away app | Notification shows; audio continues; lockscreen controls work | ✅ Pass |
| TC-PB-03 | Headphone unplug | Play song → unplug wired headphones | Playback pauses (AudioManager.ACTION_AUDIO_BECOMING_NOISY) | ✅ Pass |
| TC-PB-04 | Phone call interruption | Play song → receive call | Playback pauses; resumes after call ends | ✅ Pass |
| TC-PB-05 | Screen off wake lock | Play song → turn screen off → wait 5 min | Playback continues (WAKE_MODE_LOCAL) | ✅ Pass |
| TC-PB-06 | Next/Previous | Play playlist → tap next/prev | Skips correctly; playlist order respected (shuffle off) | ✅ Pass |
| TC-PB-07 | Shuffle mode | Enable shuffle → play playlist → observe order | Random order each play; shuffle icon highlighted | ✅ Pass |
| TC-PB-08 | Repeat modes | Cycle repeat: off → all → one → off | UI updates; behavior matches (loop all / loop one / stop) | ✅ Pass |
| TC-PB-09 | Seek via waveform | Drag waveform to 50% | Playback jumps to position; time labels update live during drag | ✅ Pass |
| TC-PB-10 | Sleep timer – preset | Start 10-min timer → wait | Playback pauses at ~10 min; label shows countdown | ✅ Pass |
| TC-PB-11 | Sleep timer – end of song | Start "End of song" → let song finish | Playback pauses exactly at song end | ✅ Pass |
| TC-PB-12 | Podcast speed change | Play podcast → Settings → set 1.5× → return | Playback speed updates immediately without restart | ✅ Pass |

#### 3.5.2 Playlist & Library Tests
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-PL-01 | Official playlists exist | Open Home → Playlists chip | 4 playlists: All Songs, Shuffle Mix, Quick Hits, Long Plays | ✅ Pass |
| TC-PL-02 | Create custom playlist | Home → Music chip → SELECT → tick 3 songs → SAVE → name "Test" | Playlist appears in Custom row; opens with 3 songs | ✅ Pass |
| TC-PL-03 | Rename custom playlist | Open custom playlist → Rename → "New Name" | Title updates; Home custom row updates; active playlist name syncs | ✅ Pass |
| TC-PL-04 | Delete custom playlist | Open custom playlist → Delete → confirm | Playlist removed from Home and storage | ✅ Pass |
| TC-PL-05 | Add song to existing playlist | Now Playing → Save → pick custom playlist | Toast "Added to..."; song appears in playlist (no duplicate) | ✅ Pass |
| TC-PL-06 | Remove song from custom playlist | Open custom playlist → long press song → remove | Song removed from playlist only; file unchanged | ✅ Pass |
| TC-PL-07 | Favorites toggle | Now Playing → tap heart → fills pink → tap again → outline | Heart state persists across app restarts | ✅ Pass |
| TC-PL-08 | Favorites library tab | Library → Favorites chip | Shows only favorited songs in library sort order | ✅ Pass |
| TC-PL-09 | Artist grouping | Library → Artists chip | Artists sorted A–Z; tap opens playlist of all their songs | ✅ Pass |
| TC-PL-10 | Quick Hits / Long Plays | Home → Playlists chip → tap each | Quick Hits: all songs <4 min; Long Plays: all ≥4 min | ✅ Pass |
| TC-PL-11 | Shuffle Mix order | Play Shuffle Mix twice | Different random order each time | ✅ Pass |

#### 3.5.3 Podcast & Video Tests
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-PC-01 | Podcast detection | Files named `podcast_show__ep.mp3` in res/raw | Appear in Podcasts chip/tab, not in Music | ✅ Pass |
| TC-PC-02 | Podcast metadata | Play podcast episode | Title = episode; Artist = show name; "Host: ..." from ID3 artist | ✅ Pass |
| TC-PC-03 | Video podcast playback | Play `podcast_*.mp4` | Video renders in Now Playing; expand button visible | ✅ Pass |
| TC-PC-04 | Fullscreen video | Tap expand on video podcast | Fullscreen landscape/portrait; controls auto-hide; seek works | ✅ Pass |
| TC-PC-05 | Picture-in-Picture | Play video podcast → tap PiP button | Floating window appears; 3 buttons (prev/play/next) work | ✅ Pass |
| TC-PC-06 | Podcast description | Play podcast episode (audio) | Description card shows string resource matching filename | ✅ Pass |
| TC-PC-07 | Podcast speed persists | Set 1.25× → kill app → reopen → play podcast | Speed remains 1.25× | ✅ Pass |

#### 3.5.4 Persistence & Settings Tests
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-PS-01 | Resume last song | Play song → pause at 1:30 → kill app → reopen | Song loads at 1:30 paused; "Resume" shows on Home | ✅ Pass |
| TC-PS-02 | Dark mode toggle | Settings → Dark mode off → wait 300ms → app restarts | Light theme applied system-wide | ✅ Pass |
| TC-PS-03 | Sort order change | Settings → Sort by Title → Home → Music chip | Songs sorted A–Z by title | ✅ Pass |
| TC-PS-04 | Welcome card name | Settings → Your name → "Alex" → Home | Welcome shows "Good morning, Alex" (time-based) | ✅ Pass |
| TC-PS-05 | Floating window toggle | Settings → Floating window off → play video → PiP button | PiP button hidden | ✅ Pass |
| TC-PS-06 | Clear favorites | Settings → Clear favorites → confirm | All hearts outline; Favorites tab empty | ✅ Pass |
| TC-PS-07 | Delete all playlists | Settings → Delete playlists → confirm | Custom playlists gone; official remain | ✅ Pass |
| TC-PS-08 | Volume mute memory | Now Playing → volume dialog → mute → unmute | Restores pre-mute level | ✅ Pass |

#### 3.5.5 UI & Navigation Tests
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-UI-01 | Splash duration | Launch app | 3s fade-in → MainActivity | ✅ Pass |
| TC-UI-02 | Bottom nav highlight | Tap each bottom icon | Correct activity opens; icon highlighted | ✅ Pass |
| TC-UI-03 | Home chip selection | Tap All/Music/Playlists/Podcasts | Sections show/hide correctly; chip styled | ✅ Pass |
| TC-UI-04 | Search real-time | Type in Search → observe lists | Results filter instantly; sections hide when empty | ✅ Pass |
| TC-UI-05 | Now Playing share | Tap share → pick app | Intent with "Listening to X by Y on MVPPLAY" | ✅ Pass |
| TC-UI-06 | Playlist hero animation | Open playlist → scroll | Hero image parallax; gradient headline | ✅ Pass |
| TC-UI-07 | Equalizer bars animation | Play playlist → observe hero | 5 bars bounce async while playing | ✅ Pass |
| TC-UI-08 | Wave rings animation | Play playlist → observe hero | 2 rings pulse; 2 ripples expand/fade | ✅ Pass |
| TC-UI-09 | Orientation lock | Now Playing (audio) → rotate | Stays portrait | ✅ Pass |
| TC-UI-10 | Orientation unlock | Now Playing (video fullscreen) → rotate | Follows sensor | ✅ Pass |

#### 3.5.6 Edge Cases & Error Handling
| ID | Test Case | Steps | Expected Result | Status |
|----|-----------|-------|-----------------|--------|
| TC-EC-01 | Empty library | Remove all res/raw files → rebuild → launch | "No songs yet" empty states; no crashes | ✅ Pass |
| TC-EC-02 | Corrupt media file | Add 0-byte file to res/raw → rebuild | Skipped silently; not in library | ✅ Pass |
| TC-EC-03 | Missing cover art | Play file without embedded art | Gradient placeholder (palette color) | ✅ Pass |
| TC-EC-04 | PiP not supported | Run on device without FEATURE_PICTURE_IN_PICTURE | Toast "Floating window not supported"; no crash | ✅ Pass |
| TC-EC-05 | Notification permission denied | Android 13+ → deny POST_NOTIFICATIONS | Foreground service still runs; no notification | ✅ Pass |
| TC-EC-06 | Rapid chip switching | Tap chips rapidly 20× | No ANR; UI stays consistent | ✅ Pass |
| TC-EC-07 | Playlist name collision | Create "All Songs" (case-insensitive) | Rejected; toast or silent fail | ✅ Pass |
| TC-EC-08 | Large playlist (100+ songs) | Add many files → create playlist | Scroll smooth; play starts <1s | ✅ Pass |

---

## Bibliography (APA 7th Edition)

Android Developers. (2024). *Media3 ExoPlayer overview*. https://developer.android.com/media/media3/exoplayer

Android Developers. (2024). *MediaSessionService and MediaController*. https://developer.android.com/media/media3/session

Android Developers. (2024). *Picture-in-Picture (PiP) support*. https://developer.android.com/develop/ui/views/picture-in-picture

Android Developers. (2024). *Foreground services*. https://developer.android.com/develop/background-work/services/foreground-services

Google. (2023). *Media3 library release notes (1.9.0)*. https://developer.android.com/jetpack/androidx/releases/media3

Google. (2024). *Material Design 3 components for Android*. https://m3.material.io/develop/android

ExoPlayer Team. (2024). *ExoPlayer developer guide*. https://exoplayer.dev/

Apple Developer. (2024). *Audio file formats and metadata*. https://developer.apple.com/documentation/avfoundation/media_assets_playback_and_editing

International Organization for Standardization. (2017). *ISO/IEC 14496-14:2003 – MP4 file format*. https://www.iso.org/standard/39891.html

Xiph.Org Foundation. (2024). *FLAC – Free Lossless Audio Codec*. https://xiph.org/flac/

Xiph.Org Foundation. (2024). *Vorbis – General audio compression*. https://xiph.org/vorbis/

Ogg, C., & Montgomery, M. (2003). *The Ogg encapsulation format version 0*. IETF RFC 3533. https://doi.org/10.17487/RFC3533

Fraunhofer IIS. (2024). *AAC – Advanced Audio Coding*. https://www.iis.fraunhofer.de/en/ff/amm/tech/aac.html

Android Open Source Project. (2024). *MediaMetadataRetriever reference*. https://developer.android.com/reference/android/media/MediaMetadataRetriever

Google. (2024). *SharedPreferences guide*. https://developer.android.com/training/data-storage/shared-preferences

Google. (2024). *RecyclerView guide*. https://developer.android.com/develop/ui/views/layout/recyclerview

Google. (2024). *ConstraintLayout guide*. https://developer.android.com/develop/ui/views/layout/constraintlayout

Palmer, S. (2002). *Feature-Driven Development*. In *Agile Software Development* (pp. 123–145). Addison-Wesley.

Cohn, M. (2004). *User Stories Applied: For Agile Software Development*. Addison-Wesley.

Kano, N., Seraku, N., Takahashi, F., & Tsuji, S. (1984). Attractive quality and must-be quality. *Journal of the Japanese Society for Quality Control*, 14(2), 39–48.

---

## Appendices

### Appendix A: Project Structure (Key Files)
```
app/src/main/java/com/appdev/mvpplay/
├── MvpApp.java                    # Application class
├── MainActivity.java              # Home dashboard
├── SplashActivity.java            # Launch screen
├── LibraryActivity.java           # Songs/Artists/Favorites/Podcasts
├── SearchActivity.java            # Real-time search
├── NowPlayingActivity.java        # Full playback UI + PiP + Fullscreen
├── PlaylistActivity.java          # Playlist detail + hero animations
├── SettingsActivity.java          # All settings UI
├── PlaybackService.java           # MediaSessionService + ExoPlayer
├── PlayerConnection.java          # MediaController singleton
├── MusicLibrary.java              # Raw resource scanner + metadata
├── PlaylistLibrary.java           # Official playlist generator
├── CustomPlaylistStore.java       # JSON persistence for custom playlists
├── FavoritesStore.java            # StringSet persistence for favorites
├── AppSettings.java               # SharedPreferences wrapper
├── SleepTimer.java                # Sleep timer logic
├── Song.java                      # Data model
├── Playlist.java                  # Data model
├── WaveformView.java              # Custom deterministic waveform
├── Thumbs.java                    # In-memory bitmap cache
├── WelcomeCard.java               # Time-based greeting
├── BottomNav.java                 # Bottom navigation bar
├── PlaylistDialogs.java           # Create/Rename/Delete/Save dialogs
├── SongAdapter.java               # Song RecyclerView adapter
├── PlaylistAdapter.java           # Playlist card adapter
├── PlaylistSongAdapter.java       # Playlist detail song adapter
├── PlaylistRowAdapter.java        # Search playlist row adapter
├── ArtistAdapter.java             # Artist list adapter
├── PodcastAdapter.java            # Podcast episode adapter
└── GradientSpan.java              # Gradient text span
```

### Appendix B: Key Permissions (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- No INTERNET, no READ_MEDIA_AUDIO, no ACCESS_NETWORK_STATE -->
```

### Appendix C: Gradle Dependencies (app/build.gradle.kts)
```kotlin
dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
```

---

*Document Version: 1.0*  
*Date: November 2026*  
*Course: Mobile Application Development*  
*Project: MVPPlay – Offline-First Music & Podcast Player*