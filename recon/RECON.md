# Wallpaperflare site contract — FROZEN (Part 2)

Date frozen: 2026-09-25 · Method: **6 real page captures** (recovered from
pages users saved into public GitHub repos — the sandbox IP is
Cloudflare-blocked for the whole `wallpaperflare.com` zone, including all
CDN subdomains) **cross-confirmed against 10 independent open-source
scrapers**, one of them actively maintained (RSS-Bridge). Evidence inventory
in §7.

Part 3's parser is written against this document and the committed fixtures
(`recon/fixtures/`). The contract tests
(`FixtureContractTest`) pin it as executable documentation.

## 1. Live-site access from the build sandbox

| Route | Result |
|---|---|
| `www.wallpaperflare.com` (any UA, incl. real browsers) | **403** Cloudflare IP-reputation block — not UA-based |
| CDN `c4`/`c0`/`r4`/`r0.wallpaperflare.com` | **403** (same zone block); `w.wallpaperflare.com` refuses connection |
| Wayback / Common Crawl index / archive.ph / Bing cache / r.jina.ai | unreachable or blocked from sandbox egress |
| **GitHub code search** | ✅ the recovery route: found real saved pages + 10 scraper sources (pinned SHAs) |

Implication unchanged from Part 1: real-device traffic is unaffected
(plain-`axios`/`requests` scrapers work from residential IPs). On-device
verification remains the Part 5 exit criterion; `recon/fixtures/CAPTURE.md`
is the residential cookbook for upgrading fixtures to live captures.

## 2. URL grammar (frozen)

