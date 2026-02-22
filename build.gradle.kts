import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.detekt) apply false
}

val detektModules = listOf("core", "compose-ui")
val detektFormattingDep = libs.detekt.formatting

subprojects {
    if (name in detektModules) {
        apply(plugin = "io.gitlab.arturbosch.detekt")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
            baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")
            autoCorrect = true
        }

        dependencies {
            "detektPlugins"(detektFormattingDep)
        }

        tasks.withType<Detekt>().configureEach {
            jvmTarget = "17"
            reports {
                sarif.required.set(true)
                html.required.set(true)
                xml.required.set(false)
                txt.required.set(false)
                md.required.set(false)
            }
        }

        tasks.withType<DetektCreateBaselineTask>().configureEach {
            jvmTarget = "17"
        }
    }
}

tasks.register("detektAll") {
    description = "Runs detekt analysis on all configured modules."
    group = "verification"

    dependsOn(detektModules.map { ":$it:detekt" })
}
