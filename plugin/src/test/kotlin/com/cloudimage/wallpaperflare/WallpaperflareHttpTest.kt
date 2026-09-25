package com.cloudimage.wallpaperflare

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The browser header set every request carries — the Cloudflare contingency
 * of RECON §8. These tests exist so that a host-side change to the extra-
 * header merge order (or an accidental revert of the header plumbing) fails
 * HERE, loudly, instead of silently on devices behind the site's bot wall.
 */
class WallpaperflareHttpTest {
    private val searchPage = TestFixtures.load("search-puerta-vieja.html")
    private val detailPage = TestFixtures.load("detail-nature-tropical.html")
    private val downloadPage = TestFixtures.syntheticDownloadPage()
    private val loadmoreFragment = TestFixtures.load("loadmore-fragment.html")

    @Test
    fun userAgentIsABrowserNotTheHostApp() {
        val ua = WallpaperflareHttp.browserHeaders["User-Agent"]

        assertTrue("User-Agent must be present", ua != null)
        assertTrue(
            "User-Agent must be a Mozilla browser identity: $ua",
            ua!!.startsWith("Mozilla/5.0 (Linux; Android"),
        )
        assertTrue("User-Agent must identify Chrome: $ua", ua.contains("Chrome/"))
        assertFalse(
            "User-Agent must not be the host's app identity, which Cloudflare challenges",
            ua.startsWith("Cloudimage/"),
        )
    }

    @Test
    fun headerSetLooksLikeABrowserNavigation() {
        val headers = WallpaperflareHttp.browserHeaders

        val accept = headers["Accept"]
        assertTrue("Accept must request HTML documents: $accept", accept!!.startsWith("text/html"))
        assertTrue("Accept-Language must be present", headers["Accept-Language"]!!.startsWith("en"))
        assertFalse(
            "Accept-Encoding must stay unset: OkHttp only gunzips transparently when it adds the header itself",
            "Accept-Encoding" in headers,
        )
    }

    @Test
    fun everyRequestPathCarriesTheBrowserHeaderSet() =
        runTest {
            val http =
                FakeHttpClient()
                    .apply {
                        respond("m=portal_loadmore", loadmoreFragment)
                        respond("search?wallpaper=", searchPage)
                        respond("/download", downloadPage)
                        respond("nature-tropical", detailPage)
                    }
            val client = WallpaperflareClient(http)

            client.popular(1)
            client.search("puerta vieja", 1)
            client.details("nature-tropical-sea-beach-wallpaper-rxpa")
            client.randomWallpapers(Random(42))

            assertEquals("all four entry points fired their requests", 5, http.requestedUrls.size)
            assertEquals(
                "every single request — popular, search, detail page, download page, random — carried exactly the browser header set",
                WallpaperflareHttp.browserHeaders,
                http.singleHeaderSet(),
            )
        }
}
