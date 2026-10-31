plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: the upload key and its passwords come from Gradle properties kept outside the
// repository – in ~/.gradle/gradle.properties, or as environment variables ORG_GRADLE_PROJECT_<name>.
// Without them, release builds are simply not signed.
val releaseSigningProperties = listOf(
    "pickelhaubeStoreFile", "pickelhaubeStorePassword", "pickelhaubeKeyAlias", "pickelhaubeKeyPassword",
)
val releaseSigning = releaseSigningProperties.map { providers.gradleProperty(it).orNull }
if (releaseSigning.any { it != null } && releaseSigning.any { it == null }) {
    val missing = releaseSigningProperties.filterIndexed { i, _ -> releaseSigning[i] == null }
    logger.warn("Release signing incomplete, release builds stay unsigned: ${missing.joinToString()} not set")
}

android {
    namespace = "org.rustygnome.pickelhaube"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.rustygnome.pickelhaube"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // "play" is the build for Google Play, which allows reading the call log only for the default
    // phone app: it leaves out the phone's own calls (and READ_CALL_LOG). "full" is everything else.
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            buildConfigField("boolean", "CALL_LOG", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "CALL_LOG", "false")
        }
    }

    signingConfigs {
        if (releaseSigning.all { it != null }) {
            create("release") {
                val (path, storePass, alias, keyPass) = releaseSigning.map { it!! }
                storeFile = file(path)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The language can be chosen in the app, so an app bundle must keep all translations.
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // XmlPullParser implementation for parser tests (Android's own is only a stub on the JVM).
    testImplementation(libs.kxml2)
    // org.json implementation for the settings file tests (Android's own is only a stub on the JVM).
    testImplementation(libs.org.json)
}
