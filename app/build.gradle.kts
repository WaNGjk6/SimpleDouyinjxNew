import java.util.Properties

// ---- 发布固定签名：keystore/release.jks + keystore/keystore.properties（整个 keystore/ 已被 .gitignore 忽略）----
// 两者都存在时才启用固定签名；缺失（如他人 clone）自动回退 debug 签名，不影响构建。
val keystoreFile = rootProject.file("keystore/release.jks")
val keystoreProps = Properties().apply {
    val p = rootProject.file("keystore/keystore.properties")
    if (p.exists()) p.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 解析/AI 密钥直接内置于构建、随 APK 分发：自用小圈子项目，friend 装机即用，
// 无需各自配置 local.properties（接受 key 在客户端可见）。

android {
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias = keystoreProps.getProperty("keyAlias", "douyinjiexi")
                keyPassword = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }
    namespace = "top.jk666.douyinjiexi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "top.jk666.douyinjiexi"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "2.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "XHS_API_KEY", "\"Shanhai-mILeTTSnLT2CUwx26XXEMFfPNmXh8ogHCHxXTX3n72gJS95r\"")
        buildConfigField("String", "AI_API_KEY", "\"sk-w2jp18W7Mt0XALoZHBY266dY3WehvgAYylh8bjzbLaM1355o\"")
        buildConfigField("String", "BUGPK_API_KEY", "\"bp_live_49fcf8006a68030bcbca537794b56f44a8ad3f00065309542035f02d26892bdc\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}