package com.cloudimage.wallpaperflare

/**
 * CDN URL grammar for wallpaperflare.com, frozen in `recon/RECON.md` §4.
 *
 * Cards only ever reference small preview files:
 * - search/loadmore thumbs:  `https://c{N}…/preview/{a}/{b}/{c}/{base}.jpg`
 * - detail/related previews: `https://c4…/wallpaper/{a}/{b}/{c}/{base}-preview.jpg`
 *   (`-thumb.jpg` siblings live in the same directory).
 *
 * The full-resolution file is not referenced anywhere in the static markup.
 * The production-proven derivation (AyGemuy `api-wudysoft`, see RECON §7)
 * converts a preview URL into the original file URL:
 * - `/wallpaper/…-preview.jpg` → drop `-preview`, host `c{N}` becomes `r{N}`;
 * - `/preview/….jpg`           → the `/preview/` segment becomes `/path/`,
 *   host `c{N}` becomes `r{N}`.
 *
 * This derivation is the one part of the site contract that cannot be
 * verified from the build sandbox (the whole CDN zone is Cloudflare-blocked
 * there, RECON §1); Part 5's on-device check is the live exit criterion.
 * Unknown URL shapes return null so callers fall back to the preview they
 * actually hold — a lower-quality but honest URL, never a guess.
 */
internal object WallpaperflareCdn {
    /** `…/wallpaper/{a}/{b}/{c}/{base}-preview.jpg` (or `-thumb.jpg`). */
    private val WALLPAPER_TREE_FILE =
        Regex(
            """^https://c(\d+)\.wallpaperflare\.com(/wallpaper/\d+/\d+/\d+/[a-z0-9-]+)-(?:preview|thumb)\.(jpg|png|webp)$""",
        )

    /** `…/preview/{a}/{b}/{c}/{base}.jpg` (search/loadmore card thumbs). */
    private val PREVIEW_TREE_FILE =
        Regex(
            """^https://c(\d+)\.wallpaperflare\.com/preview/(\d+/\d+/\d+/[a-z0-9-]+\.(?:jpg|png|webp))$""",
        )

    /**
     * Derives the original-file URL for [previewUrl], or null when it does
     * not match either known CDN grammar (the caller then keeps the preview
     * URL itself rather than inventing one).
     */
    fun deriveOriginal(previewUrl: String): String? {
        WALLPAPER_TREE_FILE.find(previewUrl)?.let { m ->
            val (host, path, ext) = m.destructured
            return "https://r$host.wallpaperflare.com$path.$ext"
        }
        PREVIEW_TREE_FILE.find(previewUrl)?.let { m ->
            val (host, rest) = m.destructured
            return "https://r$host.wallpaperflare.com/path/$rest"
        }
        return null
    }
}
