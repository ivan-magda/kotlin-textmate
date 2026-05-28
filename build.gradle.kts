import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

buildscript {
    // Force patched versions of build-time-only AGP buildscript transitive dependencies (Dependabot alerts).
    // These are build tooling (aapt2/bundletool/apksig/analytics-grpc) — NOT shipped in published core/compose-ui artifacts or the app runtime.
    configurations.classpath {
        resolutionStrategy {
            force(
                "io.netty:netty-buffer:4.1.132.Final",
                "io.netty:netty-codec:4.1.132.Final",
                "io.netty:netty-codec-http:4.1.132.Final",
                "io.netty:netty-codec-http2:4.1.132.Final",
                "io.netty:netty-codec-socks:4.1.132.Final",
                "io.netty:netty-common:4.1.132.Final",
                "io.netty:netty-handler:4.1.132.Final",
                "io.netty:netty-handler-proxy:4.1.132.Final",
                "io.netty:netty-resolver:4.1.132.Final",
                "io.netty:netty-transport:4.1.132.Final",
                "io.netty:netty-transport-native-unix-common:4.1.132.Final",
                "org.bouncycastle:bcprov-jdk18on:1.84",
                "org.bouncycastle:bcpkix-jdk18on:1.84",
                "org.bouncycastle:bcutil-jdk18on:1.84",
                "com.google.protobuf:protobuf-java:3.25.5",
                "com.google.protobuf:protobuf-java-util:3.25.5",
                "commons-io:commons-io:2.15.1", // >=2.14.0 patches CVE-2024-47554; 2.15.1 is the highest already on the classpath (avoids a downgrade)
                "org.apache.commons:commons-compress:1.26.0",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.jdom:jdom2:2.0.6.1",
            )
        }
    }
}

// Detekt needs AGP/KGP classes on the root classpath to configure Android module tasks.
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
