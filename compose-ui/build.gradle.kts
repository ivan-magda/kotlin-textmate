plugins {
    alias(libs.plugins.android.library)
    id("textmate.android-compose")
    id("textmate.publishing")
    id("textmate.detekt")
}

kotlin {
    explicitApi()
}

android {
    namespace = "dev.textmate.compose"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "kotlin-textmate-compose")
    pom {
        name.set("KotlinTextMate Compose UI")
        description.set("Jetpack Compose bridge for KotlinTextMate, providing the CodeBlock composable and theming helpers.")
    }
}

dependencies {
    api(project(":core"))
}
