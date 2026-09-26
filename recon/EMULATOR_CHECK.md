# Emulator / device check — the Part 5 release gate

This is the one verification that cannot run in CI or in the build sandbox:
the extension package installing and loading inside the **real host engine**,
on a real Android runtime, from a real HTTPS repository URL, with wallpaperflare.com
judging the app's User-Agent from a **residential IP** (the sandbox is
whole-zone Cloudflare-blocked, so every automated check deliberately stops
short of claiming this).

Everything short of it is already proven, offline, on every CI run:

- `tools/verify_package.py` re-runs the host engine's own rules — zip layout,
  `ExtensionManifest` schema + constraints, API-version gate, entry class
  defined by the dex, and the release-dex ABI audit (every external type
  reference resolves against the host's kept surface:
  `com.cloudimage.provider.api.**`, `kotlin.**`, `kotlinx.serialization.**`
  + bootclasspath);
- 98 fixture-driven JVM tests cover the provider contract, wiring and
  failure taxonomy;
- the gh-pages repository serves the package the app will actually download,
  with the sha256 the installer verifies.

What only a device can prove:

1. the dex payload loads through `DexClassLoader` in the release APK
   (R8-minified host — the exact class of regression that shipped in the
   host's v1.0.0 and is invisible on debug builds),
2. wallpaperflare.com serves the host's fixed User-Agent
   `Cloudimage/1.0 (Android; +repo)` to a residential/mobile IP without a
   Cloudflare challenge,
3. the CDN's c→r original-URL derivation (RECON §4) returns real bytes —
   images actually render and download.

## The check

**Prerequisite:** an Android emulator (or device) with API 26+, and the host
app's **signed release APK** — download `Cloudimage-v1.0.5.apk` from
https://github.com/alamsamir7666-ux/Cloud-Wallpaper/releases (the release
build is the one that matters: R8 is where plugin ABI breaks hide).

```bash
adb install Cloudimage-v1.0.5.apk
```

**Step 1 — add the repository.** In the app, open **Extensions → Add
repository by URL** and paste:

```
https://alamsamir7666-ux.github.io/WallpaperExtension
```

The app normalizes this to `…/WallpaperExtension/index.json` itself. The
repository should list as **"Wallpaperflare extension repository"** with one
package, *Wallpaperflare 0.4.0*. If the app still lists 0.3.0, re-open the
Extensions tab — the index is fetched on demand, not cached in V1.

**Step 2 — install the package.** Tap install on *Wallpaperflare*. It should
move to `READY` within a second or two — the download is ~23 KB, the sha256
is verified on arrival, and a failure here names its reason precisely
(checksum mismatch / invalid manifest / unsupported API are the host's
messages, none of which should appear).

**⚠️ If 0.3.0 is already installed, there is NO update button.** The host
v1.0.5 catalog row only checks `entry.id in installedIds` — an installed id
shows an **"Installed" chip**, never an install/update action, regardless of
versionCode. The installer *would* replace on re-install (same-id install is
a one-step replace), but the catalog UI never offers the action. **The
device-gate path is therefore: uninstall first, then install:**

1. Installed section → *Wallpaperflare* row → red **trash icon** → confirm
   uninstall;
2. Repository section → *Wallpaperflare* catalog row → **download icon** →
   installs 0.4.0 fresh.

This UI gap is a host-side limitation (worth an "Update" button when
`versionCode` is newer) — not fixable from the extension.

**Step 3 — browse (the Cloudflare gate).** Back on the browse tab, select
the Wallpaperflare source. The popular feed should render thumbnails within
a few seconds. This is the decisive step for risk #1/#2 in PLAN.md: it
proves the host UA passes Cloudflare from a real client IP.

**Step 4 — search.** Search `mountain` (or anything). Results should render;
scrolling should keep loading pages until the feed ends cleanly (no error
banner at the end of the feed).

**Step 5 — detail and apply.** Open any wallpaper: the preview should load
at full resolution (this proves the c→r CDN derivation returns real
originals), and **Set wallpaper** should succeed.

## If something fails

| Symptom | Meaning | Action |
|---|---|---|
| Feed error banner persisting after the 0.4.0 push, extension still shows 0.3.0 in the installed list | **CONFIRMED iteration 3: the catalog UI has no update path — the device keeps running 0.3.0 (app UA, still 403)** | Uninstall the installed Wallpaperflare row (trash icon), then install from the repo catalog row (download icon) → re-run steps 3–5 |
| Feed error banner on a **verified 0.4.0** install | Cloudflare is scoring beyond the UA (TLS/HTTP2 fingerprint) — note the probe is "both challenged" from datacenter IPs, so only a residential verdict counts | Report which network (Wi-Fi vs mobile data) + any visible detail; next escalation is host-level (WebView fetch or host client changes) |
| The banner says "check the Extensions tab" but the tab shows nothing wrong | Expected host observability gap: the Extensions tab lists LOAD failures only; a FETCH failure (HTTP 403 etc.) is flattened into the generic browse banner and never displayed | Diagnose by elimination: reinstall current package, then probe (below) |
| ~~Feed error banner, "source failed: wallpaperflare answered HTTP 403"~~ — **reported on the first device run; fixed in 0.4.0** | Cloudflare challenged the host's app User-Agent | Update/reinstall the package (0.4.0, browser-identified requests — RECON §8) and re-run steps 3–5 |
| Thumbnails render but previews/downloads fail | the c→r CDN derivation hit an unknown shape (PLAN.md risk, CAPTURE.md §r-grammar) — or the CDN challenges the host's Coil image loader | Report which wallpaper; run `tools/probe_site.sh` CDN rows |
| Thumbnails themselves fail to render | the CDN zone challenges Coil's default OkHttp identity — host-side fix needed | Report; host-level Coil OkHttp config is the contingency |
| Package installs but source fails to load, "source failed to bind its classes" | release-dex ABI regression on the host side | Report — this is exactly what `verify_package.py` audits against; would indicate the host's keep rules changed |
| Feed renders but is stuck after N pages | end-of-feed detection or pagination drift | Report the last page number that worked |
| Everything green | Part 5 exit criterion met | Note the device/API level and UA outcome; release unblocked |

## What is deployed right now

The preliminary gh-pages branch serves package `0.4.0` (versionCode 4,
sha256 `6ca801bf…`, 23,531 bytes): the 0.3.0 browser-header contingency
for the reported Cloudflare 403 (RECON §8). It is a **test publication**:
Part 6 replaces it with the automated publish pipeline (`publish.yml`,
force-orphan on every release) and final docs.
