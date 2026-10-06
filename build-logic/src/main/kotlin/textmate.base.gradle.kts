plugins {
    base
}

// Gradle 8 needs these explicitly; Gradle 9 makes them the defaults.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
