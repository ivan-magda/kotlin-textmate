plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

sourceSets.main {
    resources.srcDir(rootProject.file("shared-assets"))
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.benchmark.runtime)
}

benchmark {
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 2
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            param("grammar", "kotlin", "json", "markdown", "javascript")
        }
        register("smoke") {
            warmups = 2
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ms"
            param("grammar", "kotlin", "json", "markdown", "javascript")
        }
    }
    targets {
        register("main")
    }
}
