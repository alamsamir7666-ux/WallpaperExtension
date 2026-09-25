package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.ProviderHttpClient
import com.cloudimage.provider.api.ProviderHttpException
import com.cloudimage.provider.api.ProviderHttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The client over a fake `ProviderHttpClient` — the same seam the host uses
 * at runtime. Real fixtures flow through the fake's responses, so URL
 * building, parsing, pagination end-detection, failure taxonomy and the
 * fullUrl resolution order are all exercised end-to-end.
 */
class WallpaperflareClientTest {
    private val searchPage = TestFixtures.load("search-puerta-vieja.html")
    private val detailPage = TestFixtures.load("detail-nature-tropical.html")
    private val loadmoreFragment = TestFixtures.load("loadmore-fragment.html")
    private val emptyFragment = TestFixtures.load("loadmore-empty.html")
    private val emptySearch = TestFixtures.load("search-empty.html")

    // ------------------------------------------------------------------
    // Popular feed
    // ------------------------------------------------------------------

    @Test
    fun popularFetchesTheLoadmoreEndpointForEveryPage() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val client = WallpaperflareClient(http)

            client.popular(1)
            client.popular(2)

            assertEquals(
                listOf(
                    "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1",
                    "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=2",
                ),
                http.requestedUrls,
            )
        }

    @Test
    fun popularParsesCardsAdvancesPaginationAndDerivesFullUrls() =
        runTest {
            val client = WallpaperflareClient(FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) })

            val result = client.popular(1)

            assertTrue(result is WallpaperflareFetch.Ok)
            val page = (result as WallpaperflareFetch.Ok).value
            assertEquals(6, page.wallpapers.size)
            assertEquals(2, page.nextPage)
            val first = page.wallpapers.first()
            assertEquals("mountain-peaks-snow-cold-landscape-scenery-wallpaper-mqxpa", first.id)
            assertEquals("cloudimage.wallpaperflare", first.providerId)
            assertEquals("https://c0.wallpaperflare.com/preview/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg", first.thumbUrl)
            assertEquals("https://r0.wallpaperflare.com/path/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg", first.fullUrl)
            assertEquals(1921, first.width)
            assertEquals(1081, first.height)
        }

    @Test
    fun popularEndOfFeedStopsPagination() =
        runTest {
            val client = WallpaperflareClient(FakeHttpClient().apply { respond("m=portal_loadmore", emptyFragment) })

            val result = client.popular(9)

            val page = (result as WallpaperflareFetch.Ok).value
            assertEquals(0, page.wallpapers.size)
            assertNull(page.nextPage)
        }

    @Test
    fun popularMapsHttpFailuresToTypedErrors() =
        runTest {
            val client = WallpaperflareClient(FakeHttpClient().apply { respond("m=portal_loadmore", "blocked", status = 403) })

            val result = client.popular(1)

            val error = result as WallpaperflareFetch.HttpError
            assertEquals(403, error.statusCode)
            assertEquals("https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1", error.url)
        }

    @Test
    fun popularWrapsTransportFailures() =
        runTest {
            val client =
                WallpaperflareClient(
                    FakeHttpClient().apply { fail("m=portal_loadmore", ProviderHttpException("no route to host")) },
                )

            val result = client.popular(1)

            val error = result as WallpaperflareFetch.TransportError
            assertEquals("no route to host", error.cause.message)
        }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Test
    fun searchBuildsTheContractUrlAndParsesRealResults() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?wallpaper=puerta+vieja&page=1", searchPage) }
            val client = WallpaperflareClient(http)

            val result = client.search("puerta vieja", page = 1)

            assertEquals(
                "https://www.wallpaperflare.com/search?wallpaper=puerta+vieja&page=1",
                http.requestedUrls.single(),
            )
            val page = (result as WallpaperflareFetch.Ok).value
            assertEquals(2, page.wallpapers.size)
            assertEquals(2, page.nextPage)
            assertEquals(3474, page.wallpapers.first().width)
        }

    @Test
    fun searchWithRelevanceSortAppendsTheOnlyConfirmedSortValue() =
        runTest {
            val http = FakeHttpClient().apply { respond("sort=relevance", searchPage) }
            val client = WallpaperflareClient(http)

            client.search("bikes", page = 2, sort = WallpaperflareSort.RELEVANCE)

            assertEquals(
                "https://www.wallpaperflare.com/search?wallpaper=bikes&page=2&sort=relevance",
                http.requestedUrls.single(),
            )
        }

    @Test
    fun searchOnAnEmptyResultPageEndsTheFeed() =
        runTest {
            val client = WallpaperflareClient(FakeHttpClient().apply { respond("search?", emptySearch) })

            val result = client.search("zzz-no-such-thing", page = 5)

            val page = (result as WallpaperflareFetch.Ok).value
            assertEquals(0, page.wallpapers.size)
            assertNull(page.nextPage)
        }

    // ------------------------------------------------------------------
    // Details
    // ------------------------------------------------------------------

    @Test
    fun detailsFetchesTheDetailPageThenTheDownloadPage() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa/download", syntheticDownloadPage())
                    respond("wallpaper-rxpa", detailPage)
                }
            val client = WallpaperflareClient(http)

            client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            assertEquals(
                listOf(
                    "https://www.wallpaperflare.com/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa",
                    "https://www.wallpaperflare.com/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa/download",
                ),
                http.requestedUrls,
            )
        }

    @Test
    fun detailsResolvesTheOriginalFromTheStaticThumbWhenShowImgIsJsSet() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa/download", syntheticDownloadPage())
                    respond("wallpaper-rxpa", detailPage)
                }
            val client = WallpaperflareClient(http)

            val result = client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            val details = (result as WallpaperflareFetch.Ok).value
            val wallpaper = details.wallpaper
            assertEquals("https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg", wallpaper.fullUrl)
            assertEquals(
                "https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg",
                wallpaper.thumbUrl,
            )
            assertEquals("nature, turquoise, sea, beach, water, sky, summer, travel, tropical", wallpaper.title)
            assertEquals(3840, wallpaper.width)
            assertEquals(2160, wallpaper.height)
            assertEquals("3840x2160", details.resolution)
            assertEquals(1279262L, details.fileSizeBytes)
            assertEquals(
                "https://www.wallpaperflare.com/nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa",
                details.sourceUrl,
            )
            assertTrue(details.wallpaper.tags.contains("nature"))
            assertNull(details.author)
        }

    @Test
    fun detailsPrefersShowImgWhenTheSiteServesItStatically() =
        runTest {
            val withStaticShowImg =
                syntheticDownloadPage(
                    showImgSrc = "https://r4.wallpaperflare.com/wallpaper/99/99/99/nature-original.jpg",
                )
            val http =
                FakeHttpClient().apply {
                    respond("rxpa/download", withStaticShowImg)
                    respond("wallpaper-rxpa", detailPage)
                }
            val client = WallpaperflareClient(http)

            val result = client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            val details = (result as WallpaperflareFetch.Ok).value
            assertEquals(
                "https://r4.wallpaperflare.com/wallpaper/99/99/99/nature-original.jpg",
                details.wallpaper.fullUrl,
            )
        }

    @Test
    fun detailsDegradesToTheDetailPageWhenTheDownloadPageFails() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/wallpaper-rxpa/download", "gone", status = 404)
                    respond("wallpaper-rxpa", detailPage)
                }
            val client = WallpaperflareClient(http)

            val result = client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            val details = (result as WallpaperflareFetch.Ok).value
            assertEquals(
                "https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg",
                details.wallpaper.fullUrl,
            )
        }

    @Test
    fun detailsPropagatesDetailPageHttpFailures() =
        runTest {
            val client =
                WallpaperflareClient(
                    FakeHttpClient().apply { respond("wallpaperflare.com/", "blocked", status = 403) },
                )

            val result = client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            assertEquals(403, (result as WallpaperflareFetch.HttpError).statusCode)
        }

    @Test
    fun detailsReportsAParseErrorWhenNoImageUrlSurvives() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/wallpaper-rxpa/download", "gone", status = 404)
                    respond("wallpaper-rxpa", "<html><body><h1 class=\"view_h1\">HD wallpaper: drift</h1></body></html>")
                }
            val client = WallpaperflareClient(http)

            val result = client.details("nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa")

            assertTrue(result is WallpaperflareFetch.ParseError)
        }

    // ------------------------------------------------------------------
    // Random
    // ------------------------------------------------------------------

    @Test
    fun randomDrawsALoadmorePageAndReturnsItsCards() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val client = WallpaperflareClient(http)

            val result = client.randomWallpapers(Random(42))

            val wallpapers = (result as WallpaperflareFetch.Ok).value
            assertEquals(6, wallpapers.size)
            assertEquals(1, http.requestedUrls.size)
            assertTrue(http.requestedUrls.single().contains("m=portal_loadmore&page="))
        }

    @Test
    fun randomFallsBackToPageOneOnAnEmptyDraw() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("portal_loadmore&page=1", loadmoreFragment)
                    respond("m=portal_loadmore", emptyFragment)
                }
            val client = WallpaperflareClient(http)

            val result = client.randomWallpapers(Random(7))

            val wallpapers = (result as WallpaperflareFetch.Ok).value
            assertEquals(6, wallpapers.size)
            assertEquals(
                "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1",
                http.requestedUrls.last(),
            )
        }

    @Test
    fun randomPropagatesTransportFailures() =
        runTest {
            val client =
                WallpaperflareClient(
                    FakeHttpClient().apply { fail("m=portal_loadmore", ProviderHttpException("timeout")) },
                )

            val result = client.randomWallpapers(Random(1))

            assertTrue(result is WallpaperflareFetch.TransportError)
        }

    @Test
    fun randomPropagatesTheFallbacksHttpFailure() =
        runTest {
            val client = WallpaperflareClient(FakeHttpClient().apply { respond("m=portal_loadmore", "nope", status = 500) })

            val result = client.randomWallpapers(Random(1))

            assertEquals(500, (result as WallpaperflareFetch.HttpError).statusCode)
        }

    // ------------------------------------------------------------------
    // A download page modeled on the real bikes capture: show_img WITHOUT a
    // static src (the JS-set caveat) and the static dld_thumb derivation
    // anchor, pointing at the nature wallpaper's CDN tree.
    // ------------------------------------------------------------------

    private fun syntheticDownloadPage(showImgSrc: String? = null): String {
        val showImg =
            if (showImgSrc == null) {
                """<img itemprop="contentUrl" id="show_img">"""
            } else {
                """<img itemprop="contentUrl" id="show_img" src="$showImgSrc">"""
            }
        return """
            <html><body>
            <section itemprop="primaryImageOfPage" itemscope itemtype="http://schema.org/ImageObject">
            $showImg
            <img itemprop="thumbnail" id="dld_thumb"
                 src="https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg">
            <div class="dld_info">
            Current photo size:
            <span itemprop="width" itemscope itemtype="http://schema.org/QuantitativeValue">
            <span itemprop="value">3840</span><meta itemprop="unitText" content="px"></span>x
            <span itemprop="height" itemscope itemtype="http://schema.org/QuantitativeValue">
            <span itemprop="value">2160</span><meta itemprop="unitText" content="px"></span>px
            &bull; Resolution:4K
            </div>
            </section>
            </body></html>
            """.trimIndent()
    }
}

/**
 * Deterministic fake of the host's HTTP seam: routes are matched by URL
 * substring in registration order, every request is recorded, and unrouted
 * URLs fail the test loudly instead of slipping through.
 */
private class FakeHttpClient : ProviderHttpClient {
    val requestedUrls = mutableListOf<String>()
    private val routes = mutableListOf<Route>()

    private sealed interface Route {
        val match: String

        data class Respond(
            override val match: String,
            val status: Int,
            val body: String,
        ) : Route

        data class Fail(
            override val match: String,
            val error: ProviderHttpException,
        ) : Route
    }

    fun respond(
        urlContains: String,
        body: String,
        status: Int = 200,
    ) {
        routes += Route.Respond(urlContains, status, body)
    }

    fun fail(
        urlContains: String,
        error: ProviderHttpException,
    ) {
        routes += Route.Fail(urlContains, error)
    }

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): ProviderHttpResponse {
        requestedUrls += url
        val route = routes.firstOrNull { url.contains(it.match) } ?: error("no route for $url")
        return when (route) {
            is Route.Respond -> ProviderHttpResponse(route.status, emptyMap(), route.body.toByteArray(Charsets.UTF_8))
            is Route.Fail -> throw route.error
        }
    }
}
