package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.Filters
import com.cloudimage.provider.api.ProviderHttpException
import com.cloudimage.provider.api.ProviderSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The provider wiring (Part 4): every host-contract method mapped onto the
 * fixture-tested client, over the same fake `ProviderHttpClient` seam the
 * host hands over at runtime.
 *
 * What is pinned here, on top of the client's own suite:
 * - `configure()` routes ALL traffic through the received client, and
 *   calls before `configure()` fail honestly instead of crashing;
 * - the frozen filter vocabulary (RECON §5): `sorting=relevance` maps to
 *   `&sort=relevance`, everything else the host can send is honestly
 *   ignored, and the popular feed never takes a sort parameter;
 * - failures keep their meaning through the `Result` boundary: HTTP errors
 *   read as source failures, parse errors name the drift, transport errors
 *   rethrow the ORIGINAL `ProviderHttpException` (the host recovers its
 *   typed transport errors from it — offline stays offline);
 * - empty results are successes, never failures.
 */
class WallpaperflareWallpaperProviderTest {
    private val searchPage = TestFixtures.load("search-puerta-vieja.html")
    private val detailPage = TestFixtures.load("detail-nature-tropical.html")
    private val loadmoreFragment = TestFixtures.load("loadmore-fragment.html")
    private val emptyFragment = TestFixtures.load("loadmore-empty.html")
    private val emptySearch = TestFixtures.load("search-empty.html")
    private val slug = "nature-turquoise-sea-beach-water-sky-summer-travel-tropical-wallpaper-rxpa"

    private val noSettings = ProviderSettings { null }

    // ------------------------------------------------------------------
    // configure() wiring
    // ------------------------------------------------------------------

    @Test
    fun configureRoutesAllTrafficThroughTheReceivedClient() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            provider.popular(page = 1)

