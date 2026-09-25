package com.cloudimage.wallpaperflare

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Asserts `plugin/extension.json` satisfies the host's manifest rules.
 * The host parses and validates this exact file at install time
 * (ExtensionManifest.parse) — anything failing here is uninstallable.
 */
class ManifestContractTest {
    private val manifest: ManifestContract = ManifestContract.load()

    @Test
    fun manifestParsesAndValidatesUnderHostRules() {
        assertEquals("cloudimage.wallpaperflare", manifest.validate().id)
    }

    @Test
    fun manifestParseIgnoresUnknownKeysLikeTheHost() {
        val withFutureField =
            manifest.run {
                """
                {
                  "id": "$id",
                  "name": "$name",
                  "versionName": "$versionName",
                  "versionCode": $versionCode,
                  "author": "$author",
                  "description": "$description",
                  "apiVersion": $apiVersion,
                  "entryClass": "$entryClass",
                  "futureHostField": {"nested": [1, 2, 3]}
                }
                """.trimIndent()
            }
        assertEquals(manifest.id, ManifestContract.parse(withFutureField).id)
    }

    @Test
    fun idIsTheLockedPluginId() {
        assertEquals(WallpaperflareWallpaperProvider.PLUGIN_ID, manifest.id)
    }

    @Test
    fun apiVersionMatchesTheVendoredContractExactly() {
        assertEquals(
            com.cloudimage.provider.api.ProviderApi.VERSION,
            manifest.apiVersion,
        )
    }

    @Test
    fun entryClassIsTheLockedEntryPoint() {
        assertEquals(WallpaperflareWallpaperProvider.ENTRY_CLASS, manifest.entryClass)
    }
}
