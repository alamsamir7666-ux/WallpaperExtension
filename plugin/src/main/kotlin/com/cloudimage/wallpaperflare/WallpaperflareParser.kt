package com.cloudimage.wallpaperflare

/**
 * One gallery card as frozen in `recon/RECON.md` §3.1. The same `li` shape
 * appears in search pages, detail-page related galleries and loadmore AJAX
 * fragments — the parser anchors on the card, never the container, so all
 * three parse identically.
 */
internal class WallpaperflareCard(
    val slug: String,
    val thumbUrl: String,
    val width: Int?,
    val height: Int?,
    val title: String?,
    val tags: List<String>,
    val fileSizeText: String?,
)

/** Detail-page fields (`recon/RECON.md` §3.2). */
internal class WallpaperflareDetailPage(
    val title: String?,
    val previewUrl: String?,
    val width: Int?,
    val height: Int?,
    val fileSizeText: String?,
    val tags: List<String>,
)

/**
 * Download-page fields (`recon/RECON.md` §3.3). [showImgUrl] is null when
 * the site's JavaScript has not yet assigned the `src` — the normal case in
 * static HTML (the real capture proves it), which is exactly why the parser
 * also reads the always-static `dld_thumb` preview.
 */
internal class WallpaperflareDownloadPage(
    val showImgUrl: String?,
    val thumbUrl: String?,
    val width: Int?,
    val height: Int?,
)

/**
 * Hand-rolled parser over the frozen Wallpaperflare DOM contract — no HTML
 * library, so the plugin dex stays tiny and free of third-party bindings
 * (locked decision, PLAN.md).
 *
 * Design rules, pinned by `FixtureContractTest` and the committed fixtures:
 * - cards anchor on `li[itemprop=associatedMedia]`, independent of container;
 * - a card missing its identity (`a[itemprop=url]@href` → slug) or its
 *   thumbnail (`data-src`, falling back to `src`) is skipped, not fatal —
 *   one broken card must never take a feed down;
 * - unknown optional fields (`.res` dimensions, figcaption, keywords) degrade
 *   to null/empty instead of failing the parse;
 * - every text value is HTML-entity decoded;
 * - truncated markup must not loop or crash (regex scanning is stateless).
 */
internal object WallpaperflareParser {
    // ------------------------------------------------------------------
    // Card extraction (search pages, related galleries, loadmore fragments)
    // ------------------------------------------------------------------

