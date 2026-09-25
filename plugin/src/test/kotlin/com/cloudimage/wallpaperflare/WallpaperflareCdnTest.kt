package com.cloudimage.wallpaperflare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The CDN full-resolution derivation (RECON §4, AyGemuy production grammar):
 * two known preview shapes convert to `r{N}` originals; anything else must
 * yield null so callers keep the honest preview URL instead of a guess.
 * Live verification is Part 5's exit criterion — the CDN zone is blocked
 * from the build sandbox.
 */
class WallpaperflareCdnTest {
    @Test
    fun derivesTheOriginalFromADetailPagePreview() {
        assertEquals(
            "https://r4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper.jpg",
            WallpaperflareCdn.deriveOriginal(
                "https://c4.wallpaperflare.com/wallpaper/37/62/515/nature-turquoise-sea-beach-wallpaper-preview.jpg",
            ),
        )
    }

    @Test
    fun derivesTheOriginalFromARelatedCardThumbVariant() {
        assertEquals(
            "https://r4.wallpaperflare.com/wallpaper/371/373/249/nature-beach-sea-water-wallpaper.jpg",
            WallpaperflareCdn.deriveOriginal(
                "https://c4.wallpaperflare.com/wallpaper/371/373/249/nature-beach-sea-water-wallpaper-thumb.jpg",
            ),
        )
    }

    @Test
    fun derivesTheOriginalFromASearchCardThumb() {
        assertEquals(
            "https://r0.wallpaperflare.com/path/714/604/978/door-wooden-door-puerta-puerta-de-madera.jpg",
            WallpaperflareCdn.deriveOriginal(
                "https://c0.wallpaperflare.com/preview/714/604/978/door-wooden-door-puerta-puerta-de-madera.jpg",
            ),
        )
    }

    @Test
    fun keepsTheHostNumberThroughTheDerivation() {
        val derived =
            WallpaperflareCdn.deriveOriginal(
                "https://c7.wallpaperflare.com/wallpaper/1/2/3/some-wallpaper-preview.jpg",
            )
        assertEquals("https://r7.wallpaperflare.com/wallpaper/1/2/3/some-wallpaper.jpg", derived)
    }

    @Test
    fun returnsNullForUnknownCdnShapes() {
        assertNull(WallpaperflareCdn.deriveOriginal("https://c4.wallpaperflare.com/other/1/2/3/a.jpg"))
        assertNull(WallpaperflareCdn.deriveOriginal("https://example.com/preview/1/2/3/a.jpg"))
        assertNull(WallpaperflareCdn.deriveOriginal("not a url"))
        assertNull(WallpaperflareCdn.deriveOriginal(""))
    }

    @Test
    fun returnsNullWhenTheWallpaperTreeFileHasNoSizeSuffix() {
        assertNull(
            WallpaperflareCdn.deriveOriginal("https://c4.wallpaperflare.com/wallpaper/1/2/3/some-wallpaper.jpg"),
        )
    }
}
