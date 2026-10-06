import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val jvmVersion = catalog.findVersion("jvmTarget").get().requiredVersion

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    baseline = rootProject.layout.projectDirectory.file("config/detekt/baseline.xml").asFile
    // Detekt 1.x needs a Boolean value here. Gradle records this configuration input.
    autoCorrect = providers.gradleProperty("detekt.auto-correct").isPresent
}

dependencies {
    add("detektPlugins", catalog.findLibrary("detekt-formatting").get())
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = jvmVersion
    reports {
        sarif.required.set(true)
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget = jvmVersion
}
