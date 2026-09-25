import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/*
 * Packaging pipeline ported from the host app's CloudProviderConventionPlugin
 * (build-logic/convention in the Cloudimage repo), so this standalone repo
 * produces byte-compatible extension packages without the host's build-logic:
 *
 * 1. `dexProvider` dexes the module jar with d8 (from com.android.tools:r8),
 *    with the runtime classpath on `--classpath` so the contract's classes
 *    stay OUT of the payload — the host app supplies them at runtime;
 * 2. `packageExtension` zips the dex plus this module's `extension.json`
 *    manifest into `build/outputs/extension/<id>.zip`;
 * 3. `verifyPackage` asserts the zip layout (exactly `extension.json` +
 *    `classes.dex`), re-checks manifest identity, and prints the sha256 the
 *    repository index (built in a later part) will advertise.
 *
 * Deviation from the host convention: the zip is built reproducibly
 * (normalized timestamps + file order) so rebuilding the same sources yields
 * the same sha256 — the published index stays valid across rebuilds.
 */

val d8Runtime: Configuration =
    configurations.create("d8Runtime") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    implementation(project(":provider-api"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    "d8Runtime"(libs.r8)
}

val moduleManifest = layout.projectDirectory.file("extension.json")
val dexOut = layout.buildDirectory.dir("intermediates/extension-dex")
val packageOut = layout.buildDirectory.dir("outputs/extension")
val packageId = manifestId(moduleManifest.asFile)
val jarTask = tasks.named<org.gradle.jvm.tasks.Jar>("jar")

tasks.register<Exec>("dexProvider") {
    group = "extension"
    description = "Dexes the provider jar with d8."
    dependsOn(jarTask)
    inputs.files(jarTask.map { it.outputs.files })
    inputs.files(configurations.getByName("runtimeClasspath"))
    outputs.dir(dexOut)
    doFirst {
        val androidJar = findAndroidJar(rootProject)
        val jar =
            jarTask
                .get()
                .outputs.files.files
                .single { it.extension == "jar" }
        commandLine(
            "java",
            "-cp",
            d8Runtime.resolve().joinToString(File.pathSeparator) { it.absolutePath },
            "com.android.tools.r8.D8",
            "--release",
            "--min-api",
            "26",
            "--lib",
            androidJar.absolutePath,
            "--output",
            dexOut.get().asFile.absolutePath,
        )
        configurations.getByName("runtimeClasspath").resolve().forEach {
            args("--classpath", it.absolutePath)
        }
        args(jar.absolutePath)
    }
}

tasks.register<Zip>("packageExtension") {
    group = "extension"
    description = "Packages the dexed provider plus its manifest into a distributable zip."
    dependsOn(tasks.named("dexProvider"))
    from(dexOut)
    from(moduleManifest)
    destinationDirectory.set(packageOut)
    archiveFileName.set("$packageId.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.register("verifyPackage") {
    group = "extension"
    description = "Verifies the packaged extension zip: layout, manifest identity, checksum."
    dependsOn(tasks.named("packageExtension"))
    doLast {
        val zip = packageOut.get().asFile.resolve("$packageId.zip")
        require(zip.exists()) { "package missing: ${zip.absolutePath}" }
        ZipFile(zip).use { archive ->
            val names =
                archive
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .toSet()
            require(names == setOf("extension.json", "classes.dex")) {
                "package must contain exactly extension.json + classes.dex, was $names"
            }
            val manifestText = archive.getInputStream(archive.getEntry("extension.json")).readBytes().decodeToString()
            require(manifestId(manifestText) == packageId) {
                "manifest id inside the zip must match the package file name"
            }
            val apiVersion =
                manifestText
                    .substringAfter("\"apiVersion\"")
                    .substringAfter(':')
                    .substringBefore(',')
                    .trim()
                    .toIntOrNull()
            require(apiVersion == 1) { "manifest apiVersion must be 1, was $apiVersion" }
        }
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(zip.readBytes())
                .joinToString("") { "%02x".format(it) }
        logger.lifecycle("package {} verified: {} bytes, sha256={}", zip.name, zip.length(), digest)
    }
}

/** Reads the id out of an extension.json manifest text. */
fun manifestId(manifestText: String): String =
    manifestText
        .substringAfter("\"id\"")
        .substringAfter(':')
        .substringAfter('"')
        .substringBefore('"')
        .ifBlank { error("extension.json must declare a non-blank id") }

fun manifestId(manifestFile: File): String = manifestId(manifestFile.readText())

/**
 * Locates android.jar for d8's `--lib`, in the same order as the host's
 * helper, plus this repo's fallback: `tools/android.jar`, fetched once by
 * `tools/fetch_android_jar.sh` (verification only — never shipped, never
 * committed).
 */
fun findAndroidJar(rootProject: org.gradle.api.Project): File {
    val sdkCandidates =
        sequence {
            listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT")).forEach { yield(File(it)) }
            val properties = File(rootProject.projectDir, "local.properties")
            if (properties.exists()) {
                properties
                    .readLines()
                    .firstOrNull { it.startsWith("sdk.dir=") }
                    ?.substringAfter('=')
                    ?.let { yield(File(it)) }
            }
            yield(File(System.getProperty("user.home"), "Android/Sdk"))
            yield(File("/usr/local/lib/android/sdk"))
            yield(File("/opt/android-sdk"))
        }
    for (sdkRoot in sdkCandidates) {
        val jar =
            File(sdkRoot, "platforms")
                .listFiles()
                ?.filter { it.isDirectory }
                ?.maxByOrNull { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
                ?.resolve("android.jar")
        if (jar != null && jar.exists()) {
            return jar
        }
    }
    val fallback = File(rootProject.projectDir, "tools/android.jar")
    if (fallback.exists()) {
        return fallback
    }
    error(
        "android.jar not found — set ANDROID_HOME, create local.properties with sdk.dir=, " +
            "or run tools/fetch_android_jar.sh (downloads a verification-only android.jar into tools/)",
    )
}
