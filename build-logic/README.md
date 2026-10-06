# Build conventions

This included build shares the root version catalog and provides:

- `textmate.base`: reproducible archive timestamps and file ordering on Gradle 8.
- `textmate.jvm`: Kotlin/JVM with the catalog's JDK toolchain.
- `textmate.android-compose`: Kotlin/Compose, the same JDK toolchain, shared Android SDK settings and dependencies. Apply an Android application or library plugin in the module first.
- `textmate.detekt`: shared configuration, baseline, formatting rules, and reports.
- `textmate.publishing`: Dokka, Maven Central publishing, and signing.

Module scripts retain their namespaces, application settings, source directories,
and publication artifact IDs, names, and descriptions. Shared publication metadata
and `VERSION_NAME` stay in the root `gradle.properties`.

Run the functional tests from the repository root:

```sh
./gradlew -p build-logic test
```

The archive test checks byte-for-byte stability after input timestamps change and
configuration-cache reuse. The main project's build and API checks exercise the
JVM and Android conventions together.

The `build-logic/gradle.properties` file belongs to this included build's root;
application subprojects do not have their own properties files. Catalog
`findVersion`/`findLibrary` calls return `Optional`, so their `get()` calls are not
eager reads of Gradle providers. Detekt 1.x's `autoCorrect` Boolean setter requires
reading its property during configuration; Gradle tracks that input.

Development and CI checks use build and configuration caches. The release workflow
uses a fresh runner with cache restoration and build-output caching disabled.
