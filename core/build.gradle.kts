plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

// Generate TextMateGrammar.VERSION from VERSION_NAME.
val generatedVersionDir = layout.buildDirectory.dir("generated/version/kotlin")
val generateVersionSource by tasks.registering {
    val version = providers.gradleProperty("VERSION_NAME")
    inputs.property("version", version)
    outputs.dir(generatedVersionDir)
    doLast {
        val pkgDir = generatedVersionDir.get().dir("dev/textmate/grammar").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("TextMateGrammar.kt").writeText(
            """
            |package dev.textmate.grammar
            |
            |/** The KotlinTextMate library version. */
            |public object TextMateGrammar {
            |    public const val VERSION: String = "${version.get()}"
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
    explicitApi()
    sourceSets.named("main") {
        kotlin.srcDir(generateVersionSource)
    }
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
