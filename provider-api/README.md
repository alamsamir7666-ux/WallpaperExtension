# Vendored `provider:api` contract

This module is a vendored copy of the Cloudimage host app's `:provider:api`
module — the extension contract every wallpaper plugin implements.

- Upstream: https://github.com/alamsamir7666-ux/Cloud-Wallpaper
  (`provider/api/`, tag **v1.0.5**, commit `59d06d8a8cb31103d85f3fe852bf8f740c71c58f`)
- License: **MIT** (see [LICENSE](LICENSE)) — the host ships this module under
  MIT precisely so third-party extension repositories can vendor it.
- Modifications: **none** apart from a provenance header comment on each file.
  Keep it that way: re-vendor from upstream wholesale instead of editing here.

The vendored copy exists only so this standalone repository can **compile**
against the contract. At runtime the authority is always the copy inside the
installed host app — the packaging pipeline (`dexProvider` in
`plugin/build.gradle.kts`) puts this module on d8's `--classpath`, so its
classes are excluded from the shipped `classes.dex` and are supplied by the
host's class loader instead, exactly like the host's own provider modules.

## Sync rules

- `ProviderApi.VERSION` (currently `1`) must equal the host's
  `ProviderApi.VERSION`. The host gates installs on an **exact** match
  between the manifest's `apiVersion` and its own version, so a mismatch
  makes every package from this repo uninstallable.
- When the host bumps the contract version, re-vendor the sources, bump
  `apiVersion` in `plugin/extension.json`, and cut a new package version.
- The unit tests in `:plugin` assert `extension.json.apiVersion ==
  ProviderApi.VERSION` on every run, so drift fails the build loudly.
