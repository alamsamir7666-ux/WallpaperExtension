# Fixture set — provenance and rules

The parse contract for `cloudimage.wallpaperflare` is frozen against these
files. Part 3's parser is built against them; Part 7's drift watchdog
re-checks the live site against the same shapes.

## Rules

1. **Real captures stay byte-identical.** Never reformat, indent, or
   "clean up" a real capture — the byte-level noise (ad scripts, entity
   encodings, whitespace) is exactly what the parser must survive.
2. **Synthetic files are derived, not invented.** Each synthetic file is
   generated from a real capture by `scripts/` tooling (see
   [CAPTURE.md](CAPTURE.md) for regenerating with live data), so structure
   stays faithful to the site.
3. **Fixtures are test data, not product.** Real page captures are the
   property of Wallpaperflare and are committed for parser verification
   only — they are never packaged into the plugin zip (`packageExtension`
   only ships `classes.dex` + `extension.json`) and never redistributed
   as content.
4. **Live upgrades welcome.** Drop residential captures into
   `recon/fixtures/live/` (see CAPTURE.md) and re-run the tests — they must
   pass unchanged, because the contract, not the exact bytes, is what the
   tests assert.

## Provenance

| File | Kind | Origin | Notes |
|---|---|---|---|
| `search-puerta-vieja.html` | REAL | saved by a web-dev student project (`duartech-dev/entregable`, `assets/puelta.htm`, fetched from wallpaperflare.com) | search results for "puerta vieja": form, sort link, 2 genuine cards, full page shell |
| `detail-nature-tropical.html` | REAL | saved in `frridoy/tms` (`public/uploads/20231201085234.htm`) | detail page "nature, turquoise, sea…": link_btn chain, vimg preview, o_tips metadata, 50 related cards, resolution list |
| `download-bikes.html` | REAL | saved in `huzaifacreates/Vehicle-Website-Vehiclez-` (`vehiclez/assets/images/img/bike 2.htm`) | `/download` page 4096x2304: `show_img` (JS-set src — the caveat!), `dld_thumb` static src, `dld_info`, `dlgbtn('udswe')` |
| `loadmore-fragment.html` | SYNTHETIC | generated from the real search card template | 6 bare `<li>` cards — the AJAX fragment shape (`index.php?c=main&m=portal_loadmore&page=N`) |
| `search-empty.html` | SYNTHETIC | real search page with cards stripped | end-of-feed search page: shell, zero cards |
| `loadmore-empty.html` | SYNTHETIC | whitespace only | end-of-feed AJAX fragment (pagination stop) |
| `cards-edge-cases.html` | SYNTHETIC | real card template, 8 documented mutations | resilience requirements for Part 3: missing `data-src`, missing `.res`, `src=` instead of `data-src=`, unicode + XML-entity captions, anchor without `href`, truncated markup |

Additional real captures kept outside the repo (size; re-fetchable via the
pinned-SHA script, see `recon/RECON.md` §7): three more detail pages
(`salka9/s1` skier 5472x3648, `paviebi2302/pavithra.task1` blue leather,
`CoderrSakshi/kksound` DJ mixer) — all agree with the committed detail
fixture on every selector.
