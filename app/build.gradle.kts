import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
}

// Secrets are kept out of the repository. They come from Gradle properties (~/.gradle/gradle.properties)
// or environment variables (CI)
fun secret(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }

// Yandex Ads unit and AppMetrica key: ad.properties file or secrets.
// Without them the demo ad unit is used and analytics is disabled
val adProperties = Properties().apply {
    val file = file("ad.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val demoInterstitialAdId = "demo-interstitial-yandex"
val interstitialAdId = adProperties.getProperty("interstitialAdId")
    ?: secret("TRUCKREMOTE_INTERSTITIAL_AD_ID")
    ?: demoInterstitialAdId
val appMetricaApiKey = adProperties.getProperty("appMetricaApiKey")
    ?: secret("TRUCKREMOTE_APPMETRICA_API_KEY")
    ?: ""

// Without the keystore the release build is unsigned
val releaseKeystore = secret("TRUCKREMOTE_KEYSTORE_FILE")

android {
    namespace = "com.alexchurkin.truckremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alexchurkin.truckremote"
        minSdk = 24
        targetSdk = 37
        versionCode = 36
        versionName = "1.3"

        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = secret("TRUCKREMOTE_KEYSTORE_PASSWORD")
                keyAlias = secret("TRUCKREMOTE_KEY_ALIAS")
                keyPassword = secret("TRUCKREMOTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " (debug)"
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"$demoInterstitialAdId\"")
            // Debug sessions shouldn't get into statistics
            buildConfigField("String", "APPMETRICA_API_KEY", "\"\"")
            buildConfigField("boolean", "USE_LOG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"$interstitialAdId\"")
            buildConfigField("String", "APPMETRICA_API_KEY", "\"$appMetricaApiKey\"")
            buildConfigField("boolean", "USE_LOG", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.play.services.code.scanner)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.yandex.ads)
    implementation(libs.appmetrica)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.billing)
    implementation(libs.play.review)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Modules of AppMetrica that read the advertising identifiers (Google, Huawei) and the location:
// the app doesn't collect them (see the privacy policy), so they aren't included at all
configurations.configureEach {
    exclude(group = "io.appmetrica.analytics", module = "analytics-identifiers")
    exclude(group = "io.appmetrica.analytics", module = "analytics-location")
}

// Assets that aren't dependencies (icons) are described in config/libraries
aboutLibraries {
    collect {
        configPath = file("config")
        // Licenses are taken from the dependencies' POM files only, the build doesn't depend on GitHub API
        fetchRemoteLicense = false
    }
}

kotlin {
    compilerOptions {
        // Compiler warnings (deprecations, unchecked casts, unused values) fail the build
        allWarningsAsErrors = true
    }
}
