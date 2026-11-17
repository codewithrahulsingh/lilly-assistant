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
        versionCode = 15
        versionName = "3.6"
        // Sirf phone wale processor: APK chhoti aur install tez
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // Har build ek hi key se sign ho, taaki nayi APK purani ke upar install ho sake
    signingConfigs {
        create("lilly") {
            storeFile = rootProject.file("lilly.keystore")
            storePassword = "lilly-app"
            keyAlias = "lilly"
            keyPassword = "lilly-app"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("lilly") }
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
    testImplementation("junit:junit:4.13.2")
}
