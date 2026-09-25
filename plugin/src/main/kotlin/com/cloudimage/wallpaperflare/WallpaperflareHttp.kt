package com.cloudimage.wallpaperflare

/**
 * The header set every wallpaperflare.com request carries.
 *
 * The site sits behind Cloudflare bot management that challenges the host's
 * fixed app User-Agent (`Cloudimage/1.0 (Android; +repo)`) — a live probe of
 * the popular endpoint answered a managed challenge ("Just a moment...")
 * even from clean reader egress, and the first on-device run reported the
 * source failing (RECON §8). Browser traffic is what the zone is configured
 * to serve.
 *
 * The host's HTTP facade applies extra headers AFTER its own User-Agent
 * (`CloudimageHttpClient.getRaw`: `.header("User-Agent", app)` then one
 * `.header(name, value)` per extra), and OkHttp's `header()` replaces
 * same-named headers — so a plugin CAN present a browser identity
 * per-request without touching the host app. The provider contract's doc
 * comment ("User-Agent ... cannot be overridden") does not match the
 * implementation's merge order; this object relies on the implementation,
 * and `WallpaperflareClientTest` pins the headers so a host-side change to
 * the merge order fails loudly here instead of silently on devices.
 *
 * `Accept-Encoding` is deliberately absent: OkHttp transparently requests
 * gzip and decompresses ONLY when the request carries no Accept-Encoding of
 * its own.
 */
internal object WallpaperflareHttp {
    /**
     * A plain current Android Chrome identity. The exact Chrome version is
     * cosmetic — the browser-vs-app shape of the string is what Cloudflare
     * scores — but it stays plausible and current, not a years-old fossil.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-A536B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    /**
     * Sent with every GET: a browser User-Agent plus the headers a real
     * Chrome navigation always carries. `Sec-Fetch-*`/client hints are
     * omitted on purpose — they cannot be made consistent with OkHttp's
     * HTTP/2 fingerprint and add nothing but inconsistency.
     */
    val browserHeaders: Map<String, String> =
        mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
}
