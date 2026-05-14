plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}

dependencies {
    implementation(libs.joni)
    implementation(libs.gson)
    implementation(libs.jcodings)

    testImplementation(libs.junit)
}
