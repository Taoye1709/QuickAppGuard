import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.qaguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.qaguard"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    // v0.3 正式签名：CI 环境注入 QA_KEYSTORE_PATH/PASSWORD 时使用专用 keystore（PKCS12），
    // 本地或未配置密钥时退回 debug 签名，保证任何环境都能构建
    signingConfigs {
        create("ci") {
            val ksFile = System.getenv("QA_KEYSTORE_PATH")?.let { File(it) }?.takeIf { it.exists() }
            if (ksFile != null) {
                storeFile = ksFile
                storeType = "PKCS12"
                storePassword = System.getenv("QA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QA_KEYSTORE_ALIAS") ?: "quickappguard"
                keyPassword = System.getenv("QA_KEYSTORE_PASSWORD")
            }
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
            signingConfig =
                if (System.getenv("QA_KEYSTORE_PATH")?.let { File(it) }?.exists() == true) {
                    signingConfigs.getByName("ci")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    buildFeatures {
        // AGP 8 默认关闭 AIDL，Shizuku 用户服务接口需要它
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.annotation:annotation:1.8.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
