# CloudStream Desktop (cs-desktop)

A lightweight **native Windows** desktop port of [CloudStream 3](https://github.com/recloudstream/cloudstream). Display name: **CloudStream Desktop**.

This is an independent GPL-3.0 derivative of CloudStream. **It is not affiliated with, endorsed by, or maintained by reCloudStream.**

**This app includes no video sources.** It is an empty media-center shell. You add extension repositories yourself. Do **not** create or use extensions that host copyrighted media.

## Prerequisites

- **JDK 21** (the Gradle wrapper uses toolchain 21)
- Windows 10/11 for playback of Widevine/PlayReady (Edge WebView2 CDM)
- Optional: `mpv` / `ffmpeg` on `PATH` (otherwise Windows downloads a small official/LGPL libmpv build on first launch)
- Optional: [WebView2 Evergreen runtime](https://go.microsoft.com/fwlink/p/?LinkId=2124703) for DRM that is not ClearKey

Linux and macOS can compile and run the UI. libmpv must already be installed there; WebView2 DRM is Windows-only.

## Run

```bash
./gradlew :app:run
```

Windows:

```bat
gradlew.bat :app:run
```

Create a native distribution (MSI on Windows, Deb on Linux):

```bash
./gradlew :app:createDistributable
```

Tests (no network, no piracy sites):

```bash
./gradlew test
```

## First launch

1. The window opens on **Home** with zero plugins. **Extensions** is empty until you add a repository.
2. On Windows, if `mpv` / `libmpv` is not on `PATH`, the player downloads a small official LGPL **libmpv** build into `%APPDATA%/cs-desktop/mpv` (~tens of MB). That archive is **not** stored in git.
3. Installing a `.cs3` plugin converts Dalvik dex to a JVM jar (dex2jar, downloaded into `%APPDATA%/cs-desktop/tools` on first install) and caches the jar next to it.

Suggested **optional** official repository (not auto-installed):

```
https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json
```

Add it on the Extensions screen, then install a legal plugin such as **iptv-org** or **YouTube** if the official list still carries them. Custom repository URLs, GitHub blob URLs, raw `plugins.json`, and CloudStream shortcodes are accepted. MegaRepo, CNCVerse, and other third-party scraping repos are not bundled and must not be added here.

## Architecture

| Module | Role |
| --- | --- |
| `:core` | Kotlin Multiplatform **JVM-only**. Upstream CloudStream provider/extractor API (`com.lagradost.cloudstream3.*`): `MainAPI`, `ExtractorApi`, `ExtractorLink`, `DrmExtractorLink`, `LoadResponse`, `TvType` (including `Live`), `BasePlugin`, `@CloudstreamPlugin`, M3u8Helper, JsUnpacker, built-in extractors. |
| `:extloader` | Runtime `.cs3` loader: unzip APK, read `manifest.json`, dex→jar, isolated `URLClassLoader`, `android.*` / `androidx.*` stubs (`Context`, `SharedPreferences`, `Log`, `Build`, …). Repo client. `CloudflareKiller` / `WebViewResolver` fail closed with a clear message. Installs to `%APPDATA%/cs-desktop/plugins`. |
| `:app` | Compose for Desktop UI, SQLite library, libmpv player, ClearKey proxy, local stream header proxy, WebView2 + Shaka for Widevine/PlayReady. |

Data directory: `%APPDATA%/cs-desktop` on Windows, `~/.local/share/cs-desktop` on Linux, `~/Library/Application Support/cs-desktop` on macOS.

## Player

Hybrid routing from extracted links:

| Stream | Engine |
| --- | --- |
| Clear HLS / DASH / MP4 / MPEG-TS | **libmpv** (JNA). Overlay controls drawn on a Swing layer above the video (mpv owns the HWND): gradient chrome, scrub bar with buffered range, source/audio/subtitle/speed/aspect menus, PiP. Space/arrows/M mute/F fullscreen/Esc, click to pause, double-click to fullscreen, auto-hiding chrome. |
| `TvType.Live` or non-positive duration | Same engine, **seek bar hidden**, LIVE badge, no resume seek. |
| **ClearKey** (`kid`+`key` or ClearKey UUID) | Local HTTP proxy rewrites manifests / serves keys. Played in mpv. |
| **Widevine** / **PlayReady** | Embedded **WebView2** host + **Shaka Player**. Manifest URL, license URL, license headers, and `keyRequestParameters` are passed into Shaka. Uses the OS Edge CDM. **No CDM is shipped.** |
| Unknown DRM UUID | Explicit error naming the scheme. Never hangs. |

If WebView2 is missing, the player shows a one-click link to Microsoft’s Evergreen installer and a clear error — never a silent fail.

A local Ktor proxy re-attaches Referer / User-Agent / Cookie on HLS/DASH segments when extractors require them.

External and embedded subtitles (SRT/VTT/ASS) go through mpv tracks. OpenSubtitles search is available when you paste an API key in Settings.

**L1 / hardware DRM does not work on desktop.** Widevine is L3 via Edge.

Casting: Chromecast is not bundled. Use **Play in browser** or **copy stream URL** from the player.

## UI

Home, Search (bounded parallel `MainAPI.search`), Result (poster, plot, tags, seasons/episodes, Play, Bookmark, Download), Player, Library (bookmarks + history), Downloads (`%APPDATA%/cs-desktop/downloads` or a folder you choose), Live TV, Extensions, Settings (player, UI, provider filter, AniList/MAL/Simkl token paste, backup/restore zip, GPL about).

## Legal

- License: **GNU GPL-3.0** (see `LICENSE` and `NOTICE`)
- Copyright in the upstream API: reCloudStream contributors
- No Netflix / Hotstar / Prime / Disney / CNCVerse / MegaRepo / piracy plugin URLs are hardcoded
- Do not use this project to infringe copyright

## Attribution

Sources under `core/src` are adapted from https://github.com/recloudstream/cloudstream `library/` (commonMain + JVM shims) so existing Android `.cs3` plugins keep resolving the same package names.
