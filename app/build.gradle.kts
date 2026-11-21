import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val generatedIconRes = layout.buildDirectory.dir("generated/app-icon-res")
val downloadAppIcon by tasks.registering {
    outputs.file(generatedIconRes.map { it.file("drawable-nodpi/app_icon.png") })
    doLast {
        val output = generatedIconRes.get().file("drawable-nodpi/app_icon.png").asFile
        output.parentFile.mkdirs()
        val iconUrl = "https://raw.githubusercontent.com/Morepp461/Mm4savedataeditor/fix/release-signing-and-icon-upload-folder/branding/app-icon-upload/SAVE%20DATA_20261121_122401_0000.png"
        URL(iconUrl).openStream().use { input ->
            output.outputStream().use { target -> input.copyTo(target) }
        }
        check(output.length() > 10_000) { "Downloaded app icon is missing or incomplete" }
    }
}

val releaseKeystorePath = System.getenv("MM4_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("MM4_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("MM4_KEY_ALIAS")
val releaseKeyPassword = System.getenv("MM4_KEY_PASSWORD")
val hasPrivateReleaseKey = !releaseKeystorePath.isNullOrBlank()
android {
    namespace = "com.morepp.mm4savedataeditor"
    compileSdk = 35
    sourceSets.getByName("main").res.srcDir(generatedIconRes)
    defaultConfig {
        applicationId = "com.morepp.mm4savedataeditor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (hasPrivateReleaseKey) {
            create("privateRelease") {
                storeFile = file(releaseKeystorePath!!)
                require(!releaseKeystorePassword.isNullOrBlank()) { "MM4_KEYSTORE_PASSWORD is required for private release signing." }
                require(!releaseKeyAlias.isNullOrBlank()) { "MM4_KEY_ALIAS is required for private release signing." }
                require(!releaseKeyPassword.isNullOrBlank()) { "MM4_KEY_PASSWORD is required for private release signing." }
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Never silently publish a release APK signed with the development debug key.
            signingConfig = if (hasPrivateReleaseKey) signingConfigs.getByName("privateRelease") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild").configure { dependsOn(downloadAppIcon) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.lz4:lz4-java:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
