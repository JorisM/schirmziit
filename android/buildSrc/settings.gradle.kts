// buildSrc normally needs no settings file — Gradle synthesises one. This exists
// so `./gradlew -p buildSrc test` is a build in its own right: without it Gradle
// walks up, finds android/settings.gradle.kts, and refuses with "project
// directory is not part of the build". A unit test no gate can run is not a test.
rootProject.name = "buildSrc"
