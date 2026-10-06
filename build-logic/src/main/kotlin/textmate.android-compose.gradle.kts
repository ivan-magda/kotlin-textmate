import com.android.build.api.dsl.CommonExtension

plugins {
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("textmate.base")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jvmVersion = catalog.findVersion("jvmTarget").get().requiredVersion.toInt()

kotlin {
    // The toolchain also sets the default JVM target for Kotlin.
    jvmToolchain(jvmVersion)
}

fun configureAndroid() {
    extensions.configure<CommonExtension<*, *, *, *, *, *>>("android") {
        compileSdk = catalog.findVersion("compileSdk").get().requiredVersion.toInt()
        defaultConfig {
            minSdk = catalog.findVersion("minSdk").get().requiredVersion.toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(jvmVersion)
            targetCompatibility = JavaVersion.toVersion(jvmVersion)
        }
        buildFeatures.compose = true
    }

    dependencies {
        add("implementation", catalog.findLibrary("androidx-core-ktx").get())
        add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-ktx").get())
        add("implementation", platform(catalog.findLibrary("androidx-compose-bom").get()))
        add("implementation", catalog.findLibrary("androidx-compose-ui").get())
        add("implementation", catalog.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", catalog.findLibrary("androidx-compose-material3").get())
        add("implementation", catalog.findLibrary("androidx-compose-foundation").get())
        add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
        add("testImplementation", catalog.findLibrary("junit").get())
        add("androidTestImplementation", catalog.findLibrary("androidx-junit").get())
        add("androidTestImplementation", catalog.findLibrary("androidx-espresso-core").get())
    }
}

pluginManager.withPlugin("com.android.library") { configureAndroid() }
pluginManager.withPlugin("com.android.application") { configureAndroid() }
