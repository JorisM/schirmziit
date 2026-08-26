plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ch.jorisda.schirmziit.agent"
    compileSdk = 37

    defaultConfig {
        applicationId = "ch.jorisda.schirmziit.agent"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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

// Roborazzi is used as a plain library: its Gradle plugin (1.53) still asks AGP
// for `TestedExtension`, which AGP 9 removed. The plugin only adds convenience
// tasks, and the library reads these system properties directly.
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
