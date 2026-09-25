package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.Page
import com.cloudimage.provider.api.ProviderHttpClient
import com.cloudimage.provider.api.ProviderHttpException
import com.cloudimage.provider.api.Wallpaper
import com.cloudimage.provider.api.WallpaperDetails
import kotlin.random.Random

/** The plugin id this client stamps on every [Wallpaper] it produces. */
internal const val PROVIDER_ID = "cloudimage.wallpaperflare"

/**
 * Typed client failures, so Part 4's provider wiring can map each kind to
 * the host's surfaces honestly instead of string-matching exception text:
 * - [HttpError] — the site answered with a non-2xx status (a Cloudflare
 *   challenge from a datacenter IP looks like this; real devices do not);
 * - [TransportError] — the request never completed (connectivity, DNS);
 * - [ParseError] — a page fetched fine but lacks the minimum viable data.
 *
 * End-of-feed is NOT a failure: an empty card list is a valid page.
 */
internal sealed interface WallpaperflareFetch<out T> {
    data class Ok<T>(
        val value: T,
    ) : WallpaperflareFetch<T>

    data class HttpError(
        val url: String,
        val statusCode: Int,
    ) : WallpaperflareFetch<Nothing>

    data class TransportError(
        val url: String,
        val cause: ProviderHttpException,
    ) : WallpaperflareFetch<Nothing>

    data class ParseError(
        val url: String,
        val reason: String,
    ) : WallpaperflareFetch<Nothing>
}

/**
 * The Wallpaperflare client: URL building, fetching and parsing over the
 * frozen site contract (`recon/RECON.md`), mapped onto the host API types.
 *
 * All traffic goes through the [ProviderHttpClient] handed over by the host
 * (never a client of its own — locked decision, PLAN.md), one page at a
 * time, sequential by construction, every request carrying the browser
 * header set of [WallpaperflareHttp] — the site's Cloudflare zone
 * challenges the host's app User-Agent (RECON §8). Pagination ends when a
 * page yields zero cards — the stop condition Memorem's scraper proved in
 * production; page numbers are never guessed beyond +1.
 *
 * Grid wallpapers carry the CDN-derived original as `fullUrl`
 * (WallpaperflareCdn). The host's detail screen consumes a grid item's
 * `fullUrl` directly for apply/save/share, which is why the derivation —
 * not the small preview — is the value that matters here; `details()`
 * still upgrades via the download page's `show_img` whenever the site
 * serves it statically.
 */
