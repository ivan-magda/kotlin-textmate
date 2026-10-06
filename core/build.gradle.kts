plugins {
    id("textmate.jvm")
    id("textmate.publishing")
    id("textmate.detekt")
}

// Generate TextMateGrammar.VERSION from VERSION_NAME.
val generateVersionSource = tasks.register("generateVersionSource") {
    group = "build setup"
    description = "Writes TextMateGrammar.VERSION from VERSION_NAME."
    // Define providers in this block to prevent a script reference in the task action.
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
