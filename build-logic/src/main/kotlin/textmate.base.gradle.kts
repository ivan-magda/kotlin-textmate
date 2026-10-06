plugins {
    base
}

// Set these archive options for Gradle 8. Gradle 9 uses them as the defaults.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