    /** A card, from its opening `li` to the first closing `li`. */
    private val CARD =
        Regex(
            """<li[^>]*itemprop="associatedMedia"[^>]*>.*?</li>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    /** The card's identity anchor; carries the detail-page slug in `href`. */
    private val IDENTITY_ANCHOR = Regex("""<a\s[^>]*itemprop="url"[^>]*>""")

    private val HREF = Regex("""href="([^"]+)"""")

    /** Slug: dash-joined lower-case tokens, e.g. `…-wallpaper-edxqo`. */
    private val SLUG = Regex("""[a-z0-9]+(?:-[a-z0-9]+)+""")

    /** Lazy-loaded card thumbnail (`data-src`, never `data-srcset`). */
    private val DATA_SRC = Regex("""data-src="([^"]+)"""")

    /** Eagerly-loaded card thumbnail (`src=`, the resilience fallback). */
    private val IMG_SRC = Regex("""<img[^>]*\ssrc="([^"]+)"""")

    /** Search-card dimensions: `<span class="res">WxHpx</span>`. */
    private val SPAN_RES =
        Regex(
            """<span[^>]*class="res"[^>]*>\s*(\d+)\s*x\s*(\d+)\s*px\s*</span>""",
        )

    /** Related-card dimensions: `div.res` with nested microdata spans. */
    private val DIV_RES =
        Regex(
            """<div[^>]*class="res"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val DIMENSIONS = Regex("""(\d+)\s*x\s*(\d+)""")

    private val FIGCAPTION =
        Regex(
            """<figcaption[^>]*>(.*?)</figcaption>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val IMG_TITLE = Regex("""title="([^"]*)"""")

    private val KEYWORDS_META = Regex("""<meta[^>]*itemprop="keywords"[^>]*>""")

    private val CONTENT_SIZE_META = Regex("""<meta[^>]*itemprop="contentSize"[^>]*>""")

    private val CONTENT_ATTR = Regex("""content="([^"]*)"""")

    /**
     * Parses every well-formed card in [html]. Works on full pages and bare
     * loadmore fragments alike (the card anchor is container-independent);
     * zero cards means end-of-feed, never an error.
     */
    fun parseCards(html: String): List<WallpaperflareCard> = CARD.findAll(html).mapNotNull { parseCard(it.value) }.toList()

    /** File-size value like `1.22MB`, `394.33KB`, `4096B`. */
    private val FILE_SIZE_VALUE = Regex("""(\d+(?:\.\d+)?)\s*([KMGT]?B)""")

    private val WHITESPACE = Regex("\\s+")

    private val TAG = Regex("<[^>]+>")

    /** Parses one card; null when the card lacks identity or thumbnail. */
    fun parseCard(cardHtml: String): WallpaperflareCard? {
        val anchor = IDENTITY_ANCHOR.find(cardHtml)?.value ?: return null
        val slug = extractSlug(anchor) ?: return null
        val thumbUrl = extractThumbUrl(cardHtml) ?: return null
        val dimensions = extractDimensions(cardHtml)
        return WallpaperflareCard(
            slug = slug,
            thumbUrl = thumbUrl,
            width = dimensions?.first,
            height = dimensions?.second,
            title = extractTitle(cardHtml),
            tags = extractTags(cardHtml),
            fileSizeText = extractFileSizeText(cardHtml),
        )
    }

    /** Detail-page slug from the card anchor's `href` (absolute or relative). */
    private fun extractSlug(anchorTag: String): String? {
        val href = HREF.find(anchorTag)?.groupValues?.get(1) ?: return null
        val lastSegment =
            href
                .substringBefore('#')
                .substringBefore('?')
                .trimEnd('/')
                .substringAfterLast('/')
        return lastSegment.takeIf { SLUG.matches(it) }
    }

    /**
     * The card thumbnail: `data-src` (lazy-loading, the site's normal case)
     * with a graceful fallback to an eager `src` — pinned by the
     * `src-not-data-src` edge-case fixture.
     */
    private fun extractThumbUrl(cardHtml: String): String? =
        DATA_SRC
            .find(cardHtml)
            ?.groupValues
            ?.get(1)
            ?: IMG_SRC
                .find(cardHtml)
                ?.groupValues
                ?.get(1)
                ?.let { decodeEntities(it) }

    /**
     * Dimensions from `.res`, which the site renders two ways: a plain span
     * on search/loadmore cards and a microdata `div` on related cards. Both
     * shapes are frozen in RECON §3.1; anything else degrades to null.
     */
    private fun extractDimensions(cardHtml: String): Pair<Int, Int>? {
        SPAN_RES.find(cardHtml)?.let { m ->
            val (width, height) = m.destructured
            return width.toInt() to height.toInt()
        }
        DIV_RES.find(cardHtml)?.let { m ->
            val text = stripTags(m.groupValues[1])
            DIMENSIONS.find(text)?.let { d ->
                val (width, height) = d.destructured
                return width.toInt() to height.toInt()
            }
        }
        return null
    }

    /** Card title: figcaption text, falling back to the img `title` attr. */
    private fun extractTitle(cardHtml: String): String? {
        FIGCAPTION.find(cardHtml)?.let { m ->
            decodeEntities(stripTags(m.groupValues[1])).trim().takeIf { it.isNotEmpty() }?.let { return it }
        }
        return IMG_TITLE
            .find(cardHtml)
            ?.groupValues
            ?.get(1)
            ?.removeSuffix(" HD wallpaper")
            ?.let { decodeEntities(it).trim() }
            ?.takeIf { it.isNotEmpty() }
    }

    /** Tags from the `meta[itemprop=keywords]` content, comma-split. */
    private fun extractTags(cardHtml: String): List<String> {
        val meta = KEYWORDS_META.find(cardHtml)?.value ?: return emptyList()
        val content = CONTENT_ATTR.find(meta)?.groupValues?.get(1) ?: return emptyList()
        return decodeEntities(content)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** File size text from the `meta[itemprop=contentSize]` content. */
    private fun extractFileSizeText(cardHtml: String): String? {
        val meta = CONTENT_SIZE_META.find(cardHtml)?.value ?: return null
        return CONTENT_ATTR.find(meta)?.groupValues?.get(1)
    }

    // ------------------------------------------------------------------
    // Detail page (`/{slug}`)
    // ------------------------------------------------------------------

    private val H1_TITLE =
        Regex(
            """<h1[^>]*class="view_h1"[^>]*>(.*?)</h1>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val VIMG = Regex("""<img[^>]*id="vimg"[^>]*>""")

    private val SRC_ATTR = Regex("""\ssrc="([^"]+)"""")

    /** `Size: WxHpx` — the detail page's own original-file dimensions. */
    private val SIZE_TEXT = Regex("""Size:\s*(\d+)\s*x\s*(\d+)\s*px""")

    /** `File size: 1.22MB` — the detail page's own original-file size. */
    private val FILE_SIZE_TEXT = Regex("""File size:\s*([\d.]+\s*[KMGT]?B)""")

    private val TAGUL =
        Regex(
            """<ul[^>]*id="tagul"[^>]*>(.*?)</ul>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private val ANCHOR_TEXT =
        Regex(
            """<a\s[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * Parses the detail page: title (h1 minus the `HD wallpaper: ` prefix),
     * preview image (`img#vimg`), original dimensions and file size
     * (`.o_tips` texts) and tags (`ul#tagul`). Everything is optional —
     * callers decide what a missing field means.
     */
    fun parseDetailPage(html: String): WallpaperflareDetailPage {
        val title =
            H1_TITLE
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.let { decodeEntities(stripTags(it)).trim() }
                ?.removePrefix("HD wallpaper: ")
                ?.takeIf { it.isNotEmpty() }
        val previewUrl = SRC_ATTR.find(VIMG.find(html)?.value.orEmpty())?.groupValues?.get(1)
        val dimensions =
            SIZE_TEXT.find(html)?.let { m ->
                val (width, height) = m.destructured
                width.toInt() to height.toInt()
            }
        val fileSizeText =
            FILE_SIZE_TEXT
                .find(html)
                ?.groupValues
                ?.get(1)
                ?.replace(WHITESPACE, "")
        val tags = extractTagulTags(html)
        return WallpaperflareDetailPage(
            title = title,
            previewUrl = previewUrl,
            width = dimensions?.first,
            height = dimensions?.second,
            fileSizeText = fileSizeText,
            tags = tags,
        )
    }

    /** Tags from the detail page's `ul#tagul` anchor texts. */
    private fun extractTagulTags(html: String): List<String> {
        val block =
            TAGUL
                .find(html)
                ?.groupValues
                ?.get(1)
                ?: return emptyList()
        return ANCHOR_TEXT
            .findAll(block)
            .map { decodeEntities(stripTags(it.groupValues[1])).trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    // ------------------------------------------------------------------
    // Download page (`/{slug}/download`)
    // ------------------------------------------------------------------

    private fun imgTag(
        html: String,
        id: String,
    ): String? {
        val tag = Regex("""<img[^>]*id="$id"[^>]*>""")
        return tag.find(html)?.value
    }

    private val DLD_INFO =
        Regex(
            """<div[^>]*class="dld_info"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    /**
     * Parses the download page. `img#show_img` carries the original-file URL
     * only after the site's JavaScript assigns it — in static HTML the `src`
     * is absent (frozen caveat, RECON §3.3), so [showImgUrl] is null there
     * and callers derive the original from the static `dld_thumb` preview.
     */
    fun parseDownloadPage(html: String): WallpaperflareDownloadPage {
        val showImgUrl = SRC_ATTR.find(imgTag(html, "show_img").orEmpty())?.groupValues?.get(1)
        val thumbUrl = SRC_ATTR.find(imgTag(html, "dld_thumb").orEmpty())?.groupValues?.get(1)
        val dimensions =
            DLD_INFO.find(html)?.let { m ->
                DIMENSIONS.find(stripTags(m.groupValues[1]))?.let { d ->
                    val (width, height) = d.destructured
                    width.toInt() to height.toInt()
                }
            }
        return WallpaperflareDownloadPage(
            showImgUrl = showImgUrl,
            thumbUrl = thumbUrl,
            width = dimensions?.first,
            height = dimensions?.second,
        )
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** `"1.22MB"` → bytes (binary multipliers; the site's values are approximate). */
    fun parseFileSizeBytes(text: String): Long? {
        val match = FILE_SIZE_VALUE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier =
            when (match.groupValues[2].uppercase()) {
                "B" -> 1.0
                "KB" -> 1024.0
                "MB" -> 1024.0 * 1024
                "GB" -> 1024.0 * 1024 * 1024
                "TB" -> 1024.0 * 1024 * 1024 * 1024
                else -> return null
            }
        return (value * multiplier).toLong()
    }

    private val ENTITY = Regex("""&(#[xX]?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);""")

    private val NAMED_ENTITIES =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "copy" to "\u00A9",
            "reg" to "\u00AE",
            "trade" to "\u2122",
            "hellip" to "\u2026",
            "mdash" to "\u2014",
            "ndash" to "\u2013",
            "lsquo" to "\u2018",
            "rsquo" to "\u2019",
            "ldquo" to "\u201C",
            "rdquo" to "\u201D",
            "laquo" to "\u00AB",
            "raquo" to "\u00BB",
            "bull" to "\u2022",
            "deg" to "\u00B0",
            "middot" to "\u00B7",
            "sect" to "\u00A7",
            "para" to "\u00B6",
            "euro" to "\u20AC",
            "pound" to "\u00A3",
            "yen" to "\u00A5",
            "agrave" to "\u00E0",
            "aacute" to "\u00E1",
            "acirc" to "\u00E2",
            "auml" to "\u00E4",
            "ccedil" to "\u00E7",
            "egrave" to "\u00E8",
            "eacute" to "\u00E9",
            "ecirc" to "\u00EA",
            "iuml" to "\u00EF",
            "icirc" to "\u00EE",
            "ocirc" to "\u00F4",
            "ouml" to "\u00F6",
            "ugrave" to "\u00F9",
            "uuml" to "\u00FC",
        )

    /**
     * Decodes HTML entities in one pass: the common named set above plus
     * decimal (`&#39;`) and hex (`&#x27;`) code points. Unknown entities are
     * left untouched — never a crash, never a silent drop.
     */
    fun decodeEntities(input: String): String =
        ENTITY.replace(input) { match ->
            val body = match.groupValues[1]
            val decoded =
                when {
                    body.startsWith("#x", ignoreCase = true) -> codePoint(body.substring(2), 16)
                    body.startsWith("#") -> codePoint(body.substring(1), 10)
                    else -> NAMED_ENTITIES[body]
                }
            decoded ?: match.value
        }

    private fun codePoint(
        digits: String,
        radix: Int,
    ): String? = digits.toIntOrNull(radix)?.toChar()?.toString()

    /** Removes tags and collapses whitespace — the text content of a block. */
    private fun stripTags(html: String): String = html.replace(TAG, " ").replace(WHITESPACE, " ").trim()
}
