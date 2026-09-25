package com.cloudimage.wallpaperflare

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 1 stub behaviour: the provider must be usable — empty but honest —
 * before the Wallpaperflare client exists, and stateless across calls.
 */
class StubBehaviorTest {
    private val provider = WallpaperflareWallpaperProvider()

    @Test
    fun popularReturnsAnEmptySuccessfulPage() =
        runTest {
            val result = provider.popular(page = 1)

            assertTrue(result.isSuccess)
            val page = result.getOrThrow()
            assertTrue(page.wallpapers.isEmpty())
            assertNull(page.nextPage)
        }

    @Test
    fun searchReturnsAnEmptySuccessfulPageForAnyQuery() =
        runTest {
            val result = provider.search(query = "mountains", page = 2)

            assertTrue(result.isSuccess)
            val page = result.getOrThrow()
            assertTrue(page.wallpapers.isEmpty())
            assertNull(page.nextPage)
        }

    @Test
    fun detailsAndRandomFailHonestlyBeforePart4() =
        runTest {
            assertTrue(provider.details("some-slug").isFailure)
            assertTrue(provider.random().isFailure)
        }

    @Test
    fun stubIsStatelessAcrossCalls() =
        runTest {
            val first = provider.popular(page = 1).getOrThrow()
            val second = provider.popular(page = 1).getOrThrow()

            assertTrue(first.wallpapers.isEmpty())
            assertTrue(second.wallpapers.isEmpty())
            assertEquals(first.nextPage, second.nextPage)
        }
}
