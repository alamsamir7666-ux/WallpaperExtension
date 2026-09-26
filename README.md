# wallpaperflare-extension

A [Cloudimage](https://github.com/alamsamir7666-ux/Cloud-Wallpaper) wallpaper
provider plugin for [wallpaperflare.com](https://www.wallpaperflare.com/) —
popular feed, a 12-row sectioned home, search, random picks and filters,
delivered as a standalone extension repository the app can install by URL.

> **Status: 0.5.0 — the Cloudflare endgame + the sectioned home.** The
> 0.4.0 browser-UA fix was necessary but not sufficient: the zone still
> challenges non-browser traffic from some egress, and a plugin — pure JVM
> code — can never run the challenge's JavaScript. The host app's **v1.0.15**
> now solves challenges app-side (headless WebView earns the clearance; the
> client replays the request under the earned cookies + User-Agent), and
> this package leans on it. 0.5.0 also adds the CloudStream-style home:
> Popular plus 11 tag rows (Nature, Animals, Anime, Fantasy, Space, Cars,
> Gaming, Abstract, Minimalist, Architecture, People), each a `"query"`
> preset the host (v1.0.15+) routes through the provider's own search —
> exactly how the site itself browses tags. 103 tests green, package
> reproducible, dex ABI audited against the host v1.0.15 APK (0 unresolved
> refs). The repository at
> **https://alamsamir7666-ux.github.io/WallpaperExtension** serves 0.5.0 —
> update the package in the app's Extensions tab (host ≥ v1.0.15 for the
> full story; on older hosts the tag rows render but degrade to the popular
> feed, per the documented Filters contract). See [PLAN.md](PLAN.md) for
> the delivery plan and [recon/RECON.md](recon/RECON.md) for the frozen
> site contract.

## Repository layout

| Path | What it is |
|---|---|
| `provider-api/` | Vendored, unmodified copy of the host app's MIT-licensed `:provider:api` extension contract (pinned to host v1.0.15 — carries `HomeSection` and the sections/suggestTags additive defaults; `ProviderApi.VERSION` stays 1) |
| `plugin/` | The `cloudimage.wallpaperflare` provider: sources, `extension.json` manifest, tests, and the d8 → zip packaging pipeline |
| `tools/` | Build & verification helpers: `fetch_android_jar.sh` (local android.jar for d8), `verify_package.py` (host-engine rules + release-dex ABI audit), `build_repo_index.py` (repository index generator), `probe_site.sh` (live Cloudflare/CDN probe) |
| `.github/workflows/` | CI: ktlint, tests, package build & verification, host-rule + ABI verification, index generation; `site-probe.yml` (manual live-site diagnostic) |
| `recon/RECON.md` | Wallpaperflare site contract (evidence-based, cross-confirmed) |
| `recon/EMULATOR_CHECK.md` | The Part 5 release-gate checklist for the on-device verification |

## Building

Requires JDK 17+ (the Gradle wrapper fetches Gradle 9.7.1 itself):

```bash
./tools/fetch_android_jar.sh   # one-time, local only: android.jar for d8
                               # (skipped automatically if an Android SDK is present)
./gradlew ktlintCheck test packageExtension verifyPackage
```

The distributable lands in `plugin/build/outputs/extension/cloudimage.wallpaperflare.zip`.

## Content policy

The provider is rated SFW and hard-clamps itself to safe requests — it never
requests or returns sketchy/NSFW content. Wallpapers remain the property of
their respective owners; the plugin links back to wallpaperflare.com detail
pages.

## License

Apache-2.0 for this repository's own content; the vendored contract in
`provider-api/` keeps its upstream MIT license — see [LICENSE](LICENSE) and
[NOTICE](NOTICE).
