package com.cloudimage.wallpaperflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Part 2 — the frozen Wallpaperflare parse contract, pinned as executable
 * documentation over the committed fixtures in `recon/fixtures/`.
 *
 * The fixtures are a mix of REAL page captures (byte-identical, see the
 * provenance table in `recon/fixtures/README.md`) and synthetic files derived
 * from them. Part 3's parser is written against these exact shapes; Part 7's
 * drift watchdog re-checks the selectors against the live site.
 *
 * Grammar frozen here (evidence: 6 real captures + 10 independent scrapers,
 * cross-confirmed — see `recon/RECON.md`):
 * - card:      `li[itemprop=associatedMedia]` -> `a[itemprop=url][href]` (slug
 *              URL) + `img[data-src]` (cN CDN thumbnail) + `.res` ("WxHpx") +
 *              `meta[itemprop=contentSize]` + `figcaption`
 * - search:    `ul.gallery#gallery` container, form `action=/search` with
 *              `wallpaper`/`mobile`/`width`/`height` params, `sort=relevance`
 * - detail:    `a.link_btn` -> `{slug}/download`, `{slug}/crop`, `img#vimg`
 *              (c4 preview), `.o_tips` size texts, `ul#tagul` tag links,
 *              related gallery `ul.flex-images#flow`
 * - download:  `section[itemprop=primaryImageOfPage]` with `img#show_img`
 *              (src set by JS — may be absent in static HTML!) and
 *              `img#dld_thumb` (static c4 preview src — the derivation
 *              anchor), `div.dld_info` dimensions
 */
class FixtureContractTest {
    private companion object {
        const val SITE = "https://www.wallpaperflare.com"

        /** The eight documented resilience mutations in cards-edge-cases.html. */
        val EDGE_CASES =
            listOf(
                "normal",
                "no-data-src",
                "no-res-span",
                "src-not-data-src",
                "unicode-caption",
                "entity-caption",
                "no-href-anchor",
                "truncated-card",
            )

        /** Detail-page slug: `…-wallpaper-{short code}`. */
        val SLUG_URL = Regex("""https://www\.wallpaperflare\.com/[a-z0-9-]+-wallpaper-[a-z0-9]+""")

        /** Search-page lazy thumbnail on the cN CDN. */
        val THUMB_URL = Regex("""https://c\d+\.wallpaperflare\.com/preview/\d+/\d+/\d+/[a-z0-9-]+\.jpg""")

        /** Detail/download-page preview file on the c4 CDN. */
        val C4_PREVIEW_URL = Regex("""https://c4\.wallpaperflare\.com/wallpaper/\d+/\d+/\d+/[a-z0-9-]+-preview\.jpg""")

        /** Dimension text as rendered in `.res` spans and o_tips. */
        val DIMS = Regex("""\d+x\d+px""")

        /** A card, from opening li to closing li (tolerates nested markup). */
        val CARD = Regex("""<li[^>]*itemprop="associatedMedia"[^>]*>.*?</li>""", RegexOption.DOT_MATCHES_ALL)

        fun fixture(name: String): String = File(fixturesDir(), name).readText()

        /** Walks up from the test working dir to the repo's recon/fixtures. */
        fun fixturesDir(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val candidate = File(dir, "recon/fixtures")
                if (candidate.isDirectory) return candidate
                dir = dir?.parentFile
            }
            error("recon/fixtures not found; run tests from the repo")
        }

