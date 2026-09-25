# wallpaperflare-extension

A [Cloudimage](https://github.com/alamsamir7666-ux/Cloud-Wallpaper) wallpaper
provider plugin for [wallpaperflare.com](https://www.wallpaperflare.com/) —
popular feed, search, random picks and filters, delivered as a standalone
extension repository the app can install by URL.

> **Status: Part 4 of 7 — provider complete.** The build, vendored provider
> API, packaging pipeline, the frozen site contract (real HTML fixtures),
> the parsing client and the full `WallpaperProvider` wiring are done and
> verified — 98 tests green, package reproducible. Part 5 gates release on
> an on-device emulator check; publishing arrives in Part 6. See
> [PLAN.md](PLAN.md) for the delivery plan and
> [recon/RECON.md](recon/RECON.md) for the frozen site contract.
> **Not published yet — nothing to install until Part 6.**

## Repository layout

| Path | What it is |
|---|---|
| `provider-api/` | Vendored, unmodified copy of the host app's MIT-licensed `:provider:api` extension contract (pinned to host v1.0.5) |
| `plugin/` | The `cloudimage.wallpaperflare` provider: sources, `extension.json` manifest, tests, and the d8 → zip packaging pipeline |
| `tools/` | Build helpers (`fetch_android_jar.sh` — local-only android.jar for d8) |
| `.github/workflows/` | CI: ktlint, tests, package build & verification |
| `recon/RECON.md` | Wallpaperflare site contract (evidence-based, cross-confirmed) |

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
