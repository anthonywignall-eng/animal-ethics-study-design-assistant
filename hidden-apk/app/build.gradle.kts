plugins {
    id("com.android.application")
}

android {
    namespace = "com.hidden.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hidden.launcher"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "0.4.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
