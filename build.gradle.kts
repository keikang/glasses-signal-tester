plugins { id("com.android.application") version "8.7.3" }
android {
    namespace = "io.github.keikang.glassessignal"
    compileSdk = 35
    defaultConfig { applicationId = "io.github.keikang.glassessignal"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
