
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.sentry.android.gradle)
}

val sentryDsn = providers.environmentVariable("SENTRY_DSN")
  .orElse(providers.gradleProperty("sentry.dsn"))
  .orElse(providers.gradleProperty("SENTRY_DSN"))
  .getOrElse(
    run {
      val localPropertiesFile = rootProject.file("local.properties")
      if (localPropertiesFile.exists()) {
        val props = Properties()
        localPropertiesFile.inputStream().use { props.load(it) }
        props.getProperty("sentry.dsn") ?: props.getProperty("SENTRY_DSN") ?: ""
      } else {
        ""
      }
    }
  )

android {
  namespace = "com.msahil432.multitool"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.msahil432.multitool"
    minSdk = 35
    targetSdk = 37
    versionCode = providers.gradleProperty("versionCode")
      .orElse(providers.environmentVariable("VERSION_CODE"))
      .map { it.toIntOrNull() }
      .getOrNull() ?: 1
    versionName = providers.gradleProperty("versionName")
      .orElse(providers.environmentVariable("VERSION_NAME"))
      .getOrElse("1.0")

    buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    manifestPlaceholders["sentryDsn"] = sentryDsn

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = providers.environmentVariable("KEYSTORE_PATH")
        .getOrElse("${rootDir}/my-upload-key.jks")
      storeFile = file(keystorePath)
      storePassword = providers.environmentVariable("STORE_PASSWORD").getOrNull()
      keyAlias = providers.environmentVariable("KEY_ALIAS").getOrNull()
      keyPassword = providers.environmentVariable("KEY_PASSWORD").getOrNull()
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "src/main/keepRules/rules.keep"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debug") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE"
      excludes += "META-INF/LICENSE.txt"
      excludes += "META-INF/license.txt"
      excludes += "META-INF/NOTICE"
      excludes += "META-INF/NOTICE.txt"
      excludes += "META-INF/notice.txt"
      excludes += "META-INF/ASL2.0"
      excludes += "META-INF/*.kotlin_module"
    }
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    abortOnError = false
    checkReleaseBuilds = true
    fatal += setOf("HardcodedDebugMode", "ExportedReceiver", "ExportedService", "InsecureBaseConfiguration")
  }
}


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.play.services.location)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.sentry.android)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

sentry {
  // Uploads ProGuard/R8 mapping file automatically on release builds
  // so obfuscated stack traces get de-obfuscated in the Sentry dashboard.
  autoUploadProguardMapping = true

  // Uploads native (NDK) debug symbols if you ever add native code.
  uploadNativeSymbols = false

  // Adds breadcrumbs for clicks, fragment lifecycle, etc. automatically.
  tracingInstrumentation {
    enabled = true
  }

  // Recommended: don't let the plugin phone home telemetry about your build.
  telemetry = false

  // Enable size analysis in sentry
  sizeAnalysis {
    enabled = providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent
  }
}