internal class WallpaperflareClient(
    private val http: ProviderHttpClient,
) {
    /** The popular feed, one page at a time, via the loadmore endpoint. */
    suspend fun popular(page: Int): WallpaperflareFetch<Page> {
        val pageNumber = page.coerceAtLeast(1)
        return loadFeed(WallpaperflareUrls.popular(pageNumber), pageNumber)
    }

    /** Search results for [query], one page at a time. */
    suspend fun search(
        query: String,
        page: Int,
        sort: WallpaperflareSort = WallpaperflareSort.NONE,
    ): WallpaperflareFetch<Page> {
        val pageNumber = page.coerceAtLeast(1)
        return loadFeed(WallpaperflareUrls.search(query, pageNumber, sort), pageNumber)
    }

    /**
     * Full detail payload for a wallpaper [slug]: the detail page first
     * (title, preview, size, file size, tags), then the download page for
     * the original-file anchors. A failing download-page fetch degrades to
     * the detail page's own derivation instead of failing the call; only a
     * detail-page failure, or no image URL anywhere, is an error.
     */
    suspend fun details(slug: String): WallpaperflareFetch<WallpaperDetails> {
        val detailUrl = WallpaperflareUrls.detail(slug)
        val detailResult = fetch(detailUrl) { WallpaperflareParser.parseDetailPage(it) }
        val detail =
            when (detailResult) {
                is WallpaperflareFetch.Ok -> detailResult.value
                is WallpaperflareFetch.HttpError -> return detailResult
                is WallpaperflareFetch.TransportError -> return detailResult
                is WallpaperflareFetch.ParseError -> return detailResult
            }
        val downloadResult = fetch(WallpaperflareUrls.download(slug)) { WallpaperflareParser.parseDownloadPage(it) }
        val downloadPage = (downloadResult as? WallpaperflareFetch.Ok)?.value

        val previewUrl = detail.previewUrl ?: downloadPage?.thumbUrl
        val fullUrl =
            downloadPage?.showImgUrl
                ?: previewUrl?.let { WallpaperflareCdn.deriveOriginal(it) ?: it }
                ?: return WallpaperflareFetch.ParseError(detailUrl, "no image URL on detail or download page")
        val width = detail.width ?: downloadPage?.width
        val height = detail.height ?: downloadPage?.height

        val wallpaper =
            Wallpaper(
                id = slug,
                providerId = PROVIDER_ID,
                thumbUrl = previewUrl ?: fullUrl,
                fullUrl = fullUrl,
                title = detail.title,
                width = width,
                height = height,
                tags = detail.tags,
            )
        return WallpaperflareFetch.Ok(
            WallpaperDetails(
                wallpaper = wallpaper,
                author = null,
                resolution = width?.let { w -> height?.let { h -> "${w}x$h" } },
                fileSizeBytes = detail.fileSizeText?.let(WallpaperflareParser::parseFileSizeBytes),
                sourceUrl = detailUrl,
            ),
        )
    }

    /**
     * Random wallpapers: draws a uniformly random loadmore page
     * (1..[RANDOM_PAGE_MAX], Anning01's production mechanism, RECON §6)
     * and returns its cards; an empty draw falls back to page 1. A
     * transport failure propagates — a dead network cannot serve page 1
     * either — while an HTTP error on the random draw only triggers the
     * fallback (an out-of-range page can legitimately answer badly).
     */
    suspend fun randomWallpapers(random: Random = Random.Default): WallpaperflareFetch<List<Wallpaper>> {
        val drawnPage = random.nextInt(RANDOM_PAGE_MAX) + 1
        val drawn =
            when (val result = fetchCards(WallpaperflareUrls.popular(drawnPage))) {
                is WallpaperflareFetch.Ok -> result.value
                is WallpaperflareFetch.TransportError -> return result
                is WallpaperflareFetch.HttpError -> null
                is WallpaperflareFetch.ParseError -> null
            }
        val cards =
            if (drawn.isNullOrEmpty()) {
                when (val fallback = fetchCards(WallpaperflareUrls.popular(1))) {
                    is WallpaperflareFetch.Ok -> fallback.value
                    is WallpaperflareFetch.HttpError -> return fallback
                    is WallpaperflareFetch.TransportError -> return fallback
                    is WallpaperflareFetch.ParseError -> return fallback
                }
            } else {
                drawn
            }
        return WallpaperflareFetch.Ok(cards.map(::toWallpaper))
    }

    /** Pages below 1 are clamped to 1; pages past the end end the feed. */
    private suspend fun loadFeed(
        url: String,
        page: Int,
    ): WallpaperflareFetch<Page> =
        when (val result = fetchCards(url)) {
            is WallpaperflareFetch.Ok -> {
                val wallpapers = result.value.map(::toWallpaper)
                val nextPage = if (result.value.isEmpty()) null else page + 1
                WallpaperflareFetch.Ok(Page(wallpapers = wallpapers, nextPage = nextPage))
            }
            is WallpaperflareFetch.HttpError -> result
            is WallpaperflareFetch.TransportError -> result
            is WallpaperflareFetch.ParseError -> result
        }

    private suspend fun fetchCards(url: String): WallpaperflareFetch<List<WallpaperflareCard>> =
        fetch(url) { WallpaperflareParser.parseCards(it) }

    /** GETs [url] with the browser header set, parses on success, wraps failures. */
    private suspend fun <T> fetch(
        url: String,
        parse: (String) -> T,
    ): WallpaperflareFetch<T> =
        try {
            val response = http.get(url, WallpaperflareHttp.browserHeaders)
            if (response.isSuccessful) {
                WallpaperflareFetch.Ok(parse(response.bodyText))
            } else {
                WallpaperflareFetch.HttpError(url, response.statusCode)
            }
        } catch (error: ProviderHttpException) {
            WallpaperflareFetch.TransportError(url, error)
        }

    private fun toWallpaper(card: WallpaperflareCard): Wallpaper =
        Wallpaper(
            id = card.slug,
            providerId = PROVIDER_ID,
            thumbUrl = card.thumbUrl,
            fullUrl = WallpaperflareCdn.deriveOriginal(card.thumbUrl) ?: card.thumbUrl,
            title = card.title,
            width = card.width,
            height = card.height,
            tags = card.tags,
        )

    private companion object {
        /** Upper bound for the random loadmore draw (RECON §6). */
        const val RANDOM_PAGE_MAX = 10_000
    }
}
