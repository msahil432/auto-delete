
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  id("io.sentry.android.gradle") version "6.19.0"
}

val sentryDsn: String = run {
  val envDsn = System.getenv("SENTRY_DSN")
  if (!envDsn.isNullOrBlank()) return@run envDsn

  val propDsn = (project.findProperty("sentry.dsn") as? String)
    ?: (project.findProperty("SENTRY_DSN") as? String)
  if (!propDsn.isNullOrBlank()) return@run propDsn

  val localPropertiesFile = rootProject.file("local.properties")
  if (localPropertiesFile.exists()) {
    val props = Properties()
    localPropertiesFile.inputStream().use { stream ->
      props.load(stream)
    }
    props.getProperty("sentry.dsn") ?: props.getProperty("SENTRY_DSN") ?: ""
  } else {
    ""
  }
}

android {
  namespace = "com.msahil432.multitool"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.msahil432.multitool"
    minSdk = 35
    targetSdk = 37
    versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull()
      ?: System.getenv("VERSION_CODE")?.toIntOrNull()
      ?: 1
    versionName = (project.findProperty("versionName") as? String)
      ?: System.getenv("VERSION_NAME")
      ?: "1.0"

    buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    manifestPlaceholders["sentryDsn"] = sentryDsn

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")
      keyPassword = System.getenv("KEY_PASSWORD")
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
