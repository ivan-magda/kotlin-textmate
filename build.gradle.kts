import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Detekt needs AGP/KGP classes on the root classpath to configure Android module tasks.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
}

val detektModules = listOf("core", "compose-ui")
val detektFormattingDep = libs.detekt.formatting
val jvmTargetVersion = libs.versions.jvmTarget.get()

subprojects {
    if (name in detektModules) {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
            baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")
            autoCorrect = providers.gradleProperty("detekt.auto-correct").isPresent
        }

        dependencies {
            "detektPlugins"(detektFormattingDep)
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = jvmTargetVersion
            reports {
                sarif.required.set(true)
                html.required.set(true)
                xml.required.set(false)
                txt.required.set(false)
                md.required.set(false)
            }
        }

        tasks.withType<DetektCreateBaselineTask>().configureEach {
            jvmTarget = jvmTargetVersion
        }
    }
}

tasks.register("detektAll") {
    description = "Runs detekt analysis on all configured modules."
    group = "verification"

    dependsOn(detektModules.map { ":$it:detekt" })
}