        fun cards(html: String): List<String> = CARD.findAll(html).map { it.value }.toList()
    }

    // ------------------------------------------------------------------
    // REAL search page (query "puerta vieja" — 2 genuine result cards)
    // ------------------------------------------------------------------

    @Test
    fun searchPageHasTheGalleryContainerContract() {
        val html = fixture("search-puerta-vieja.html")
        assertTrue(html.contains("""class="gallery""""))
        assertTrue(html.contains("""id="gallery""""))
        assertTrue(html.contains("""itemtype="http://schema.org/ImageGallery""""))
    }

    @Test
    fun searchPageCardsCarryTheFullCardContract() {
        val cards = cards(fixture("search-puerta-vieja.html"))
        assertTrue("expected >= 1 card, got ${cards.size}", cards.isNotEmpty())
        for (card in cards) {
            assertTrue("card missing a[itemprop=url]", card.contains("""itemprop="url""""))
            assertTrue("card missing slug href", SLUG_URL.containsMatchIn(card))
            assertTrue("card missing lazy img", card.contains("data-src="))
            assertTrue("card missing .res dims", Regex("""class="res"""").containsMatchIn(card))
            assertTrue("card missing contentSize meta", card.contains("""itemprop="contentSize""""))
            assertTrue("card missing figcaption", card.contains("<figcaption"))
            assertTrue("card missing thumb URL", THUMB_URL.containsMatchIn(card))
            assertTrue("card missing dimension text", DIMS.containsMatchIn(card))
        }
    }

    @Test
    fun searchPageFormAndParamsMatchTheUrlGrammar() {
        val html = fixture("search-puerta-vieja.html")
        assertTrue(html.contains("""action="https://www.wallpaperflare.com/search""""))
        assertTrue(html.contains("""name="wallpaper""""))
        assertTrue(html.contains("""name="mobile""""))
        assertTrue(html.contains("""name="width""""))
        assertTrue(html.contains("""name="height""""))
        // plus-encoded query, as really rendered
        assertTrue(html.contains("wallpaper=puerta+vieja"))
        // the only sort value confirmed by real markup
        assertTrue(html.contains("&sort=relevance"))
    }

    // ------------------------------------------------------------------
    // REAL detail page (nature/tropical, 3840x2160, 50 related cards)
    // ------------------------------------------------------------------

    @Test
    fun detailPageExposesTheOriginalDownloadChain() {
        val html = fixture("detail-nature-tropical.html")
        assertTrue(
            "link_btn original download anchor missing",
            Regex("""class="link_btn aq mt20"[^>]*href="[^"]*/download"""").containsMatchIn(html),
        )
        assertTrue(html.contains("Download original wallpaper: 3840x2160px"))
        assertTrue("crop link missing", Regex("""href="[^"]*/crop"""").containsMatchIn(html))
        assertTrue("resolution links missing", Regex("""/download/\d+x\d+"""").findAll(html).count() >= 20)
    }

    @Test
    fun detailPageCarriesPreviewAndMetadataAnchors() {
        val html = fixture("detail-nature-tropical.html")
        assertTrue("img#vimg missing", html.contains("""id="vimg""""))
        assertTrue("vimg must be the itemprop contentUrl", html.contains("""itemprop="contentUrl""""))
        assertTrue("vimg c4 preview src missing", C4_PREVIEW_URL.containsMatchIn(html))
        assertTrue("h1.view_h1 missing", html.contains("""class="view_h1""""))
        assertTrue("o_tips size missing", Regex("""Size:\s*\d+x\d+px""").containsMatchIn(html))
        assertTrue("o_tips file size missing", Regex("""File size:\s*[\d.]+[KMG]?B""").containsMatchIn(html))
    }

    @Test
    fun detailPageRelatedGalleryUsesTheSameCardContract() {
        val html = fixture("detail-nature-tropical.html")
        assertTrue("flex-images container missing", html.contains("""class="flex-images""""))
        val cards = cards(html)
        assertEquals("detail page related gallery should have 50 cards", 50, cards.size)
        for (card in cards) {
            assertTrue("related card missing slug href", SLUG_URL.containsMatchIn(card))
            assertTrue("related card missing data-w/data-h", Regex("""data-w="\d+" data-h="\d+"""").containsMatchIn(card))
            assertTrue("related card missing lazy img", card.contains("data-src="))
        }
    }

    @Test
    fun detailPageTagLinksFollowTheSearchGrammar() {
        val html = fixture("detail-nature-tropical.html")
        assertTrue("ul#tagul missing", html.contains("""id="tagul""""))
        val tagLinks = Regex("""href="https://www\.wallpaperflare\.com/search\?wallpaper=[a-z+]+"""").findAll(html).count()
        assertTrue("expected >= 5 tag links, got $tagLinks", tagLinks >= 5)
    }

    // ------------------------------------------------------------------
    // REAL download page (bikes 4096x2304; show_img src is JS-set)
    // ------------------------------------------------------------------

    @Test
    fun downloadPageHasThePrimaryImageSection() {
        val html = fixture("download-bikes.html")
        assertTrue(
            "section[itemprop=primaryImageOfPage] missing",
            html.contains("""itemprop="primaryImageOfPage""""),
        )
        assertTrue("img#show_img missing", Regex("""<img[^>]*id="show_img"""").containsMatchIn(html))
        assertTrue("h1 title missing", html.contains("4096x2304px (4K) free download"))
    }

    @Test
    fun downloadPageShowImgSrcIsJavascriptSetInStaticHtml() {
        // The CAVEAT that drives the fallback design: show_img has NO src in
        // the static markup — the site assigns it client-side. A parser must
        // not rely on show_img alone; dld_thumb + the c->r derivation is the
        // static fallback (see recon/RECON.md, CDN grammar).
        val html = fixture("download-bikes.html")
        val showImg = Regex("""<img[^>]*id="show_img"[^>]*>""").find(html)?.value
        assertTrue("show_img tag not found", showImg != null)
        assertFalse("show_img unexpectedly has a static src", showImg!!.contains("src="))
    }

    @Test
    fun downloadPageThumbAndInfoAreStaticAndParseable() {
        val html = fixture("download-bikes.html")
        val dldThumb = Regex("""<img[^>]*id="dld_thumb"[^>]*>""").find(html)?.value
        assertTrue("img#dld_thumb missing", dldThumb != null)
        assertTrue(
            "dld_thumb must carry a static c4 preview src",
            dldThumb!!.contains("src=\"https://c4.wallpaperflare.com/wallpaper/"),
        )
        assertTrue("dld_info block missing", html.contains("""class="dld_info""""))
        assertTrue("current photo size text missing", html.contains("Current photo size:"))
        assertTrue("width value missing", html.contains("""<span itemprop="value">4096</span>"""))
        assertTrue("height value missing", html.contains("""<span itemprop="value">2304</span>"""))
        // the wallpaper id used by the site's own download JS
        assertTrue("dlgbtn id missing", html.contains("dlgbtn('udswe')"))
    }

    @Test
    fun downloadPageRelatedKeywordsFollowTheSearchGrammar() {
        val html = fixture("download-bikes.html")
        assertTrue("ul.tag related keywords missing", html.contains("""class="related_list""""))
        assertTrue(
            "related keyword links missing",
            Regex("""href="https://www\.wallpaperflare\.com/search\?wallpaper=[a-z]+"""").containsMatchIn(html),
        )
    }

    // ------------------------------------------------------------------
    // SYNTHETIC loadmore fragment (bare <li> sequence, no ul wrapper)
    // ------------------------------------------------------------------

    @Test
    fun loadmoreFragmentIsABareCardSequence() {
        val html = fixture("loadmore-fragment.html")
        assertFalse("fragment must not wrap cards in a ul", html.contains("<ul"))
        val cards = cards(html)
        assertEquals("fragment should carry 6 cards", 6, cards.size)
        for (card in cards) {
            assertTrue("fragment card missing slug href", SLUG_URL.containsMatchIn(card))
            assertTrue("fragment card missing thumb", THUMB_URL.containsMatchIn(card))
            assertTrue("fragment card missing dims", DIMS.containsMatchIn(card))
        }
    }

    // ------------------------------------------------------------------
    // SYNTHETIC end-of-feed pages (pagination stop conditions)
    // ------------------------------------------------------------------

    @Test
    fun searchEmptyPageHasZeroCardsButKeepsTheShell() {
        val html = fixture("search-empty.html")
        assertEquals("end-of-feed search page must have zero cards", 0, cards(html).size)
        assertFalse(html.contains("""itemprop="associatedMedia""""))
        assertTrue("search form shell must survive", html.contains("""action="https://www.wallpaperflare.com/search""""))
    }

    @Test
    fun loadmoreEmptyFragmentIsEmpty() {
        val html = fixture("loadmore-empty.html")
        assertTrue("empty fragment should be blank", html.isBlank())
        assertEquals(0, cards(html).size)
    }

    // ------------------------------------------------------------------
    // SYNTHETIC edge-case cards (parser resilience requirements)
    // ------------------------------------------------------------------

    @Test
    fun edgeCaseFixtureDocumentsAllEightMutations() {
        val html = fixture("cards-edge-cases.html")
        for (case in EDGE_CASES) {
            assertTrue("missing edge case '$case'", html.contains("""data-case="$case""""))
        }
    }

    @Test
    fun edgeCaseBaselineCardStillSatisfiesTheContract() {
        val html = fixture("cards-edge-cases.html")
        val normal = Regex("""<li data-case="normal">.*?</li>""", RegexOption.DOT_MATCHES_ALL).find(html)?.value
        assertTrue("normal case not found", normal != null)
        assertTrue(SLUG_URL.containsMatchIn(normal!!))
        assertTrue(THUMB_URL.containsMatchIn(normal))
        assertTrue(DIMS.containsMatchIn(normal))
    }

    @Test
    fun edgeCaseMutationsActuallyMutate() {
        val html = fixture("cards-edge-cases.html")
        val case = { name: String ->
            Regex("""<li data-case="$name">.*?</li>""", RegexOption.DOT_MATCHES_ALL).find(html)?.value.orEmpty()
        }
        assertFalse("no-data-src case still has data-src", case("no-data-src").contains("data-src="))
        assertFalse("no-res-span case still has .res", case("no-res-span").contains("""class="res""""))
        assertFalse("src-not-data-src case still lazy", case("src-not-data-src").contains("data-src="))
        assertTrue("src-not-data-src must keep a src", case("src-not-data-src").contains("src="))
        assertTrue("unicode caption lost its text", case("unicode-caption").contains("東京"))
        assertTrue("entity caption lost its entity", case("entity-caption").contains("&amp;"))
        assertFalse("no-href-anchor case still has href", Regex("""itemprop="url"[^>]*href""").containsMatchIn(case("no-href-anchor")))
        assertFalse("truncated card looks complete", case("truncated-card").contains("</figure>"))
    }
}
