plugins {
    alias(libs.plugins.android.application)
    id("textmate.android-compose")
}

android {
    namespace = "dev.textmate.sample"

    defaultConfig {
        applicationId = "dev.textmate.sample"
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        ignoreAssetsPatterns += "benchmark"
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(rootProject.file("shared-assets"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":compose-ui"))

    implementation(libs.androidx.activity.compose)
}
