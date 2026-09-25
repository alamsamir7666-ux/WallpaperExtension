#!/usr/bin/env bash
# Live-site probe — answers one question per URL: does wallpaperflare.com's
# Cloudflare zone serve this request, or challenge it?
#
# Purpose (recon/EMULATOR_CHECK.md triage): the Part 5 device gate reported
# the source failing. The sandbox is whole-zone Cloudflare-blocked (RECON
# §1), so UA-vs-IP isolation needs a different egress — this script runs
# from wherever CI runs it (GitHub-hosted runner) and A/B-tests the host
# app's fixed User-Agent against the extension's browser identity on the
# exact endpoints the plugin requests, plus the CDN grammar (c->r
# derivation) that no sandbox check can reach.
#
# Reading the results:
#   HTTP 200 + card markup            the zone serves this client shape;
#   HTTP 403 + "Just a moment..."     Cloudflare managed challenge;
#   both UAs challenged               inconclusive from datacenter IPs —
#                                     runner IPs are low-reputation;
#   BROWSER 200, APP 403              UA is the lever (the 0.4.0 fix is
#                                     aimed exactly there);
#   both 200                          the zone is open right now — an
#                                     on-device failure is NOT Cloudflare;
#                                     look at the CDN rows next.
#
# Manual diagnostic only; Part 7's watchdog owns any scheduling.

set -uo pipefail

APP_UA='Cloudimage/1.0 (Android; +https://github.com/alamsamir7666-ux/Cloud-Wallpaper)'
# MUST stay byte-identical to WallpaperflareHttp.USER_AGENT (plugin/src/main).
BROWSER_UA='Mozilla/5.0 (Linux; Android 14; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36'

POPULAR='https://www.wallpaperflare.com/index.php?c=main&m=portal_loadmore&page=1'
SEARCH='https://www.wallpaperflare.com/search?wallpaper=mountain&page=1'
# Real CDN URLs from the committed fixtures (recon/fixtures); the r{N}/path
# ones are exactly what WallpaperflareCdn.deriveOriginal produces for them.
CDN_C4_PREVIEW='https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg'
CDN_R4_ORIGINAL='https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg'
CDN_C0_PREVIEW='https://c0.wallpaperflare.com/preview/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg'
CDN_R0_ORIGINAL='https://r0.wallpaperflare.com/path/711/601/971/mountain-peaks-snow-cold-landscape-scenery.jpg'

probe() {
    local label="$1" url="$2" ua="$3"
    local body
    body=$(mktemp)
    local code
    code=$(curl -sS -m 30 -A "$ua" \
        -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8' \
        -H 'Accept-Language: en-US,en;q=0.9' \
        -o "$body" -w '%{http_code}' "$url" 2>/dev/null) || code='CURL-ERR'
    local bytes
    bytes=$(wc -c <"$body" | tr -d ' ')
    local marker=''
    if grep -qai 'just a moment\|challenge-platform\|cf-chl' "$body" 2>/dev/null; then
        marker='  <-- CLOUDFLARE CHALLENGE'
    fi
    local cards=''
    if grep -qai 'itemprop=.associatedMedia' "$body" 2>/dev/null; then
        cards='  <-- REAL CARD MARKUP'
    fi
    printf '%-26s HTTP %-8s %9s bytes%s%s\n' "$label" "$code" "$bytes" "$marker" "$cards"
    printf '    %s\n' "$(head -c 80 "$body" | tr -d '\0' | tr '\n\r' '  ')"
    rm -f "$body"
}

echo "==================================================================="
echo " wallpaperflare.com live probe  ($(date -u '+%Y-%m-%dT%H:%M:%SZ'))"
echo "==================================================================="
probe 'popular endpoint  APP UA' "$POPULAR" "$APP_UA"
probe 'popular endpoint  BROWSER UA' "$POPULAR" "$BROWSER_UA"
probe 'search endpoint   BROWSER UA' "$SEARCH" "$BROWSER_UA"
probe 'detail page       BROWSER UA' 'https://www.wallpaperflare.com/nature-turquoise-sea-beach-wallpaper-rxpa' "$BROWSER_UA"
echo "-------------------------------------------------------------------"
echo " CDN grammar (c->r derivation, RECON §4):"
echo "-------------------------------------------------------------------"
probe 'cdn c4 .. -preview.jpg' "$CDN_C4_PREVIEW" "$BROWSER_UA"
probe 'cdn r4 .. wallpaper.jpg' "$CDN_R4_ORIGINAL" "$BROWSER_UA"
probe 'cdn c0 /preview/..' "$CDN_C0_PREVIEW" "$BROWSER_UA"
probe 'cdn r0 /path/..' "$CDN_R0_ORIGINAL" "$BROWSER_UA"
echo "==================================================================="
echo " Remember: a challenged APP-UA row + green BROWSER-UA row confirms"
echo " the 0.4.0 fix target. Both-challenged is inconclusive (datacenter"
echo " IP reputation); both-green means on-device failures are NOT the"
echo " HTML endpoints — check the CDN rows."
echo "==================================================================="
