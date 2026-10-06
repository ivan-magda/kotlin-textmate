plugins {
    id("textmate.jvm")
    id("textmate.publishing")
    id("textmate.detekt")
}

// Generate TextMateGrammar.VERSION from VERSION_NAME.
val generateVersionSource by tasks.registering {
    group = "build setup"
    description = "Generates the library version constant from VERSION_NAME."
    // Keep providers local so the action does not capture the Gradle script.
    val generatedVersionDir = layout.buildDirectory.dir("generated/version/kotlin")
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
    coordinates(artifactId = "kotlin-textmate-core")
    pom {
        name.set("KotlinTextMate Core")
        description.set("Kotlin port of vscode-textmate: TextMate grammar tokenizer for syntax highlighting on JVM/Android.")
    }
}

dependencies {
    implementation(libs.joni)
    implementation(libs.gson)
    implementation(libs.jcodings)

    testImplementation(libs.junit)
}