| Purpose | URL | Evidence |
|---|---|---|
| Search | `https://www.wallpaperflare.com/search?wallpaper={query}&page={N}` | real search page + 8 scrapers |
| Search + sort | `…&sort=relevance` — the only sort value confirmed in real markup | real search page (`sort by relevance` link) |
| Search filters (site-native, not exposed in V1) | `&mobile=ok`, `&width={min}`, `&height={min}` | real search form (`remove_null()` strips empties on submit) |
| Query encoding | spaces as `+` (`wallpaper=puerta+vieja`) | real search page |
| Popular feed (AJAX) | `https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page={N}` | 4 scrapers (Memorem, PeskyPotato, Abhirup-99, Anning01) |
| Detail page | `https://www.wallpaperflare.com/{slug}` where slug = `{words}-wallpaper-{5-char id}` (e.g. `…-wallpaper-rxpa`) | real detail pages |
| Original download page | `{slug}/download` | real detail page `link_btn` |
| Sized download page | `{slug}/download/{W}x{H}` | real detail page resolution list (106 links on one page) |
| Crop page | `{slug}/crop` | real detail page |
| Resolution dialog | `/dlg-{id}` (id = slug's 5-char code) | AyGemuy scraper + `dlgbtn('udswe')` in real download page |

## 3. DOM contract (frozen)

### 3.1 Card — search pages AND detail-related galleries AND loadmore fragments

The card is the same `li` everywhere; only the container differs. **Parse
anchor: `li[itemprop="associatedMedia"]`, never the container.**

```html
<!-- search page container:  <ul …itemtype="http://schema.org/ImageGallery"
                                     class="gallery" id="gallery">
     detail related gallery: <ul … class="flex-images" id="flow">
     loadmore fragment:      bare <li> sequence, no container (body > li)      -->
<li itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject"
    class="item shadow" data-w="{thumbW}" data-h="{thumbH}">      <!-- data-w/h: detail-related only -->
  <span class="res">{W}x{H}px</span>                               <!-- search: span; detail-related: div.res with nested spans -->
  <figure>
    <meta itemprop="fileFormat"    content="image/jpeg">
    <meta itemprop="keywords"      content="tag, tag, …">
    <meta itemprop="description"   content="This HD wallpaper is about …,
                      Original wallpaper dimensions is {W}x{H}px, file size is {SIZE}">
    <meta itemprop="contentSize"   content="{SIZE}">
    <a itemprop="url" href="https://www.wallpaperflare.com/{slug}" target="_blank">
      <img class="lazy" itemprop="contentUrl" alt="{tags} HD wallpaper"
           title="{tags} HD wallpaper" width="400" height="{H}"
           data-src="https://c0.wallpaperflare.com/preview/{a}/{b}/{c}/{slug}.jpg">
    </a>
    <figcaption itemprop="caption description">{tags}</figcaption>
  </figure>
</li>
```

Extracted per card: `id` = slug, `thumbUrl` = `img[data-src]`, `width`/`height`
from `.res` text, `title` from figcaption/`img@title`, `tags` from keywords
meta (comma-split), `fileSize` from contentSize meta.

### 3.2 Detail page

| What | Selector | Notes |
|---|---|---|
| Title | `h1.view_h1` | "HD wallpaper: {tags}" |
| Original download link | `a.link_btn.aq.mt20[href$="/download"]` | anchor text: "Download original wallpaper: {W}x{H}px" |
| Crop link | `a.link_btn.view_crop[href$="/crop"]` | |
| Preview image | `img#vimg[itemprop=contentUrl]` | `src` = `c4.wallpaperflare.com/wallpaper/{a}/{b}/{c}/{slug}-preview.jpg` |
| Size / file size text | `.o_tips` | "Size: {W}x{H}px", "File size: {SIZE}" |
| Tags | `ul#tagul li a[rel=tag]` | href follows the search grammar — free tag browsing |
| Resolution list | `a[href*="/download/"]` with `class="l"` | "1920x1080" texts, `/download/{W}x{H}` hrefs |
| Related gallery | `ul.flex-images#flow` | 50 cards, §3.1 shape |

### 3.3 Download page (`{slug}/download`)

| What | Selector | Notes |
|---|---|---|
| Primary section | `section[itemprop=primaryImageOfPage]` | |
| Final image | `img#show_img[itemprop=contentUrl]` | ⚠️ **src is set by JavaScript** — absent from static HTML (real capture proves it). Three scrapers read it after a plain fetch (their captures evidently differed), so treat `show_img.src` as PRIMARY when present |
| Static thumbnail | `img#dld_thumb[itemprop=thumbnail]` | **always has a static `src`** (c4 preview) — the fallback derivation anchor |
| Dimensions | `div.dld_info` | "Current photo size: {W}x{H}px • Resolution:{tier}" + nested QuantitativeValue spans |
| Download JS | `onclick="dlgbtn('{id}')"` | the slug's 5-char id; backs the `/dlg-{id}` endpoint |
| Related keywords | `section.related_list ul.tag a` | search-grammar hrefs |

## 4. CDN grammar (frozen, one caveat)

| File | URL shape | Verified |
|---|---|---|
| Search/card thumbnail | `https://c{N}.wallpaperflare.com/preview/{a}/{b}/{c}/{slug}.jpg` | ✅ real pages |
| Detail/download preview | `https://c4.wallpaperflare.com/wallpaper/{a}/{b}/{c}/{slug}-preview.jpg` | ✅ real pages; directly downloadable (hak5 payload ships one) |
| Small thumb variant | `…/wallpaper/{a}/{b}/{c}/{slug}-thumb.jpg` | ✅ real download page |
| **Full resolution** | `https://r{N}.wallpaperflare.com/wallpaper/{a}/{b}/{c}/{slug}.jpg` — derived from the preview by `c→r` + dropping `-preview` | ⚠️ scraper-derived (AyGemuy, production use), unverifiable from sandbox (zone-blocked). Also `/preview/→/path/` on the c{N} thumb host |

**fullUrl resolution order (Part 3/4):** grid items carry the preview URL
(verified, directly fetchable); `details()` upgrades to the original via
`show_img.src` when present, else the c→r derivation from `dld_thumb`/`vimg`.
Both paths are exercised by fixtures; the live tie-break is CAPTURE.md's
`r4` HEAD check / Part 5.

## 4a. Part 3 refinements (post-freeze, new evidence)

Part 3 re-read the host app and the pinned scraper evidence before
implementing; three refinements extend — never contradict — the frozen
contract above:

1. **The host app never calls `details()` (v1.0.5).** The detail screen
   (`DetailViewModel` + `WallpaperPreview`) consumes the GRID item's
   `fullUrl` directly for preview, apply, save and share; grep across the
   host repo finds `details(` only in provider implementations and tests.
   Consequence: a grid item pointing at a small preview would silently
   apply/save low-resolution files. The Part 3 client therefore sets grid
   `fullUrl` to the **CDN-derived original** (the AyGemuy production
   grammar below) and keeps the preview as `thumbUrl` — the derivation is
   the value that matters, and Part 5's on-device check gates it. The
   `details()` upgrade path (§4 order) still exists for host versions that
   call it.
2. **The AyGemuy derivation is exact and two-shaped** (read from its
   pinned source, `wf-captures/` evidence): `…/wallpaper/{a}/{b}/{c}/{base}-preview.jpg`
   (or `-thumb.jpg`) → `https://r{N}…/wallpaper/{a}/{b}/{c}/{base}.jpg`
   (drop the size suffix, `c{N}`→`r{N}`); and search thumbs
   `…/preview/{a}/{b}/{c}/{base}.jpg` → `https://r{N}…/path/{a}/{b}/{c}/{base}.jpg`
   (`/preview/`→`/path/`, `c`→`r`). Unknown shapes must NOT be guessed —
   keep the preview URL itself (`WallpaperflareCdn.deriveOriginal`
   returns null; callers fall back).
3. **PeskyPotato (production) paginates the popular feed through
   `portal_loadmore&page=N` starting at page 1** — no homepage fetch. The
   Part 3 client follows this: `popular(page)` hits the loadmore endpoint
   for every page, including page 1. (PeskyPotato also reads `show_img`
   `src` after a plain fetch — the dual-path fullUrl design is confirmed,
   not just defensive.)

## 5. Host vocabulary mapping (frozen)

| Host filter | Wallpaperflare expression |
|---|---|
| `"sorting" = "relevance"` | `&sort=relevance` |
| `"sorting"` = toplist/date/random; `"order"`; `"seed"`; `"category"`; `"purity"` | **ignored** — the site has no equivalents (no category buckets, no purity flag); honesty over pretending |
| (future, V2) width/height/mobile | site-native form params exist — deliberately not mapped in V1 (no bespoke UI) |

`ContentRating`: provider stays SFW-clamped (site-level); note the site
itself marks items `isFamilyFriendly` in microdata on some pages — extra
defense, not relied upon.

## 6. Pagination, feed sizes, RANDOM

- **Items per page:** not pinned by any capture (real search capture has 2
  cards for a rare query; detail-related galleries hold 50). **Correctness
  does not depend on it:** end-of-feed = zero cards on the page/fragment
  (Memorem's proven stop condition). CAPTURE.md's step 1 records the real
  number for documentation.
- **Popular feed:** loadmore AJAX returns bare `li` fragments; page N via
  `&page={N}`; page 1 = homepage's own gallery (also parseable the same way).
- **RANDOM — GO.** Mechanism (Anning01/AIMedia, in production): fetch
  `portal_loadmore` with `page = random(1..10000)`, pick a random card;
  fall back to page 1 on an empty draw. Declared as RANDOM in Part 4.
- **Rate hygiene:** sequential page fetches; no page-number guessing beyond
  +1 (except the single random jump).

## 7. Evidence inventory

Real captures (committed byte-identical — see `fixtures/README.md`):
search page (query "puerta vieja"), detail page (nature/tropical 3840x2160),
download page (bikes 4096x2304). Extra real detail pages held outside the
repo (re-fetchable via pinned-SHA script under `scripts/` in the build
workspace): skier 5472x3648 (`salka9/s1`), blue leather
(`paviebi2302/pavithra.task1`), DJ mixer (`CoderrSakshi/kksound`) — selector
agreement 100%.

Scraper cross-confirmation (pinned refs, 2026-09-25):

| Implementation | Language | Confirms |
|---|---|---|
| RSS-Bridge `WallpaperflareBridge` (maintained) | PHP/XPath | figure items, `a[@itemprop="url"]`, `img/@data-src`, figcaption, img@title |
| AyGemuy `api-wudysoft` | JS/cheerio | card selectors, detail selectors (`.o_tips`, `#vimg`, `#tagul`), `/dlg-{id}`, **c→r CDN derivation** |
| Memorem `wallpaperflare_parser` | Python/BS4 | search+loadmore pagination, `ul.gallery`/`body > li`, download chain |
| PeskyPotato `scrapers` | Python/BS4 | `li[itemprop=associatedMedia]`, loadmore, `show_img` |
| theGobindSingh `anime-wallpaper-api` | TS/jsdom | search params (mobile/width/height), `ul#gallery li a` + `/download` + `show_img` |
| osamalzabidi `wallpaper-downloader` | Python/BS4+lxml | `dld_info`/`dld_thumb`/`show_img`, card metas |
| jonathanggg `MitaBot-MD` | JS/cheerio | card metas (`.res`, contentSize, fileFormat, figcaption, `img.lazy[data-src]`) |
| Bucky-26 `EASY_API_1` | JS/cheerio | `ul.gallery li[itemprop=associatedMedia]` |
| Anning01 `AIMedia` | Python | **RANDOM via loadmore random page** |
| Local clones from Part 1 recon (vishal2376, amineboucenna, Ibaraki) | Py/TS | search grammar, `+` encoding, sort param |

No discrepancy across sources on any committed selector. The only
unverified-from-sandbox items are the full-res URL grammar (§4 caveat) and
`show_img` src presence in fresh HTML — both are Part 5 / CAPTURE.md items
with fallbacks already designed in.
