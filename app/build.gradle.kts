import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Keys live in local.properties (never committed). Debug (sideload) builds always use Google's
// public test ad IDs so they never show real ads. Release (Play) builds use Ampli's own AdMob
// app and banner IDs; these are public identifiers (they ship inside every APK), not secrets.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String, default: String = "") = localProps.getProperty(name) ?: default

android {
    namespace = "com.vikash.mp3player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vikash.ampli"
        minSdk = 24
        targetSdk = 36
        // CI passes the run number so each test build installs as an update over the last one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${prop("JAMENDO_CLIENT_ID")}\"")
        buildConfigField("String", "AUDD_API_TOKEN", "\"${prop("AUDD_API_TOKEN")}\"")
        // Help & feedback: the Ampli Owner server (empty = send by email to SUPPORT_EMAIL instead; defaults to support@ampliapps.in, repo variable overrides).
        buildConfigField("String", "OWNER_API_URL", "\"${prop("OWNER_API_URL")}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${prop("SUPPORT_EMAIL").ifBlank { "support@ampliapps.in" }}\"")
        buildConfigField("String", "REMOVE_ADS_PRODUCT_ID", "\"${prop("REMOVE_ADS_PRODUCT_ID", "ampli_pro")}\"")
        // Consumable "buy the developer a chai" tips; create both in Play Console as in-app products.
        buildConfigField("String", "TIP_SMALL_ID", "\"${prop("TIP_SMALL_ID").ifBlank { "ampli_tip_49" }}\"")
        buildConfigField("String", "TIP_LARGE_ID", "\"${prop("TIP_LARGE_ID").ifBlank { "ampli_tip_99" }}\"")
        buildConfigField("String", "TIP_XL_ID", "\"${prop("TIP_XL_ID").ifBlank { "ampli_tip_199" }}\"")
        // Closed-test period: every build unlocks Pro for testers, with no purchase. Set the repo
        // variable TESTER_PRO=false before a production release; CI refuses a production upload
        // while it is on. It also switches itself off after TESTER_PRO_UNTIL (1 Dec 2026, 00:00 IST)
        // in case an old tester build is ever promoted.
        buildConfigField("boolean", "TESTER_PRO", prop("TESTER_PRO", "true").ifBlank { "true" })
        buildConfigField("long", "TESTER_PRO_UNTIL", "1796063400000L")
    }

    // One fixed key for test APKs. Without it every CI machine signs with a new random key,
    // and Android refuses to install the next build over the previous one.
    // For test builds only: Play Store releases use your own upload key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("test-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Play upload key. CI writes it from the UPLOAD_KEYSTORE_B64 / UPLOAD_* repo secrets;
        // without them no release bundle is signed, so the public test key never reaches Play.
        if (prop("UPLOAD_STORE_FILE").isNotEmpty()) {
            create("upload") {
                storeFile = file(prop("UPLOAD_STORE_FILE"))
                storePassword = prop("UPLOAD_STORE_PASSWORD")
                keyAlias = prop("UPLOAD_KEY_ALIAS")
                keyPassword = prop("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("boolean", "ONLINE_MUSIC", "true")
        }
        // The fast test APK: same as debug (test ads, same signing key so it installs over earlier
        // builds) but not debuggable. A debuggable app runs Compose and its animations several times
        // slower, which is why test builds felt sluggish on the phone. BuildConfig.DEBUG is false here,
        // so the test "unlock Pro" switch is hidden in this build.
        create("qa") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            matchingFallbacks += listOf("debug")
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
            // R8 shrinks, optimises and obfuscates the code and drops unused resources. Keep rules
            // for our own reflection users are in proguard-rules.pro; Media3, Billing, AdMob, Cast,
            // Coil and kotlinx.serialization ship their own. The mapping file goes into the AAB, so
            // Play shows readable crash reports; CI also attaches it to the GitHub release.
            isMinifyEnabled = true
            isShrinkResources = true
            // Jamendo needs a paid licence for an ad-supported app, so v1 ships without it.
            buildConfigField("boolean", "ONLINE_MUSIC", prop("ONLINE_MUSIC_IN_RELEASE", "false"))
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID", "ca-app-pub-8097353665002950~8534221783")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("ADMOB_BANNER_ID", "ca-app-pub-8097353665002950/5945560776")}\"")
            // Blank until a Rewarded ad unit exists in AdMob; the "try Pro for 24 hours" button hides itself then.
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"${prop("ADMOB_REWARDED_ID")}\"")
            // Blank until a Native ad unit exists in AdMob; the ad card under the Now Playing controls hides itself then.
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"${prop("ADMOB_NATIVE_ID")}\"")
        }
        // Same R8 settings as release, but test ads and the debug key so it installs over the test
        // APK on a phone: the build for checking that R8 removed nothing the app needs (Ampli-N-r8.apk).
        create("r8") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Installs the baseline profiles that Compose and other AndroidX libraries ship, so a sideloaded
    // APK gets its hot paths compiled ahead of time instead of running interpreted.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.cast)
    implementation(libs.media3.hls) // internet radio streams in HLS
    implementation(libs.smbj) // network folders (SMB / Windows shares)
    implementation(libs.androidx.mediarouter)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.billing.ktx)
    implementation(libs.installreferrer)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
}
