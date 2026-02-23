plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared-assets"))
    }
}

dependencies {
    implementation(libs.joni)
    implementation(libs.gson)
    implementation(libs.jcodings)

    testImplementation(libs.junit)
}
