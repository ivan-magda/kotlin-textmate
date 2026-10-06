# Build plugins

The `build-logic` build contains plugins for common build settings.
It uses the version catalog in the repository root.

- `textmate.base` sets timestamps and file order for reproducible archives on Gradle 8.
- `textmate.jvm` sets the JDK toolchain for Kotlin/JVM from the version catalog.
- `textmate.android-compose` sets the JDK toolchain, Android SDK settings, and common dependencies for Kotlin and Compose.
- `textmate.detekt` sets the Detekt configuration, baseline, formatting rules, and reports.
- `textmate.publishing` sets the Dokka, Maven Central, and signing configuration.

Before you apply `textmate.android-compose`, apply an Android application plugin or an Android library plugin to the module.

Keep the namespace, application settings, and source directories in the build script for each module.
Set the publication artifact ID, name, and description in the `mavenPublishing` block for each module.
Keep common publication metadata and `VERSION_NAME` in the root `gradle.properties` file.

Use this command to do the functional tests from the repository root:

```sh
./gradlew -p build-logic test
```

The archive test compares files from two builds with different input timestamps.
The files must have the same bytes.
The test also makes sure that Gradle uses the configuration cache again.

Use the build and API checks in the main project to do tests of the JVM and Android plugins together.

The `build-logic/gradle.properties` file sets properties for the included build.
Do not add `gradle.properties` files to application subprojects.

The catalog methods `findVersion` and `findLibrary` return an `Optional` value.
Their `get()` methods return the value inside the `Optional`.
They do not force Gradle to read a provider.

Detekt 1.x needs a Boolean value for `autoCorrect`.
Read this property during configuration.
Gradle records the property as a configuration input.

The development builds and CI checks use the build cache and configuration cache.
The release workflow uses a new runner.
It does not restore cached data or use cached build outputs.
