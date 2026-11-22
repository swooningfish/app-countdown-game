import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appName = providers.gradleProperty("app.name").get()

// CI sets VERSION_NAME / VERSION_CODE (see .github/workflows/android.yml); local builds use gradle.properties.
val appVersionName = providers.environmentVariable("VERSION_NAME").orNull?.takeIf { it.isNotBlank() }
    ?: providers.gradleProperty("app.versionName").get()
val appVersionCode = (
    providers.environmentVariable("VERSION_CODE").orNull?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("app.versionCode").get()
    ).toInt()
val appAndroidId = providers.gradleProperty("app.androidId").get()
val appIosBundleId = providers.gradleProperty("app.iosBundleId").get()

/** Writes AppInfo.kt so the app can show its own name, version and IDs. */
abstract class GenerateAppInfo : DefaultTask() {
    @get:Input abstract val values: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val v = values.get()
        val file = outputDir.file("com/xrmx/countdown_game/AppInfo.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Generated from gradle.properties by :composeApp:generateAppInfo. Do not edit.
            |package com.xrmx.countdown_game
            |
            |object AppInfo {
            |    const val NAME = "${v["name"]}"
            |    const val VERSION_NAME = "${v["versionName"]}"
            |    const val VERSION_CODE = ${v["versionCode"]}
            |    const val ANDROID_ID = "${v["androidId"]}"
            |    const val IOS_BUNDLE_ID = "${v["iosBundleId"]}"
            |}
            |""".trimMargin(),
        )
    }
}

val generateAppInfo = tasks.register<GenerateAppInfo>("generateAppInfo") {
    values.putAll(
        mapOf(
            "name" to appName,
            "versionName" to appVersionName,
            "versionCode" to appVersionCode.toString(),
            "androidId" to appAndroidId,
            "iosBundleId" to appIosBundleId,
        ),
    )
    outputDir.set(layout.buildDirectory.dir("generated/appInfo/commonMain/kotlin"))
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Desktop JVM target: runs the tests and the screenshot generator on any PC.
    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonMain {
            kotlin.srcDir(generateAppInfo)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.xrmx.countdown_game.MainKt"
    }
}

// ./gradlew :composeApp:generateScreenshots renders the README screenshots into docs/screenshots.
tasks.register<JavaExec>("generateScreenshots") {
    group = "documentation"
    description = "Renders app screenshots for the README"
    val testCompilation = kotlin.targets.getByName("desktop").compilations.getByName("test")
    classpath = files(testCompilation.output.allOutputs, testCompilation.runtimeDependencyFiles)
    mainClass.set("com.xrmx.countdown_game.ScreenshotsKt")
    args(rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath)
}

compose.resources {
    packageOfResClass = "com.xrmx.countdown_game.resources"
}

/**
 * A stable signing key for CI builds, so an APK from a later run installs over an earlier one.
 * Each CI runner otherwise makes a fresh debug keystore and Android refuses the update
 * (signature mismatch). Set all four variables to use it; see README "Signing key for CI".
 */
data class CountdownSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun countdownSigning(): CountdownSigning? {
    val path = System.getenv("COUNTDOWN_KEYSTORE_PATH")?.takeIf { it.isNotBlank() } ?: return null
    val file = File(path).takeIf { it.isFile } ?: return null
    val storePassword = System.getenv("COUNTDOWN_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() } ?: return null
    val alias = System.getenv("COUNTDOWN_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: return null
    val keyPassword = System.getenv("COUNTDOWN_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: storePassword
    return CountdownSigning(file, storePassword, alias, keyPassword)
}

android {
    namespace = "com.xrmx.countdown_game"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = appAndroidId
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val signing = countdownSigning()
    if (signing != null) {
        signingConfigs {
            create("countdown") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (signing != null) signingConfig = signingConfigs.getByName("countdown")
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}
