plugins {
    id("org.jetbrains.kotlin.jvm")
    id("textmate.base")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvmToolchain(catalog.findVersion("jvmTarget").get().requiredVersion.toInt())
}
