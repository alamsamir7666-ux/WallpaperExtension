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
- [ ] **Part 3 — Wallpaperflare client (parsing layer)** · URL builder
      (search `?wallpaper=&page=&sort=`, popular via loadmore, detail pages),
      the lightweight HTML parser over the frozen card structure
      (`a[itemprop='url']` + `img[data-src]`), pagination end-detection,
      typed failures. Fixture-driven tests: URL building, card parsing,
      malformed-HTML resilience, empty/end-of-feed pages.
      *Exit: client suite green over fixtures.*
- [ ] **Part 4 — WallpaperProvider wiring** · meta (SFW, capabilities),
      `configure()` stores the client, popular/search/details/random mapped
      onto the client, host filter vocabulary → Wallpaperflare params
      (sorting/order mapping per Part 2), `Result`-wrapped honest errors,
      details payload (resolution, source URL, filesize when present).
      Fake-client tests: happy path, empty, HTTP error, malformed body,
      pagination. *Exit: provider complete and tested.*
- [ ] **Part 5 — Packaging verification & engine integration** · validate the
      built zip end-to-end: manifest parses under the host's
      `ExtensionManifest` rules, layout matches `extension.json` +
      `classes.dex`, dex external refs resolve against the host's kept
      classes (mini audit in the style of `tools/audit_release_dex.py`),
      index.json generated with correct sha256/sizeBytes. User-assisted
      emulator check: install the app, add a local repo, load the plugin,
      confirm the host UA is not challenged and the feed renders.
      *Exit: package installs & loads in the real engine.*
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
