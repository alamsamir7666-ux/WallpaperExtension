# wallpaperflare-extension

A [Cloudimage](https://github.com/alamsamir7666-ux/Cloud-Wallpaper) wallpaper
provider plugin for [wallpaperflare.com](https://www.wallpaperflare.com/) —
popular feed, search, random picks and filters, delivered as a standalone
extension repository the app can install by URL.

> **Status: Part 5 of 7 — device gate iteration 2.** The first on-device
> run reported the source failing; root-caused to the Cloudflare bot gate
> challenging the host app's fixed User-Agent and fixed in **0.4.0** —
> every request now carries a browser identity through the host client's
> extra-header override (RECON §8). 101 tests green, package reproducible,
> every host-engine rule re-verified on each CI run. The repository at
> **https://alamsamir7666-ux.github.io/WallpaperExtension** serves 0.4.0 —
> reinstall the package in the app and re-run the gate per
> `recon/EMULATOR_CHECK.md`. Part 6 automates publishing; Part 7 adds the
> drift watchdog. See [PLAN.md](PLAN.md) for the delivery plan and
> [recon/RECON.md](recon/RECON.md) for the frozen site contract.

## Repository layout

| Path | What it is |
|---|---|
| `provider-api/` | Vendored, unmodified copy of the host app's MIT-licensed `:provider:api` extension contract (pinned to host v1.0.5) |
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
