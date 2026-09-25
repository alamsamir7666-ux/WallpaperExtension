package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.ProviderHttpClient
import com.cloudimage.provider.api.ProviderHttpException
import com.cloudimage.provider.api.ProviderHttpResponse
import java.io.File

/**
 * The shared test kit: the committed fixtures in `recon/fixtures/`, a
 * synthetic download page modeled on the real bikes capture, and the
 * deterministic fake of the host's HTTP seam.
 *
 * The same files pin the frozen parse contract in `FixtureContractTest`
 * (Part 2); the client tests (Part 3) and the provider wiring tests
 * (Part 4) run against them unchanged.
 */
internal object TestFixtures {
    fun load(name: String): String = File(fixturesDir(), name).readText()

    /** Walks up from the test working dir to the repo's recon/fixtures. */
    private fun fixturesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val candidate = File(dir, "recon/fixtures")
            if (candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        error("recon/fixtures not found; run tests from the repo")
    }

    /**
     * A download page modeled on the real bikes capture: `show_img` WITHOUT
     * a static src (the JS-set caveat, RECON §3.3) and the always-static
     * `dld_thumb` derivation anchor, pointing at the nature wallpaper's
     * CDN tree. Pass [showImgSrc] to simulate a server that serves the
     * original statically.
     */
    fun syntheticDownloadPage(showImgSrc: String? = null): String {
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
 * Deterministic fake of the host's HTTP seam — the same boundary the host
 * uses at runtime. Routes are matched by URL substring in registration
 * order, every request is recorded, and unrouted URLs fail the test loudly
 * instead of slipping through.
 */
internal class FakeHttpClient : ProviderHttpClient {
    val requestedUrls = mutableListOf<String>()
    val requestedHeaders = mutableListOf<Map<String, String>>()
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
        requestedHeaders += headers
        val route = routes.firstOrNull { url.contains(it.match) } ?: error("no route for $url")
        return when (route) {
            is Route.Respond -> ProviderHttpResponse(route.status, emptyMap(), route.body.toByteArray(Charsets.UTF_8))
            is Route.Fail -> throw route.error
        }
    }

    /** The header map every recorded request carried, for header-pinning tests. */
    fun singleHeaderSet(): Map<String, String> {
        check(requestedHeaders.isNotEmpty()) { "no requests were made" }
        check(requestedHeaders.distinct().size == 1) {
            "requests carried different header sets: $requestedHeaders"
        }
        return requestedHeaders.first()
    }
}
