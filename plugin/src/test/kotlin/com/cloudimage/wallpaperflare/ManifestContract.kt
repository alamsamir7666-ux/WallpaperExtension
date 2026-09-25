package com.cloudimage.wallpaperflare

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Test-side mirror of the host app's `ExtensionManifest`
 * (com.cloudimage.extensions.core.ExtensionManifest in the Cloudimage repo).
 *
 * The host is the authority at install and load time; this mirror repeats
 * its exact schema, JSON configuration and validation rules (ported
 * verbatim from host v1.0.5) so this repository can assert that its
 * package manifest satisfies the host before anything is published.
 */
@Serializable
data class ManifestContract(
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Int,
    val author: String = "",
    val description: String = "",
    val apiVersion: Int,
    val entryClass: String,
) {
    fun validate(): ManifestContract {
        require(ID_PATTERN.matches(id)) {
            "id must be lower-case reverse-DNS with at least two segments, was '$id'"
        }
        require(name.isNotBlank()) { "name must not be blank" }
        require(versionName.isNotBlank()) { "versionName must not be blank" }
        require(versionCode >= 1) { "versionCode must be >= 1, was $versionCode" }
        require(apiVersion >= 1) { "apiVersion must be >= 1, was $apiVersion" }
        require(ENTRY_CLASS_PATTERN.matches(entryClass)) {
            "entryClass must be a fully-qualified class name, was '$entryClass'"
        }
        return this
    }

    companion object {
        /** Reverse-DNS, lower-case, at least two segments ("cloudimage.demo"). */
        val ID_PATTERN: Regex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

        /** Fully-qualified class name for the entry point. */
        val ENTRY_CLASS_PATTERN: Regex = Regex("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+$")

        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                explicitNulls = false
            }

        fun parse(text: String): ManifestContract {
            val manifest =
                try {
                    json.decodeFromString(serializer(), text)
                } catch (e: SerializationException) {
                    throw IllegalArgumentException("manifest is not valid JSON: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("manifest is not valid JSON: ${e.message}")
                }
            return manifest.validate()
        }

        /** Loads and validates this plugin module's `extension.json`. */
        fun load(): ManifestContract {
            val file = File("extension.json")
            require(file.exists()) {
                "extension.json not found — tests must run with the :plugin module directory as working directory"
            }
            return parse(file.readText())
        }
    }
}
