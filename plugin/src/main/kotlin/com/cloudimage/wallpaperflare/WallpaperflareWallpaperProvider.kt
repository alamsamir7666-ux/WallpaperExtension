package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.Capability
import com.cloudimage.provider.api.ContentRating
import com.cloudimage.provider.api.Filters
import com.cloudimage.provider.api.Page
import com.cloudimage.provider.api.ProviderHttpClient
import com.cloudimage.provider.api.ProviderMeta
import com.cloudimage.provider.api.ProviderSettings
import com.cloudimage.provider.api.Wallpaper
import com.cloudimage.provider.api.WallpaperDetails
import com.cloudimage.provider.api.WallpaperProvider

/**
 * Wallpaperflare provider — the complete host-contract wiring over the
 * fixture-tested [WallpaperflareClient].
 *
 * [configure] wraps the host-supplied [ProviderHttpClient] in the client
 * exactly once; every method below is then a thin translation between the
 * host contract and that client:
 * - `popular` walks the loadmore feed page by page, `search` the search
 *   grammar — grid items carry the CDN-derived original as `fullUrl`
 *   (RECON §4a: the host's detail screen consumes the grid item's `fullUrl`
 *   directly for apply/save/share);
 * - `details` serves the two-GET payload — resolution, file size, source
 *   URL, tags — degrading gracefully when the download page fails;
 * - `random` draws a loadmore page (Anning01's production mechanism,
 *   RECON §6) with the page-1 fallback.
 *
 * Filter vocabulary, frozen in RECON §5: only `"sorting" = "relevance"`
 * maps onto the site (`&sort=relevance`, confirmed in real markup);
 * toplist/date/random, order, seed, category and purity are honestly
 * ignored — the site has no equivalents, and pretending otherwise would
 * feed users mislabeled results. The popular feed takes no sort parameter
 * at all, so filters never touch it.
 *
 * Failure taxonomy: the host maps a failed [Result] onto its own
 * NetworkError surfaces, so each client failure keeps its meaning — an
 * HTTP error becomes a readable "source failed: wallpaperflare answered
 * HTTP …", a parse failure names the site drift, and a transport failure
 * rethrows the ORIGINAL [com.cloudimage.provider.api.ProviderHttpException]
 * (when the host's facade raised its typed subclass, offline stays offline
 * across the plugin boundary). Empty results are successes, never
 * failures: an empty page is a valid end of feed.
 *
 * The provider is rated SFW and hard-clamps itself to safe requests — no
 * sketchy/NSFW category path is ever requested and every item is reported
 * SFW (the host re-enforces this per item anyway). Wallpaperflare serves
 * keyless content, so [ProviderSettings] is accepted and deliberately
 * unused: there is nothing a key could unlock on this site.
 */
class WallpaperflareWallpaperProvider : WallpaperProvider {
    /** The wired client, set once by [configure]; read-only afterwards. */
    private var wired: WallpaperflareClient? = null

    override val meta =
        ProviderMeta(
            id = PLUGIN_ID,
            name = "Wallpaperflare",
            versionName = "0.4.0",
            author = "alamsamir7666-ux",
            contentRating = ContentRating.SFW,
            language = "en",
            description =
                "Wallpapers from wallpaperflare.com - popular feed, search, random picks and filters. " +
                    "Browser-identified requests pass the site's Cloudflare zone.",
        )

    override val capabilities: Set<Capability> =
        setOf(
            Capability.POPULAR,
            Capability.SEARCH,
            Capability.RANDOM,
            Capability.FILTERS,
        )

    override fun configure(
        client: ProviderHttpClient,
        settings: ProviderSettings,
    ) {
        wired = WallpaperflareClient(client)
    }

    override suspend fun popular(
        page: Int,
        filters: Filters,
    ): Result<Page> = runFetch { popular(page) }

    override suspend fun search(
        query: String,
        page: Int,
        filters: Filters,
    ): Result<Page> = runFetch { search(query, page, sortFrom(filters)) }

    override suspend fun details(id: String): Result<WallpaperDetails> = runFetch { details(id) }

    override suspend fun random(): Result<List<Wallpaper>> = runFetch { randomWallpapers() }

    /**
     * Runs one client operation and translates its typed outcome into the
     * host's [Result] surface without losing the failure's meaning.
     */
    private suspend fun <T> runFetch(operation: suspend WallpaperflareClient.() -> WallpaperflareFetch<T>): Result<T> {
        val client = wired ?: return Result.failure(IllegalStateException("configure() was not called"))
        return when (val result = operation(client)) {
            is WallpaperflareFetch.Ok -> Result.success(result.value)
            is WallpaperflareFetch.HttpError ->
                Result.failure(
                    IllegalStateException("wallpaperflare answered HTTP ${result.statusCode} for ${result.url}"),
                )
            is WallpaperflareFetch.TransportError -> Result.failure(result.cause)
            is WallpaperflareFetch.ParseError ->
                Result.failure(
                    IllegalStateException(
                        "wallpaperflare page drifted from the frozen contract (${result.reason}) for ${result.url}",
                    ),
                )
        }
    }

    /** The frozen host-vocabulary mapping — see the class KDoc and RECON §5. */
    private fun sortFrom(filters: Filters): WallpaperflareSort =
        if (filters.isSelected("sorting", "relevance")) {
            WallpaperflareSort.RELEVANCE
        } else {
            WallpaperflareSort.NONE
        }

    companion object {
        const val PLUGIN_ID = PROVIDER_ID
        const val ENTRY_CLASS = "com.cloudimage.wallpaperflare.WallpaperflareWallpaperProvider"
    }
}
