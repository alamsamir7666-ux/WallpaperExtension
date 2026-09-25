# Wallpaperflare Extension — Delivery Plan

Repo `wallpaperflare-extension` (standalone) · plugin id `cloudimage.wallpaperflare` · host contract `:provider:api` v1 (Cloudimage v1.0.5)

This is the single source of truth for scope and progress, in the style of the
host app's PLAN.md. Every part ends green: plugin builds, package verifies,
tests pass, CI clean.

## Locked decisions

| Decision | Choice |
|---|---|
| Repo model | Standalone GitHub repo; gh-pages hosts `index.json` + plugin zip; added in-app by URL |
| Plugin id | `cloudimage.wallpaperflare` (host's ID_PATTERN: lower-case reverse-DNS, ≥2 segments) |
| Entry class | `com.cloudimage.wallpaperflare.WallpaperflareWallpaperProvider` |
| Provider API | v1, exact-match gated; **vendored MIT sources** of `:provider:api` (it is not published to Maven — the MIT license exists precisely for this) |
| Data source | **HTML scraping over the shared `ProviderHttpClient`** — see `recon/RECON.md`. No official API exists; three independent open-source scrapers cross-confirm a stable URL grammar and DOM contract |
| Parser | Hand-rolled lightweight HTML parser (regex over the stable card structure). **No jsoup** — keeps the plugin dex tiny and free of third-party bindings |
| Content policy | SFW hard clamp — provider rated `ContentRating.SFW`; no sketchy/NSFW category paths requested; item-level ratings default SFW |
| Resolution | `fullUrl` = original/max-quality file the detail page serves |
| Capabilities | `POPULAR`, `SEARCH`, `FILTERS`, `RANDOM` (RANDOM only if a workable mechanism is confirmed in Part 2 — else dropped from the declaration) |
| Popular feed | Homepage load-more AJAX (`index.php?c=main&m=portal_loadmore`) — lighter markup than full pages |
| Tests | Fixture-driven unit tests over a fake `ProviderHttpClient` (same pattern as `providers/wallhaven` tests) |
| Build | Gradle kotlin-jvm + d8 dex (from `com.android.tools:r8`, like the host's convention plugin) + `extension.json` at module root + zip |
| Dex classpath | Contract classes stay OUT of the payload via d8 `--classpath` — the host supplies them at runtime (mirrors `CloudProviderConventionPlugin`) |
| android.jar | CI: GitHub runners ship it. Local: `ANDROID_HOME`/`local.properties`; fallback = pinned community mirror copy in `tools/` (verification only, never shipped) |
| CI | build+test on push/PR; publish to gh-pages on main; scheduled drift watchdog (Part 7) |
| Licenses | Repo Apache-2.0; vendored `provider-api/` keeps its upstream MIT LICENSE + attribution header |
| Distribution | gh-pages → user pastes `https://<user>.github.io/wallpaperflare-extension` in the Extensions tab (host auto-appends `index.json`) |

## Known risks (from recon, 2026-09-25)

1. **Cloudflare IP-reputation blocking.** The live site 403s every datacenter
   IP this sandbox tried (curl variants, headless Chrome with Turnstile click,
   reader proxies). Residential/mobile clients are unaffected — plain
   `axios` scrapers work from home IPs. The app's requests come from real
   devices, so this is expected to be a non-issue in production; it is
   **verified in Part 5** on an emulator/device with the host's exact UA.
2. **Host User-Agent is fixed** (`Cloudimage/1.0 (Android; +repo)`) and
   plugins cannot override it. If Wallpaperflare's Cloudflare challenges that
   UA from real devices, the contingency is a one-line host-side UA change in
   `CloudimageHttpClient` (user owns the host app). Blocked = provider dead,
   so the Part 5 emulator check is the exit criterion that gates release.
3. **HTML drift.** Mitigated by fixture-driven parsing, honest failure
   taxonomy (`Result` → the host's "a source failed" state, v1.0.2+), and the
   Part 7 watchdog. Note: the watchdog itself may see Cloudflare 403s from
   GitHub runners — 403/challenge is treated as *inconclusive*, only
   parse-drift on fetched content fails.
4. **RANDOM capability unconfirmed** on Wallpaperflare (no documented random
   endpoint yet). Declared only if Part 2 confirms a mechanism.

## The 7 parts

- [x] **Part 1 — Repo foundation, vendored API & CI** · git init, LICENSEs,
      README stub, this PLAN. Gradle standalone build: `:provider-api`
      (vendored MIT sources) + `:plugin` (kotlin-jvm, ktlint, JUnit).
      Ported packaging pipeline: `dexProvider` (d8, contract on `--classpath`)
      + `packageExtension` (zip: `classes.dex` + `extension.json`).
      `build.yml` on GitHub Actions (JDK 17, assemble, ktlint, test, package,
      artifact upload). A stub provider (empty `popular`) builds into a valid
      package that passes manifest/index/sha256 validation tests.
      *Exit: green CI over a stub package.*
- [x] **Part 2 — Live-site contract & fixtures** · real HTML recovered
      without residential access: the sandbox is Cloudflare-blocked for the
      whole zone, but GitHub code search exposed genuine saved pages (a real
      search page, a real detail page, a real `/download` page — committed
      byte-identical) plus 10 cross-confirming scrapers incl. maintained
      RSS-Bridge. Contract FROZEN in `recon/RECON.md` (URL grammar incl.
      `sort=relevance` + `mobile`/`width`/`height` form params; card =
      `li[itemprop=associatedMedia]` → `a[itemprop=url]` + `img[data-src]` +
      `.res` + metas, independent of container; detail `link_btn`/`vimg`/
      `o_tips`/`tagul`; download `show_img` (**src is JS-set — real capture
      proves it**) + static `dld_thumb` fallback; c→r CDN derivation).
      RANDOM = GO (loadmore random page, Anning01's production mechanism).
      Fixtures: 3 real + 4 synthetic (loadmore fragment, empty pages, 8
      edge-case mutations), pinned by `FixtureContractTest` (17 tests).
      `CAPTURE.md` = residential cookbook for live upgrades.
      *Exit: contract frozen, fixtures committed — done.*
- [x] **Part 3 — Wallpaperflare client (parsing layer)** · shipped as four
      small classes: `WallpaperflareUrls` (the §2 URL grammar: search with
      `+`-encoded queries and the single `sort=relevance`, loadmore,
      detail/download paths), `WallpaperflareCdn` (the §4 full-resolution
      derivation, two production grammars, null on unknown shapes),
      `WallpaperflareParser` (cards anchored on `li[itemprop=associatedMedia]`
      — both `.res` shapes, entity decoding, malformed-card skips — plus
      detail/download page parsing) and `WallpaperflareClient`
      (orchestration over `ProviderHttpClient`, typed failures
      `Ok/HttpError/TransportError/ParseError`, pagination end-detection =
      zero cards, `details()` two-GET flow, random via loadmore draw with
      page-1 fallback). Grid wallpapers carry the CDN-derived original as
      `fullUrl` (host-flow discovery: v1.0.5 never calls `details()` —
      RECON §4a). 51 new fixture-driven tests (81 total), ktlint clean,
      package 19,978 bytes reproducible (sha256 01e8ce14…), dex defines
      only `com/cloudimage/wallpaperflare/*` with the contract on
      `--classpath`.
      *Exit: client suite green over fixtures — done.*
- [x] **Part 4 — WallpaperProvider wiring** · `configure()` wraps the host
      client once (settings unused — keyless site), popular/search/details/
      random mapped onto the client, the frozen filter vocabulary applied
      (only `sorting=relevance` → `&sort=relevance`; toplist/date/random,
      order, seed, category, purity honestly ignored; the popular feed
      never takes a sort), `Result`-wrapped honest errors — HTTP → readable
      source failure, parse → named contract drift, transport → the
      ORIGINAL `ProviderHttpException` rethrown so the host's typed
      transport errors survive the plugin boundary (offline stays offline);
      RANDOM declared and served (loadmore draw + page-1 fallback);
      `details` carries resolution, file size and the source URL. 21 wiring
      tests over the shared fake seam — configure-gating, statelessness,
      happy paths on real fixtures, empty/garbage-as-end-of-feed, HTTP 403/
      503, transport preservation, full filter matrix, random draw/empty/
      transport — plus the deleted stub suite (98 tests total), ktlint
      clean, package 23,024 bytes reproducible (sha256 67c94f6b…), dex
      payload audit: defines only `com/cloudimage/wallpaperflare/*`.
      *Exit: provider complete and tested — done.*
- [ ] **Part 5 — Packaging verification & engine integration** · the
      automated half is DONE and wired into CI: `tools/verify_package.py`
      re-runs the host engine's own rules offline (zip layout per
      `ExtensionPackages`, the exact `ExtensionManifest` schema/patterns/
      constraints, the `ProviderApi` exact-match gate, entryClass defined
      by the payload dex, `<id>.zip` naming) PLUS the release-dex ABI audit
      in the host's `tools/audit_release_dex.py` style — every external
      type reference must resolve against the host's kept surface
      (`com.cloudimage.provider.api.**`, `kotlin.**`,
      `kotlinx.serialization.**` + bootclasspath); the current payload's 81
      external references ALL resolve (12 contract classes, kotlin stdlib
      incl. the `suspend`-lowered `kotlin.coroutines`, java/dalvik
      bootclasspath — zero references outside the kept surface, and notably
      zero kotlinx.coroutines: the compile classpath is exactly
      provider:api + kotlin-stdlib). `tools/build_repo_index.py` (adapted
      from the host's, attribution in the header) generates `index.json`
      with measured sha256/sizeBytes. CI runs both on every push and
      uploads zip + index as artifacts. Two findings shaped the gate: the
      host app blocks cleartext HTTP (no `usesCleartextTraffic`) so a
      localhost repo is NOT installable — a PRELIMINARY gh-pages branch was
      pushed instead (`.nojekyll` + index.json + zip, Pages enabled,
      verified live: downloaded sha256 matches the index promise), and the
      host's signed release APK `Cloudimage-v1.0.5.apk` is downloadable
      from GitHub Releases — the R8 build where plugin ABI breaks actually
      show. `recon/EMULATOR_CHECK.md` is the step-by-step gate. Remaining:
      the user's device run (add repo URL → install package → feed renders
      → search → detail/apply) and its verdict; the r{N} CDN grammar
      verifies there too (CAPTURE.md's residential cookbook documents the
      fallback).
      *Exit: package installs & loads in the real engine — awaiting the
      user's device check.*
- [ ] **Part 6 — Publish pipeline & docs** · `publish.yml` → gh-pages
      (index.json + zip, force-orphan, mirrors the host's publish-repo.yml),
      adapted `tools/build_repo_index.py`. README: what it is, the exact
      paste-into-the-app URL, install steps, content policy, attribution,
      Cloudflare caveats. Tag `v1.0.0`.
      *Exit: live repo URL installs into the app.*
- [ ] **Part 7 — Drift watchdog & hardening** · scheduled Action probing
      search/loadmore/detail (403 = inconclusive; parse drift = fail + issue),
      edge cases (unicode queries, `+` encoding, empty result sets), rate
      hygiene (sequential page fetches, conservative pagination), final
      polish; `v1.0.1` if fixes land. *Exit: watchdog merged, docs final.*

## Out of scope

- Sketchy/NSFW content paths (hard-clamped away)
- Device-matched resolution picking (original/max only)
- Bundling into the host's official repo (separate decision for later)
- Any host-app changes except the documented UA contingency
- i18n of provider strings

## Status log

- **2026-09-25 — Recon done, plan drafted.** Live site unreachable from the
  build sandbox (Cloudflare IP reputation — every datacenter path blocked,
  including headless Chrome + Turnstile). Site contract reconstructed from
  four cross-confirming open-source scrapers; see `recon/RECON.md`. Data
  source locked: HTML scraping via the shared GET-only `ProviderHttpClient`,
  lightweight hand parser, no jsoup. Seven parts scoped; awaiting go for
  Part 1.
- **2026-09-25 — Part 1 done: foundation green.** Repo `wallpaperflare-extension`
  scaffolded (renamed from the workspace folder `wallpaperflare-repo`):
  Apache-2.0 LICENSE + NOTICE, vendored `:provider-api` (unmodified MIT
  sources from host v1.0.5 / commit 59d06d8, provenance headers, sync rules
  documented), `:plugin` with the ported packaging pipeline
  (`dexProvider` → `packageExtension` → `verifyPackage`), CI `build.yml`
  mirroring the host's (JDK 17, ktlint, test, package, artifact upload),
  `tools/fetch_android_jar.sh` local-d8 helper (Sable mirror with Google
  fallback; git-ignored). Local end-to-end green: 13/13 tests, ktlint clean,
  package = 3,142 bytes reproducible (same sha256 across forced rebuilds:
  559cd8c6…), dex defines ONLY `com/cloudimage/wallpaperflare/*` — the
  vendored contract classes are correctly excluded from the payload.
  Deviations from the host convention, both documented in the build script:
  reproducible zip flags (stable published sha256) and a JDK 17 toolchain
  via the foojay resolver (build machines with only a JRE still work; CI
  resolves to its Temurin 17). Not yet on GitHub — first push happens after
  the user reviews Part 1, so CI itself is unexercised until then.
- **2026-09-25 — Part 1 pushed, CI green on first run.** Repo live at
  `alamsamir7666-ux/WallpaperExtension` (commit `f0eb81b`): the user's
  `main` received the 31-file tree; Actions run #1 passed end-to-end
  (ktlint, 13 tests, package+verify, artifact upload) in ~90 s — the
  environment strategy (foojay JDK 17, android.jar guard) works on GitHub
  runners as designed.
- **2026-09-25 — Part 2 done: contract frozen on REAL evidence.** Whole-zone
  Cloudflare block (site + all CDN subdomains) forced a different recovery
  route: GitHub code search found pages users had saved into public repos —
  a real search page (`puerta vieja`, 2 cards, form + sort link), a real
  detail page (nature/tropical, 50 related cards, full download chain), and
  a real `/download` page (bikes 4096x2304, `dld_thumb`, `dld_info`, and the
  key caveat: `show_img` has NO static src). Cross-confirmed against 10
  scrapers (RSS-Bridge maintained; AyGemuy's c→r full-res derivation;
  Anning01's random-page mechanism → RANDOM = GO). Committed: 3 real + 4
  synthetic fixtures with provenance README + CAPTURE.md cookbook, RECON.md
  rewritten as the frozen contract, `FixtureContractTest` (17 tests) pinning
  it. Workspace note: the build sandbox was restored from a pre-Part-1
  snapshot (Part 1 re-cloned from GitHub, commit intact); the extra real
  detail pages live outside the repo and are re-fetchable via the pinned-SHA
  script recorded in RECON.md §7.
- **2026-09-25 — Part 3 done: the Wallpaperflare client is real.** Before
  writing code, re-read the host app's detail flow and found the decisive
  fact: **v1.0.5 never calls `details()`** — `DetailViewModel`/
  `WallpaperPreview` apply/save/share the GRID item's `fullUrl` directly,
  so grid items must carry the derived original, not the small preview
  (documented as RECON §4a). Also extracted AyGemuy's exact two-grammar
  derivation from the pinned evidence (`-preview`/`-thumb` drop + c→r;
  `/preview/`→`/path/` + c→r) and PeskyPotato's page-1 loadmore
  pagination. Implemented `WallpaperflareUrls`/`WallpaperflareCdn`/
  `WallpaperflareParser`/`WallpaperflareClient` with the typed
  `WallpaperflareFetch` failure taxonomy; provider stub id unified onto the
  client's `PROVIDER_ID`; `extension.json` bumped to 0.2.0/versionCode 2.
  51 new tests over the unchanged Part-2 fixtures (URL grammar, both `.res`
  shapes, all 8 edge-case mutations, download-page caveat, c→r derivation,
  two-GET details with degradation, random draw + fallback, Http/
  Transport/Parse failures) — 81/81 green, ktlint clean, package
  reproducible at 19,978 bytes (sha256 01e8ce14…), dex payload audit:
  defines only `com/cloudimage/wallpaperflare/*`, references only the
  contract + platform/kotlin-stdlib.
- **2026-09-25 — Part 4 done: the provider is wired.** The host contract's
  four methods now delegate to the Part 3 client through one `runFetch`
  seam that preserves each failure's meaning: HTTP errors surface as
  readable source failures, parse errors name the frozen-contract drift,
  and transport failures rethrow the ORIGINAL `ProviderHttpException` —
  the host's `ExtensionWallpaperSources.toNetworkError` recovers its typed
  transport errors from that subclass (offline stays offline), so the
  plugin boundary stays honest end to end. The frozen filter vocabulary
  (RECON §5) is applied exactly: `sorting=relevance` → `&sort=relevance`,
  everything else the host can send is ignored without pretending, and the
  popular feed never takes a sort parameter. RANDOM is now declared and
  served (loadmore draw + page-1 fallback, Anning01's mechanism).
  `StubBehaviorTest` retired with the stub; 21 wiring tests took its place
  over a shared fake seam extracted into `TestFixtures` (98/98 green).
  `extension.json` → 0.3.0/versionCode 3. Package reproducible at 23,024
  bytes (sha256 67c94f6b…); dex audit (scripts/audit_extension_dex.py)
  confirms the payload still defines only plugin classes. One workspace
  note: the environment restored files with mode 755 — `core.fileMode` is
  now off locally so phantom diffs stay out of commits.
- **2026-09-25 — Part 5, automated half done: host rules verified, repo
  live, device gate armed.** Every check the host engine runs at install
  and load time now runs here too, offline, on every CI build:
  `tools/verify_package.py` (host-exact manifest rules ported from
  `ExtensionManifest`/`ProviderApi`/`ExtensionPackages`, plus the
  release-dex ABI audit mirroring the host's `tools/audit_release_dex.py`
  — the payload's 81 external type references all resolve against the
  kept surface `provider.api`/`kotlin`/`kotlinx.serialization` +
  bootclasspath; zero kotlinx.coroutines references, proving the compile
  classpath is exactly provider:api + kotlin-stdlib). Two environment
  findings changed the gate's shape: the host blocks cleartext HTTP, so a
  localhost repo is uninstallable — a preliminary gh-pages branch was
  pushed instead (Pages enabled via API; live URLs verified: downloaded
  zip's sha256 matches the index promise), and the host's signed release
  APK is on GitHub Releases, so the device check runs the R8 build where
  ABI breaks actually appear. `recon/EMULATOR_CHECK.md` documents the
  five-step gate and the failure-triage table. The single remaining Part 5
  action is the user's device run; its verdict also verifies the r{N} CDN
  grammar (the one sandbox-unverifiable piece since Part 3).
