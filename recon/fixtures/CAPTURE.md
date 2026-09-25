# CAPTURE.md — residential capture cookbook

The build sandbox (datacenter IP) is Cloudflare-blocked for the whole
`wallpaperflare.com` zone, so real fixtures were recovered from pages users
happened to save in public repos (see README.md provenance). Running these
commands **from your home/mobile connection** captures fresh ground truth.
Five minutes, five commands.

## Commands

Run from any directory; drop the outputs into `recon/fixtures/live/`
(create it). Use a plain browser User-Agent.

```bash
mkdir -p live && cd live

# 1. A normal search page (multi-word query exercises the `+` encoding,
#    page 1) — also reveals the real items-per-page when you count cards:
curl -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36" \
     "https://www.wallpaperflare.com/search?wallpaper=mountain+lake&page=1" -o search-live.html

# 2. Page 2 of the same search (pagination ground truth):
curl -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36" \
     "https://www.wallpaperflare.com/search?wallpaper=mountain+lake&page=2" -o search-live-p2.html

# 3. The popular-feed AJAX fragment (what popular() actually parses):
curl -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36" \
     "https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=2" -o loadmore-live.html

# 4. Detail + download pages for the first card of search-live.html
#    (replace {slug} with a `...-wallpaper-xxxxx` path from step 1):
curl -A "Mozilla/5.0" "https://www.wallpaperflare.com/{slug}" -o detail-live.html
curl -A "Mozilla/5.0" "https://www.wallpaperflare.com/{slug}/download" -o download-live.html

# 5. The two open ground-truth items (robots stance + full-res URL grammar):
curl -A "Mozilla/5.0" "https://www.wallpaperflare.com/robots.txt" -o robots-live.txt
# full-resolution derivation check — take the preview URL from detail-live.html
# (img#vimg src, c4.wallpaperflare.com/...-preview.jpg) and flip it:
#   c4 -> r4, drop "-preview"          e.g.
curl -I "https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg"
# also record what img#show_img's src actually is after JS runs (browser
# devtools on download-live.html) — expected on the w./rN CDN.
```

## What each capture unlocks

| Capture | Open question it answers |
|---|---|
| `search-live.html` | real items-per-page (count `li[itemprop=associatedMedia]`); current sort UI vocabulary |
| `search-live-p2.html` | pagination shape: same cards, new set; confirms `page=` |
| `loadmore-live.html` | exact fragment markup (bare `<li>` sequence confirmed by 4 scrapers) |
| `detail-live.html` / `download-live.html` | whether `show_img` ships a static `src` in fresh HTML; the true full-res URL grammar (`w.` vs `rN` CDN) |
| `robots-live.txt` | crawler hygiene for the Part 7 watchdog cadence |
| `r4` HEAD check | validates the c→r derivation used as the static fallback |

## After capturing

```bash
# from the repo root — contract tests must pass against live data too:
./gradlew :plugin:test
```

If a live capture breaks a contract test, that is a **site change**: update
`recon/RECON.md` (new contract) and the Part 3 parser together in one commit.