            assertEquals(
                listOf("https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1"),
                http.requestedUrls,
            )
        }

    @Test
    fun callsBeforeConfigureFailHonestlyOnEveryMethod() =
        runTest {
            val provider = WallpaperflareWallpaperProvider()

            val results = listOf(provider.popular(page = 1), provider.search("bikes"), provider.details(slug), provider.random())

            results.forEach { result ->
                assertTrue(result.isFailure)
                assertEquals("configure() was not called", result.exceptionOrNull()!!.message)
            }
        }

    @Test
    fun repeatedCallsStayStateless() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val first = provider.popular(page = 1).getOrThrow()
            val second = provider.popular(page = 1).getOrThrow()

            assertEquals(first.wallpapers.size, second.wallpapers.size)
            assertEquals(first.nextPage, second.nextPage)
            assertEquals(first.wallpapers.first(), second.wallpapers.first())
        }

    // ------------------------------------------------------------------
    // Popular feed
    // ------------------------------------------------------------------

    @Test
    fun popularServesTheParsedFeedWithDerivedOriginals() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val page = provider.popular(page = 1).getOrThrow()

            assertEquals(6, page.wallpapers.size)
            assertEquals(2, page.nextPage)
            val first = page.wallpapers.first()
            assertEquals("cloudimage.wallpaperflare", first.providerId)
            assertEquals(
                "https://c0.wallpaperflare.com/preview/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg",
                first.thumbUrl,
            )
            assertEquals(
                "https://r0.wallpaperflare.com/path/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg",
                first.fullUrl,
            )
        }

    @Test
    fun popularPaginatesThroughTheLoadmoreEndpoint() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            provider.popular(page = 2)

            assertEquals(
                "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=2",
                http.requestedUrls.single(),
            )
        }

    @Test
    fun popularEndsTheFeedOnAnEmptyPage() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", emptyFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val page = provider.popular(page = 9).getOrThrow()

            assertTrue(page.wallpapers.isEmpty())
            assertNull(page.nextPage)
        }

    @Test
    fun popularIgnoresFiltersTheFeedCannotExpress() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }
            val fullHostVocabulary =
                Filters.of(
                    "category" to "anime",
                    "purity" to "sfw",
                    "sorting" to "relevance",
                    "order" to "desc",
                    "seed" to "7f3a",
                )

            provider.popular(page = 1, filters = fullHostVocabulary)

            assertEquals(
                "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1",
                http.requestedUrls.single(),
            )
        }

    @Test
    fun popularMapsHttpErrorsToHonestSourceFailures() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", "blocked", status = 403) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.popular(page = 1)

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()!!.message!!
            assertTrue(message.contains("HTTP 403"))
            assertTrue(message.contains("m=portal_loadmore"))
        }

    @Test
    fun popularRethrowsTheOriginalTransportException() =
        runTest {
            val offline = ProviderHttpException("no route to host")
            val http = FakeHttpClient().apply { fail("m=portal_loadmore", offline) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.popular(page = 1)

            assertSame(offline, result.exceptionOrNull())
        }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Test
    fun searchServesRealResults() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?wallpaper=puerta+vieja&page=1", searchPage) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val page = provider.search("puerta vieja").getOrThrow()

            assertEquals(
                "https://www.wallpaperflare.com/search?wallpaper=puerta+vieja&page=1",
                http.requestedUrls.single(),
            )
            assertEquals(2, page.wallpapers.size)
            assertEquals(2, page.nextPage)
            assertEquals(3474, page.wallpapers.first().width)
        }

    @Test
    fun searchMapsRelevanceSortOntoTheSiteGrammar() =
        runTest {
            val http = FakeHttpClient().apply { respond("sort=relevance", searchPage) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            provider.search("bikes", filters = Filters.of("sorting" to "relevance"))

            assertEquals(
                "https://www.wallpaperflare.com/search?wallpaper=bikes&page=1&sort=relevance",
                http.requestedUrls.single(),
            )
        }

    @Test
    fun searchHonestlyIgnoresTheRestOfTheHostVocabulary() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?", searchPage) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }
            val unsupported =
                Filters.of(
                    "category" to "people",
                    "purity" to "sketchy",
                    "sorting" to "toplist",
                    "order" to "asc",
                    "seed" to "deadbeef",
                )

            provider.search("mountains", page = 3, filters = unsupported)

            assertEquals(
                "https://www.wallpaperflare.com/search?wallpaper=mountains&page=3",
                http.requestedUrls.single(),
            )
        }

    @Test
    fun searchOnAnEmptyResultIsASuccessfulEndOfFeed() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?", emptySearch) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val page = provider.search("zzz-no-such-thing").getOrThrow()

            assertTrue(page.wallpapers.isEmpty())
            assertNull(page.nextPage)
        }

    @Test
    fun searchOnGarbageHtmlEndsTheFeedInsteadOfFailing() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?", "<html><body><h1>maintenance</h1></body></html>") }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val page = provider.search("bikes").getOrThrow()

            assertTrue(page.wallpapers.isEmpty())
            assertNull(page.nextPage)
        }

    @Test
    fun searchMapsHttpErrorsToHonestSourceFailures() =
        runTest {
            val http = FakeHttpClient().apply { respond("search?", "blocked", status = 503) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.search("bikes")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 503"))
        }

    // ------------------------------------------------------------------
    // Details
    // ------------------------------------------------------------------

    @Test
    fun detailsServesTheFullPayload() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/$slug/download", TestFixtures.syntheticDownloadPage())
                    respond(slug, detailPage)
                }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val details = provider.details(slug).getOrThrow()

            assertEquals(
                "https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg",
                details.wallpaper.fullUrl,
            )
            assertEquals(
                "https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg",
                details.wallpaper.thumbUrl,
            )
            assertEquals(3840, details.wallpaper.width)
            assertEquals(2160, details.wallpaper.height)
            assertEquals("3840x2160", details.resolution)
            assertEquals(1279262L, details.fileSizeBytes)
            assertEquals("https://www.wallpaperflare.com/$slug", details.sourceUrl)
            assertTrue(details.wallpaper.tags.contains("nature"))
        }

    @Test
    fun detailsPropagatesHttpFailures() =
        runTest {
            val http = FakeHttpClient().apply { respond("wallpaperflare.com/", "blocked", status = 403) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.details(slug)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 403"))
        }

    @Test
    fun detailsReportsSiteDriftAsAFailure() =
        runTest {
            val http =
                FakeHttpClient().apply {
                    respond("/$slug/download", "gone", status = 404)
                    respond(slug, "<html><body><h1 class=\"view_h1\">HD wallpaper: drift</h1></body></html>")
                }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.details(slug)

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()!!.message!!
            assertTrue(message.contains("frozen contract"))
        }

    // ------------------------------------------------------------------
    // Random
    // ------------------------------------------------------------------

    @Test
    fun randomServesDrawnCards() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", loadmoreFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val wallpapers = provider.random().getOrThrow()

            assertEquals(6, wallpapers.size)
            assertEquals(1, http.requestedUrls.size)
            assertTrue(http.requestedUrls.single().contains("m=portal_loadmore&page="))
        }

    @Test
    fun randomOnAnEmptyEverywhereIsASuccessfulEmptyList() =
        runTest {
            val http = FakeHttpClient().apply { respond("m=portal_loadmore", emptyFragment) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val wallpapers = provider.random().getOrThrow()

            assertTrue(wallpapers.isEmpty())
        }

    @Test
    fun randomRethrowsTheOriginalTransportException() =
        runTest {
            val timeout = ProviderHttpException("connect timed out")
            val http = FakeHttpClient().apply { fail("m=portal_loadmore", timeout) }
            val provider = WallpaperflareWallpaperProvider().apply { configure(http, noSettings) }

            val result = provider.random()

            assertSame(timeout, result.exceptionOrNull())
        }
}
