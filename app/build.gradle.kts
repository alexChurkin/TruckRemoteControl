import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Real AdMob ids are kept out of the repository; test ids are used without the file
val adProperties = Properties().apply {
    val file = file("ad.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val admobAppId = adProperties.getProperty("admobAppId", "ca-app-pub-3940256099942544~3347511713")
val interstitialAdId = adProperties.getProperty("interstitialAdId", "ca-app-pub-3940256099942544/1033173712")

android {
    namespace = "com.alexchurkin.truckremote"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alexchurkin.truckremote"
        minSdk = 24
        targetSdk = 37
        versionCode = 34
        versionName = "1.22"

        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        manifestPlaceholders["admobAppId"] = admobAppId

        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " (debug)"
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("boolean", "USE_LOG", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "INTERSTITIAL_AD_ID", "\"$interstitialAdId\"")
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
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.ads)
    implementation(libs.billing)

    testImplementation(libs.junit)
}
