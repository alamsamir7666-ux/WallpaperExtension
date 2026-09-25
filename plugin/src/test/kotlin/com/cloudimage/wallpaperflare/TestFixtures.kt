package com.cloudimage.wallpaperflare

import java.io.File

/**
 * Loads the committed fixtures in `recon/fixtures/` for the Part 3 tests.
 * The same files pin the frozen parse contract in `FixtureContractTest`
 * (Part 2); the client tests run against them unchanged.
 */
internal object TestFixtures {
    fun load(name: String): String = File(fixturesDir(), name).readText()

    /** Walks up from the test working dir to the repo's recon/fixtures. */
    private fun fixturesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val candidate = File(dir, "recon/fixtures")
            if (candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        error("recon/fixtures not found; run tests from the repo")
    }
}
