plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rahul.lilly"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.rahul.lilly"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "2.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Offline wake word ("Lilly") — awaaz phone se bahar nahi jaati, beep nahi aati
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
}
