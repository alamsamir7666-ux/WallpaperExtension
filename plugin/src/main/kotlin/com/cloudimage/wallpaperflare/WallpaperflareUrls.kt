package com.cloudimage.wallpaperflare

import java.net.URLEncoder

/**
 * Sort values the site's search endpoint understands, frozen in
 * `recon/RECON.md` §2. `sort=relevance` is the only value confirmed by real
 * markup (the "sort by relevance" link on the saved search page); the host's
 * other sorting vocabulary (toplist/date/random) has no site equivalent and
 * is honestly ignored instead of being mapped onto something unrelated.
 */
enum class WallpaperflareSort {
    /** No sort parameter — the site's own default ordering. */
    NONE,

    /** `&sort=relevance`. */
    RELEVANCE,
}

/**
 * The wallpaperflare.com URL grammar, frozen in `recon/RECON.md` §2.
 *
 * Every request the client makes goes through one of these builders, so the
 * grammar lives in exactly one place: drift shows up as a failing URL test,
 * not as a subtly different request. Spaces in queries encode as `+`,
 * matching the site's own form submission (real capture:
 * `wallpaper=puerta+vieja`).
 */
internal object WallpaperflareUrls {
    const val SITE = "https://www.wallpaperflare.com"

    /** Search results page; [query] is form-encoded (spaces become `+`). */
    fun search(
        query: String,
        page: Int,
        sort: WallpaperflareSort = WallpaperflareSort.NONE,
    ): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sortParam =
            when (sort) {
                WallpaperflareSort.NONE -> ""
                WallpaperflareSort.RELEVANCE -> "&sort=relevance"
            }
        return "$SITE/search?wallpaper=$encoded&page=$page$sortParam"
    }

    /**
     * Popular-feed AJAX fragment (`index.php?c=main&m=portal_loadmore`).
     *
     * The endpoint returns bare `li` cards (no `ul` wrapper), one page per
     * request, and is the production-proven pagination route for the popular
     * feed — PeskyPotato's scraper walks pages 1..N through it directly
     * (see RECON §7); Anning01's draws random pages from it.
     */
    fun popular(page: Int): String = "$SITE/index.php?c=main&m=portal_loadmore&page=$page"

    /** Detail page for a wallpaper slug, e.g. `…-wallpaper-rxpa`. */
    fun detail(slug: String): String = "$SITE/$slug"

    /** Original-file download page for a wallpaper slug. */
    fun download(slug: String): String = "$SITE/$slug/download"
}
