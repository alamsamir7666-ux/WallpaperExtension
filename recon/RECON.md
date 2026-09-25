# Wallpaperflare site contract — recon evidence

Date: 2026-09-25 · Method: live probing + cross-confirmation from four
independent open-source scrapers · Purpose: freeze the parse contract for
`cloudimage.wallpaperflare`

## 1. Live-site access from this environment

| Attempt | Result |
|---|---|
| `curl` (browser UA / no UA / curl UA / app-like UA) | **403** Cloudflare "Attention Required!" — IP-reputation block on the datacenter IP, not UA-based |
| `http://` variant | 301 → 403 |
| Headless Chrome (agent-browser) | Cloudflare "Just a moment…" challenge loop; Turnstile checkbox click does not clear it |
| Reader proxy (r.jina.ai) | 401 — proxy itself blocked for "bad IP reputation" |
| Wayback Machine | unreachable from sandbox egress (000) |
| page_reader service | "Just a moment…" challenge page |

Conclusion: the sandbox IP cannot fetch the live site. All structure knowledge
below comes from four independent scrapers (cloned under `recon/ref/`) that
agree with each other, plus search-engine indexing confirming the site is
public and serving. Residential/mobile access works — two of the reference
scrapers use plain non-browser HTTP clients (`axios` defaults) and function.

**Implication:** real fixtures must be captured from a residential connection
(Part 2, curl cookbook) or confirmed on-device in Part 5. Until then, parsers
are built against synthetic fixtures derived from this contract.

## 2. URL grammar (cross-confirmed)

| Purpose | URL | Confirmed by |
|---|---|---|
| Search | `https://www.wallpaperflare.com/search?wallpaper={query}&page={N}` | vishal2376, Memorem, Ibaraki |
| Search + sort | `…&sort=relevance` | amineboucenna |
| Homepage feed (page N) | `https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page={N}` | Memorem |
| Detail page | `https://www.wallpaperflare.com/{slug}` — slug ends in a short code, e.g. `fate-series-…-wallpaper-ynyvq` | Ibaraki (live URL in their sample) |
| Crop/customize page | `https://www.wallpaperflare.com/{slug}/crop` (contains `img#resize`) | Ibaraki |

Query encoding: spaces as `+` (Ibaraki: `replaceAll(" ", "+")` + `encodeURI`).

## 3. DOM contract

| Element | Selector | Source |
|---|---|---|
| Result card anchor | `a[itemprop='url']` — inside `ul.gallery > li` on search pages; bare `body > li` items in load-more AJAX responses | Memorem, Ibaraki, amineboucenna |
| Thumbnail | `img[data-src]` inside the anchor (lazy-load attribute, not `src`) | amineboucenna |
| Detail preview image | `section[itemprop="primaryImageOfPage"] > img#show_img` (`src`) | Memorem |
| Download button | `a.link_btn.aq.mt20` (`href`) — leads to the page/URL that serves the image via `show_img` | Memorem |
| Crop preview | `img#resize` | Ibaraki |

Download flow (Memorem's working pipeline): detail page → `a.link_btn` href →
fetch that → `img#show_img` `src` = the actual image file. The final image URL
lives on the `w.wallpaperflare.com` CDN (direct root probe of the CDN from
this IP failed — expected, same egress block).

## 4. Behavior notes

- Pagination on search pages: `page=` increments; Memorem stops when a page
  returns nothing. Exact items-per-page unknown (to capture in Part 2).
- `sort=relevance` is one confirmed value; the full sort vocabulary and any
  category/tag browse URLs (e.g. top/anime listings) are Part 2 captures.
- No official/public API exists; no JSON endpoints surfaced by any scraper —
  the load-more endpoint returns HTML fragments.
- The site tolerates non-browser UAs from residential IPs (plain `axios`
  clients work). Cloudflare here is IP-reputation mode, which matters for the
  CI watchdog (GitHub runners may see 403 → treat as inconclusive) but not
  for real app users.

## 5. Reference scrapers (cloned under `recon/ref/`)

- `vishal2376/wallpaperflare-downloader` — Python, `search?wallpaper={q}`
- `Memorem/wallpaperflare_parser` — Python/aiohttp+BS4, the most complete:
  search + loadmore pagination, card selectors, detail/download chain
- `amineboucenna/wallpaperflare-search-app` — Node/axios+cheerio search app;
  confirms `data-src` thumbnails and `sort=relevance`
- `Ibaraki-Scraping/WallpaperFlare` — TypeScript; real detail-page URL sample
  and the `/crop` page

## 6. Open questions for Part 2 (live capture checklist)

1. Items per page on search & loadmore (page-size for `Page.nextPage` math)
2. Full `sort` vocabulary exposed by the UI (map to host `sorting` filter)
3. Category/tag browse URLs — do dedicated listing pages exist
   (nature/anime/…)? Map to host `category` filter if so
4. Detail page: where dimensions, resolution list, and file size live
   (for `WallpaperDetails`), and the exact original-image URL grammar
5. Whether a random/shuffle endpoint exists (RANDOM capability go/no-go)
6. robots.txt stance (unreachable from here — capture it) and any visible
   ToS notes for scraper hygiene
7. Confirm `data-src` vs `src` on real cards, and the CDN URL shape
   (`w.wallpaperflare.com/…`) for thumb vs full
