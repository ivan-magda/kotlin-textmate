// Use the same plugin versions in module scripts and convention plugins.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlinx.benchmark) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

apiValidation {
    ignoredProjects.addAll(listOf("sample-app", "benchmark"))
}

val detektModules = listOf("core", "compose-ui")

tasks.register("detektAll") {
    description = "Runs detekt analysis on all configured modules."
    group = "verification"

    dependsOn(detektModules.map { ":$it:detekt" })
}
