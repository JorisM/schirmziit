plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The one place a version is written on Android. release-please rewrites this
// string on release — see release-please-config.json — and versionCode is
// computed from it in buildSrc, so the two cannot drift apart.
val appVersion = "0.3.0" // x-release-please-version

// Where the release key comes from, and what an absent one means, is decided in
// buildSrc/ReleaseSigning.kt where it can be tested. A Gradle property wins over
// the environment, so a local release build can be pointed at the keystore
// without exporting anything into the shell that runs it.
val releaseSigning = releaseSigningOf(
    keystore = (findProperty("sz.keystore") as String?) ?: System.getenv("ANDROID_KEYSTORE_PATH"),
    password = (findProperty("sz.keystorePassword") as String?) ?: System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    alias = (findProperty("sz.keyAlias") as String?) ?: System.getenv("ANDROID_KEY_ALIAS"),
)

android {
    namespace = "ch.jorisda.schirmziit.agent"
    compileSdk = 37

    defaultConfig {
        applicationId = "ch.jorisda.schirmziit.agent"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = file(signing.keystore).also {
                    // The keystore is written out of a secret a step earlier, so
                    // a missing file here means that step failed quietly. Say
                    // which path was tried; a signing failure two tasks later
                    // names neither the file nor the reason.
                    require(it.isFile) { "no keystore at ${it.absolutePath}" }
                }
                storePassword = signing.password
                keyAlias = signing.alias
                // One password, twice: the keystore is a PKCS12, which holds a
                // single password for the store and the key alike.
                keyPassword = signing.password

                // v2 covers every phone this app runs on (v1 is for API < 24,
                // and minSdk is 26). v3 is off by default and switched on here
                // because it is what makes a later key rotation possible at
                // all — without it, this key is the app's identity forever.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Null wherever no keystore was configured, which is AGP's own
            // default and leaves app-release-unsigned.apk exactly as it was.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    sourceSets {
        getByName("main") { kotlin.srcDir("src/main/kotlin") }
        getByName("test") { kotlin.srcDir("src/test/kotlin") }
        getByName("androidTest") { kotlin.srcDir("src/androidTest/kotlin") }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin { jvmToolchain(21) }

// JVM unit tests load the host build of the core (and any other JNA consumer
// ships its natives) from an <os>-<arch>/lib<name> resource directory. CI
// copies it into src/test/resources/linux-x86-64/, but AGP's java-resource
// merge silently drops **/*.so from that output — a .so in resources is
// "a native lib in the wrong pipeline" and belongs in jniLibs — while a mac's
// .dylib sails through. That is why the tests load fine locally and die on CI
// with "Native library (linux-x86-64/libschirmziit_core.so) not found in
// resource path".
//
// JNA's classpath loader reads exactly this layout from jar entries too —
// that is how jna.jar itself ships libjnidispatch — and a jar is an ordinary
// classpath file the resource merge never inspects. So the build re-packages
// the host natives into one and puts it on the unit test classpath.
val testNativeLibs by tasks.registering(Jar::class) {
    archiveFileName.set("schirmziit-test-natives.jar")
    from(layout.projectDirectory.dir("src/test/resources")) {
        include("**/*.so", "**/*.dylib")
    }
}

// Roborazzi is used as a plain library: its Gradle plugin asked AGP for
// `TestedExtension` up to 1.53, which AGP 9 removed. 1.73 no longer does, so
// adopting the plugin is possible again — a separate change, since it moves
// record and verify off these system properties. The library reads them
// directly.
//
//   ./gradlew test                          verify against the committed images
//   ./gradlew test -Precord.snapshots       re-record them, deliberately
tasks.withType<Test>().configureEach {
    // CI prints one line per failure and links an HTML report that is never
    // uploaded, so a red build says "UnsatisfiedLinkError" and nothing about
    // which library or why. Print the whole thing instead.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }

    val recording = project.hasProperty("record.snapshots")
    // Real Compose rendering; the default stub graphics produce blank images.
    systemProperty("robolectric.graphicsMode", "NATIVE")
    systemProperty("roborazzi.test.record", recording.toString())
    systemProperty("roborazzi.test.verify", (!recording).toString())

    // The goldens are read from the filesystem at runtime, not off the
    // classpath, so Gradle saw no input change when one was edited: the task
    // stayed UP-TO-DATE and the comparison never ran — a green gate on an image
    // nothing had looked at. Declaring the directory is what re-runs the check.
    // Not while recording: then it is the output, not the input.
    if (!recording) {
        inputs.dir(layout.projectDirectory.dir("src/test/snapshots"))
            .withPropertyName("roborazziGoldens")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.work)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // JVM tests load the desktop build of the core through JNA.
    testImplementation(libs.jna)
    // The host build copied into src/test/resources by `just android-bindings`
    // (or CI) reaches the tests through this jar, because the merged resource
    // dir above never contains a .so.
    // `files(...)` and not the provider itself: a dependency notation has to be
    // a FileCollection, and this form also carries the task dependency, so the
    // jar is built before the tests run.
    testRuntimeOnly(files(testNativeLibs))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
