package com.cloudimage.wallpaperflare

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * URL grammar over the frozen contract (`recon/RECON.md` §2): every request
 * the client can ever make is pinned here, including the `+` space encoding
 * the real search page proved and the single supported sort value.
 */
class WallpaperflareUrlsTest {
    @Test
    fun searchEncodesSpacesAsPlusLikeTheSitesOwnForm() {
        assertEquals(
            "https://www.wallpaperflare.com/search?wallpaper=puerta+vieja&page=1",
            WallpaperflareUrls.search("puerta vieja", page = 1),
        )
    }

    @Test
    fun searchPutsThePageNumberInTheGrammarPosition() {
        assertEquals(
            "https://www.wallpaperflare.com/search?wallpaper=bikes&page=3",
            WallpaperflareUrls.search("bikes", page = 3),
        )
    }

    @Test
    fun searchEncodesUnicodeQueries() {
        assertEquals(
            "https://www.wallpaperflare.com/search?wallpaper=%E6%9D%B1%E4%BA%AC&page=1",
            WallpaperflareUrls.search("\u6771\u4EAC", page = 1),
        )
    }

    @Test
    fun searchEncodesReservedCharactersSafely() {
        assertEquals(
            "https://www.wallpaperflare.com/search?wallpaper=a%26b%3Dc&page=1",
            WallpaperflareUrls.search("a&b=c", page = 1),
        )
    }

    @Test
    fun searchOmitsTheSortParameterByDefault() {
        val url = WallpaperflareUrls.search("bikes", page = 1)
        assertEquals(false, url.contains("sort="))
    }

    @Test
    fun searchAppendsTheOnlyConfirmedSortValue() {
        assertEquals(
            "https://www.wallpaperflare.com/search?wallpaper=bikes&page=2&sort=relevance",
            WallpaperflareUrls.search("bikes", page = 2, sort = WallpaperflareSort.RELEVANCE),
        )
    }

    @Test
    fun popularIsTheLoadmoreAjaxEndpoint() {
        assertEquals(
            "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1",
            WallpaperflareUrls.popular(page = 1),
        )
        assertEquals(
            "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=7",
            WallpaperflareUrls.popular(page = 7),
        )
    }

    @Test
    fun detailIsTheSlugPath() {
        assertEquals(
            "https://www.wallpaperflare.com/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa",
            WallpaperflareUrls.detail("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa"),
        )
    }

    @Test
    fun downloadAppendsTheDownloadSegment() {
        assertEquals(
            "https://www.wallpaperflare.com/bikes-motorcycle-wallpaper-abcde/download",
            WallpaperflareUrls.download("bikes-motorcycle-wallpaper-abcde"),
        )
    }
}
