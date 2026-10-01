import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.agromarket.onaqfq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val envLines = run {
      val envFile = rootProject.file(".env")
      if (envFile.exists()) envFile.readLines() else emptyList<String>()
    }

    val resolvedSupabaseUrl = run {
      var url = System.getenv("SUPABASE_URL") ?: ""
      if (url.isBlank()) {
        envLines.forEach { line ->
          val trimmed = line.trim()
          if (trimmed.startsWith("SUPABASE_URL=")) {
            url = trimmed.substringAfter("SUPABASE_URL=").trim()
          }
        }
      }
      while (url.startsWith("SUPABASE_URL=", ignoreCase = true) || url.startsWith("SUPABASE_URL =", ignoreCase = true)) {
        url = url.substringAfter("=").trim()
      }
      url = url.trim().removeSurrounding("\"").removeSurrounding("'").trim()
      if (url.isBlank() || url.contains("your-project-ref")) "https://wqqvikjsfbcprueivxzn.supabase.co" else url
    }

    val resolvedSupabaseAnonKey = run {
      var key = System.getenv("SUPABASE_ANON_KEY") ?: ""
      if (key.isBlank()) {
        envLines.forEach { line ->
          val trimmed = line.trim()
          if (trimmed.startsWith("SUPABASE_ANON_KEY=")) {
            key = trimmed.substringAfter("SUPABASE_ANON_KEY=").trim()
          }
        }
      }
      while (key.startsWith("SUPABASE_ANON_KEY=", ignoreCase = true) || key.startsWith("SUPABASE_ANON_KEY =", ignoreCase = true)) {
        key = key.substringAfter("=").trim()
      }
      key = key.trim().removeSurrounding("\"").removeSurrounding("'").trim()
      if (key.isBlank() || key == "your_supabase_anon_key_here") "sb_publishable_n2zQAa9SMGD9BrqayYfcVQ_VfwU1ItX" else key
    }

    buildConfigField("String", "SUPABASE_URL", "\"$resolvedSupabaseUrl\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"$resolvedSupabaseAnonKey\"")
    buildConfigField("String", "PAYHERE_MERCHANT_ID", "\"1234567\"")
    buildConfigField("Boolean", "PAYHERE_SANDBOX", "true")
    buildConfigField("String", "PICKME_PACKAGE_ID", "\"com.pickme.passenger\"")
    buildConfigField("String", "PICKME_STORE_URL", "\"https://play.google.com/store/apps/details?id=com.pickme.passenger\"")
    buildConfigField("String", "TEXTLK_BASE_URL", "\"https://app.text.lk/api/v3/\"")
    buildConfigField("String", "TEXTLK_SENDER_ID", "\"TextLKDemo\"")

    val resolvedTextLkToken = run {
      var token = System.getenv("TEXTLK_API_TOKEN") ?: ""
      if (token.isBlank()) {
        envLines.forEach { line ->
          val trimmed = line.trim()
          if (trimmed.startsWith("TEXTLK_API_TOKEN=")) {
            token = trimmed.substringAfter("TEXTLK_API_TOKEN=").trim()
          }
        }
      }
      if (token.isBlank() || token == "your_textlk_api_token_here") {
        token = "7897|083piwmpX8LpfYtv66sPAO9rs1NXVXbAThcUdV3u53de5487"
      }
      token
    }
    buildConfigField("String", "TEXTLK_API_TOKEN", "\"$resolvedTextLkToken\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("TEXTLK_API_TOKEN")
  ignoreList.add("SUPABASE_ANON_KEY")
  ignoreList.add("SUPABASE_URL")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
