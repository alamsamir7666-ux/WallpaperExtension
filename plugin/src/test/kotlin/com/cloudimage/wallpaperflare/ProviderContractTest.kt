package com.cloudimage.wallpaperflare

import com.cloudimage.provider.api.Capability
import com.cloudimage.provider.api.ContentRating
import com.cloudimage.provider.api.WallpaperProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asserts the provider satisfies the load-time half of the host contract
 * (what ExtensionLoader does with the manifest's entryClass): the class
 * resolves, implements the interface, instantiates without arguments, and
 * its identity agrees with the package manifest.
 */
class ProviderContractTest {
    private val manifest: ManifestContract = ManifestContract.load()
    private val provider: WallpaperProvider = newProvider()

    private fun newProvider(): WallpaperProvider {
        val clazz = Class.forName(manifest.entryClass)
        assertTrue(
            "entry class must implement the provider contract",
            WallpaperProvider::class.java.isAssignableFrom(clazz),
        )
        return clazz.getDeclaredConstructor().newInstance() as WallpaperProvider
    }

    @Test
    fun entryClassResolvesAndImplementsTheContract() {
        assertEquals(
            manifest.entryClass,
            provider.javaClass.name,
        )
    }

    @Test
    fun metaAgreesWithThePackageManifest() {
        assertEquals(manifest.id, provider.meta.id)
        assertEquals(manifest.name, provider.meta.name)
        assertEquals(manifest.versionName, provider.meta.versionName)
        assertEquals(manifest.author, provider.meta.author)
    }

    @Test
    fun providerIsRatedSfw() {
        assertEquals(ContentRating.SFW, provider.meta.contentRating)
    }

    @Test
    fun declaresOnlyConfirmedCapabilities() {
        assertEquals(
            setOf(Capability.POPULAR, Capability.SEARCH, Capability.FILTERS),
            provider.capabilities,
        )
    }
}
