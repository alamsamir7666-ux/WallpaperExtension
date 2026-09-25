package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.Capability
import com.cloudimage.provider.api.ContentRating
import com.cloudimage.provider.api.Filters
import com.cloudimage.provider.api.Page
import com.cloudimage.provider.api.ProviderMeta
import com.cloudimage.provider.api.Wallpaper
import com.cloudimage.provider.api.WallpaperDetails
import com.cloudimage.provider.api.WallpaperProvider

/**
 * Wallpaperflare provider — Part 1 stub.
 *
 * The identity, policy and packaging contract is final from this part on;
 * only the data plumbing arrives later (client in Part 3, wiring in Part 4):
 * - [meta] must stay in sync with `extension.json` (tests enforce it),
 * - [capabilities] declares only what is confirmed against the site:
 *   RANDOM stays out until a workable mechanism is verified (see
 *   recon/RECON.md), because a declared capability the host cannot use
 *   is a broken UI promise,
 * - the provider is rated SFW and hard-clamps itself to safe requests —
 *   no sketchy/NSFW category path is ever requested and every item is
 *   reported as SFW (the host re-enforces this per item anyway).
 *
 * The stub returns empty successful pages so the package installs, loads
 * and degrades gracefully before the client exists; [details] and [random]
 * fail honestly instead of pretending.
 */
class WallpaperflareWallpaperProvider : WallpaperProvider {
    override val meta =
        ProviderMeta(
            id = PLUGIN_ID,
            name = "Wallpaperflare",
            versionName = "0.1.0",
            author = "alamsamir7666-ux",
            contentRating = ContentRating.SFW,
            language = "en",
            description = "Wallpapers from wallpaperflare.com - popular feed, search and filters.",
        )

    override val capabilities: Set<Capability> =
        setOf(
            Capability.POPULAR,
            Capability.SEARCH,
            Capability.FILTERS,
        )

    override suspend fun popular(
        page: Int,
        filters: Filters,
    ): Result<Page> = emptyPage()

    override suspend fun search(
        query: String,
        page: Int,
        filters: Filters,
    ): Result<Page> = emptyPage()

    override suspend fun details(id: String): Result<WallpaperDetails> =
        Result.failure(NotImplementedError("Wallpaperflare details arrive in Part 4"))

    override suspend fun random(): Result<List<Wallpaper>> = Result.failure(NotImplementedError("Wallpaperflare does not support random"))

    private fun emptyPage(): Result<Page> = Result.success(Page(wallpapers = emptyList(), nextPage = null))

    companion object {
        const val PLUGIN_ID = "cloudimage.wallpaperflare"
        const val ENTRY_CLASS = "com.cloudimage.wallpaperflare.WallpaperflareWallpaperProvider"
    }
}
