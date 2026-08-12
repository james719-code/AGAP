import java.io.FileInputStream
import java.util.Locale
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * A deliberately small source-level guard for the design-system rules. It is
 * scoped to screen source files because theme declarations and reusable
 * components legitimately define colours and dimensions themselves.
 */
data class DesignSystemBaselineKey(
    val rule: String,
    val path: String,
    val value: String
)

private fun normalizeHexColor(value: String): String =
    "0x" + value.removePrefix("0x").removePrefix("0X").uppercase(Locale.ROOT)

/**
 * Exception list for intentional legacy screen-source values. Keep this empty
 * where possible; every entry should be justified in design-system.md.
 */
private val designSystemLegacyBaseline = emptySet<DesignSystemBaselineKey>()

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    FileInputStream(localPropsFile).use { localProps.load(it) }
}

fun getProp(key: String, defaultValue: String = ""): String {
    return (project.findProperty(key) as? String)
        ?: localProps.getProperty(key)
        ?: System.getenv(key)
        ?: defaultValue
}

android {
    namespace = "com.travelingsalesman.agap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.travelingsalesman.agap"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"${getProp("AGAP_API_URL", "http://10.0.2.2:8080/api/v1")}\"")
        buildConfigField("String", "WS_URL", "\"${getProp("AGAP_WS_URL", "ws://10.0.2.2:8080/ws")}\"")
        buildConfigField("String", "DEFAULT_TARGET_ID", "\"${getProp("DEFAULT_TARGET_ID", "05-05-12-01-04::4a8f9c12-3b4e-4f10-912a-87c2b3f10111")}\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(getProp("AGAP_KEYSTORE_PATH", "keystore/agap.jks"))
            storePassword = getProp("AGAP_KEYSTORE_PASSWORD", "")
            keyAlias = getProp("AGAP_KEY_ALIAS", "agap")
            keyPassword = getProp("AGAP_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        val releaseKeystore = file(System.getenv("AGAP_KEYSTORE_PATH") ?: "keystore/agap.jks")
        if (releaseKeystore.exists()) {
            signingConfig = signingConfigs.getByName("release")
        } else {
            signingConfig = signingConfigs.getByName("debug")
        }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.compose.foundation)
    // Core Android & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation.compose)

    // Compose BOM & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking - Retrofit & OkHttp (REST + WebSocket)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Map UI - OSMDroid
    implementation(libs.osmdroid.android)

    // Database - Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Preferences DataStore
    implementation(libs.datastore.preferences)

    // Fused Location Provider
    implementation(libs.play.services.location)
    implementation(libs.play.services.nearby)

    // Security & Crypto
    implementation(libs.security.crypto)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Logging & Splashscreen
    implementation(libs.timber)
    implementation(libs.core.splashscreen)
    implementation(libs.zxing.core)


    // WorkManager + Hilt integration
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Cheap source-level enforcement for the design-system conventions.
 *
 * This deliberately checks only screen source: theme and shared component
 * files are the legitimate homes for token definitions. The rule rejects
 * direct ARGB colors, as well as exact spacing/shape values that already have
 * an AgapSpacing or AgapShape token.
 */
tasks.register("verifyDesignSystem") {
    group = "verification"
    description = "Rejects non-token colors, spacing, and shapes in screen source."

    doLast {
        val screensRoot = file("src/main/java/com/travelingsalesman/agap/ui/screens")
        if (!screensRoot.isDirectory) return@doLast

        val rawHexColor = Regex("""Color\s*\(\s*(0x[0-9a-fA-F]{8})\s*\)""")
        val rawTokenSpacing = Regex(
            """(?:padding|spacedBy)\s*\([^)]*\b(4|8|12|16|20|24)\.dp"""
        )
        val rawTokenShape = Regex(
            """RoundedCornerShape\s*\(\s*(8|10|12|16|20|24)\.dp\s*\)"""
        )
        val violations = mutableSetOf<DesignSystemBaselineKey>()

        screensRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { sourceFile ->
                val path = sourceFile.relativeTo(projectDir).invariantSeparatorsPath
                val source = sourceFile.readText()

                rawHexColor.findAll(source).forEach { match ->
                    violations += DesignSystemBaselineKey(
                        rule = "raw-hex-color",
                        path = path,
                        value = normalizeHexColor(match.groupValues[1])
                    )
                }
                rawTokenSpacing.findAll(source).forEach { match ->
                    violations += DesignSystemBaselineKey(
                        rule = "raw-token-spacing",
                        path = path,
                        value = "${match.groupValues[1]}.dp"
                    )
                }
                rawTokenShape.findAll(source).forEach { match ->
                    violations += DesignSystemBaselineKey(
                        rule = "raw-token-shape",
                        path = path,
                        value = "${match.groupValues[1]}.dp"
                    )
                }
            }

        val unapproved = violations - designSystemLegacyBaseline
        if (unapproved.isNotEmpty()) {
            val details = unapproved
                .sortedWith(compareBy({ it.path }, { it.rule }, { it.value }))
                .joinToString(separator = "\n") {
                    "- ${it.path}: ${it.rule} (${it.value})"
                }
            throw GradleException(
                "Design-system token violations found. Use a theme token or add a narrowly justified legacy baseline entry:\n$details"
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn("verifyDesignSystem")
}
