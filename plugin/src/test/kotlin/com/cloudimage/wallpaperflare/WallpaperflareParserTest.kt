package com.cloudimage.wallpaperflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser against the committed fixtures — real captures, synthetic
 * loadmore/empty shapes and the eight edge-case mutations. These tests are
 * the executable version of the frozen DOM contract (`recon/RECON.md` §3):
 * if the site drifts, these fail before anything ships.
 */
class WallpaperflareParserTest {
    // ------------------------------------------------------------------
    // REAL search page (query "puerta vieja" — 2 genuine cards)
    // ------------------------------------------------------------------

    @Test
    fun parsesBothRealSearchCardsWithFullFields() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("search-puerta-vieja.html"))

        assertEquals(2, cards.size)
        val first = cards[0]
        assertEquals("door-wooden-door-puerta-puerta-de-madera-old-door-puerta-vieja-wallpaper-edxqo", first.slug)
        assertEquals("https://c0.wallpaperflare.com/preview/714/604/978/door-wooden-door-puerta-puerta-de-madera.jpg", first.thumbUrl)
        assertEquals(3474, first.width)
        assertEquals(2468, first.height)
        assertEquals("door, wooden door, puerta, puerta de madera, old door, puerta vieja", first.title)
        assertEquals("1.99MB", first.fileSizeText)

        val second = cards[1]
        assertEquals("cartagena-colombia-fachada-wall-paredes-puertas-door-wallpaper-eazua", second.slug)
        assertEquals("https://c0.wallpaperflare.com/preview/535/273/271/cartagena-colombia-fachada-wall.jpg", second.thumbUrl)
        assertEquals(6000, second.width)
        assertEquals(4000, second.height)
        assertEquals("2.91MB", second.fileSizeText)
    }

    @Test
    fun searchCardsCarryTheKeywordsTagList() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("search-puerta-vieja.html"))

        val tags = cards[0].tags
        assertTrue("first tag should be 'door', was ${tags.firstOrNull()}", tags.first() == "door")
        assertTrue("expected the full keyword list (44 tags), got ${tags.size}", tags.size == 44)
        assertTrue(tags.contains("puerta vieja"))
        assertTrue(cards[1].tags.contains("colombia"))
    }

    // ------------------------------------------------------------------
    // REAL detail page (nature/tropical 3840x2160)
    // ------------------------------------------------------------------

    @Test
    fun parsesTheDetailPageTitlePreviewSizeAndFileSize() {
        val detail = WallpaperflareParser.parseDetailPage(TestFixtures.load("detail-nature-tropical.html"))

        assertEquals("nature, turquoise, sea, beach, water, sky, summer, travel, tropical", detail.title)
        assertEquals(
            "https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg",
            detail.previewUrl,
        )
        assertEquals(3840, detail.width)
        assertEquals(2160, detail.height)
        assertEquals("1.22MB", detail.fileSizeText)
    }

    @Test
    fun parsesTheDetailPageTagList() {
        val detail = WallpaperflareParser.parseDetailPage(TestFixtures.load("detail-nature-tropical.html"))

        assertEquals(40, detail.tags.size)
        assertEquals("nature", detail.tags.first())
        assertTrue(detail.tags.contains("tropical"))
    }

    @Test
    fun parsesTheRelatedGalleryWithTheMicrodataDimensionShape() {
        val html = TestFixtures.load("detail-nature-tropical.html")
        val related = WallpaperflareParser.parseCards(html)

        assertEquals("the detail page's related gallery holds 50 cards", 50, related.size)
        val first = related[0]
        assertEquals("nature-beach-sea-water-ocean-turquoise-sand-coast-travel-wallpaper-rrke", first.slug)
        assertEquals("https://c4.wallpaperflare.com/wallpaper/371/373/249/nature-beach-sea-water-wallpaper-thumb.jpg", first.thumbUrl)
        assertEquals(1920, first.width)
        assertEquals(1080, first.height)
        assertEquals("nature, beach, sea, water, ocean, turquoise, sand, coast, travel", first.title)
        assertEquals("394.33KB", first.fileSizeText)
    }

    // ------------------------------------------------------------------
    // REAL download page (bikes 4096x2304; show_img src is JS-set)
    // ------------------------------------------------------------------

    @Test
    fun downloadPageShowImgCarriesNoSrcInStaticHtml() {
        val download = WallpaperflareParser.parseDownloadPage(TestFixtures.load("download-bikes.html"))

        assertNull("show_img src is assigned by JavaScript — the frozen caveat", download.showImgUrl)
        assertEquals(
            "https://c4.wallpaperflare.com/wallpaper/362/277/203/bikes-motorcycle-1920x1080-cool-wallpaper-preview.jpg",
            download.thumbUrl,
        )
        assertEquals(4096, download.width)
        assertEquals(2304, download.height)
    }

    @Test
    fun downloadPageShowImgIsReadWhenTheSiteServesItStatically() {
        // PeskyPotato's scraper reads show_img src after a plain fetch —
        // some page variants carry it statically. The parser prefers it.
        val html =
            """
            <section itemprop="primaryImageOfPage">
              <img itemprop="contentUrl" id="show_img" src="https://r4.wallpaperflare.com/wallpaper/1/2/3/full.jpg">
              <img itemprop="thumbnail" id="dld_thumb" src="https://c4.wallpaperflare.com/wallpaper/1/2/3/base-preview.jpg">
            </section>
            """.trimIndent()

        val download = WallpaperflareParser.parseDownloadPage(html)

        assertEquals("https://r4.wallpaperflare.com/wallpaper/1/2/3/full.jpg", download.showImgUrl)
        assertEquals("https://c4.wallpaperflare.com/wallpaper/1/2/3/base-preview.jpg", download.thumbUrl)
    }

    // ------------------------------------------------------------------
    // SYNTHETIC loadmore fragment and end-of-feed pages
    // ------------------------------------------------------------------

    @Test
    fun parsesTheBareLoadmoreFragment() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("loadmore-fragment.html"))

        assertEquals(6, cards.size)
        assertEquals("mountain-peaks-snow-cold-landscape-scenery-wallpaper-mqxpa", cards[0].slug)
        assertEquals("https://c0.wallpaperflare.com/preview/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg", cards[0].thumbUrl)
        assertEquals(1921, cards[0].width)
        assertEquals(1081, cards[0].height)
        assertEquals("northern-lights-aurora-borealis-stars-sky-wallpaper-auroa", cards[5].slug)
    }

    @Test
    fun endOfFeedPagesYieldZeroCards() {
        assertEquals(0, WallpaperflareParser.parseCards(TestFixtures.load("search-empty.html")).size)
        assertEquals(0, WallpaperflareParser.parseCards(TestFixtures.load("loadmore-empty.html")).size)
        assertEquals(0, WallpaperflareParser.parseCards("").size)
    }

    // ------------------------------------------------------------------
    // SYNTHETIC edge-case cards (resilience requirements)
    // ------------------------------------------------------------------

    @Test
    fun skipsCardsWithoutIdentityOrThumbnailAndKeepsTheRest() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("cards-edge-cases.html"))

        // 8 mutations: no-data-src and no-href-anchor are unusable and must
        // be skipped; the other six (including the truncated card) parse.
        assertEquals(6, cards.size)
        assertTrue(cards.all { it.slug == "edge-case-base-card-normal-wallpaper-edge1" })
    }

    @Test
    fun edgeCaseMissingResYieldsNullDimensionsButAUsableCard() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("cards-edge-cases.html"))

        // Card order follows the fixture: normal, no-res-span,
        // src-not-data-src, unicode-caption, entity-caption, truncated-card.
        assertNull(cards[1].width)
        assertNull(cards[1].height)
        assertEquals("edge-case-base-card-normal", cards[1].title)
    }

    @Test
    fun edgeCaseEagerSrcFallsBackToThePlainSrcAttribute() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("cards-edge-cases.html"))

        assertEquals(
            "https://c0.wallpaperflare.com/preview/717/607/977/edge-case-base-card-normal.jpg",
            cards[2].thumbUrl,
        )
    }

    @Test
    fun edgeCaseUnicodeAndEntityCaptionsDecode() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("cards-edge-cases.html"))

        assertEquals("\u6771\u4EAC \u30BF\u30EF\u30FC \u591C\u666F & cityscape", cards[3].title)
        assertEquals("rock & roll, fish & chips, caf\u00E9", cards[4].title)
    }

    @Test
    fun edgeCaseTruncatedCardParsesWithoutCrashing() {
        val cards = WallpaperflareParser.parseCards(TestFixtures.load("cards-edge-cases.html"))

        val truncated = cards[5]
        assertEquals("edge-case-base-card-normal-wallpaper-edge1", truncated.slug)
        assertEquals(1927, truncated.width)
        assertEquals("edge-case-base-card-normal", truncated.title)
    }

    @Test
    fun truncatedHttpBodyYieldsOnlyCompleteCards() {
        val body = TestFixtures.load("search-puerta-vieja.html").substring(0, 7000)

        val cards = WallpaperflareParser.parseCards(body)

        assertTrue("cards from a truncated body must be a prefix of the full set", cards.size <= 2)
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    @Test
    fun parsesFileSizeTextIntoBytes() {
        assertEquals(4096L, WallpaperflareParser.parseFileSizeBytes("4096B"))
        assertEquals(403793L, WallpaperflareParser.parseFileSizeBytes("394.33KB"))
        assertEquals(2086666L, WallpaperflareParser.parseFileSizeBytes("1.99MB"))
        assertEquals(1279262L, WallpaperflareParser.parseFileSizeBytes("1.22MB"))
        assertEquals(3051356L, WallpaperflareParser.parseFileSizeBytes("2.91MB"))
    }

    @Test
    fun rejectsUnparseableFileSizeText() {
        assertNull(WallpaperflareParser.parseFileSizeBytes("huge"))
        assertNull(WallpaperflareParser.parseFileSizeBytes(""))
    }

    @Test
    fun decodesNamedAndNumericEntitiesInOnePass() {
        assertEquals("& < > \" '", WallpaperflareParser.decodeEntities("&amp; &lt; &gt; &quot; &#39;"))
        assertEquals("\u00E9 \u00E8 \u00E7", WallpaperflareParser.decodeEntities("&eacute; &egrave; &ccedil;"))
        assertEquals("\u00E9", WallpaperflareParser.decodeEntities("&#xe9;"))
        assertEquals("left &ampersand; alone", WallpaperflareParser.decodeEntities("left &ampersand; alone"))
        assertEquals("&lt;", WallpaperflareParser.decodeEntities("&amp;lt;"))
    }
}
